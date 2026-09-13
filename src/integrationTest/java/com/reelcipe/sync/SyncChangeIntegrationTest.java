package com.reelcipe.sync;

import com.reelcipe.sync.domain.SyncChange;
import com.reelcipe.sync.domain.SyncChangeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.role=api", "spring.profiles.active=test"})
@Testcontainers
class SyncChangeIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private SyncChangeService syncChangeService;

    @Autowired
    private SyncChangeRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentChangesForOneUserHaveGapFreeMonotonicSequences() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<SyncChange>> futures = new ArrayList<>();

        for (int index = 0; index < 2; index++) {
            UUID entityId = UUID.randomUUID();
            futures.add(executor.submit(() -> {
                start.await();
                return syncChangeService.record(userId, "recipe", entityId, "UPDATE", 1);
            }));
        }
        start.countDown();

        List<Long> sequences = List.of(futures.get(0).get().sequence(), futures.get(1).get().sequence());
        executor.shutdown();

        assertThat(sequences).containsExactlyInAnyOrder(1L, 2L);
        assertThat(syncChangeService.captureWatermark(userId)).isEqualTo(2L);
    }

    @Test
    void rollbackRemovesStateAndChange() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> transaction.execute(status -> {
                    syncChangeService.record(userId, "recipe", UUID.randomUUID(), "DELETE", 1);
                    throw new RuntimeException("rollback");
                }))).hasMessage("rollback");

        assertThat(repository.countByUserId(userId)).isZero();
        assertThat(syncChangeService.captureWatermark(userId)).isZero();
    }
}
