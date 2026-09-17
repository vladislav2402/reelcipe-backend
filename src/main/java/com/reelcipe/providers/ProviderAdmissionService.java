package com.reelcipe.providers;

import com.reelcipe.providers.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ProviderAdmissionService {
    private final ProviderBudgetPeriodRepository budgets;
    private final ProviderAdmissionRepository admissions;
    private final ProviderUserFailureRepository failures;
    private final Clock clock;
    private final long dailyBudgetUnits;
    private final int maxConcurrentAttempts;
    private final Duration reservationTtl;
    private final Duration failureWindow;
    private final int maxFailuresPerUser;
    private final Duration userBackoff;

    public ProviderAdmissionService(
            ProviderBudgetPeriodRepository budgets,
            ProviderAdmissionRepository admissions,
            ProviderUserFailureRepository failures,
            Clock clock,
            @Value("${app.providers.admission.daily-budget-units:100000}")
            long dailyBudgetUnits,
            @Value("${app.providers.admission.max-concurrent-attempts:2}")
            int maxConcurrentAttempts,
            @Value("${app.providers.admission.reservation-ttl:PT5M}")
            Duration reservationTtl,
            @Value("${app.providers.admission.failure-window:PT1H}")
            Duration failureWindow,
            @Value("${app.providers.admission.max-failures-per-user:3}")
            int maxFailuresPerUser,
            @Value("${app.providers.admission.user-backoff:PT15M}")
            Duration userBackoff) {
        this.budgets = budgets;
        this.admissions = admissions;
        this.failures = failures;
        this.clock = clock;
        this.dailyBudgetUnits = dailyBudgetUnits;
        this.maxConcurrentAttempts = maxConcurrentAttempts;
        this.reservationTtl = reservationTtl;
        this.failureWindow = failureWindow;
        this.maxFailuresPerUser = maxFailuresPerUser;
        this.userBackoff = userBackoff;
    }

    @Transactional
    public Reservation reserve(Request request) {
        Instant now = clock.instant();
        Period period = period(now);
        budgets.createIfMissing(
                UUID.randomUUID(),
                request.provider(),
                period.start(),
                period.end(),
                dailyBudgetUnits,
                maxConcurrentAttempts,
                now);
        ProviderBudgetPeriod budget = budgets.findByProviderAndPeriodStart(
                        request.provider(), period.start())
                .orElseThrow(() -> new IllegalStateException("Provider budget period was not created"));
        checkUserBackoff(request.userId(), request.provider(), now);
        long estimatedUnits = Math.max(1, request.estimatedUnits());
        if (budget.getConsumedUnits() + budget.getReservedUnits() + estimatedUnits
                > budget.getBudgetUnits()) {
            throw new ProviderAdmissionException(
                    "PROVIDER_BUDGET_EXHAUSTED",
                    budget.getPeriodEnd());
        }
        if (budget.getActiveAttempts() >= budget.getMaxConcurrent()) {
            throw new ProviderAdmissionException(
                    "PROVIDER_CONCURRENCY_LIMIT",
                    now.plusSeconds(30));
        }
        ProviderAdmission admission = new ProviderAdmission(
                UUID.randomUUID(),
                request.attemptId(),
                request.importId(),
                request.userId(),
                request.provider(),
                request.operation(),
                estimatedUnits,
                now.plus(reservationTtl),
                now);
        budget.reserve(estimatedUnits, now);
        budgets.save(budget);
        admissions.save(admission);
        return new Reservation(admission.getId(), estimatedUnits);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConsumeResult consume(UUID admissionId, long actualUnits) {
        if (admissionId == null) {
            return ConsumeResult.notTracked();
        }
        ProviderAdmission admission = admissions.findLockedById(admissionId).orElse(null);
        if (admission == null || admission.getState() != ProviderAdmissionState.RESERVED) {
            return ConsumeResult.notTracked();
        }
        Instant now = clock.instant();
        ProviderBudgetPeriod budget = lockBudget(admission.getProvider(), now);
        long chargedUnits = Math.max(0, actualUnits);
        long spentWithoutReservation = budget.getConsumedUnits()
                + budget.getReservedUnits()
                - admission.getEstimatedUnits();
        if (spentWithoutReservation + chargedUnits > budget.getBudgetUnits()) {
            admission.unknown("PROVIDER_USAGE_OVER_BUDGET", now);
            budget.consumeReservation(
                    admission.getEstimatedUnits(),
                    admission.getEstimatedUnits(),
                    now);
            budgets.save(budget);
            admissions.save(admission);
            recordFailure(admission.getUserId(), admission.getProvider(), now);
            return new ConsumeResult(false, budget.getPeriodEnd());
        }
        admission.consume(chargedUnits, now);
        budget.consumeReservation(admission.getEstimatedUnits(), chargedUnits, now);
        budgets.save(budget);
        admissions.save(admission);
        return new ConsumeResult(true, null);
    }

    @Transactional
    public void unknown(UUID admissionId, String failureCode) {
        if (admissionId == null) {
            return;
        }
        settleUnknown(admissionId, failureCode);
    }

    @Transactional
    public void stale(UUID admissionId) {
        if (admissionId == null) {
            return;
        }
        settleUnknown(admissionId, "PROVIDER_ATTEMPT_STALE");
    }

    @Transactional
    public void recoverExpired() {
        admissions.findExpired(clock.instant())
                .forEach(admission -> settleUnknown(admission.getId(), "PROVIDER_ATTEMPT_EXPIRED"));
    }

    private void settleUnknown(UUID admissionId, String failureCode) {
        ProviderAdmission admission = admissions.findLockedById(admissionId).orElse(null);
        if (admission == null || admission.getState() != ProviderAdmissionState.RESERVED) {
            return;
        }
        Instant now = clock.instant();
        ProviderBudgetPeriod budget = lockBudget(admission.getProvider(), now);
        admission.unknown(failureCode, now);
        budget.consumeReservation(admission.getEstimatedUnits(), admission.getEstimatedUnits(), now);
        budgets.save(budget);
        admissions.save(admission);
        recordFailure(admission.getUserId(), admission.getProvider(), now);
    }

    private void recordFailure(UUID userId, String provider, Instant now) {
        Instant windowStart = failureWindowStart(now);
        failures.createIfMissing(
                UUID.randomUUID(), userId, provider, windowStart, now);
        ProviderUserFailure failure = failures
                .findByUserIdAndProviderAndWindowStart(userId, provider, windowStart)
                .orElseThrow(() -> new IllegalStateException("Provider failure window was not created"));
        failure.record(maxFailuresPerUser, now.plus(userBackoff), now);
        failures.save(failure);
    }

    private void checkUserBackoff(UUID userId, String provider, Instant now) {
        ProviderUserFailure failure = failures
                .findByUserIdAndProviderAndWindowStart(userId, provider, failureWindowStart(now))
                .orElse(null);
        if (failure != null && failure.getBlockedUntil() != null
                && failure.getBlockedUntil().isAfter(now)) {
            throw new ProviderAdmissionException(
                    "PROVIDER_USER_BACKOFF",
                    failure.getBlockedUntil());
        }
    }

    private ProviderBudgetPeriod lockBudget(String provider, Instant now) {
        Period period = period(now);
        budgets.createIfMissing(
                UUID.randomUUID(),
                provider,
                period.start(),
                period.end(),
                dailyBudgetUnits,
                maxConcurrentAttempts,
                now);
        return budgets.findByProviderAndPeriodStart(provider, period.start())
                .orElseThrow(() -> new IllegalStateException("Provider budget period was not created"));
    }

    private Instant failureWindowStart(Instant now) {
        long seconds = Math.max(1, failureWindow.getSeconds());
        long epoch = now.getEpochSecond() / seconds * seconds;
        return Instant.ofEpochSecond(epoch);
    }

    private Period period(Instant now) {
        Instant start = now.atZone(ZoneOffset.UTC).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        return new Period(start, start.plus(1, ChronoUnit.DAYS));
    }

    public record Request(
            UUID attemptId,
            UUID importId,
            UUID userId,
            String provider,
            String operation,
            long estimatedUnits) {
    }

    public record Reservation(UUID id, long estimatedUnits) {
    }

    public record ConsumeResult(boolean accepted, Instant retryAfter) {
        private static ConsumeResult notTracked() {
            return new ConsumeResult(true, null);
        }
    }

    private record Period(Instant start, Instant end) {
    }
}
