package com.reelcipe.imports.audio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class AudioExtractorConfiguration {
    @Bean
    @ConditionalOnProperty(
            name = "app.media-tools.provider",
            havingValue = "compose",
            matchIfMissing = true)
    AudioExtractor composeAudioExtractor(
            ObjectMapper objectMapper,
            @Value("${app.media-tools.compose-command:docker}") String command,
            @Value("${app.media-tools.compose-env-file:infra/.env.local}") String envFile,
            @Value("${app.media-tools.compose-file:infra/compose.local.yml}") String composeFile,
            @Value("${app.media-tools.compose-service:media-tools}") String service,
            @Value("${app.media-tools.work-directory:${user.dir}/.local/media-work}")
            String workDirectory,
            @Value("${app.media-tools.timeout:PT2M}") Duration timeout,
            @Value("${app.media-tools.max-output-bytes:65536}") int maxOutputBytes) {
        List<String> prefix = List.of(
                command,
                "compose",
                "--env-file",
                envFile,
                "-f",
                composeFile,
                "exec",
                "-T",
                service);
        Path hostWorkDirectory = Path.of(workDirectory).toAbsolutePath().normalize();
        return new CommandAudioExtractor(
                append(prefix, "ffprobe"),
                append(prefix, "ffmpeg"),
                true,
                hostWorkDirectory,
                objectMapper,
                timeout,
                maxOutputBytes);
    }

    @Bean
    @ConditionalOnProperty(name = "app.media-tools.provider", havingValue = "binaries")
    AudioExtractor binaryAudioExtractor(
            ObjectMapper objectMapper,
            @Value("${app.media-tools.ffprobe-command:ffprobe}") String ffprobe,
            @Value("${app.media-tools.ffmpeg-command:ffmpeg}") String ffmpeg,
            @Value("${app.media-tools.work-directory:${user.dir}/.local/media-work}")
            String workDirectory,
            @Value("${app.media-tools.timeout:PT2M}") Duration timeout,
            @Value("${app.media-tools.max-output-bytes:65536}") int maxOutputBytes) {
        return new CommandAudioExtractor(
                List.of(ffprobe),
                List.of(ffmpeg),
                false,
                Path.of(workDirectory),
                objectMapper,
                timeout,
                maxOutputBytes);
    }

    private List<String> append(List<String> prefix, String command) {
        return java.util.stream.Stream.concat(prefix.stream(), java.util.stream.Stream.of(command)).toList();
    }
}
