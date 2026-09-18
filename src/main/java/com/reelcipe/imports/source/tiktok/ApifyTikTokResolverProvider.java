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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class ApifyTikTokResolverProvider implements TikTokResolverProvider {
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final URI apiBaseUrl;
    private final String apiToken;
    private final String actorId;
    private final Duration timeout;
    private final int maxResponseBytes;

    @Autowired
    public ApifyTikTokResolverProvider(
            ObjectMapper mapper,
            @Value("${app.import.tiktok.apify-api-base-url:https://api.apify.com/v2}")
            String apiBaseUrl,
            @Value("${APIFY_API_TOKEN:}") String apiToken,
            @Value("${APIFY_TIKTOK_ACTOR_ID:lurkapi~tiktok-scraper-all-in-one}")
            String actorId,
            @Value("${app.import.tiktok.timeout:PT120S}") Duration timeout,
            @Value("${app.import.tiktok.max-response-bytes:10485760}") int maxResponseBytes) {
        this(
                mapper,
                URI.create(apiBaseUrl),
                apiToken,
                actorId,
                timeout,
                maxResponseBytes);
    }

    ApifyTikTokResolverProvider(
            ObjectMapper mapper,
            URI apiBaseUrl,
            String apiToken,
            String actorId,
            Duration timeout,
            int maxResponseBytes) {
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.apiBaseUrl = apiBaseUrl.toString().endsWith("/")
                ? URI.create(apiBaseUrl.toString().substring(0, apiBaseUrl.toString().length() - 1))
                : apiBaseUrl;
        this.apiToken = apiToken;
        this.actorId = actorId;
        this.timeout = timeout;
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public String providerName() {
        return "apify";
    }

    @Override
    public boolean configured() {
        return apiToken != null && !apiToken.isBlank();
    }

    @Override
    public Resolution resolve(URI sourceUrl) {
        if (!configured()) {
            throw new ImportProcessingException(
                    ImportFailure.permanent("APIFY_NOT_CONFIGURED"));
        }
        try {
            JsonNode run = startActor(sourceUrl);
            String status = text(run, "status");
            if (!"SUCCEEDED".equalsIgnoreCase(status)) {
                throw new ImportProcessingException(
                        ImportFailure.transientError("APIFY_RUN_" + safeStatus(status)));
            }
            JsonNode item = firstDatasetItem(text(run, "defaultDatasetId"));
            return resolution(item, sourceUrl, text(run, "defaultKeyValueStoreId"));
        } catch (ImportProcessingException exception) {
            throw exception;
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("APIFY_TIMEOUT"), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ImportProcessingException(
                    ImportFailure.transientError("APIFY_INTERRUPTED"), exception);
        } catch (IOException | RuntimeException exception) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("APIFY_REQUEST_FAILED"), exception);
        }
    }

    private JsonNode startActor(URI sourceUrl) throws IOException, InterruptedException {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("videoUrls", List.of(sourceUrl.toString()));
        input.put("maxResults", 1);
        input.put("mediaType", "video");
        input.put("region", "ALL");
        input.put("includeTranscripts", true);
        input.put("includeVideoDownload", true);
        input.put("outputStats", false);
        input.put("outputMusic", false);
        input.put("outputPoi", false);
        input.put("outputAdFlags", false);

        URI endpoint = apiBaseUrl.resolve(
                "/acts/" + encodeActorId(actorId) + "/runs?waitForFinish="
                        + Math.max(1, timeout.toSeconds()));
        HttpResponse<String> response = sendJson(endpoint, "POST", mapper.writeValueAsBytes(input));
        JsonNode root = parse(response, "APIFY_RUN_RESPONSE_INVALID");
        return required(root, "data", "APIFY_RUN_RESPONSE_INVALID");
    }

    private JsonNode firstDatasetItem(String datasetId) throws IOException, InterruptedException {
        if (datasetId == null || datasetId.isBlank()) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("APIFY_DATASET_MISSING"));
        }
        URI endpoint = apiBaseUrl.resolve(
                "/datasets/" + encode(datasetId) + "/items?clean=true&limit=1");
        HttpResponse<String> response = sendJson(endpoint, "GET", null);
        JsonNode root = parse(response, "APIFY_DATASET_RESPONSE_INVALID");
        if (!root.isArray() || root.isEmpty()) {
            throw new ImportProcessingException(
                    ImportFailure.needsInput("SOURCE_UNAVAILABLE"));
        }
        JsonNode item = root.get(0);
        String error = text(item, "error");
        if (error != null && !error.isBlank()) {
            throw new ImportProcessingException(
                    ImportFailure.needsInput("SOURCE_UNAVAILABLE"));
        }
        return item;
    }

    private Resolution resolution(JsonNode item, URI originalUrl, String storeId) {
        String username = firstText(
                item,
                "authorUsername",
                "author.uniqueId",
                "author.username",
                "author.name");
        URI videoUrl = mediaUrl(item, storeId);
        String description = firstText(item, "title", "description");
        String transcript = text(item, "transcript");
        return new Resolution(
                uriOrDefault(item, "webUrl", originalUrl),
                username,
                username == null ? null : URI.create("https://www.tiktok.com/@" + username),
                firstText(item, "bio", "author.bio", "author.signature"),
                description == null ? "EMPTY" : "AVAILABLE",
                videoUrl == null ? "MISSING" : "AVAILABLE",
                videoUrl == null ? "MISSING" : "VIDEO_FOR_AUDIO",
                null,
                videoUrl,
                transcript,
                null);
    }

    private URI mediaUrl(JsonNode item, String storeId) {
        String directUrl = firstText(item, "downloadUrl", "videoUrl");
        if (directUrl != null) {
            return URI.create(directUrl);
        }
        String key = text(item, "savedVideoKey");
        if (key == null || key.isBlank() || storeId == null || storeId.isBlank()) {
            return null;
        }
        String endpoint = apiBaseUrl + "/key-value-stores/" + encode(storeId)
                + "/records/" + encode(key)
                + "?token=" + encode(apiToken);
        return URI.create(endpoint);
    }

    private HttpResponse<String> sendJson(URI endpoint, String method, byte[] body)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiToken)
                .header("Accept", "application/json");
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        } else {
            builder.GET();
        }
        HttpResponse<String> response = client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        byte[] responseBytes = response.body().getBytes(StandardCharsets.UTF_8);
        if (responseBytes.length > maxResponseBytes) {
            throw new ImportProcessingException(
                    ImportFailure.permanent("APIFY_RESPONSE_TOO_LARGE"));
        }
        if (response.statusCode() == 404 || response.statusCode() == 410) {
            throw new ImportProcessingException(
                    ImportFailure.needsInput("SOURCE_UNAVAILABLE"));
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new ImportProcessingException(
                    ImportFailure.permanent("APIFY_UNAUTHORIZED"));
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("APIFY_HTTP_" + response.statusCode()));
        }
        return response;
    }

    private JsonNode parse(HttpResponse<String> response, String errorCode) {
        try {
            return mapper.readTree(response.body());
        } catch (IOException exception) {
            throw new ImportProcessingException(ImportFailure.permanent(errorCode), exception);
        }
    }

    private JsonNode required(JsonNode root, String field, String errorCode) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            throw new ImportProcessingException(ImportFailure.permanent(errorCode));
        }
        return value;
    }

    private URI uriOrDefault(JsonNode root, String field, URI fallback) {
        String value = text(root, field);
        return value == null ? fallback : URI.create(value);
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
        }
        return value == null || value.isNull() ? null : value.asText();
    }

    private String encodeActorId(String value) {
        return value.replace("~", "%7E");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String safeStatus(String status) {
        return status == null || status.isBlank() ? "UNKNOWN" : status;
    }
}
