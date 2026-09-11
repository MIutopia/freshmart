package com.freshmart.refund;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshmart.auth.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class RefundService {
    private final JdbcTemplate tradeJdbcTemplate;
    private final JdbcTemplate deliveryJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final int windowMinutes;

    public RefundService(@Qualifier("tradeJdbcTemplate") JdbcTemplate tradeJdbcTemplate,
            @Qualifier("deliveryJdbcTemplate") JdbcTemplate deliveryJdbcTemplate, ObjectMapper objectMapper,
            @Value("${commerce.refund.default-window-minutes:1440}") int windowMinutes) {
        this.tradeJdbcTemplate = tradeJdbcTemplate;
        this.deliveryJdbcTemplate = deliveryJdbcTemplate;
        this.objectMapper = objectMapper;
        this.windowMinutes = windowMinutes;
    }

    @Transactional("tradeTransactionManager")
    public RefundView apply(CurrentUser user, long orderId, String issueType, String description,
            List<String> images, String idempotencyKey) {
        RefundView existing = findByIdempotencyKey(user.userId(), idempotencyKey);
        if (existing != null) {
            return existing;
        }
        if (!"OUT_OF_STOCK".equals(issueType) && !"QUALITY".equals(issueType)) {
            throw new ResponseStatusException(BAD_REQUEST, "issue type must be OUT_OF_STOCK or QUALITY");
        }
        if (description == null || description.isBlank() || images == null || images.isEmpty()
                || images.stream().anyMatch(image -> image == null || image.isBlank())) {
            throw new ResponseStatusException(BAD_REQUEST, "refund evidence image and description are required");
        }
        OrderPayment order = tradeJdbcTemplate.query("""
                SELECT orders.id, orders.payable_amount, orders.user_id, payment.id AS payment_id
                FROM orders JOIN payment_orders payment ON payment.trade_id = orders.trade_id AND payment.status = 'PAID'
                WHERE orders.id = ? AND orders.user_id = ?
                ORDER BY payment.id DESC LIMIT 1
                """, (rs, row) -> new OrderPayment(rs.getLong("id"), rs.getBigDecimal("payable_amount"),
                rs.getLong("user_id"), rs.getLong("payment_id")), orderId, user.userId()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "paid order not found"));
        LocalDateTime deliveredAt = deliveryJdbcTemplate.query("""
                SELECT delivered_at FROM delivery_tasks WHERE order_id = ? AND status = 'DELIVERED'
                """, (rs, row) -> rs.getObject(1, LocalDateTime.class), orderId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "order has not been delivered"));
        if (deliveredAt.plusMinutes(windowMinutes).isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(CONFLICT, "refund window has expired");
        }
        String refundNo = "R" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
        tradeJdbcTemplate.update("""
                INSERT INTO refund_orders (refund_no, payment_id, order_id, reason, issue_type,
                                           evidence_description, evidence_images_json, amount, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                """, refundNo, order.paymentId(), order.id(), description.trim(), issueType,
                description.trim(), json(images), order.amount(), idempotencyKey);
        long id = tradeJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return new RefundView(id, refundNo, order.id(), issueType, order.amount(), "PENDING", deliveredAt);
    }

    private RefundView findByIdempotencyKey(long userId, String key) {
        return tradeJdbcTemplate.query("""
                SELECT refund.id, refund.refund_no, refund.order_id, refund.issue_type, refund.amount,
                       refund.status, delivery.delivered_at
                FROM refund_orders refund JOIN orders ON orders.id = refund.order_id
                LEFT JOIN freshmart_delivery.delivery_tasks delivery ON delivery.order_id = refund.order_id
                    AND delivery.status = 'DELIVERED'
                WHERE refund.idempotency_key = ? AND orders.user_id = ?
                """, (rs, row) -> new RefundView(rs.getLong("id"), rs.getString("refund_no"), rs.getLong("order_id"),
                rs.getString("issue_type"), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getObject("delivered_at", LocalDateTime.class)), key, userId).stream().findFirst().orElse(null);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "refund evidence cannot be serialized");
        }
    }

    private record OrderPayment(long id, BigDecimal amount, long userId, long paymentId) {
    }

    public record RefundView(long id, String refundNo, long orderId, String issueType, BigDecimal amount,
            String status, LocalDateTime deliveredAt) {
    }
}
