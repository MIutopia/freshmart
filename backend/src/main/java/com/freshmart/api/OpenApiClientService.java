package com.freshmart.api;

import com.freshmart.auth.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class OpenApiClientService {
    private static final Set<String> ALLOWED_SCOPES = Set.of("products:read", "inventory:read", "orders:read",
            "receipts:read", "traceability:read");
    private final JdbcTemplate merchantJdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final int signatureMaxAgeSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public OpenApiClientService(@Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate,
            PasswordEncoder passwordEncoder, @Value("${open-api.signature-max-age-seconds:300}") int signatureMaxAgeSeconds) {
        this.merchantJdbcTemplate = merchantJdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.signatureMaxAgeSeconds = signatureMaxAgeSeconds;
    }

    @Transactional("merchantTransactionManager")
    public CreatedClient create(CurrentUser admin, Long merchantId, String name, Set<String> scopes,
            LocalDateTime expiresAt) {
        if (name == null || name.isBlank() || scopes == null || scopes.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "client fields are invalid");
        }
        if (!ALLOWED_SCOPES.containsAll(scopes)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "unsupported API scope");
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

    public ClientAccess authenticateSigned(String key, String secret, String timestamp, String nonce, String signature,
            String canonicalRequest, String requiredScope) {
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
        verifySignature(secret, timestamp, nonce, signature, canonicalRequest == null ? "" : canonicalRequest);
        if (!merchantJdbcTemplate.query("SELECT id FROM api_access_logs WHERE api_client_id = ? AND request_nonce = ?",
                (rs, row) -> rs.getLong(1), client.id(), nonce).isEmpty()) {
            throw new ResponseStatusException(UNAUTHORIZED, "API nonce has already been used");
        }
        return new ClientAccess(client.id(), client.merchantId(), scopes, nonce);
    }

    public void log(ClientAccess client, String method, String path, int status, long durationMs, String requestId) {
        merchantJdbcTemplate.update("""
                INSERT INTO api_access_logs (api_client_id, request_id, request_nonce, method, path, response_status, duration_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, client.clientId(), requestId, client.nonce(), method, path, status, durationMs);
    }

    private Set<String> parseScopes(String json) {
        if (json == null || json.isBlank()) return Set.of();
        return Arrays.stream(json.replace("[", "").replace("]", "").replace("\"", "").split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void verifySignature(String secret, String timestamp, String nonce, String signature, String canonicalRequest) {
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(UNAUTHORIZED, "API timestamp is invalid");
        }
        if (Math.abs(Instant.now().getEpochSecond() - epochSeconds) > signatureMaxAgeSeconds
                || nonce == null || !nonce.matches("[A-Za-z0-9_-]{16,64}") || signature == null || signature.isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "API signature has expired or is invalid");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            byte[] received = Base64.getUrlDecoder().decode(signature);
            if (!MessageDigest.isEqual(expected, received)) {
                throw new ResponseStatusException(UNAUTHORIZED, "API signature is invalid");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(UNAUTHORIZED, "API signature is invalid");
        }
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private record ClientRecord(long id, Long merchantId, String secretHash, String scopesJson,
            String status, LocalDateTime expiresAt) {
    }

    public record ClientAccess(long clientId, Long merchantId, Set<String> scopes, String nonce) {
    }

    public record CreatedClient(String clientKey, String clientSecret, Long merchantId,
            Set<String> scopes, LocalDateTime expiresAt) {
    }
}
