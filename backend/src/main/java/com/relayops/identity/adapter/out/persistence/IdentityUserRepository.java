package com.relayops.identity.adapter.out.persistence;

import com.relayops.identity.application.UserDirectory;
import com.relayops.identity.domain.IdentityUser;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Console accounts. */
@Repository
public class IdentityUserRepository implements UserDirectory {

    private final NamedParameterJdbcTemplate jdbc;

    public IdentityUserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdentityUser> findByUsername(String username) {
        List<IdentityUser> found = jdbc.query("""
                select id, username, password_hash, display_name, role, enabled
                  from identity_user where username = :username
                """, new MapSqlParameterSource("username", username), (rs, rowNum) -> new IdentityUser(
                rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("display_name"), rs.getString("role"), rs.getBoolean("enabled")));
        return found.stream().findFirst();
    }

    public long count() {
        Long count = jdbc.queryForObject("select count(*) from identity_user",
                new MapSqlParameterSource(), Long.class);
        return count == null ? 0 : count;
    }

    public void insert(String username, String passwordHash, String displayName, String role) {
        jdbc.update("""
                insert into identity_user (username, password_hash, display_name, role)
                values (:username, :passwordHash, :displayName, :role)
                on conflict (username) do nothing
                """, new MapSqlParameterSource()
                .addValue("username", username)
                .addValue("passwordHash", passwordHash)
                .addValue("displayName", displayName)
                .addValue("role", role));
    }
}
