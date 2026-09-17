package com.reelcipe.bootstrap;

import com.reelcipe.common.UuidV7;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InstanceIdConfiguration {

    @Bean("instanceId")
    String instanceId(@Value("${app.instance-id:}") String configuredInstanceId) {
        return configuredInstanceId == null || configuredInstanceId.isBlank()
                ? UuidV7.randomUuid().toString()
                : configuredInstanceId.trim();
    }
}
