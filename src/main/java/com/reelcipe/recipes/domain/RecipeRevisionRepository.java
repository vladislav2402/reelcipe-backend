package com.reelcipe.recipes.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RecipeRevisionRepository extends JpaRepository<RecipeRevision, UUID> {
}
