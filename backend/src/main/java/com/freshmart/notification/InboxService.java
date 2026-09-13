package com.freshmart.notification;

import com.freshmart.auth.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class InboxService {
    private final JdbcTemplate logJdbcTemplate;

    public InboxService(@Qualifier("logJdbcTemplate") JdbcTemplate logJdbcTemplate) {
        this.logJdbcTemplate = logJdbcTemplate;
    }

    public List<MessageView> list(CurrentUser user, boolean unreadOnly) {
        return logJdbcTemplate.query("""
                SELECT id, message_type, title, body, card_svg_url, card_svg_content, business_type, business_id, read_at, created_at
                FROM inbox_messages WHERE user_id = ? AND (? = FALSE OR read_at IS NULL)
                ORDER BY created_at DESC, id DESC LIMIT 100
                """, (rs, row) -> new MessageView(rs.getLong("id"), rs.getString("message_type"),
                rs.getString("title"), rs.getString("body"), rs.getString("card_svg_url"), rs.getString("card_svg_content"),
                rs.getString("business_type"), (Long) rs.getObject("business_id"),
                rs.getObject("read_at", LocalDateTime.class), rs.getObject("created_at", LocalDateTime.class)),
                user.userId(), unreadOnly);
    }

    @Transactional("logTransactionManager")
    public void markRead(CurrentUser user, long messageId) {
        int updated = logJdbcTemplate.update("UPDATE inbox_messages SET read_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
                messageId, user.userId());
        if (updated == 0) {
            throw new ResponseStatusException(NOT_FOUND, "message not found");
        }
    }

    @Transactional("logTransactionManager")
    public MessageView send(long userId, String messageType, String title, String body, String cardSvgUrl,
            String businessType, Long businessId) {
        if (messageType == null || messageType.isBlank() || title == null || title.isBlank()
                || body == null || body.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "message type, title and body are required");
        }
        logJdbcTemplate.update("""
                INSERT INTO inbox_messages (user_id, message_type, title, body, card_svg_url, business_type, business_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, userId, messageType.trim(), title.trim(), body.trim(), cardSvgUrl, businessType, businessId);
        return logJdbcTemplate.query("""
                SELECT id, message_type, title, body, card_svg_url, card_svg_content, business_type, business_id, read_at, created_at
                FROM inbox_messages WHERE id = LAST_INSERT_ID()
                """, (rs, row) -> new MessageView(rs.getLong("id"), rs.getString("message_type"),
                rs.getString("title"), rs.getString("body"), rs.getString("card_svg_url"), rs.getString("card_svg_content"),
                rs.getString("business_type"), (Long) rs.getObject("business_id"),
                rs.getObject("read_at", LocalDateTime.class), rs.getObject("created_at", LocalDateTime.class)))
                .stream().findFirst().orElseThrow();
    }

    @Transactional("logTransactionManager")
    public MessageView sendWithSvg(long userId, String messageType, String title, String body, String cardSvgUrl,
            String cardSvgContent, String businessType, Long businessId) {
        if (messageType == null || messageType.isBlank() || title == null || title.isBlank()
                || body == null || body.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "message type, title and body are required");
        }
        logJdbcTemplate.update("""
                INSERT INTO inbox_messages (user_id, message_type, title, body, card_svg_url, card_svg_content, business_type, business_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, messageType.trim(), title.trim(), body.trim(), cardSvgUrl, cardSvgContent, businessType, businessId);
        return logJdbcTemplate.query("""
                SELECT id, message_type, title, body, card_svg_url, card_svg_content, business_type, business_id, read_at, created_at
                FROM inbox_messages WHERE id = LAST_INSERT_ID()
                """, (rs, row) -> new MessageView(rs.getLong("id"), rs.getString("message_type"),
                rs.getString("title"), rs.getString("body"), rs.getString("card_svg_url"), rs.getString("card_svg_content"),
                rs.getString("business_type"), (Long) rs.getObject("business_id"),
                rs.getObject("read_at", LocalDateTime.class), rs.getObject("created_at", LocalDateTime.class)))
                .stream().findFirst().orElseThrow();
    }

    public record MessageView(long id, String messageType, String title, String body, String cardSvgUrl,
            String cardSvgContent, String businessType, Long businessId, LocalDateTime readAt, LocalDateTime createdAt) { }
}
