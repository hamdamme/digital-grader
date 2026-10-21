package com.digitalgrader.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import com.digitalgrader.common.ApiException;
import com.digitalgrader.users.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;
    public record Tokens(String accessToken, String refreshToken, Role role) {}

    public AuthService(UserRepository users, PasswordEncoder passwords, TokenService tokens, JdbcTemplate jdbc, Clock clock) {
        this.users = users; this.passwords = passwords; this.tokens = tokens; this.jdbc = jdbc; this.clock = clock;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public Tokens login(AuthRequests.Login request) {
        var user = users.findByEmail(request.email());
        boolean matches = passwords.matches(request.password(), user.map(User::passwordHash).orElse(dummyHash));
        if (!matches || user.isEmpty()) throw ApiException.unauthorized();
        return issue(user.get());
    }
    @Transactional
    public Tokens refresh(String token) {
        // DELETE RETURNING makes concurrent reuse fail; rollback preserves the old token if issuance fails.
        var ids = jdbc.queryForList("DELETE FROM refresh_tokens WHERE token_hash=? AND expires_at>? RETURNING user_id",
                Long.class, hash(token), Timestamp.from(clock.instant()));
        if (ids.isEmpty()) throw ApiException.unauthorized();
        return issue(users.findById(ids.get(0)).orElseThrow(ApiException::unauthorized));
    }
    public void logout(String token) { jdbc.update("DELETE FROM refresh_tokens WHERE token_hash=?", hash(token)); }
    private Tokens issue(User user) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO refresh_tokens(user_id,token_hash,expires_at) VALUES (?,?,?)",
                user.id(), hash(refresh), Timestamp.from(clock.instant().plus(tokens.refreshLifetime())));
        return new Tokens(tokens.issue(user, hash(refresh)), refresh, user.role());
    }
    public boolean isSessionActive(long userId, String sessionHash) {
        if (sessionHash == null || !sessionHash.matches("[0-9a-f]{64}")) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM refresh_tokens WHERE user_id=? AND token_hash=? AND expires_at>?)",
                Boolean.class, userId, sessionHash, Timestamp.from(clock.instant())));
    }
    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 unavailable", error); }
    }
}

