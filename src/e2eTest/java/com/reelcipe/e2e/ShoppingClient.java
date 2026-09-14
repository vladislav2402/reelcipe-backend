package com.reelcipe.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

public final class ShoppingClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private String accessToken;

    public ShoppingClient(RestClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Response addRecipe(RecipeAdditionPayload payload, String idempotencyKey) {
        return exchange("POST", "/v1/shopping-list/from-recipe", payload, idempotencyKey, null);
    }

    public Response getShoppingList() {
        return exchange("GET", "/v1/shopping-list", null, null, null);
    }

    public Response deleteItem(String itemId, String expectedVersion, String idempotencyKey) {
        return exchange("DELETE", "/v1/shopping-list/items/" + itemId, null, idempotencyKey, expectedVersion);
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
            return new Response(e.getStatusCode().value(), e.getResponseBodyAsString(),
                    e.getResponseHeaders().getFirst(HttpHeaders.ETAG));
        } catch (Exception e) {
            throw new AssertionError("Unable to execute shopping-list request", e);
        }
    }

    private Response response(ResponseEntity<String> response) {
        return new Response(response.getStatusCode().value(), response.getBody(), response.getHeaders().getETag());
    }

    public record RecipeAdditionPayload(String recipeId, long recipeVersion, UUID additionId) {
    }

    public record Response(int status, String body, String etag) {
    }
}
