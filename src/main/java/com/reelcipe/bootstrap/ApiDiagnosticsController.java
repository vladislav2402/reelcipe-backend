package com.reelcipe.bootstrap;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.role", havingValue = "api")
@Profile("!production")
public class ApiDiagnosticsController {

    private final Environment environment;
    private final String instanceId;
    private final int maxMediaDurationSeconds;
    private final long maxVideoSizeBytes;
    private final long maxAudioSizeBytes;
    private final int maxDescriptionCharacters;
    private final int maxActiveImportsPerUser;
    private final boolean shareExtension;
    private final boolean appleSignIn;
    private final boolean subscriptions;
    private final String enabledLinkPlatforms;

    public ApiDiagnosticsController(
            Environment environment,
            @Value("${app.instance-id:local-api}") String instanceId,
            @Value("${app.limits.max-media-duration-seconds:180}") int maxMediaDurationSeconds,
            @Value("${app.limits.max-video-size-bytes:104857600}") long maxVideoSizeBytes,
            @Value("${app.limits.max-audio-size-bytes:20971520}") long maxAudioSizeBytes,
            @Value("${app.limits.max-description-characters:20000}") int maxDescriptionCharacters,
            @Value("${app.limits.max-active-imports-per-user:2}") int maxActiveImportsPerUser,
            @Value("${app.features.share-extension:false}") boolean shareExtension,
            @Value("${app.features.apple-sign-in:false}") boolean appleSignIn,
            @Value("${app.features.subscriptions:false}") boolean subscriptions,
            @Value("${app.features.enabled-link-platforms:}") String enabledLinkPlatforms) {
        this.environment = environment;
        this.instanceId = instanceId;
        this.maxMediaDurationSeconds = maxMediaDurationSeconds;
        this.maxVideoSizeBytes = maxVideoSizeBytes;
        this.maxAudioSizeBytes = maxAudioSizeBytes;
        this.maxDescriptionCharacters = maxDescriptionCharacters;
        this.maxActiveImportsPerUser = maxActiveImportsPerUser;
        this.shareExtension = shareExtension;
        this.appleSignIn = appleSignIn;
        this.subscriptions = subscriptions;
        this.enabledLinkPlatforms = enabledLinkPlatforms;
    }

    @GetMapping("/v1/config")
    public Map<String, Object> config() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("application", "reelcipe-backend");
        response.put("role", "api");
        response.put("profile", activeProfile());
        response.put("instanceId", instanceId);
        response.put("providersMode", environment.getProperty("app.providers.mode", "mock"));
        response.put("limits", Map.of(
                "maxMediaDurationSeconds", maxMediaDurationSeconds,
                "maxVideoSizeBytes", maxVideoSizeBytes,
                "maxAudioSizeBytes", maxAudioSizeBytes,
                "maxDescriptionCharacters", maxDescriptionCharacters,
                "maxActiveImportsPerUser", maxActiveImportsPerUser));
        response.put("featureFlags", Map.of(
                "shareExtension", shareExtension,
                "appleSignIn", appleSignIn,
                "subscriptions", subscriptions,
                "enabledLinkPlatforms", enabledLinkPlatforms.isBlank()
                        ? java.util.List.of()
                        : java.util.Arrays.stream(enabledLinkPlatforms.split(","))
                                .map(String::trim)
                                .filter(value -> !value.isBlank())
                                .toList()));
        return response;
    }

    private String activeProfile() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(",", profiles);
    }
}
