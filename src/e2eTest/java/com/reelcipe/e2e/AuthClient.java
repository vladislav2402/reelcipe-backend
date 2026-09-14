package com.reelcipe.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public final class AuthClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private String accessToken;

    public AuthClient(RestClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public Response devLogin(String username) {
        return exchange("POST", "/v1/auth/dev", new DevLoginPayload(username), false);
    }

    public Response refresh(String refreshToken) {
        return exchange("POST", "/v1/auth/refresh", new RefreshPayload(refreshToken), false);
    }

    public Response logout() {
        return exchange("POST", "/v1/auth/logout", null, true);
    }

    public Response getCurrentUser() {
        return exchange("GET", "/v1/me", null, accessToken != null);
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    private Response exchange(String method, String path, Object body, boolean authenticated) {
        try {
            RestClient.RequestBodySpec request = client
                    .method(org.springframework.http.HttpMethod.valueOf(method))
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON);
            if (authenticated) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            }

            ResponseEntity<String> response = request
                    .body(body == null ? "" : objectMapper.writeValueAsString(body))
                    .retrieve()
                    .toEntity(String.class);
            return new Response(response.getStatusCode().value(), response.getBody());
        } catch (RestClientResponseException e) {
            return new Response(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new AssertionError("Unable to execute auth request", e);
        }
    }

    private record DevLoginPayload(String fixture) {
    }

    private record RefreshPayload(String refreshToken) {
    }

    public record Response(int status, String body) {
    }
}
