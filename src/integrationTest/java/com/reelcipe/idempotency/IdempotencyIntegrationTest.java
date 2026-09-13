package com.reelcipe.idempotency;

import com.reelcipe.idempotency.domain.IdempotencyResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.role=api", "spring.profiles.active=test"})
@Testcontainers
class IdempotencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private IdempotencyService idempotencyService;

    @Test
    void twentyConcurrentCallsProduceOneCommandEffect() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        AtomicInteger commandExecutions = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Future<IdempotencyResult>> results = new ArrayList<>();

        for (int index = 0; index < 20; index++) {
            results.add(executor.submit(() -> {
                start.await();
                return idempotencyService.execute(
                        userId, "test.command", "test-target", "same-key", "{\"value\":1}",
                        () -> {
                            commandExecutions.incrementAndGet();
                            return new IdempotencyResult(201, "{\"id\":\"one\"}", "application/json");
                        });
            }));
        }
        start.countDown();

        List<IdempotencyResult> completed = new ArrayList<>();
        for (Future<IdempotencyResult> result : results) {
            completed.add(result.get());
        }
        executor.shutdown();

        assertThat(commandExecutions).hasValue(1);
        assertThat(completed).containsOnly(new IdempotencyResult(201, "{\"id\":\"one\"}", "application/json"));
    }
}
