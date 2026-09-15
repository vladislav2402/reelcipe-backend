package com.reelcipe.imports.audio;

import java.util.UUID;

public record AudioExtractionResult(Outcome outcome, UUID audioAssetId) {
    public enum Outcome {
        AUDIO_READY,
        DESCRIPTION_ONLY
    }

    public static AudioExtractionResult audioReady(UUID audioAssetId) {
        return new AudioExtractionResult(Outcome.AUDIO_READY, audioAssetId);
    }

    public static AudioExtractionResult descriptionOnly() {
        return new AudioExtractionResult(Outcome.DESCRIPTION_ONLY, null);
    }
}
