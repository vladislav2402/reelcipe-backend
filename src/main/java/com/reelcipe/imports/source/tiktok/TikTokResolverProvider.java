package com.reelcipe.imports.source.tiktok;

import java.net.URI;

public interface TikTokResolverProvider {
    default String providerName() {
        return "custom";
    }

    default boolean configured() {
        return true;
    }

    Resolution resolve(URI sourceUrl);

    record Resolution(
            URI canonicalUrl,
            String authorName,
            URI authorUrl,
            String authorDescription,
            String descriptionStatus,
            String audioStatus,
            String mediaKind,
            URI audioUrl,
            URI videoUrl,
            String transcript,
            String transcriptLanguage) {
        public Resolution(
                URI canonicalUrl,
                String authorName,
                URI authorUrl,
                String authorDescription,
                String descriptionStatus,
                String audioStatus,
                String mediaKind,
                URI audioUrl,
                URI videoUrl) {
            this(
                    canonicalUrl,
                    authorName,
                    authorUrl,
                    authorDescription,
                    descriptionStatus,
                    audioStatus,
                    mediaKind,
                    audioUrl,
                    videoUrl,
                    null,
                    null);
        }
    }
}
