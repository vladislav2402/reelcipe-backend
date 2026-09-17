package com.reelcipe.providers.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProviderBudgetPeriodRepository extends JpaRepository<ProviderBudgetPeriod, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO provider_budget_periods
                (id, provider, period_start, period_end, budget_units,
                 reserved_units, consumed_units, active_attempts, max_concurrent,
                 created_at, updated_at)
            VALUES (:id, :provider, :periodStart, :periodEnd, :budgetUnits,
                    0, 0, 0, :maxConcurrent, :now, :now)
            ON CONFLICT (provider, period_start) DO NOTHING
            """, nativeQuery = true)
    int createIfMissing(
            @Param("id") UUID id,
            @Param("provider") String provider,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd,
            @Param("budgetUnits") long budgetUnits,
            @Param("maxConcurrent") int maxConcurrent,
            @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProviderBudgetPeriod> findByProviderAndPeriodStart(
            String provider,
            Instant periodStart);
}
