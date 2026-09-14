package com.reelcipe.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

public final class ImportClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private String accessToken;

    public ImportClient(RestClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Response createLink(UUID clientRequestId, String sourceUrl, String idempotencyKey) {
        return exchange(
                "POST",
                "/v1/imports",
                new CreatePayload(clientRequestId, "LINK", sourceUrl, null, null, null, null, null),
                idempotencyKey);
    }

    public Response get(String importId) {
        return exchange("GET", "/v1/imports/" + importId, null, null);
    }

    public Response list() {
        return exchange("GET", "/v1/imports", null, null);
    }

    private Response exchange(String method, String path, Object body, String idempotencyKey) {
        try {
            RestClient.RequestBodySpec request = client
                    .method(org.springframework.http.HttpMethod.valueOf(method))
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON);
            if (idempotencyKey != null) {
                request.header("Idempotency-Key", idempotencyKey);
            }
            ResponseEntity<String> response = request
                    .body(body == null ? "" : objectMapper.writeValueAsString(body))
                    .retrieve()
                    .toEntity(String.class);
            return new Response(response.getStatusCode().value(), response.getBody());
        } catch (RestClientResponseException exception) {
            return new Response(
                    exception.getStatusCode().value(), exception.getResponseBodyAsString());
        } catch (Exception exception) {
            throw new AssertionError("Unable to execute imports request", exception);
        }
    }

    public record CreatePayload(
            UUID clientRequestId,
            String sourceType,
            String sourceUrl,
            String mediaKind,
            String fileName,
            String contentType,
            Long sizeBytes,
            String descriptionText) {
    }

    public record Response(int status, String body) {
    }
}
