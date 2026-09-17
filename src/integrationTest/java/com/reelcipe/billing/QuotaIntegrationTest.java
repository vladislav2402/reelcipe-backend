package com.reelcipe.billing;

import com.reelcipe.auth.FixtureUserInitializer;
import com.reelcipe.billing.domain.QuotaReservationState;
import com.reelcipe.common.UuidV7;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.role=api", "spring.profiles.active=test"})
@Testcontainers
class QuotaIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired QuotaService quota;

    @Test
    void concurrentReservationsCannotExceedFreeLimit() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(11);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                calls.add(() -> {
                    try {
                        quota.reserve(FixtureUserInitializer.ALICE_ID, UuidV7.randomUuid());
                        return true;
                    } catch (Exception exception) {
                        return false;
                    }
                });
            }

            long successful = executor.invokeAll(calls).stream()
                    .filter(future -> get(future))
                    .count();

            assertThat(successful).isEqualTo(10);
            assertThat(quota.current(FixtureUserInitializer.ALICE_ID).reserved()).isEqualTo(10);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void consumeAndReleaseAreDifferentAndSettlementIsOneTime() {
        UUID importId = UuidV7.randomUuid();
        var reservation = quota.reserve(FixtureUserInitializer.BOB_ID, importId);

        quota.consume(FixtureUserInitializer.BOB_ID, reservation.getId());
        var snapshot = quota.current(FixtureUserInitializer.BOB_ID);

        assertThat(snapshot.consumed()).isEqualTo(1);
        assertThat(snapshot.reserved()).isZero();
        assertThat(reservation.getState()).isEqualTo(QuotaReservationState.RESERVED);
        quota.consume(FixtureUserInitializer.BOB_ID, reservation.getId());
        assertThat(quota.current(FixtureUserInitializer.BOB_ID).consumed()).isEqualTo(1);
    }

    private static boolean get(java.util.concurrent.Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            return false;
        }
    }
}
