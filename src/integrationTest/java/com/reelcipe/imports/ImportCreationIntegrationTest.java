package com.reelcipe.imports;

import com.reelcipe.auth.domain.User;
import com.reelcipe.auth.domain.UserRepository;
import com.reelcipe.auth.domain.UserStatus;
import com.reelcipe.billing.QuotaService;
import com.reelcipe.imports.domain.ImportJobRepository;
import com.reelcipe.imports.domain.ImportSourceType;
import com.reelcipe.imports.domain.ImportStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"app.role=api", "spring.profiles.active=test"})
@Testcontainers
class ImportCreationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @Autowired
    private ImportService imports;

    @Autowired
    private ImportJobRepository jobs;

    @Autowired
    private UserRepository users;

    @Autowired
    private QuotaService quota;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void twentyIdenticalRequestsCreateOneJobAndOneReservation() throws Exception {
        UUID userId = createUser();
        UUID clientRequestId = UUID.randomUUID();
        ImportService.ImportCommand command = link(clientRequestId);
        ExecutorService executor = Executors.newFixedThreadPool(20);
        try {
            List<Callable<ImportService.ImportView>> calls = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                calls.add(() -> imports.create(userId, "same-key", command));
            }

            List<ImportService.ImportView> results = executor.invokeAll(calls).stream()
                    .map(ImportCreationIntegrationTest::get)
                    .toList();

            assertThat(results).allMatch(result -> result.status() == ImportStatus.QUEUED);
            assertThat(results.stream().map(ImportService.ImportView::id).distinct()).hasSize(1);
            assertThat(jobs.findByUserIdOrderByCreatedAtDescIdDesc(userId)).hasSize(1);
            assertThat(quota.current(userId).reserved()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void activeImportLimitIsEnforced() {
        UUID userId = createUser();
        imports.create(userId, "key-1", link(UUID.randomUUID()));
        imports.create(userId, "key-2", link(UUID.randomUUID()));

        assertThatThrownBy(() -> imports.create(userId, "key-3", link(UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(409);
    }

    @Test
    void quotaFailureRollsBackCreatedJob() {
        UUID userId = createUser();
        for (int i = 0; i < 10; i++) {
            quota.reserve(userId, UUID.randomUUID());
        }
        UUID clientRequestId = UUID.randomUUID();

        assertThatThrownBy(() -> imports.create(userId, "quota-key", link(clientRequestId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(429);
        assertThat(jobs.findByUserIdAndClientRequestId(userId, clientRequestId)).isEmpty();
    }

    @Test
    void foreignUserCannotReadImport() {
        UUID ownerId = createUser();
        UUID foreignId = createUser();
        ImportService.ImportView created = imports.create(ownerId, "owner-key", link(UUID.randomUUID()));

        assertThatThrownBy(() -> imports.get(foreignId, created.id()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(404);
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        users.saveAndFlush(new User(id, "Import test", UserStatus.ACTIVE, now, now));
        return id;
    }

    private ImportService.ImportCommand link(UUID clientRequestId) {
        return new ImportService.ImportCommand(
                clientRequestId,
                ImportSourceType.LINK,
                "https://example.com/video/" + clientRequestId,
                null,
                null,
                null,
                null,
                "Import test");
    }

    private static ImportService.ImportView get(
            java.util.concurrent.Future<ImportService.ImportView> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError("Concurrent import request failed", exception);
        }
    }
}
