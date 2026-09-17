package com.reelcipe.billing;

import com.reelcipe.billing.domain.*;
import com.reelcipe.common.UuidV7;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {
    @Mock UsagePeriodRepository periods;
    @Mock QuotaReservationRepository reservations;
    @Mock UsageLedgerRepository ledger;
    @Mock EntitlementService entitlements;

    @Test
    void reserveCreatesReservationAndLedgerEntry() {
        Instant now = Instant.parse("2026-09-14T12:00:00Z");
        QuotaService service = new QuotaService(
                periods,
                reservations,
                ledger,
                entitlements,
                Clock.fixed(now, ZoneOffset.UTC));
        UUID userId = UuidV7.randomUuid();
        UUID importId = UuidV7.randomUuid();
        UsagePeriod period = new UsagePeriod(
                UuidV7.randomUuid(), userId, java.time.LocalDate.of(2026, 9, 1), 10, now);
        when(entitlements.currentPlan(userId)).thenReturn(QuotaPlan.FREE);
        when(periods.findLocked(userId, period.getPeriodStart())).thenReturn(Optional.of(period));
        when(reservations.findTopByImportIdOrderByGenerationDesc(importId)).thenReturn(Optional.empty());

        QuotaReservation result = service.reserve(userId, importId);

        assertEquals(1, period.getReserved());
        assertEquals(1, result.getUnits());
        verify(reservations).save(any(QuotaReservation.class));
        verify(ledger).save(any());
    }

    @Test
    void currentQuotaUsesConfiguredPlanAndUtcMonth() {
        Instant now = Instant.parse("2026-09-30T23:30:00Z");
        QuotaService service = new QuotaService(
                periods,
                reservations,
                ledger,
                entitlements,
                Clock.fixed(now, ZoneOffset.UTC));
        UUID userId = UuidV7.randomUuid();
        UsagePeriod period = new UsagePeriod(
                UuidV7.randomUuid(), userId, java.time.LocalDate.of(2026, 9, 1), 100, now);
        when(entitlements.currentPlan(userId)).thenReturn(QuotaPlan.PRO);
        when(periods.findLocked(userId, period.getPeriodStart())).thenReturn(Optional.of(period));

        QuotaService.QuotaSnapshot snapshot = service.current(userId);

        assertEquals(QuotaPlan.PRO, snapshot.plan());
        assertEquals(java.time.LocalDate.of(2026, 9, 1), snapshot.periodStart());
        assertEquals(100, snapshot.limit());
    }
}
