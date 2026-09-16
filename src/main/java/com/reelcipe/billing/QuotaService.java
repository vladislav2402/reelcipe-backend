package com.reelcipe.billing;

import com.reelcipe.billing.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.UUID;

@Service
public class QuotaService {
    private final UsagePeriodRepository periods;
    private final QuotaReservationRepository reservations;
    private final UsageLedgerRepository ledger;
    private final EntitlementService entitlements;
    private final Clock clock;

    public QuotaService(
            UsagePeriodRepository periods,
            QuotaReservationRepository reservations,
            UsageLedgerRepository ledger,
            EntitlementService entitlements,
            Clock clock) {
        this.periods = periods;
        this.reservations = reservations;
        this.ledger = ledger;
        this.entitlements = entitlements;
        this.clock = clock;
    }

    @Transactional
    public QuotaReservation reserve(UUID userId, UUID importId) {
        return reserve(userId, importId, nextGeneration(importId));
    }

    @Transactional
    public QuotaReservation reserveNextGeneration(UUID userId, UUID importId) {
        return reserve(userId, importId, nextGeneration(importId));
    }

    @Transactional
    public QuotaSnapshot current(UUID userId) {
        UsagePeriod period = lockedPeriod(userId, currentPeriod(), entitlements.currentPlan(userId));
        return snapshot(period, entitlements.currentPlan(userId));
    }

    @Transactional
    public QuotaReservation consume(UUID userId, UUID reservationId) {
        return finish(userId, reservationId, true);
    }

    @Transactional
    public QuotaReservation release(UUID userId, UUID reservationId) {
        return finish(userId, reservationId, false);
    }

    @Transactional
    public boolean releaseImport(UUID userId, UUID importId) {
        validateIds(userId, importId);
        QuotaReservation reservation = reservations
                .findTopByImportIdAndStateOrderByGenerationDesc(
                        importId, QuotaReservationState.RESERVED)
                .orElse(null);
        if (reservation == null) {
            return false;
        }
        if (!reservation.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quota reservation not found");
        }
        finish(userId, reservation.getId(), false);
        return true;
    }

    @Transactional
    public boolean consumeImport(UUID userId, UUID importId) {
        validateIds(userId, importId);
        QuotaReservation reservation = reservations
                .findTopByImportIdAndStateOrderByGenerationDesc(
                        importId, QuotaReservationState.RESERVED)
                .orElse(null);
        if (reservation == null) {
            return false;
        }
        if (!reservation.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quota reservation not found");
        }
        finish(userId, reservation.getId(), true);
        return true;
    }

    private QuotaReservation reserve(UUID userId, UUID importId, int generation) {
        validateIds(userId, importId);
        QuotaPlan plan = entitlements.currentPlan(userId);
        LocalDate periodStart = currentPeriod();
        Instant now = clock.instant();
        UsagePeriod period = lockedPeriod(userId, periodStart, plan);
        QuotaReservation latest = reservations.findTopByImportIdOrderByGenerationDesc(importId).orElse(null);
        if (latest != null && latest.getState() == QuotaReservationState.RESERVED) {
            return latest;
        }
        if (latest != null && latest.getState() == QuotaReservationState.CONSUMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Import quota was already consumed");
        }
        if (period.getRemaining() < 1) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Import quota exceeded");
        }
        period.reserve(1, now);
        periods.save(period);
        QuotaReservation reservation = new QuotaReservation(
                UUID.randomUUID(), userId, importId, periodStart, generation, 1, now);
        reservations.save(reservation);
        ledger.save(new UsageLedger(
                UUID.randomUUID(), userId, importId, reservation.getId(), periodStart,
                UsageOperation.RESERVE, 1, now));
        return reservation;
    }

    private QuotaReservation finish(UUID userId, UUID reservationId, boolean consume) {
        if (userId == null || reservationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quota identifiers are required");
        }
        QuotaReservation reservation = reservations.findLocked(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quota reservation not found"));
        if (!reservation.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quota reservation not found");
        }
        if (reservation.getState() != QuotaReservationState.RESERVED) {
            if ((consume && reservation.getState() == QuotaReservationState.CONSUMED)
                    || (!consume && reservation.getState() == QuotaReservationState.RELEASED)) {
                return reservation;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quota reservation is already completed");
        }
        UsagePeriod period = periods.findLocked(userId, reservation.getPeriodStart()).orElseThrow();
        Instant now = clock.instant();
        if (consume) {
            period.consume(reservation.getUnits(), now);
            reservation.consume(now);
        } else {
            period.release(reservation.getUnits(), now);
            reservation.release(now);
        }
        periods.save(period);
        reservations.save(reservation);
        ledger.save(new UsageLedger(
                UUID.randomUUID(), userId, reservation.getImportId(), reservation.getId(),
                reservation.getPeriodStart(), consume ? UsageOperation.CONSUME : UsageOperation.RELEASE,
                reservation.getUnits(), now));
        return reservation;
    }

    private UsagePeriod lockedPeriod(UUID userId, LocalDate periodStart, QuotaPlan plan) {
        periods.ensureExists(UUID.randomUUID(), userId, periodStart, plan.monthlyLimit());
        UsagePeriod period = periods.findLocked(userId, periodStart).orElseThrow();
        period.applyPlanLimit(plan.monthlyLimit(), clock.instant());
        return period;
    }

    private int nextGeneration(UUID importId) {
        return reservations.findTopByImportIdOrderByGenerationDesc(importId)
                .map(reservation -> reservation.getGeneration() + 1)
                .orElse(1);
    }

    private LocalDate currentPeriod() {
        return YearMonth.from(clock.instant().atZone(ZoneOffset.UTC)).atDay(1);
    }

    private QuotaSnapshot snapshot(UsagePeriod period, QuotaPlan plan) {
        return new QuotaSnapshot(
                plan,
                period.getPeriodStart(),
                period.getLimit(),
                period.getReserved(),
                period.getConsumed(),
                period.getRemaining());
    }

    private void validateIds(UUID userId, UUID importId) {
        if (userId == null || importId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quota identifiers are required");
        }
    }

    public record QuotaSnapshot(
            QuotaPlan plan,
            LocalDate periodStart,
            int limit,
            int reserved,
            int consumed,
            int remaining) {
    }
}
