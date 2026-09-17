package com.reelcipe.providers;

import com.reelcipe.providers.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderAdmissionServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");

    @Mock
    ProviderBudgetPeriodRepository budgets;
    @Mock
    ProviderAdmissionRepository admissions;
    @Mock
    ProviderUserFailureRepository failures;

    @Test
    void rejectsTheLastBudgetSlotAtomically() {
        ProviderBudgetPeriod budget = budget(10, 2);
        when(budgets.findByProviderAndPeriodStart(anyString(), any())).thenReturn(Optional.of(budget));
        when(failures.findByUserIdAndProviderAndWindowStart(any(), anyString(), any()))
                .thenReturn(Optional.empty());
        ProviderAdmissionService service = service(10, 2);

        service.reserve(request(6));

        assertThatThrownBy(() -> service.reserve(request(5)))
                .isInstanceOf(ProviderAdmissionException.class)
                .extracting(exception -> ((ProviderAdmissionException) exception).failure().errorCode())
                .isEqualTo("PROVIDER_BUDGET_EXHAUSTED");
        assertThat(budget.getReservedUnits()).isEqualTo(6);
    }

    @Test
    void unknownAttemptConsumesReservationAndBlocksRepeatedFailures() {
        ProviderBudgetPeriod budget = budget(100, 2);
        when(budgets.findByProviderAndPeriodStart(anyString(), any())).thenReturn(Optional.of(budget));
        when(failures.findByUserIdAndProviderAndWindowStart(any(), anyString(), any()))
                .thenReturn(Optional.empty());
        ProviderAdmissionService service = service(100, 2);
        ProviderAdmissionService.Reservation reservation = service.reserve(request(10));
        ProviderAdmission admission = new ProviderAdmission(
                reservation.id(),
                request(10).attemptId(),
                request(10).importId(),
                request(10).userId(),
                "stub",
                "ASR",
                10,
                NOW.plusSeconds(300),
                NOW);
        when(admissions.findLockedById(reservation.id())).thenReturn(Optional.of(admission));
        ProviderUserFailure failure = new ProviderUserFailure(
                UUID.randomUUID(),
                request(10).userId(),
                "stub",
                NOW.minusSeconds(3600),
                NOW);
        when(failures.findByUserIdAndProviderAndWindowStart(any(), anyString(), any()))
                .thenReturn(Optional.of(failure));

        service.unknown(reservation.id(), "HTTP_503");

        assertThat(budget.getConsumedUnits()).isEqualTo(10);
        assertThat(budget.getReservedUnits()).isZero();
        assertThat(admission.getState()).isEqualTo(ProviderAdmissionState.UNKNOWN);
    }

    private ProviderAdmissionService service(long budget, int concurrency) {
        return new ProviderAdmissionService(
                budgets,
                admissions,
                failures,
                Clock.fixed(NOW, ZoneOffset.UTC),
                budget,
                concurrency,
                Duration.ofMinutes(5),
                Duration.ofHours(1),
                3,
                Duration.ofMinutes(15));
    }

    private ProviderBudgetPeriod budget(long units, int concurrency) {
        return new ProviderBudgetPeriod(
                UUID.randomUUID(),
                "stub",
                NOW.truncatedTo(java.time.temporal.ChronoUnit.DAYS),
                NOW.plusSeconds(12 * 3600),
                units,
                concurrency,
                NOW);
    }

    private ProviderAdmissionService.Request request(long units) {
        return new ProviderAdmissionService.Request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "stub",
                "ASR",
                units);
    }
}
