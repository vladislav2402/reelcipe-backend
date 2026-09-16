package com.reelcipe.imports.recipe;

import com.reelcipe.imports.domain.AudioOutcome;
import com.reelcipe.imports.transcription.domain.SpeechStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public record RecipeTextSnapshot(
        UUID transcriptionId,
        int transcriptionVersion,
        String transcriptHash,
        List<TranscriptSegment> transcriptSegments,
        String authorDescription,
        String userText,
        AudioOutcome audioOutcome,
        SpeechStatus speechStatus,
        String sourceLanguage,
        String targetLanguage,
        String promptVersion,
        String schemaVersion,
        String pipelineVersion,
        String provider,
        String model) {

    public RecipeTextSnapshot {
        transcriptSegments = transcriptSegments == null
                ? List.of()
                : List.copyOf(transcriptSegments);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void append(StringBuilder target, Object value) {
        String text = value == null ? "<null>" : value.toString();
        target.append(text.length()).append(':').append(text).append('|');
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public boolean hasTranscript() {
        return transcriptSegments.stream().anyMatch(segment -> !segment.text().isBlank());
    }

    public boolean hasDescription() {
        return hasText(authorDescription) || hasText(userText);
    }

    public boolean hasUsableText() {
        return hasTranscript() || hasDescription();
    }

    public String sourceCoverage() {
        if (hasTranscript() && hasDescription()) {
            return "AUDIO_AND_DESCRIPTION";
        }
        if (hasTranscript()) {
            return "AUDIO_ONLY";
        }
        return "DESCRIPTION_ONLY";
    }

    public String inputHash() {
        StringBuilder canonical = new StringBuilder();
        append(canonical, transcriptionId);
        append(canonical, transcriptionVersion);
        append(canonical, transcriptHash);
        append(canonical, authorDescription);
        append(canonical, userText);
        append(canonical, audioOutcome);
        append(canonical, speechStatus);
        append(canonical, sourceLanguage);
        append(canonical, targetLanguage);
        append(canonical, promptVersion);
        append(canonical, schemaVersion);
        append(canonical, pipelineVersion);
        append(canonical, provider);
        append(canonical, model);
        for (TranscriptSegment segment : transcriptSegments) {
            append(canonical, segment.id());
            append(canonical, segment.index());
            append(canonical, segment.startMs());
            append(canonical, segment.endMs());
            append(canonical, segment.text());
        }
        return sha256(canonical.toString());
    }

    public record TranscriptSegment(
            UUID id,
            int index,
            long startMs,
            long endMs,
            String text) {
    }
}
