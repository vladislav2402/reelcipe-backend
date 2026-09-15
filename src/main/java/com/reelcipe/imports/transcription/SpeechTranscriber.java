package com.reelcipe.imports.transcription;

import com.reelcipe.imports.transcription.domain.SpeechStatus;

import java.io.InputStream;
import java.util.List;

public interface SpeechTranscriber {
    Result transcribe(InputStream audio, Request request);

    record Request(String inputHash, int durationSeconds) {
    }

    record Result(
            String language,
            SpeechStatus speechStatus,
            List<Segment> segments,
            Usage usage) {
        public Result {
            segments = List.copyOf(segments);
        }
    }

    record Segment(long startMs, long endMs, String text) {
    }

    record Usage(int inputSeconds, int units) {
    }
}
