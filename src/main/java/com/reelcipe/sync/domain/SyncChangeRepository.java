package com.reelcipe.sync.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SyncChangeRepository extends JpaRepository<SyncChange, UUID> {

    long countByUserId(UUID userId);
}
