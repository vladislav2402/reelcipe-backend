package com.reelcipe.shopping.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {
    Optional<ShoppingList> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT list FROM ShoppingList list WHERE list.userId = :userId")
    Optional<ShoppingList> findLockedByUserId(UUID userId);
}
