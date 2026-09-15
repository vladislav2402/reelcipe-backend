package com.reelcipe.imports.transcription;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TranscriptionConfiguration {
    public TranscriptionConfiguration(
            @Value("${app.asr.provider:mock}") String provider) {
        if (provider.isBlank()) {
            throw new IllegalArgumentException("ASR provider is required");
        }
    }
}
