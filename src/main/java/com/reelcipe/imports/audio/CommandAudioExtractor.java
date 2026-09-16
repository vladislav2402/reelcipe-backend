package com.reelcipe.imports.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public final class CommandAudioExtractor implements AudioExtractor {
    private final List<String> ffprobePrefix;
    private final List<String> ffmpegPrefix;
    private final boolean containerPaths;
    private final Path hostWorkDirectory;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final int maxOutputBytes;

    public CommandAudioExtractor(
            List<String> ffprobePrefix,
            List<String> ffmpegPrefix,
            boolean containerPaths,
            Path hostWorkDirectory,
            ObjectMapper objectMapper,
            Duration timeout,
            int maxOutputBytes) {
        this.ffprobePrefix = List.copyOf(ffprobePrefix);
        this.ffmpegPrefix = List.copyOf(ffmpegPrefix);
        this.containerPaths = containerPaths;
        this.hostWorkDirectory = hostWorkDirectory.toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        this.timeout = timeout;
        this.maxOutputBytes = maxOutputBytes;
    }

    @Override
    public Probe probe(Path input) {
        List<String> command = new ArrayList<>(ffprobePrefix);
        command.addAll(List.of(
                "-v", "error",
                "-show_entries",
                "format=duration:stream=codec_type,codec_name,sample_rate,channels",
                "-of", "json",
                toolPath(input)));
        String output = run(command, AudioExtractionException.Kind.INVALID_MEDIA);
        try {
            JsonNode root = objectMapper.readTree(output);
            JsonNode format = root.path("format");
            double seconds = format.path("duration").asDouble(Double.NaN);
            if (!Double.isFinite(seconds) || seconds < 0) {
                throw invalid("Media duration is unavailable");
            }
            int audioStreams = 0;
            int sampleRate = 0;
            int channels = 0;
            String codec = null;
            for (JsonNode stream : root.path("streams")) {
                if (!"audio".equals(stream.path("codec_type").asText())) {
                    continue;
                }
                audioStreams++;
                if (codec == null) {
                    codec = stream.path("codec_name").asText(null);
                    sampleRate = stream.path("sample_rate").asInt(0);
                    channels = stream.path("channels").asInt(0);
                }
            }
            return new Probe(
                    Duration.ofMillis((long) Math.ceil(seconds * 1000)),
                    audioStreams,
                    sampleRate,
                    channels,
                    codec);
        } catch (AudioExtractionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("Unable to parse ffprobe output", exception);
        }
    }

    @Override
    public void extract(Path input, Path output) {
        List<String> command = new ArrayList<>(ffmpegPrefix);
        command.addAll(List.of(
                "-v", "error",
                "-nostdin",
                "-y",
                "-i", toolPath(input),
                "-map_metadata", "0",
                "-map", "0:a:0",
                "-vn",
                "-ac", "1",
                "-ar", "16000",
                "-c:a", "flac",
                toolPath(output)));
        run(command, AudioExtractionException.Kind.INVALID_MEDIA);
        if (!Files.isRegularFile(output)) {
            throw new AudioExtractionException(
                    AudioExtractionException.Kind.OUTPUT_INVALID,
                    "FFmpeg did not create an output file");
        }
    }

    private String toolPath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!containerPaths) {
            return normalized.toString();
        }
        if (!normalized.startsWith(hostWorkDirectory)) {
            throw new IllegalArgumentException("Media file is outside the mounted work directory");
        }
        return "/work/" + hostWorkDirectory.relativize(normalized)
                .toString()
                .replace('\\', '/');
    }

    private String run(List<String> command, AudioExtractionException.Kind failureKind) {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException exception) {
            throw new AudioExtractionException(
                    AudioExtractionException.Kind.TOOL_UNAVAILABLE,
                    "Unable to start media tool",
                    exception);
        }

        ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
        Future<byte[]> output = readerExecutor.submit(() -> readBounded(process));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new AudioExtractionException(
                        AudioExtractionException.Kind.TIMEOUT,
                        "Media tool timed out");
            }
            byte[] bytes = output.get(2, TimeUnit.SECONDS);
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new AudioExtractionException(failureKind, text);
            }
            return text;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new AudioExtractionException(
                    AudioExtractionException.Kind.TIMEOUT,
                    "Media tool was interrupted",
                    exception);
        } catch (TimeoutException exception) {
            process.destroyForcibly();
            throw new AudioExtractionException(
                    AudioExtractionException.Kind.TIMEOUT,
                    "Media tool output reader timed out",
                    exception);
        } catch (ExecutionException exception) {
            process.destroyForcibly();
            throw new AudioExtractionException(
                    AudioExtractionException.Kind.INVALID_MEDIA,
                    "Media tool output exceeded the configured limit",
                    exception.getCause());
        } finally {
            readerExecutor.shutdownNow();
        }
    }

    private byte[] readBounded(Process process) throws IOException {
        try (var input = process.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxOutputBytes) {
                    throw new IOException("Media tool output is too large");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private AudioExtractionException invalid(String message) {
        return new AudioExtractionException(
                AudioExtractionException.Kind.INVALID_MEDIA, message);
    }

    private AudioExtractionException invalid(String message, Throwable cause) {
        return new AudioExtractionException(
                AudioExtractionException.Kind.INVALID_MEDIA, message, cause);
    }
}
