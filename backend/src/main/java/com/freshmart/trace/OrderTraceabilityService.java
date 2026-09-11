package com.freshmart.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshmart.auth.CurrentUser;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class OrderTraceabilityService {
    private final JdbcTemplate tradeJdbcTemplate;
    private final JdbcTemplate merchantJdbcTemplate;
    private final ObjectMapper objectMapper;

    public OrderTraceabilityService(@Qualifier("tradeJdbcTemplate") JdbcTemplate tradeJdbcTemplate,
            @Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate, ObjectMapper objectMapper) {
        this.tradeJdbcTemplate = tradeJdbcTemplate;
        this.merchantJdbcTemplate = merchantJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional("tradeTransactionManager")
    public ReceiptView receipt(CurrentUser user, long orderId) {
        OrderAccess order = requireAccess(user, orderId);
        requirePaidOrder(order);
        ReceiptView existing = tradeJdbcTemplate.query("SELECT receipt_no, receipt_snapshot, issued_at FROM electronic_receipts WHERE order_id = ?",
                (rs, row) -> new ReceiptView(rs.getString("receipt_no"), orderId,
                        rs.getString("receipt_snapshot"), rs.getObject("issued_at", LocalDateTime.class)), orderId)
                .stream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        List<Map<String, Object>> items = tradeJdbcTemplate.query("""
                SELECT item.product_name_snapshot, item.weight_grams, item.user_price_per_kg,
                       item.user_goods_amount, allocation.batch_id, allocation.allocated_grams,
                       allocation.warehouse_id, batch.batch_no, batch.expires_on
                FROM order_items item
                LEFT JOIN order_item_batch_allocations allocation ON allocation.order_item_id = item.id
                LEFT JOIN freshmart_merchant.inventory_batches batch ON batch.id = allocation.batch_id
                WHERE item.order_id = ? ORDER BY item.id, allocation.id
                """, (rs, row) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("productName", rs.getString("product_name_snapshot"));
                    item.put("weightGrams", rs.getInt("weight_grams"));
                    item.put("userPricePerKg", rs.getBigDecimal("user_price_per_kg"));
                    item.put("userGoodsAmount", rs.getBigDecimal("user_goods_amount"));
                    item.put("batchId", rs.getObject("batch_id"));
                    item.put("batchNo", rs.getString("batch_no"));
                    item.put("allocatedGrams", rs.getObject("allocated_grams"));
                    item.put("warehouseId", rs.getObject("warehouse_id"));
                    item.put("expiresOn", rs.getObject("expires_on", LocalDate.class));
                    return item;
                }, orderId);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("orderId", orderId);
        snapshot.put("orderNo", order.orderNo());
        snapshot.put("merchantId", order.merchantId());
        snapshot.put("warehouseId", order.warehouseId());
        snapshot.put("deliveryZoneId", order.deliveryZoneId());
        snapshot.put("goodsAmount", order.goodsAmount());
        snapshot.put("freightAmount", order.freightAmount());
        snapshot.put("discountAmount", order.discountAmount());
        snapshot.put("payableAmount", order.payableAmount());
        snapshot.put("items", items);
        String receiptNo = "RCP" + order.orderNo();
        String json = json(snapshot);
        tradeJdbcTemplate.update("INSERT INTO electronic_receipts (order_id, receipt_no, receipt_snapshot) VALUES (?, ?, CAST(? AS JSON))",
                orderId, receiptNo, json);
        return new ReceiptView(receiptNo, orderId, json, LocalDateTime.now());
    }

    public List<TraceView> traceability(CurrentUser user, long orderId) {
        requirePaidOrder(requireAccess(user, orderId));
        return tradeJdbcTemplate.query("""
                SELECT item.product_name_snapshot, allocation.batch_id, batch.batch_no,
                       allocation.warehouse_id, allocation.allocated_grams, batch.expires_on
                FROM order_items item
                LEFT JOIN order_item_batch_allocations allocation ON allocation.order_item_id = item.id
                LEFT JOIN freshmart_merchant.inventory_batches batch ON batch.id = allocation.batch_id
                WHERE item.order_id = ? ORDER BY item.id, allocation.batch_id
                """, (rs, row) -> new TraceView(rs.getString("product_name_snapshot"),
                (Long) rs.getObject("batch_id"), rs.getString("batch_no"), (Long) rs.getObject("warehouse_id"),
                (Integer) rs.getObject("allocated_grams"), rs.getObject("expires_on", LocalDate.class)), orderId);
    }

    private OrderAccess requireAccess(CurrentUser user, long orderId) {
        OrderAccess order = tradeJdbcTemplate.query("""
                SELECT id, order_no, user_id, merchant_id, warehouse_id, delivery_zone_id, status,
                       goods_amount, freight_amount, discount_amount, payable_amount
                FROM orders WHERE id = ?
                """, (rs, row) -> new OrderAccess(rs.getLong("id"), rs.getString("order_no"), rs.getLong("user_id"),
                rs.getLong("merchant_id"), rs.getLong("warehouse_id"), rs.getLong("delivery_zone_id"), rs.getString("status"),
                rs.getBigDecimal("goods_amount"), rs.getBigDecimal("freight_amount"), rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("payable_amount")), orderId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "order not found"));
        if (user.hasRole("ADMIN") || order.userId() == user.userId()) {
            return order;
        }
        if (user.hasRole("MERCHANT") && !merchantJdbcTemplate.query("SELECT id FROM merchants WHERE id = ? AND owner_user_id = ?",
                (rs, row) -> rs.getLong(1), order.merchantId(), user.userId()).isEmpty()) {
            return order;
        }
        throw new ResponseStatusException(FORBIDDEN, "order is outside the current account scope");
    }

    private void requirePaidOrder(OrderAccess order) {
        if ("PENDING_PAYMENT".equals(order.status()) || "CANCELLED".equals(order.status())) {
            throw new ResponseStatusException(CONFLICT, "receipt is available after payment confirmation");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("receipt snapshot cannot be serialized", exception);
        }
    }

    private record OrderAccess(long id, String orderNo, long userId, long merchantId, long warehouseId,
            long deliveryZoneId, String status, java.math.BigDecimal goodsAmount, java.math.BigDecimal freightAmount,
            java.math.BigDecimal discountAmount, java.math.BigDecimal payableAmount) {
    }

    public record ReceiptView(String receiptNo, long orderId, String snapshotJson, LocalDateTime issuedAt) {
    }

    public record TraceView(String productName, Long batchId, String batchNo, Long warehouseId,
            Integer allocatedGrams, LocalDate expiresOn) {
    }
}
