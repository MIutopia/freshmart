package com.freshmart.api;

import com.freshmart.auth.CurrentUser;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class OpenApiClientService {
    private final JdbcTemplate merchantJdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public OpenApiClientService(@Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.merchantJdbcTemplate = merchantJdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional("merchantTransactionManager")
    public CreatedClient create(CurrentUser admin, Long merchantId, String name, Set<String> scopes,
            LocalDateTime expiresAt) {
        if (name == null || name.isBlank() || scopes == null || scopes.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "client fields are invalid");
        }
        if (merchantId != null && merchantJdbcTemplate.query("SELECT id FROM merchants WHERE id = ? AND status = 'ACTIVE'",
                (rs, row) -> rs.getLong(1), merchantId).isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "merchant not found");
        }
        String key = "fm_" + randomToken(18);
        String secret = randomToken(32);
        String scopesJson = scopes.stream().sorted().map(scope -> "\"" + scope.replace("\"", "") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        merchantJdbcTemplate.update("""
                INSERT INTO api_clients (merchant_id, name, client_key, secret_hash, scopes_json, expires_at, created_by)
                VALUES (?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                """, merchantId, name.trim(), key, passwordEncoder.encode(secret), scopesJson, expiresAt, admin.userId());
        return new CreatedClient(key, secret, merchantId, scopes, expiresAt);
    }

    public ClientAccess authenticate(String key, String secret, String requiredScope) {
        if (key == null || secret == null || key.isBlank() || secret.isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "API credentials are required");
        }
        ClientRecord client = merchantJdbcTemplate.query("""
                SELECT id, merchant_id, secret_hash, scopes_json, status, expires_at
                FROM api_clients WHERE client_key = ?
                """, (rs, row) -> new ClientRecord(rs.getLong("id"), (Long) rs.getObject("merchant_id"),
                rs.getString("secret_hash"), rs.getString("scopes_json"), rs.getString("status"),
                rs.getObject("expires_at", LocalDateTime.class)), key).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "invalid API credentials"));
        if (!"ACTIVE".equals(client.status()) || (client.expiresAt() != null && !client.expiresAt().isAfter(LocalDateTime.now()))
                || !passwordEncoder.matches(secret, client.secretHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "invalid API credentials");
        }
        Set<String> scopes = parseScopes(client.scopesJson());
        if (requiredScope != null && !scopes.contains(requiredScope)) {
            throw new ResponseStatusException(FORBIDDEN, "API scope is not granted");
        }
        return new ClientAccess(client.id(), client.merchantId(), scopes);
    }

    public void log(ClientAccess client, String method, String path, int status, long durationMs, String requestId) {
        merchantJdbcTemplate.update("""
                INSERT INTO api_access_logs (api_client_id, request_id, method, path, response_status, duration_ms)
                VALUES (?, ?, ?, ?, ?, ?)
                """, client.clientId(), requestId, method, path, status, durationMs);
    }

    private Set<String> parseScopes(String json) {
        if (json == null || json.isBlank()) return Set.of();
        return Set.of(json.replace("[", "").replace("]", "").replace("\"", "").split(","));
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private record ClientRecord(long id, Long merchantId, String secretHash, String scopesJson,
            String status, LocalDateTime expiresAt) {
    }

    public record ClientAccess(long clientId, Long merchantId, Set<String> scopes) {
    }

    public record CreatedClient(String clientKey, String clientSecret, Long merchantId,
            Set<String> scopes, LocalDateTime expiresAt) {
    }
}
