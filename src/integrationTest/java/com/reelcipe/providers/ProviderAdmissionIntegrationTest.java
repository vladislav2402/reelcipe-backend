package com.reelcipe.providers;

import com.reelcipe.auth.FixtureUserInitializer;
import com.reelcipe.common.UuidV7;
import com.reelcipe.imports.domain.*;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.role=api",
        "spring.profiles.active=test",
        "app.providers.admission.daily-budget-units=10",
        "app.providers.admission.max-concurrent-attempts=2"
})
@Testcontainers
class ProviderAdmissionIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @Autowired
    ProviderAdmissionService admissions;

    @Autowired
    ImportJobRepository jobs;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void concurrentWorkersCannotReserveTheSameLastBudgetSlot() {
        String provider = "b24-race-" + UuidV7.randomUuid();
        UUID firstImport = createImport();
        UUID secondImport = createImport();
        AtomicInteger success = new AtomicInteger();
        CompletableFuture<?> first = CompletableFuture.runAsync(
                () -> reserve(provider, firstImport, success));
        CompletableFuture<?> second = CompletableFuture.runAsync(
                () -> reserve(provider, secondImport, success));

        CompletableFuture.allOf(first, second).join();

        assertThat(success).hasValue(1);
    }

    private UUID createImport() {
        UUID id = UuidV7.randomUuid();
        Instant now = Instant.now();
        jobs.save(new ImportJob(
                id,
                FixtureUserInitializer.ALICE_ID,
                UuidV7.randomUuid(),
                ImportSourceType.LINK,
                "https://example.com/" + id,
                "b24-" + id,
                ImportStatus.QUEUED,
                ImportStage.RESOLVING,
                now.plusSeconds(3600),
                now.plusSeconds(3600),
                now));
        return id;
    }

    private void reserve(String provider, UUID importId, AtomicInteger success) {
        try {
            admissions.reserve(new ProviderAdmissionService.Request(
                    UuidV7.randomUuid(),
                    importId,
                    FixtureUserInitializer.ALICE_ID,
                    provider,
                    "ASR",
                    6));
            success.incrementAndGet();
        } catch (ProviderAdmissionException ignored) {
            // The second worker must observe the committed reservation.
        }
    }
}
