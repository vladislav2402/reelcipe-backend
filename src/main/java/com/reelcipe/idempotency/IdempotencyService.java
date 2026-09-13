package com.reelcipe.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reelcipe.idempotency.domain.IdempotencyRepository;
import com.reelcipe.idempotency.domain.IdempotencyRequest;
import com.reelcipe.idempotency.domain.IdempotencyResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;
    private final Duration retention;

    public IdempotencyService(
            IdempotencyRepository repository,
            ObjectMapper objectMapper,
            @Value("${app.idempotency.retention:PT24H}") Duration retention) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.retention = retention;
    }

    @Transactional
    public IdempotencyResult execute(
            UUID userId,
            String operation,
            String target,
            String idempotencyKey,
            String requestBody,
            Supplier<IdempotencyResult> command) {
        validate(userId, operation, target, idempotencyKey);
        Instant now = Instant.now();
        IdempotencyRequest candidate = new IdempotencyRequest(
                UUID.randomUUID(), userId, operation, target, idempotencyKey,
                canonicalBodyHash(requestBody), null, null, null, now.plus(retention), null);
        repository.upsertIfExpired(candidate.id(), candidate.userId(), candidate.operation(), candidate.target(),
                candidate.idempotencyKey(), candidate.requestHash(), candidate.expiresAt());
        IdempotencyRequest request = repository
                .findLockedByUserIdAndOperationAndTargetAndIdempotencyKey(
                        userId, operation, target, idempotencyKey)
                .orElseThrow();

        if (!request.requestHash().equals(canonicalBodyHash(requestBody))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was used with another request body");
        }
        if (request.isCompleted()) {
            return new IdempotencyResult(request.responseStatus(), request.responseBody(), request.responseContentType());
        }

        IdempotencyResult result = command.get();
        repository.complete(request.id(), result.status(), result.body(), result.contentType(), Instant.now());
        return result;
    }

    String canonicalBodyHash(String requestBody) {
        try {
            JsonNode json = objectMapper.readTree(requestBody == null ? "null" : requestBody);
            return sha256(objectMapper.writeValueAsBytes(canonicalize(json)));
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body must be valid JSON", exception);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            List<String> fields = new ArrayList<>();
            node.fieldNames().forEachRemaining(fields::add);
            Collections.sort(fields);
            fields.forEach(field -> result.set(field, canonicalize(node.get(field))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> result.add(canonicalize(value)));
            return result;
        }
        return node;
    }

    private void validate(UUID userId, String operation, String target, String idempotencyKey) {
        if (userId == null || operation == null || operation.isBlank() || target == null || target.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid idempotency scope");
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
