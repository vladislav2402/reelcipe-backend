package com.reelcipe.imports.source;

import java.net.URI;

public record SourceMetadata(
        URI canonicalUrl,
        String authorDescription,
        String userText,
        SourceAvailability availability,
        DescriptionAvailability descriptionAvailability) {
}
