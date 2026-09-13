package com.reelcipe.sync.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface UserSyncStateRepository extends JpaRepository<UserSyncState, UUID> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO user_sync_state (user_id, last_sequence)
            VALUES (:userId, 0)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int ensureExists(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT state FROM UserSyncState state WHERE state.userId = :userId")
    Optional<UserSyncState> findLocked(@Param("userId") UUID userId);

}
