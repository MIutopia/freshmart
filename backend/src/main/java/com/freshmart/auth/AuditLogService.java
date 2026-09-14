package com.freshmart.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final JdbcTemplate jdbcTemplate;

    public AuditLogService(@Qualifier("logJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(Long actorUserId, String actionCode, String resourceType, String resourceId, String sourceIp) {
        jdbcTemplate.update("""
                INSERT INTO audit_logs (actor_user_id, action_code, resource_type, resource_id, source_ip)
                VALUES (?, ?, ?, ?, ?)
                """, actorUserId, actionCode, resourceType, resourceId, sourceIp);
    }
}
