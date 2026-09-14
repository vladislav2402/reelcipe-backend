package com.reelcipe.shopping.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shopping_items")
public class ShoppingItem {
    @Id
    private UUID id;
    @Column(name = "list_id")
    private UUID listId;
    private String name;
    private BigDecimal amount;
    private String unit;
    @Column(name = "checked")
    private boolean checked;
    private long version;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ShoppingItem() {
    }

    public ShoppingItem(UUID id, UUID listId, String name, BigDecimal amount, String unit, Instant now) {
        this.id = id;
        this.listId = listId;
        this.name = name;
        this.amount = amount;
        this.unit = unit;
        this.checked = false;
        this.version = 1;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, BigDecimal amount, String unit, Boolean checked, Instant now) {
        if (name != null) {
            this.name = name;
        }
        if (amount != null) {
            this.amount = amount;
        }
        if (unit != null) {
            this.unit = unit;
        }
        if (checked != null) {
            this.checked = checked;
        }
        version++;
        updatedAt = now;
    }

    public void delete(Instant now) {
        deletedAt = now;
        version++;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getListId() {
        return listId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getUnit() {
        return unit;
    }

    public boolean isChecked() {
        return checked;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
