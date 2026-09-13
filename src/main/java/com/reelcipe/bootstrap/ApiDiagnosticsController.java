package com.reelcipe.bootstrap;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.role", havingValue = "api")
public class ApiDiagnosticsController {

    private final Environment environment;
    private final String instanceId;

    public ApiDiagnosticsController(
            Environment environment,
            @Value("${app.instance-id:local-api}") String instanceId) {
        this.environment = environment;
        this.instanceId = instanceId;
    }

    @GetMapping("/v1/config")
    public Map<String, String> config() {
        return Map.of(
                "application", "reelcipe-backend",
                "role", "api",
                "profile", activeProfile(),
                "instanceId", instanceId,
                "providersMode", environment.getProperty("app.providers.mode", "mock"));
    }

    private String activeProfile() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(",", profiles);
    }
}
