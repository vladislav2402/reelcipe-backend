package com.reelcipe.imports.source;

import java.net.URI;

public record SourceMetadata(
        URI canonicalUrl,
        String platform,
        String authorName,
        String authorUrl,
        String authorDescription,
        String userText,
        SourceAvailability availability,
        DescriptionAvailability descriptionAvailability,
        AudioAvailability audioAvailability,
        String transcript,
        String transcriptLanguage,
        String transcriptProvider) {
}
