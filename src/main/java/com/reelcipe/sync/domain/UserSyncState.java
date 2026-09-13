package com.reelcipe.sync.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sync_state")
public class UserSyncState {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserSyncState() { }

    public UserSyncState(UUID userId) {
        this.userId = userId;
        this.lastSequence = 0;
        this.updatedAt = Instant.now();
    }

    public long increment() {
        lastSequence++;
        updatedAt = Instant.now();
        return lastSequence;
    }

    public long lastSequence() { return lastSequence; }
}
