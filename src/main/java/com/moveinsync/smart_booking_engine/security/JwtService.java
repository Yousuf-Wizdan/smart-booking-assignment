package com.moveinsync.smart_booking_engine.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Minimal JWT issuer/validator (jjwt 0.12.6). Subject = username; claims carry
 * user id, role and linked employee id so ownership checks need no extra query.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expiryMinutes;

    public JwtService(
            @Value("${app.security.jwt-secret:}") String secret,
            @Value("${app.security.jwt-expiry:480}") long expiryMinutes) {
        this.expiryMinutes = expiryMinutes;
        byte[] secretBytes;
        if (secret == null || secret.isBlank()) {
            // Dev only: random per-boot secret, tokens do not survive restart.
            secretBytes = new byte[32];
            new SecureRandom().nextBytes(secretBytes);
        } else {
            secretBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (secretBytes.length < 32) {
                throw new IllegalStateException(
                        "app.security.jwt-secret must be at least 32 bytes when set");
            }
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(CustomUserDetails user) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiryMinutes * 60)))
                .claim("uid", user.getId().toString())
                .claim("role", user.getRole());
        if (user.getEmployeeId() != null) {
            builder.claim("eid", user.getEmployeeId().toString());
        }
        return builder.signWith(key).compact();
    }

    /** Returns claims if the signature and expiry are valid, otherwise null. */
    public Claims parseValid(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
