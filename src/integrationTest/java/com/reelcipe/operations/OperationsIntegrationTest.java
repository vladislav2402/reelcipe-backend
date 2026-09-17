package com.reelcipe.operations;

import com.reelcipe.common.UuidV7;
import com.reelcipe.operations.deletion.DeletionDispatcher;
import com.reelcipe.operations.deletion.DeletionHandler;
import com.reelcipe.operations.deletion.DeletionLeaseService;
import com.reelcipe.operations.deletion.DeletionTaskService;
import com.reelcipe.operations.deletion.domain.DeletionStatus;
import com.reelcipe.operations.deletion.domain.DeletionTask;
import com.reelcipe.operations.deletion.domain.DeletionTaskRepository;
import com.reelcipe.operations.outbox.OutboxConsumer;
import com.reelcipe.operations.outbox.OutboxDispatcher;
import com.reelcipe.operations.outbox.OutboxLeaseService;
import com.reelcipe.operations.outbox.OutboxService;
import com.reelcipe.operations.outbox.domain.OperationOutbox;
import com.reelcipe.operations.outbox.domain.OperationOutboxRepository;
import com.reelcipe.operations.outbox.domain.OutboxStatus;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.role=worker",
        "spring.profiles.active=test",
        "app.worker.enabled=false",
        "app.worker.clock-skew-tolerance=PT5S",
        "app.worker.operations-retry-jitter-ratio=0",
        "app.worker.operations-retry-initial-delay=PT30S",
        "app.worker.operations-max-attempts=2"
})
@Testcontainers
class OperationsIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @Autowired
    private OutboxService outbox;

    @Autowired
    private OutboxLeaseService outboxLeases;

    @Autowired
    private OperationOutboxRepository outboxes;

    @Autowired
    private DeletionTaskService deletionTasks;

    @Autowired
    private DeletionLeaseService deletionLeases;

    @Autowired
    private DeletionTaskRepository deletions;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void committedOutboxIsDeliveredOnceAfterDispatcherRestart() {
        UUID eventId = outbox.enqueue(
                "TEST_EVENT",
                "TEST",
                UuidV7.randomUuid(),
                "test-" + UuidV7.randomUuid(),
                "{\"value\":1}");
        AtomicInteger calls = new AtomicInteger();
        OutboxConsumer consumer = new TestConsumer("TEST_EVENT", calls, false);
        OutboxDispatcher dispatcher = new OutboxDispatcher(
                outboxLeases,
                List.of(consumer),
                "test-worker",
                20);

        assertThat(dispatcher.dispatchOnce("test-worker:outbox")).isTrue();
        assertThat(dispatcher.dispatchOnce("test-worker:outbox")).isFalse();
        assertThat(calls).hasValue(1);
        assertThat(outboxes.findById(eventId).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.DELIVERED);
    }

    @Test
    void concurrentDispatchersDoNotRunOneEventTwice() throws Exception {
        outbox.enqueue(
                "CONCURRENT_EVENT",
                "TEST",
                UuidV7.randomUuid(),
                "concurrent-" + UuidV7.randomUuid(),
                "{}");
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        OutboxConsumer consumer = new BlockingConsumer(calls, handlerStarted, releaseHandler);
        OutboxDispatcher first = new OutboxDispatcher(
                outboxLeases,
                List.of(consumer),
                "worker-a",
                20);
        OutboxDispatcher second = new OutboxDispatcher(
                outboxLeases,
                List.of(consumer),
                "worker-b",
                20);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var firstResult = executor.submit(() -> first.dispatchOnce("worker-a:outbox"));
            assertThat(handlerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(second.dispatchOnce("worker-b:outbox")).isFalse();
            releaseHandler.countDown();
            assertThat(firstResult.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(1);
        } finally {
            releaseHandler.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void rolledBackTransactionDoesNotPublishOutboxEvent() {
        String deduplicationKey = "rollback-" + UuidV7.randomUuid();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            outbox.enqueue(
                    "ROLLED_BACK_EVENT",
                    "TEST",
                    UuidV7.randomUuid(),
                    deduplicationKey,
                    "{}");
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outboxes.findByEventTypeAndDeduplicationKey(
                "ROLLED_BACK_EVENT", deduplicationKey)).isEmpty();
    }

    @Test
    void failedConsumerSchedulesRetryWithoutHoldingLease() {
        UUID eventId = outbox.enqueue(
                "RETRY_EVENT",
                "TEST",
                UuidV7.randomUuid(),
                "retry-" + UuidV7.randomUuid(),
                "{}");
        OutboxConsumer consumer = new TestConsumer("RETRY_EVENT", new AtomicInteger(), true);
        OutboxDispatcher dispatcher = new OutboxDispatcher(
                outboxLeases,
                List.of(consumer),
                "test-worker",
                20);

        dispatcher.dispatchOnce("test-worker:outbox");

        OperationOutbox event = outboxes.findById(eventId).orElseThrow();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.RETRY_WAIT);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLeaseOwner()).isNull();
        assertThat(event.getNextAttemptAt()).isNotNull();
    }

    @Test
    void deletionTaskKeepsResourceReferenceUntilHandlerCompletes() {
        String resourceKey = "bucket/user/file-" + UuidV7.randomUuid();
        UUID taskId = deletionTasks.enqueue(
                UuidV7.randomUuid(),
                "OBJECT",
                resourceKey,
                "object-" + UuidV7.randomUuid());
        AtomicInteger calls = new AtomicInteger();
        DeletionHandler handler = new TestDeletionHandler(resourceKey, calls);
        DeletionDispatcher dispatcher = new DeletionDispatcher(
                deletionLeases,
                List.of(handler),
                "test-worker",
                20);

        assertThat(dispatcher.dispatchOnce("test-worker:deletion")).isTrue();
        DeletionTask task = deletions.findById(taskId).orElseThrow();
        assertThat(task.getResourceKey()).isEqualTo(resourceKey);
        assertThat(task.getStatus()).isEqualTo(DeletionStatus.COMPLETED);
        assertThat(calls).hasValue(1);
    }

    private static final class TestConsumer implements OutboxConsumer {
        private final String eventType;
        private final AtomicInteger calls;
        private final boolean fail;

        private TestConsumer(String eventType, AtomicInteger calls, boolean fail) {
            this.eventType = eventType;
            this.calls = calls;
            this.fail = fail;
        }

        @Override
        public boolean supports(String candidate) {
            return eventType.equals(candidate);
        }

        @Override
        public void consume(OperationOutbox event) {
            calls.incrementAndGet();
            if (fail) {
                throw new IllegalStateException("temporary outbox failure");
            }
        }
    }

    private static final class BlockingConsumer implements OutboxConsumer {
        private final AtomicInteger calls;
        private final CountDownLatch started;
        private final CountDownLatch release;

        private BlockingConsumer(
                AtomicInteger calls,
                CountDownLatch started,
                CountDownLatch release) {
            this.calls = calls;
            this.started = started;
            this.release = release;
        }

        @Override
        public boolean supports(String eventType) {
            return "CONCURRENT_EVENT".equals(eventType);
        }

        @Override
        public void consume(OperationOutbox event) {
            calls.incrementAndGet();
            started.countDown();
            try {
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Test handler was interrupted", exception);
            }
        }
    }

    private static final class TestDeletionHandler implements DeletionHandler {
        private final String expectedResourceKey;
        private final AtomicInteger calls;

        private TestDeletionHandler(String expectedResourceKey, AtomicInteger calls) {
            this.expectedResourceKey = expectedResourceKey;
            this.calls = calls;
        }

        @Override
        public boolean supports(String resourceType) {
            return "OBJECT".equals(resourceType);
        }

        @Override
        public void delete(DeletionTask task) {
            assertThat(task.getResourceKey()).isEqualTo(expectedResourceKey);
            calls.incrementAndGet();
        }
    }
}
