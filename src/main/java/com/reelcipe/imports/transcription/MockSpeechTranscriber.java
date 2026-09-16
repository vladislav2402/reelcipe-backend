package com.reelcipe.imports.transcription;

import com.reelcipe.imports.transcription.domain.SpeechStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@ConditionalOnProperty(
        name = "app.asr.provider",
        havingValue = "mock",
        matchIfMissing = true)
public class MockSpeechTranscriber implements SpeechTranscriber {
    private static final byte[] RECIPE_FIXTURE =
            "reelcipe-b20-fixture:recipe-audio-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NO_SPEECH_FIXTURE =
            "reelcipe-b20-fixture:no-speech-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RECIPE_MARKER =
            "reelcipe-b20-fixture:recipe-audio-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] B23_RECIPE_MARKER =
            "reelcipe-b23-fixture:recipe-video-v1".getBytes(StandardCharsets.UTF_8);

    public static byte[] recipeFixture() {
        return RECIPE_FIXTURE.clone();
    }

    public static byte[] noSpeechFixture() {
        return NO_SPEECH_FIXTURE.clone();
    }

    @Override
    public Result transcribe(InputStream audio, Request request) {
        byte[] bytes = read(audio);
        if (java.util.Arrays.equals(bytes, RECIPE_FIXTURE)
                || contains(bytes, RECIPE_MARKER)
                || contains(bytes, B23_RECIPE_MARKER)) {
            return new Result(
                    "en",
                    SpeechStatus.SPEECH,
                    List.of(
                            new Segment(0, 1800, "Add pasta to boiling water."),
                            new Segment(1800, 3600, "Season with salt and serve.")),
                    new Usage(request.durationSeconds(), 2));
        }
        if (java.util.Arrays.equals(bytes, NO_SPEECH_FIXTURE)) {
            return new Result(
                    "und",
                    SpeechStatus.NO_SPEECH,
                    List.of(),
                    new Usage(request.durationSeconds(), 0));
        }
        throw new SpeechTranscriptionException(
                SpeechTranscriptionException.Kind.UNKNOWN,
                "Mock ASR fixture is not recognized");
    }

    private byte[] read(InputStream input) {
        try (InputStream source = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            source.transferTo(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new SpeechTranscriptionException(
                    SpeechTranscriptionException.Kind.UNKNOWN,
                    "Mock ASR input could not be read",
                    exception);
        }
    }

    private boolean contains(byte[] source, byte[] target) {
        for (int start = 0; start <= source.length - target.length; start++) {
            boolean matches = true;
            for (int offset = 0; offset < target.length; offset++) {
                if (source[start + offset] != target[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }
}
