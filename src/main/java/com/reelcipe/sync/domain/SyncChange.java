package com.reelcipe.sync.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_changes")
public class SyncChange {

    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "entity_type", nullable = false)
    private String entityType;
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;
    @Column(nullable = false)
    private String operation;
    @Column(nullable = false)
    private long version;
    @Column(name = "sequence", nullable = false)
    private long sequence;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SyncChange() {
    }

    public SyncChange(UUID id, UUID userId, String entityType, UUID entityId, String operation,
                      long version, long sequence, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.operation = operation;
        this.version = version;
        this.sequence = sequence;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String entityType() {
        return entityType;
    }

    public UUID entityId() {
        return entityId;
    }

    public String operation() {
        return operation;
    }

    public long version() {
        return version;
    }

    public long sequence() {
        return sequence;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
