package com.reelcipe.imports.source;

import java.net.URI;
import java.nio.file.Path;

public record DownloadedSource(
        URI canonicalUrl,
        Path file,
        long sizeBytes,
        String contentType,
        String sha256,
        SourceVariant variant) {
}
