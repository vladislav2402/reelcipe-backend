package com.reelcipe.imports;

import com.reelcipe.imports.domain.ImportLease;
import com.reelcipe.imports.domain.ImportStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportWorkerTest {
    @Mock
    private ImportQueue queue;

    @Mock
    private ImportLeasePersistence persistence;

    @Test
    void handlerRunsOnExecutorAndHeartbeatCanFenceIt() throws Exception {
        UUID importId = UUID.randomUUID();
        ImportLease lease = new ImportLease(
                importId,
                UUID.randomUUID(),
                "worker-a",
                1,
                1,
                ImportStage.RESOLVING,
                Instant.now().plusSeconds(90));
        CountDownLatch started = new CountDownLatch(1);
        ImportLeaseControl[] control = new ImportLeaseControl[1];
        ImportStageHandler handler = (claimedLease, leaseControl) -> {
            control[0] = leaseControl;
            started.countDown();
            await();
        };
        when(queue.claimNext("worker-a")).thenReturn(Optional.of(lease), Optional.empty());
        when(persistence.heartbeat(lease)).thenReturn(false);

        ImportWorker worker = new ImportWorker(
                queue,
                persistence,
                provider(handler),
                "worker-a",
                1);
        try {
            worker.poll();
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

            worker.heartbeat();

            assertThat(control[0].isValid()).isFalse();
            verify(persistence).heartbeat(lease);
        } finally {
            worker.shutdown();
        }
    }

    private ObjectProvider<ImportStageHandler> provider(ImportStageHandler handler) {
        return new ObjectProvider<>() {
            @Override
            public ImportStageHandler getIfAvailable() {
                return handler;
            }
        };
    }

    private void await() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
