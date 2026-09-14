package com.reelcipe.shopping.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shopping_lists")
public class ShoppingList {
    @Id
    private UUID id;
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ShoppingList() {
    }

    public ShoppingList(UUID id, UUID userId, Instant now) {
        this.id = id;
        this.userId = userId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch(Instant now) {
        updatedAt = now;
    }
}
