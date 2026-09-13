package com.freshmart.payment;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FinancialStatusLogService {
    private final JdbcTemplate jdbc;

    public FinancialStatusLogService(@Qualifier("tradeJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String entityType, String entityNo, String fromStatus, String toStatus,
            String actionCode, Long operatorUserId, String remark, String sourceType) {
        jdbc.update("""
                INSERT INTO financial_status_logs
                    (entity_type, entity_no, from_status, to_status, action_code, operator_user_id, remark, source_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, entityType, entityNo, fromStatus, toStatus, actionCode, operatorUserId, remark, sourceType);
    }
}
