package com.reelcipe.imports;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {
    @Test
    void usesExponentialBackoffAndCapsTheDelay() {
        RetryPolicy policy = new RetryPolicy(
                Duration.ofSeconds(2), Duration.ofSeconds(10), 0);
        Instant now = Instant.parse("2026-09-15T12:00:00Z");

        assertThat(policy.nextAttemptAt(now, 1)).isEqualTo(now.plusSeconds(2));
        assertThat(policy.nextAttemptAt(now, 2)).isEqualTo(now.plusSeconds(8));
        assertThat(policy.nextAttemptAt(now, 3)).isEqualTo(now.plusSeconds(10));
    }
}
