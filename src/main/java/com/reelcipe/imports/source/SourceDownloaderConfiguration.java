package com.reelcipe.imports.source;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class SourceDownloaderConfiguration {
    @Bean
    public ManagedHttpDownloader managedHttpDownloader(
            @Value("${app.providers.http.connect-timeout:PT5S}") Duration connectTimeout,
            @Value("${app.import.source.read-timeout:PT60S}") Duration readTimeout,
            @Value("${app.import.source.max-bytes:104857600}") long maxBytes,
            @Value("${app.import.source.max-redirects:3}") int maxRedirects,
            @Value("${app.import.source.temp-directory:${java.io.tmpdir}/reelcipe-source}")
            String tempDirectory,
            @Value("${app.import.source.allow-local-addresses:false}") boolean allowLocalAddresses,
            @Value("${app.import.source.allow-non-standard-ports:false}")
            boolean allowNonStandardPorts) {
        return new ManagedHttpDownloader(
                new UrlSafetyPolicy(
                        InetAddress::getAllByName,
                        allowLocalAddresses,
                        allowNonStandardPorts),
                connectTimeout,
                readTimeout,
                maxBytes,
                maxRedirects,
                Path.of(tempDirectory));
    }
}
