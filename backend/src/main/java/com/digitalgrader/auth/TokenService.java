package com.digitalgrader.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import com.digitalgrader.users.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
    private final SecretKey key;
    private final Clock clock;
    private final Duration accessLifetime;
    private final Duration refreshLifetime;

    public TokenService(@Value("${JWT_SECRET}") String secret,
            @Value("${JWT_ACCESS_TTL_MINUTES:60}") long minutes,
            @Value("${JWT_REFRESH_TTL_DAYS:7}") long days, Clock clock) {
        if (secret.length() < 64 || secret.equals("replace_with_generated_secret"))
            throw new IllegalArgumentException("JWT_SECRET must contain at least 64 characters");
        if (minutes < 1 || minutes > 60 || days < 1 || days > 30)
            throw new IllegalArgumentException("Token lifetimes must be 1–60 minutes and 1–30 days");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
        this.accessLifetime = Duration.ofMinutes(minutes);
        this.refreshLifetime = Duration.ofDays(days);
    }
    public String issue(User user, String sessionHash) {
        if (sessionHash == null || !sessionHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid session identifier");
        var now = clock.instant();
        return Jwts.builder().issuer("digital-grader").subject(Long.toString(user.id()))
                .claim("role", user.role().name()).claim("token_use", "access").claim("session", sessionHash)
                .id(UUID.randomUUID().toString()).issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessLifetime))).signWith(key, Jwts.SIG.HS256).compact();
    }
    public Claims verify(String token) {
        var jwt = Jwts.parser().verifyWith(key).requireIssuer("digital-grader").require("token_use", "access")
                .clock(() -> Date.from(clock.instant())).build().parseSignedClaims(token);
        if (!"HS256".equals(jwt.getHeader().getAlgorithm()) || jwt.getPayload().getExpiration() == null)
            throw new MalformedJwtException("Invalid access token");
        return jwt.getPayload();
    }
    public Duration refreshLifetime() { return refreshLifetime; }
}

