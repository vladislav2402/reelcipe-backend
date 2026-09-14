package com.reelcipe.shopping;

import com.reelcipe.auth.FixtureUserInitializer;
import com.reelcipe.shopping.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.role=api", "spring.profiles.active=test"})
@Testcontainers
class ShoppingRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired
    ShoppingListRepository lists;
    @Autowired
    ShoppingItemRepository items;
    @Autowired
    ShoppingItemSourceRepository sources;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void repositoriesPersistShoppingSnapshotAndSource() {
        UUID listId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        UUID ingredientId = UUID.randomUUID();
        UUID additionId = UUID.randomUUID();
        Instant now = Instant.now();
        lists.save(new ShoppingList(listId, FixtureUserInitializer.ALICE_ID, now));
        items.save(new ShoppingItem(itemId, listId, "Flour", null, "g", now));
        sources.save(new ShoppingItemSource(
                UUID.randomUUID(), listId, itemId, recipeId, 1L, ingredientId, additionId));

        assertThat(lists.findByUserId(FixtureUserInitializer.ALICE_ID)).isPresent();
        assertThat(items.findByListIdAndDeletedAtIsNullOrderByCreatedAtAsc(listId)).hasSize(1);
        assertThat(sources.findByListIdAndAdditionId(listId, additionId)).extracting("itemId")
                .containsExactly(itemId);
    }
}
