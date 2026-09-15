package com.reelcipe.imports.transcription;

import com.reelcipe.imports.transcription.domain.SpeechStatus;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockSpeechTranscriberTest {
    private final MockSpeechTranscriber transcriber = new MockSpeechTranscriber();

    @Test
    void returnsDeterministicTranscriptOnlyForKnownFixture() {
        SpeechTranscriber.Result result = transcriber.transcribe(
                new ByteArrayInputStream(MockSpeechTranscriber.recipeFixture()),
                new SpeechTranscriber.Request("fixture-hash", 4));

        assertThat(result.speechStatus()).isEqualTo(SpeechStatus.SPEECH);
        assertThat(result.language()).isEqualTo("en");
        assertThat(result.segments()).hasSize(2);
        assertThat(result.segments().getFirst().startMs()).isZero();
        assertThat(result.segments().get(1).endMs()).isEqualTo(3600);
    }

    @Test
    void returnsNoSpeechForExplicitNoSpeechFixture() {
        SpeechTranscriber.Result result = transcriber.transcribe(
                new ByteArrayInputStream(MockSpeechTranscriber.noSpeechFixture()),
                new SpeechTranscriber.Request("fixture-hash", 4));

        assertThat(result.speechStatus()).isEqualTo(SpeechStatus.NO_SPEECH);
        assertThat(result.segments()).isEmpty();
    }

    @Test
    void doesNotInventTranscriptForUnknownInput() {
        assertThatThrownBy(() -> transcriber.transcribe(
                new ByteArrayInputStream("other-audio".getBytes()),
                new SpeechTranscriber.Request("other-hash", 4)))
                .isInstanceOf(SpeechTranscriptionException.class)
                .extracting(exception -> ((SpeechTranscriptionException) exception).kind())
                .isEqualTo(SpeechTranscriptionException.Kind.UNKNOWN);
    }
}
