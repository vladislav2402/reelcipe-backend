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

public interface ProviderUserFailureRepository extends JpaRepository<ProviderUserFailure, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO provider_user_failures
                (id, user_id, provider, window_start, failure_count, blocked_until, updated_at)
            VALUES (:id, :userId, :provider, :windowStart, 0, NULL, :now)
            ON CONFLICT (user_id, provider, window_start) DO NOTHING
            """, nativeQuery = true)
    int createIfMissing(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("provider") String provider,
            @Param("windowStart") Instant windowStart,
            @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProviderUserFailure> findByUserIdAndProviderAndWindowStart(
            UUID userId,
            String provider,
            Instant windowStart);
}
