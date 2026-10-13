package com.digitalgrader.users;

import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<User> MAPPER = (row, index) -> new User(row.getLong("id"),
            row.getString("email"), row.getString("password_hash"), row.getString("full_name"),
            Role.valueOf(row.getString("role")));

    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public static String canonicalEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    public Optional<User> findByEmail(String email) {
        return jdbc.query("SELECT * FROM users WHERE lower(email) = ?", MAPPER, canonicalEmail(email)).stream().findFirst();
    }
    public Optional<User> findById(long id) {
        return jdbc.query("SELECT * FROM users WHERE id = ?", MAPPER, id).stream().findFirst();
    }
    public User create(String email, String passwordHash, String fullName, Role role) {
        return jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name,role) VALUES (?,?,?,?) RETURNING *",
                MAPPER, canonicalEmail(email), passwordHash, fullName.strip(), role.name());
    }
}
