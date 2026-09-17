package dev.qiqi.dataagent.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class IdentityService {
    private final JdbcTemplate jdbc;

    public IdentityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserIdentity> findActiveByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        List<UserIdentity> users = jdbc.query("""
                        SELECT id, username, display_name, data_scope, department_id
                        FROM app_user WHERE username = ? AND active = TRUE
                        """, (rs, row) -> new UserIdentity(
                        rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                        rs.getString("data_scope"), (Long) rs.getObject("department_id")), username.trim());
        return users.stream().findFirst();
    }

    public Optional<UserIdentity> findActiveById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        try {
            List<UserIdentity> users = jdbc.query("""
                            SELECT id, username, display_name, data_scope, department_id
                            FROM app_user WHERE id = ? AND active = TRUE
                            """, (rs, row) -> new UserIdentity(
                            rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                            rs.getString("data_scope"), (Long) rs.getObject("department_id")), Long.parseLong(id));
            return users.stream().findFirst();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public List<UserIdentity> listDemoUsers() {
        return jdbc.query("""
                SELECT id, username, display_name, data_scope, department_id
                FROM app_user WHERE active = TRUE ORDER BY id
                """, (rs, row) -> new UserIdentity(
                rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                rs.getString("data_scope"), (Long) rs.getObject("department_id")));
    }
}
