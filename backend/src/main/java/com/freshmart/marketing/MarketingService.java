package com.freshmart.marketing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshmart.auth.CurrentUser;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class MarketingService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MarketingService(@Qualifier("merchantJdbcTemplate") JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional("merchantTransactionManager")
    public long createPromotion(CurrentUser user, String promotionType, String name, Map<String, Object> rule,
            LocalDateTime startsAt, LocalDateTime endsAt, boolean stackable) {
        Long merchantId = jdbcTemplate.query("SELECT id FROM merchants WHERE owner_user_id = ? AND status = 'ACTIVE'",
                (rs, row) -> rs.getLong(1), user.userId()).stream().findFirst().orElse(null);
        if (merchantId == null) {
            throw new ResponseStatusException(FORBIDDEN, "active merchant profile is required");
        }
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(BAD_REQUEST, "promotion time range is invalid");
        }
        jdbcTemplate.update("""
                INSERT INTO promotion_rules (merchant_id, promotion_type, name, rule_json, starts_at, ends_at, stackable)
                VALUES (?, ?, ?, CAST(? AS JSON), ?, ?, ?)
                """, merchantId, promotionType, name, json(rule), startsAt, endsAt, stackable);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "promotion rule cannot be serialized");
        }
    }
}
