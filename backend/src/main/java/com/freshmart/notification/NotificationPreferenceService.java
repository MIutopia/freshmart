package com.freshmart.notification;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {
    private final JdbcTemplate userJdbcTemplate;

    public NotificationPreferenceService(@Qualifier("userJdbcTemplate") JdbcTemplate userJdbcTemplate) {
        this.userJdbcTemplate = userJdbcTemplate;
    }

    public PreferenceView find(long userId) {
        Boolean enabled = userJdbcTemplate.query("SELECT seasonal_card_enabled FROM user_notification_preferences WHERE user_id = ?",
                (rs, row) -> rs.getBoolean(1), userId).stream().findFirst().orElse(Boolean.TRUE);
        return new PreferenceView(enabled);
    }

    @Transactional("userTransactionManager")
    public PreferenceView update(long userId, boolean seasonalCardEnabled) {
        userJdbcTemplate.update("""
                INSERT INTO user_notification_preferences (user_id, seasonal_card_enabled)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE seasonal_card_enabled = VALUES(seasonal_card_enabled), updated_at = CURRENT_TIMESTAMP
                """, userId, seasonalCardEnabled);
        return new PreferenceView(seasonalCardEnabled);
    }

    public record PreferenceView(boolean seasonalCardEnabled) {
    }
}
