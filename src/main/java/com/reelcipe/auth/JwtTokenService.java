package com.reelcipe.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.reelcipe.common.UuidV7;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final byte[] secret;
    private final String issuer;
    private final long accessTokenTtlSeconds;

    public JwtTokenService(
            @Value("${app.auth.jwt-secret}") String secret,
            @Value("${app.auth.issuer}") String issuer,
            @Value("${app.auth.access-token-ttl-seconds:900}") long accessTokenTtlSeconds) {
        this.secret = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalArgumentException("app.auth.jwt-secret must be at least 32 bytes");
        }
        this.issuer = issuer;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public String issueAccessToken(UUID userId, UUID sessionId, Instant now) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("sid", sessionId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(accessTokenTtlSeconds)))
                .jwtID(UuidV7.randomUuid().toString())
                .build();
        try {
            SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            token.sign(new MACSigner(secret));
            return token.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Could not issue access token", exception);
        }
    }

    public AuthenticatedToken parseAndVerify(String serializedToken, Instant now) {
        try {
            SignedJWT token = SignedJWT.parse(serializedToken);
            if (!token.verify(new MACVerifier(secret))) {
                throw new InvalidTokenException();
            }
            JWTClaimsSet claims = token.getJWTClaimsSet();
            if (!issuer.equals(claims.getIssuer())
                    || claims.getSubject() == null
                    || claims.getExpirationTime() == null
                    || !claims.getExpirationTime().toInstant().isAfter(now)) {
                throw new InvalidTokenException();
            }
            return new AuthenticatedToken(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString((String) claims.getClaim("sid")));
        } catch (ParseException | JOSEException | RuntimeException exception) {
            throw new InvalidTokenException();
        }
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public record AuthenticatedToken(UUID userId, UUID sessionId) {
    }

    public static class InvalidTokenException extends RuntimeException {
    }
}
