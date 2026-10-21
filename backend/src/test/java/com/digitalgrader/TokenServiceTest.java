package com.digitalgrader;

import java.time.*;
import com.digitalgrader.auth.TokenService;
import com.digitalgrader.users.*;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TokenServiceTest {
    private static final String SECRET = "test-only-key-".repeat(6);
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);
    private static final User USER = new User(12, "fixture@example.org", "hash", "Fixture", Role.INSTRUCTOR);
    @Test void signsAndVerifiesExpectedClaims() {
        var tokens = new TokenService(SECRET, 60, 7, NOW);
        var claims = tokens.verify(tokens.issue(USER, "a".repeat(64)));
        assertThat(claims.getSubject()).isEqualTo("12");
        assertThat(claims.get("role")).isEqualTo("INSTRUCTOR");
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.instant().plusSeconds(3600));
    }
    @Test void rejectsExpiredAndForeignSignatures() {
        var tokens = new TokenService(SECRET, 60, 7, NOW);
        String token = tokens.issue(USER, "a".repeat(64));
        assertThatThrownBy(() -> new TokenService(SECRET, 60, 7, Clock.offset(NOW, Duration.ofMinutes(61))).verify(token))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> new TokenService("different-key-".repeat(6), 60, 7, NOW).verify(token)).isInstanceOf(JwtException.class);
    }
    @Test void rejectsTamperingAndMalformedTokens() {
        var tokens = new TokenService(SECRET, 60, 7, NOW);
        String token = tokens.issue(USER, "a".repeat(64));
        String[] parts = token.split("\\.");
        String signature = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        assertThatThrownBy(() -> tokens.verify(parts[0] + "." + parts[1] + "." + signature)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> tokens.verify("not.a.jwt")).isInstanceOf(JwtException.class);
    }
    @Test void rejectsWrongIssuerUsageAlgorithmAndMissingExpiration() {
        var tokens = new TokenService(SECRET, 60, 7, NOW);
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var expiration = java.util.Date.from(NOW.instant().plusSeconds(60));
        String wrongIssuer = io.jsonwebtoken.Jwts.builder().issuer("another-app").claim("token_use", "access").expiration(expiration).signWith(key, io.jsonwebtoken.Jwts.SIG.HS256).compact();
        String wrongUsage = io.jsonwebtoken.Jwts.builder().issuer("digital-grader").claim("token_use", "refresh").expiration(expiration).signWith(key, io.jsonwebtoken.Jwts.SIG.HS256).compact();
        String wrongAlgorithm = io.jsonwebtoken.Jwts.builder().issuer("digital-grader").claim("token_use", "access").expiration(expiration).signWith(key, io.jsonwebtoken.Jwts.SIG.HS512).compact();
        String missingExpiration = io.jsonwebtoken.Jwts.builder().issuer("digital-grader").claim("token_use", "access").signWith(key, io.jsonwebtoken.Jwts.SIG.HS256).compact();
        for (String invalid : java.util.List.of(wrongIssuer, wrongUsage, wrongAlgorithm, missingExpiration))
            assertThatThrownBy(() -> tokens.verify(invalid)).isInstanceOf(JwtException.class);
    }
    @Test void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new TokenService("short", 60, 7, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenService(SECRET, 0, 7, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenService(SECRET, 60, 31, NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}

