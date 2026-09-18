package com.reelcipe.imports.source.tiktok;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reelcipe.imports.ImportProcessingException;
import com.reelcipe.imports.domain.ImportFailure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class RapidApiTikTokResolverProvider implements TikTokResolverProvider {
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final URI baseUrl;
    private final String apiKey;
    private final String apiHost;
    private final Duration timeout;
    private final int maxResponseBytes;

    @Autowired
    public RapidApiTikTokResolverProvider(
            ObjectMapper mapper,
            @Value("${app.import.tiktok.rapidapi-base-url:https://tiktok-downloader-download-tiktok-videos-without-watermark.p.rapidapi.com}")
            String baseUrl,
            @Value("${RAPIDAPI_TIKTOK_API_KEY:}") String apiKey,
            @Value("${RAPIDAPI_TIKTOK_HOST:tiktok-downloader-download-tiktok-videos-without-watermark.p.rapidapi.com}")
            String apiHost,
            @Value("${app.import.tiktok.timeout:PT120S}") Duration timeout,
            @Value("${app.import.tiktok.max-response-bytes:10485760}") int maxResponseBytes) {
        this(mapper, URI.create(baseUrl), apiKey, apiHost, timeout, maxResponseBytes);
    }

    RapidApiTikTokResolverProvider(
            ObjectMapper mapper,
            URI baseUrl,
            String apiKey,
            String apiHost,
            Duration timeout,
            int maxResponseBytes) {
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.apiHost = apiHost;
        this.timeout = timeout;
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public String providerName() {
        return "rapidapi";
    }

    @Override
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public Resolution resolve(URI sourceUrl) {
        if (!configured()) {
            throw new ImportProcessingException(
                    ImportFailure.permanent("RAPIDAPI_NOT_CONFIGURED"));
        }
        try {
            HttpResponse<String> response = request(sourceUrl);
            JsonNode root = parse(response);
            String videoUrl = firstText(root, "video", "data.video", "play", "data.play");
            if (videoUrl == null || videoUrl.isBlank()) {
                throw new ImportProcessingException(
                        ImportFailure.needsInput("SOURCE_UNAVAILABLE"));
            }

            String description = firstText(
                    root,
                    "description",
                    "title",
                    "data.description",
                    "data.title");
            String author = firstText(
                    root,
                    "author",
                    "author.nickname",
                    "data.author",
                    "data.author.nickname");
            return new Resolution(
                    sourceUrl,
                    author,
                    authorUrl(author),
                    null,
                    description == null ? "EMPTY" : "AVAILABLE",
                    "AVAILABLE",
                    "VIDEO_FOR_AUDIO",
                    null,
                    URI.create(videoUrl),
                    null,
                    null);
        } catch (ImportProcessingException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("RAPIDAPI_TIMEOUT"), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ImportProcessingException(
                    ImportFailure.transientError("RAPIDAPI_INTERRUPTED"), exception);
        } catch (IOException | RuntimeException exception) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("RAPIDAPI_REQUEST_FAILED"), exception);
        }
    }

    private HttpResponse<String> request(URI sourceUrl)
            throws IOException, InterruptedException {
        String encodedUrl = URLEncoder.encode(sourceUrl.toString(), StandardCharsets.UTF_8);
        URI endpoint = baseUrl.resolve("/vid/index?url=" + encodedUrl);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("X-RapidAPI-Key", apiKey)
                .header("X-RapidAPI-Host", apiHost)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        byte[] responseBytes = response.body().getBytes(StandardCharsets.UTF_8);
        if (responseBytes.length > maxResponseBytes) {
            throw new ImportProcessingException(
                    ImportFailure.permanent("RAPIDAPI_RESPONSE_TOO_LARGE"));
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new ImportProcessingException(
                    ImportFailure.permanent("RAPIDAPI_UNAUTHORIZED"));
        }
        if (response.statusCode() == 429) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("RAPIDAPI_RATE_LIMITED"));
        }
        if (response.statusCode() == 404 || response.statusCode() == 410) {
            throw new ImportProcessingException(
                    ImportFailure.needsInput("SOURCE_UNAVAILABLE"));
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("RAPIDAPI_HTTP_" + response.statusCode()));
        }
        return response;
    }

    private JsonNode parse(HttpResponse<String> response) {
        try {
            return mapper.readTree(response.body());
        } catch (IOException exception) {
            throw new ImportProcessingException(
                    ImportFailure.permanent("RAPIDAPI_RESPONSE_INVALID"), exception);
        }
    }

    private String firstText(JsonNode root, String... fields) {
        for (String field : fields) {
            String value = text(root, field);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root;
        for (String part : field.split("\\.")) {
            value = value == null ? null : value.get(part);
            if (value != null && value.isArray() && !value.isEmpty()) {
                value = value.get(0);
            }
        }
        return value == null || value.isNull() || !value.isValueNode()
                ? null
                : value.asText();
    }

    private URI authorUrl(String author) {
        if (author == null || author.isBlank() || author.contains("/")) {
            return null;
        }
        return URI.create("https://www.tiktok.com/@" + author.replaceFirst("^@", ""));
    }

    private URI trimTrailingSlash(URI value) {
        String text = value.toString();
        return text.endsWith("/")
                ? URI.create(text.substring(0, text.length() - 1))
                : value;
    }
}
