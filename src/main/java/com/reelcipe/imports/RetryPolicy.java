package com.reelcipe.imports;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RetryPolicy {
    private final Duration initialDelay;
    private final Duration maximumDelay;
    private final double jitterRatio;

    public RetryPolicy(
            @Value("${app.worker.retry-initial-delay:PT2S}") Duration initialDelay,
            @Value("${app.worker.retry-maximum-delay:PT15M}") Duration maximumDelay,
            @Value("${app.worker.retry-jitter-ratio:0.2}") double jitterRatio) {
        this.initialDelay = initialDelay;
        this.maximumDelay = maximumDelay;
        this.jitterRatio = jitterRatio;
    }

    public Instant nextAttemptAt(Instant now, int attemptNumber) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("Attempt number must be positive");
        }
        long multiplier = 1;
        for (int i = 1; i < attemptNumber; i++) {
            multiplier = Math.min(multiplier * 4, Long.MAX_VALUE / 4);
        }
        long baseMillis = Math.min(
                initialDelay.toMillis() * multiplier,
                maximumDelay.toMillis());
        double jitter = jitterRatio == 0 ? 0 : ThreadLocalRandom.current().nextDouble(-jitterRatio, jitterRatio);
        long delayedMillis = Math.max(1, (long) (baseMillis * (1 + jitter)));
        return now.plusMillis(Math.min(delayedMillis, maximumDelay.toMillis()));
    }
}
