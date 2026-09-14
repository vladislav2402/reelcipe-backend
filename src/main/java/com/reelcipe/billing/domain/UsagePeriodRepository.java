package com.reelcipe.billing.domain;

import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface UsagePeriodRepository extends JpaRepository<UsagePeriod, UUID> {
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO usage_periods (id, user_id, period_start, quota_limit, reserved, consumed, created_at, updated_at)
            VALUES (:id, :userId, :periodStart, :quotaLimit, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, period_start) DO NOTHING
            """, nativeQuery = true)
    void ensureExists(UUID id, UUID userId, LocalDate periodStart, int quotaLimit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT period FROM UsagePeriod period WHERE period.userId = :userId AND period.periodStart = :periodStart")
    Optional<UsagePeriod> findLocked(UUID userId, LocalDate periodStart);
}
