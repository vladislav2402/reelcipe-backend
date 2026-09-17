package com.reelcipe.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstanceIdConfigurationTest {

    private final InstanceIdConfiguration configuration = new InstanceIdConfiguration();

    @Test
    void createsVersionSevenInstanceIdByDefault() {
        UUID instanceId = UUID.fromString(configuration.instanceId(""));

        assertEquals(7, instanceId.version());
    }

    @Test
    void keepsConfiguredInstanceId() {
        assertEquals("worker-a", configuration.instanceId(" worker-a "));
    }
}
