package com.freshmart.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AiInteractionAuditService {
    private final JdbcTemplate logJdbcTemplate;

    public AiInteractionAuditService(@Qualifier("logJdbcTemplate") JdbcTemplate logJdbcTemplate) {
        this.logJdbcTemplate = logJdbcTemplate;
    }

    public void success(long actorUserId, String scenario, String subjectReference, String modelName,
            String request, String response) {
        write(actorUserId, scenario, subjectReference, modelName, request, response, "SUCCESS", null);
    }

    public void failure(long actorUserId, String scenario, String subjectReference, String modelName,
            String request, String errorCode) {
        write(actorUserId, scenario, subjectReference, modelName, request, null, "FAILED", errorCode);
    }

    private void write(long actorUserId, String scenario, String subjectReference, String modelName,
            String request, String response, String status, String errorCode) {
        logJdbcTemplate.update("""
                INSERT INTO ai_interaction_logs (actor_user_id, scenario, subject_reference, model_name,
                    request_sha256, response_sha256, status, error_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, actorUserId, scenario, subjectReference, modelName, sha256(request),
                response == null ? null : sha256(response), status, errorCode);
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
