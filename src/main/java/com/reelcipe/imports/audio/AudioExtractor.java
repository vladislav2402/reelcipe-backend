package com.reelcipe.imports.audio;

import java.nio.file.Path;
import java.time.Duration;

public interface AudioExtractor {
    Probe probe(Path input);

    void extract(Path input, Path output);

    record Probe(
            Duration duration,
            int audioStreams,
            int sampleRate,
            int channels,
            String codec) {
        public boolean hasAudio() {
            return audioStreams > 0;
        }
    }
}
