package com.secondmemory.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AppUserRepository {
    private final JdbcTemplate jdbc;

    public AppUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AppUser create(String username, String passwordHash) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, username, password_hash)
                VALUES (?, ?, ?)
                """, id, username, passwordHash);
        return findByUsername(username).orElseThrow();
    }

    public Optional<AppUser> findByUsername(String username) {
        return jdbc.query("SELECT * FROM app_user WHERE username = ?", this::mapRow, username)
                .stream().findFirst();
    }

    public Optional<AppUser> findById(UUID id) {
        return jdbc.query("SELECT * FROM app_user WHERE id = ?", this::mapRow, id)
                .stream().findFirst();
    }

    public boolean existsById(UUID id) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM app_user WHERE id = ?)", Boolean.class, id);
        return Boolean.TRUE.equals(exists);
    }

    public long count() {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class);
        return total == null ? 0 : total;
    }

    private AppUser mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AppUser(
                rs.getObject("id", UUID.class),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
