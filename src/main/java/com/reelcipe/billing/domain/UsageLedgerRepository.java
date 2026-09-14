package com.reelcipe.billing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UsageLedgerRepository extends JpaRepository<UsageLedger, UUID> {
    boolean existsByReservationIdAndOperation(UUID reservationId, UsageOperation operation);
}
