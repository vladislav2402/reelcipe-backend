package com.reelcipe.billing.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface QuotaReservationRepository extends JpaRepository<QuotaReservation, UUID> {
    Optional<QuotaReservation> findTopByImportIdOrderByGenerationDesc(UUID importId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QuotaReservation> findTopByImportIdAndStateOrderByGenerationDesc(UUID importId,
            QuotaReservationState state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT reservation FROM QuotaReservation reservation WHERE reservation.id = :id")
    Optional<QuotaReservation> findLocked(UUID id);
}
