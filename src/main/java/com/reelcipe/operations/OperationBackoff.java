package com.reelcipe.operations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class OperationBackoff {
    private final Duration initialDelay;
    private final Duration maximumDelay;
    private final double jitterRatio;

    public OperationBackoff(
            @Value("${app.worker.operations-retry-initial-delay:PT2S}") Duration initialDelay,
            @Value("${app.worker.operations-retry-maximum-delay:PT15M}") Duration maximumDelay,
            @Value("${app.worker.operations-retry-jitter-ratio:0.2}") double jitterRatio) {
        this.initialDelay = initialDelay;
        this.maximumDelay = maximumDelay;
        this.jitterRatio = jitterRatio;
    }

    public Instant nextAttemptAt(Instant now, int attemptNumber) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("Attempt number must be positive");
        }
        long initialMillis = initialDelay.toMillis();
        long maximumMillis = maximumDelay.toMillis();
        long maximumMultiplier = Math.max(1L, maximumMillis / initialMillis);
        long multiplier = 1L;
        for (int index = 1; index < attemptNumber; index++) {
            long nextMultiplier = multiplier > Long.MAX_VALUE / 4
                    ? Long.MAX_VALUE
                    : multiplier * 4L;
            multiplier = Math.min(nextMultiplier, maximumMultiplier);
        }
        long baseMillis = Math.min(initialMillis * multiplier, maximumMillis);
        double jitter = jitterRatio == 0
                ? 0
                : ThreadLocalRandom.current().nextDouble(-jitterRatio, jitterRatio);
        long delayMillis = Math.max(1L, Math.round(baseMillis * (1 + jitter)));
        return now.plusMillis(Math.min(delayMillis, maximumDelay.toMillis()));
    }
}
