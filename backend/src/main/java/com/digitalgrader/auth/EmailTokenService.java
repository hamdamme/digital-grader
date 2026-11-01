package com.digitalgrader.auth;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import com.digitalgrader.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Single-use emailed tokens for verification and password reset, stored only as SHA-256 hashes. */
@Service
public class EmailTokenService {
    public enum Purpose { VERIFY_EMAIL, PASSWORD_RESET }
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public EmailTokenService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    public String issue(long userId, Purpose purpose, Duration lifetime) {
        // Outstanding tokens of the same purpose stop working as soon as a newer one is issued.
        jdbc.update("UPDATE email_tokens SET used_at=? WHERE user_id=? AND purpose=? AND used_at IS NULL",
                Timestamp.from(clock.instant()), userId, purpose.name());
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO email_tokens(user_id,token_hash,purpose,expires_at) VALUES (?,?,?,?)",
                userId, AuthService.hash(token), purpose.name(), Timestamp.from(clock.instant().plus(lifetime)));
        return token;
    }

    /** Consumes the token if it exists, matches the purpose, is unused and unexpired. */
    @Transactional
    public long consume(String token, Purpose purpose) {
        var ids = jdbc.queryForList(
                "UPDATE email_tokens SET used_at=? WHERE token_hash=? AND purpose=? AND used_at IS NULL AND expires_at>? RETURNING user_id",
                Long.class, Timestamp.from(clock.instant()), AuthService.hash(token), purpose.name(), Timestamp.from(clock.instant()));
        if (ids.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired link");
        return ids.get(0);
    }
}
