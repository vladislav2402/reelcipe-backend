package com.reelcipe.shopping.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, UUID> {
    List<ShoppingItem> findByListIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID listId);

    Optional<ShoppingItem> findByIdAndListIdAndDeletedAtIsNull(UUID id, UUID listId);
}
