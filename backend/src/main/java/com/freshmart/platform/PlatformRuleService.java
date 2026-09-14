package com.freshmart.platform;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PlatformRuleService {
    private final JdbcTemplate jdbcTemplate;

    public PlatformRuleService(@Qualifier("coreJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RuleView> list() {
        return jdbcTemplate.query("""
                SELECT rule_key, rule_value, value_type, description, updated_by, updated_at
                FROM platform_rules ORDER BY rule_key
                """, (rs, row) -> new RuleView(rs.getString("rule_key"), rs.getString("rule_value"),
                rs.getString("value_type"), rs.getString("description"), (Long) rs.getObject("updated_by"),
                rs.getObject("updated_at", java.time.LocalDateTime.class)));
    }

    public RuleView update(long adminUserId, String ruleKey, String value) {
        RuleRecord rule = jdbcTemplate.query("""
                SELECT rule_key, rule_value, value_type, description
                FROM platform_rules WHERE rule_key = ?
                """, (rs, row) -> new RuleRecord(rs.getString("rule_key"), rs.getString("rule_value"),
                rs.getString("value_type"), rs.getString("description")), ruleKey).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "platform rule not found"));
        String normalized = normalize(rule.valueType(), value);
        jdbcTemplate.update("""
                UPDATE platform_rules SET rule_value = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE rule_key = ?
                """, normalized, adminUserId, rule.ruleKey());
        return jdbcTemplate.query("""
                SELECT rule_key, rule_value, value_type, description, updated_by, updated_at
                FROM platform_rules WHERE rule_key = ?
                """, (rs, row) -> new RuleView(rs.getString("rule_key"), rs.getString("rule_value"),
                rs.getString("value_type"), rs.getString("description"), (Long) rs.getObject("updated_by"),
                rs.getObject("updated_at", java.time.LocalDateTime.class)), rule.ruleKey()).stream().findFirst().orElseThrow();
    }

    public BigDecimal decimalOrDefault(String ruleKey, BigDecimal fallback) {
        return value(ruleKey).map(raw -> {
            try {
                return new BigDecimal(raw);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }).orElse(fallback);
    }

    public int integerOrDefault(String ruleKey, int fallback) {
        return value(ruleKey).map(raw -> {
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }).orElse(fallback);
    }

    private Optional<String> value(String ruleKey) {
        return jdbcTemplate.query("SELECT rule_value FROM platform_rules WHERE rule_key = ?",
                (rs, row) -> rs.getString(1), ruleKey).stream().findFirst();
    }

    private String normalize(String valueType, String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "rule value is required");
        }
        String raw = value.trim();
        try {
            return switch (valueType.toUpperCase(Locale.ROOT)) {
                case "DECIMAL" -> new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP).toPlainString();
                case "INTEGER" -> Integer.toString(Integer.parseInt(raw));
                case "BOOLEAN" -> {
                    if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException();
                    }
                    yield raw.toLowerCase(Locale.ROOT);
                }
                default -> raw;
            };
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "rule value does not match " + valueType);
        }
    }

    private record RuleRecord(String ruleKey, String value, String valueType, String description) {
    }

    public record RuleView(String ruleKey, String value, String valueType, String description,
            Long updatedBy, java.time.LocalDateTime updatedAt) {
    }
}
