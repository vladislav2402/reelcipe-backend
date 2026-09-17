package com.reelcipe.imports.transcription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelcipe.imports.transcription.domain.SpeechStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnExpression(
        "'${app.asr.provider:mock}' == 'groq'"
                + " && '${app.providers.real-calls-enabled:false}' == 'true'")
public class GroqSpeechTranscriber implements SpeechTranscriber {
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxRequestBytes;
    private final Clock clock;

    @Autowired
    public GroqSpeechTranscriber(
            ObjectMapper mapper,
            @Value("${app.providers.adapters.asr.base-url:https://api.groq.com/openai/v1}")
            String baseUrl,
            @Value("${GROQ_API_KEY:}") String apiKey,
            @Value("${app.asr.groq-model:whisper-large-v3-turbo}") String model,
            @Value("${app.providers.http.read-timeout:PT60S}") Duration timeout,
            @Value("${app.providers.http.max-request-bytes:26214400}") int maxRequestBytes,
            Clock clock) {
        this(
                mapper,
                URI.create(baseUrl + "/audio/transcriptions"),
                apiKey,
                model,
                timeout,
                maxRequestBytes,
                clock);
    }

    GroqSpeechTranscriber(
            ObjectMapper mapper,
            URI endpoint,
            String apiKey,
            String model,
            Duration timeout,
            int maxRequestBytes,
            Clock clock) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("GROQ_API_KEY is required when Groq ASR is enabled");
        }
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.maxRequestBytes = maxRequestBytes;
        this.clock = clock;
    }

    @Override
    public Result transcribe(InputStream audio, Request request) {
        try {
            byte[] bytes = readLimited(audio);
            String boundary = "----reelcipe-" + UUID.randomUUID();
            byte[] body = multipart(bytes, boundary, request);
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = client.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseResponse(response);
        } catch (HttpTimeoutException exception) {
            throw new SpeechTranscriptionException(
                    SpeechTranscriptionException.Kind.TIMEOUT,
                    "Groq ASR request timed out",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SpeechTranscriptionException(
                    SpeechTranscriptionException.Kind.TIMEOUT,
                    "Groq ASR request was interrupted",
                    exception);
        } catch (IOException exception) {
            throw new SpeechTranscriptionException(
                    SpeechTranscriptionException.Kind.UNKNOWN,
                    "Groq ASR request failed",
                    exception);
        }
    }

    private Result parseResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 429) {
            throw new SpeechTranscriptionException(
                    SpeechTranscriptionException.Kind.UNKNOWN,
                    "Groq ASR rate limit reached",
                    status,
                    retryAfter(response));
        }
        if (status >= 500) {
            throw new SpeechTranscriptionException(
                    SpeechTranscriptionException.Kind.UNKNOWN,
                    "Groq ASR provider error",
                    status,
                    null);
        }
        if (status < 200 || status >= 300) {
            throw new SpeechTranscriptionException(
                    SpeechTranscriptionException.Kind.INVALID_RESPONSE,
                    "Groq ASR rejected the request",
                    status,
                    null);
        }
        try {
            JsonNode root = mapper.readTree(response.body());
            String language = text(root, "language");
            String requestId = text(root, "id");
            List<Segment> segments = parseSegments(root.path("segments"));
            SpeechStatus speechStatus = segments.isEmpty()
                    ? SpeechStatus.NO_SPEECH
                    : SpeechStatus.SPEECH;
            int durationSeconds = billedSeconds(root, segments);
            return new Result(
                    language == null || language.isBlank() ? "und" : language,
                    speechStatus,
                    segments,
                    new Usage(durationSeconds, durationSeconds),
                    requestId);
        } catch (Exception exception) {
            if (exception instanceof SpeechTranscriptionException speechException) {
                throw speechException;
            }
            throw new SpeechTranscriptionException(
                    SpeechTranscriptionException.Kind.INVALID_RESPONSE,
                    "Groq ASR response is invalid",
                    exception);
        }
    }

    private List<Segment> parseSegments(JsonNode segmentsNode) {
        List<Segment> segments = new ArrayList<>();
        if (!segmentsNode.isArray()) {
            return segments;
        }
        for (JsonNode node : segmentsNode) {
            double start = node.path("start").asDouble(-1);
            double end = node.path("end").asDouble(-1);
            String text = text(node, "text");
            if (start < 0 || end <= start || text == null || text.isBlank()) {
                throw new SpeechTranscriptionException(
                        SpeechTranscriptionException.Kind.INVALID_RESPONSE,
                        "Groq ASR segment is invalid");
            }
            segments.add(new Segment(
                    Math.round(start * 1000),
                    Math.round(end * 1000),
                    text.trim()));
        }
        return List.copyOf(segments);
    }

    private int billedSeconds(JsonNode root, List<Segment> segments) {
        double responseDuration = root.path("duration").asDouble(0);
        double segmentDuration = segments.stream()
                .mapToLong(segment -> segment.endMs())
                .max()
                .orElse(0) / 1000.0;
        return Math.max(1, (int) Math.ceil(Math.max(responseDuration, segmentDuration)));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private byte[] readLimited(InputStream audio) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = audio.read(buffer)) != -1) {
            total += read;
            if (total > maxRequestBytes) {
                throw new SpeechTranscriptionException(
                        SpeechTranscriptionException.Kind.INVALID_RESPONSE,
                        "Audio exceeds Groq ASR request limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private byte[] multipart(byte[] audio, String boundary, Request request) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeField(output, boundary, "model", model);
        writeField(output, boundary, "response_format", "verbose_json");
        writeField(output, boundary, "timestamp_granularities[]", "segment");
        if (request.language() != null && !request.language().isBlank()) {
            writeField(output, boundary, "language", request.language());
        }
        writeBytes(output, boundary, request, audio);
        writeText(output, "--" + boundary + "--\r\n");
        return output.toByteArray();
    }

    private void writeField(ByteArrayOutputStream output, String boundary, String name, String value) {
        writeText(output, "--" + boundary + "\r\n");
        writeText(output, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        writeText(output, value + "\r\n");
    }

    private void writeBytes(
            ByteArrayOutputStream output,
            String boundary,
            Request request,
            byte[] audio) {
        writeText(output, "--" + boundary + "\r\n");
        writeText(output, "Content-Disposition: form-data; name=\"file\"; filename=\""
                + safeFileName(request.fileName()) + "\"\r\n");
        writeText(output, "Content-Type: " + safeContentType(request.contentType()) + "\r\n\r\n");
        output.writeBytes(audio);
        writeText(output, "\r\n");
    }

    private String safeFileName(String fileName) {
        return fileName == null || fileName.isBlank() ? "audio.flac" : fileName.replaceAll("[\\\\\"\r\n]", "_");
    }

    private String safeContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? "audio/flac" : contentType;
    }

    private void writeText(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private Instant retryAfter(HttpResponse<String> response) {
        String value = response.headers().firstValue("Retry-After").orElse(null);
        if (value == null || value.isBlank()) {
            return clock.instant().plusSeconds(30);
        }
        try {
            return clock.instant().plusSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            try {
                return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            } catch (DateTimeParseException ignoredDate) {
                return clock.instant().plusSeconds(30);
            }
        }
    }
}
