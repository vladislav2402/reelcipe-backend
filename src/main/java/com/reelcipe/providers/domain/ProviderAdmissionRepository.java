package com.reelcipe.providers.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderAdmissionRepository extends JpaRepository<ProviderAdmission, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProviderAdmission> findLockedById(UUID id);

    @Query("""
            select admission from ProviderAdmission admission
            where admission.state = com.reelcipe.providers.domain.ProviderAdmissionState.RESERVED
              and admission.expiresAt < :now
            """)
    List<ProviderAdmission> findExpired(Instant now);
}
