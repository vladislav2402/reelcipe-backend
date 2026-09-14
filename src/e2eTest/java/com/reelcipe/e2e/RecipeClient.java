package com.reelcipe.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;

public final class RecipeClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private String accessToken;

    public RecipeClient(RestClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Response createRecipe(RecipePayload payload, String idempotencyKey) {
        return exchange("POST", "/v1/recipes", payload, idempotencyKey, null);
    }

    public Response updateRecipe(String recipeId, String expectedVersion, RecipePayload payload, String idempotencyKey) {
        return exchange("PATCH", "/v1/recipes/" + recipeId, payload, idempotencyKey, expectedVersion);
    }

    public Response getRecipe(String recipeId) {
        return exchange("GET", "/v1/recipes/" + recipeId, null, null, null);
    }

    public Response deleteRecipe(String recipeId, String expectedVersion, String idempotencyKey) {
        return exchange("DELETE", "/v1/recipes/" + recipeId, null, idempotencyKey, expectedVersion);
    }

    private Response exchange(String method, String path, Object body, String idempotencyKey, String ifMatch) {
        try {
            RestClient.RequestBodySpec request = client
                    .method(org.springframework.http.HttpMethod.valueOf(method))
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON);
            if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
            if (ifMatch != null) request.header(HttpHeaders.IF_MATCH, ifMatch);

            ResponseEntity<String> response = request
                    .body(body == null ? "" : objectMapper.writeValueAsString(body))
                    .retrieve()
                    .toEntity(String.class);
            return response(response);
        } catch (RestClientResponseException e) {
            assert e.getResponseHeaders() != null;
            return new Response(e.getStatusCode().value(), e.getResponseBodyAsString(),
                    e.getResponseHeaders().getFirst(HttpHeaders.ETAG));
        } catch (Exception e) {
            throw new AssertionError("Unable to execute recipe request", e);
        }
    }

    private Response response(ResponseEntity<String> response) {
        return new Response(response.getStatusCode().value(), response.getBody(), response.getHeaders().getETag());
    }

    public record RecipePayload(
            String title,
            String language,
            IngredientPayload[] ingredients,
            StepPayload[] steps) {
    }

    public record IngredientPayload(String name, BigDecimal amount, String unit) {
    }

    public record StepPayload(String text) {
    }

    public record Response(int status, String body, String etag) {
    }
}
