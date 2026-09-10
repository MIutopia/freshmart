package com.freshmart.auth;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final int sessionHours;

    public AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            @Value("${auth.session-hours:8}") int sessionHours) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.sessionHours = sessionHours;
    }

    @Transactional
    public LoginResult login(String loginName, String password, String sourceIp) {
        LoginUser user = findLoginUser(loginName)
                .filter(candidate -> "ACTIVE".equals(candidate.status()))
                .filter(candidate -> candidate.passwordHash() != null && passwordEncoder.matches(password, candidate.passwordHash()))
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "invalid login name or password"));
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(sessionHours);
        String token = AuthTokenCodec.newToken();
        jdbcTemplate.update("INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                user.userId(), AuthTokenCodec.hash(token), expiresAt);
        auditLogService.record(user.userId(), "AUTH_LOGIN", "USER", user.userId().toString(), sourceIp);
        return new LoginResult(token, expiresAt, currentUser(null, user));
    }

    public Optional<CurrentUser> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        List<SessionUser> matched = jdbcTemplate.query("""
                SELECT s.id AS session_id, u.id AS user_id, u.login_name, u.user_type, u.role
                FROM auth_sessions s JOIN users u ON u.id = s.user_id
                WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > CURRENT_TIMESTAMP AND u.status = 'ACTIVE'
                """, (resultSet, rowNum) -> new SessionUser(
                resultSet.getLong("session_id"),
                resultSet.getLong("user_id"),
                resultSet.getString("login_name"),
                resultSet.getString("user_type"),
                resultSet.getString("role")), AuthTokenCodec.hash(token));
        if (matched.isEmpty()) {
            return Optional.empty();
        }
        SessionUser user = matched.get(0);
        jdbcTemplate.update("UPDATE auth_sessions SET last_seen_at = CURRENT_TIMESTAMP WHERE id = ?", user.sessionId());
        return Optional.of(currentUser(user.sessionId(), user));
    }

    @Transactional
    public void logout(CurrentUser user, String sourceIp) {
        jdbcTemplate.update("UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
                user.sessionId(), user.userId());
        auditLogService.record(user.userId(), "AUTH_LOGOUT", "USER", user.userId().toString(), sourceIp);
    }

    private Optional<LoginUser> findLoginUser(String loginName) {
        return jdbcTemplate.query("""
                SELECT id, login_name, password_hash, user_type, role, status
                FROM users WHERE login_name = ?
                """, (resultSet, rowNum) -> new LoginUser(
                resultSet.getLong("id"), resultSet.getString("login_name"), resultSet.getString("password_hash"),
                resultSet.getString("user_type"), resultSet.getString("role"), resultSet.getString("status")), loginName)
                .stream().findFirst();
    }

    private CurrentUser currentUser(Long sessionId, UserIdentity user) {
        Set<String> roles = new LinkedHashSet<>(jdbcTemplate.queryForList(
                "SELECT role_code FROM user_role_assignments WHERE user_id = ?", String.class, user.userId()));
        addRole(roles, user.userType());
        addRole(roles, user.legacyRole());
        return new CurrentUser(user.userId(), sessionId, user.loginName(), Set.copyOf(roles));
    }

    private void addRole(Set<String> roles, String role) {
        if (role == null || role.isBlank()) {
            return;
        }
        roles.add("USER".equals(role) ? "CONSUMER" : role);
    }

    public record LoginResult(String accessToken, LocalDateTime expiresAt, CurrentUser user) {
    }

    private interface UserIdentity {
        Long userId();
        String loginName();
        String userType();
        String legacyRole();
    }

    private record LoginUser(Long userId, String loginName, String passwordHash, String userType, String legacyRole, String status)
            implements UserIdentity {
    }

    private record SessionUser(Long sessionId, Long userId, String loginName, String userType, String legacyRole)
            implements UserIdentity {
    }
}
