package com.reelcipe.shopping.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShoppingItemSourceRepository extends JpaRepository<ShoppingItemSource, UUID> {
    List<ShoppingItemSource> findByListIdAndAdditionId(UUID listId, UUID additionId);
}
