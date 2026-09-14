package com.freshmart.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class OpenApiReadService {
    private final JdbcTemplate merchantJdbcTemplate;
    private final JdbcTemplate tradeJdbcTemplate;
    private final ObjectMapper objectMapper;

    public OpenApiReadService(@Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate,
            @Qualifier("tradeJdbcTemplate") JdbcTemplate tradeJdbcTemplate, ObjectMapper objectMapper) {
        this.merchantJdbcTemplate = merchantJdbcTemplate;
        this.tradeJdbcTemplate = tradeJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<InventoryView> inventory(Long merchantId, Long productId) {
        return merchantJdbcTemplate.query("""
                SELECT batch.id, batch.product_id, product.name product_name, product.merchant_id,
                       batch.warehouse_id, batch.batch_no, batch.available_grams, batch.reserved_grams, batch.expires_on
                FROM inventory_batches batch JOIN products product ON product.id = batch.product_id
                WHERE product.status = 'ACTIVE' AND (? IS NULL OR product.merchant_id = ?)
                  AND (? IS NULL OR batch.product_id = ?)
                ORDER BY batch.expires_on IS NULL, batch.expires_on, batch.id
                """, (rs, row) -> new InventoryView(rs.getLong("id"), rs.getLong("product_id"),
                rs.getString("product_name"), rs.getLong("merchant_id"), rs.getLong("warehouse_id"),
                rs.getString("batch_no"), rs.getInt("available_grams"), rs.getInt("reserved_grams"),
                rs.getObject("expires_on", LocalDate.class)), merchantId, merchantId, productId, productId);
    }

    public OrderView order(String orderNo, Long merchantId) {
        return tradeJdbcTemplate.query("""
                SELECT id, order_no, user_id, merchant_id, warehouse_id, delivery_zone_id, status,
                       goods_amount, freight_amount, discount_amount, payable_amount, created_at
                FROM orders WHERE order_no = ? AND (? IS NULL OR merchant_id = ?)
                """, (rs, row) -> new OrderView(rs.getLong("id"), rs.getString("order_no"), rs.getLong("user_id"),
                rs.getLong("merchant_id"), rs.getLong("warehouse_id"), rs.getLong("delivery_zone_id"),
                rs.getString("status"), rs.getBigDecimal("goods_amount"), rs.getBigDecimal("freight_amount"),
                rs.getBigDecimal("discount_amount"), rs.getBigDecimal("payable_amount"),
                rs.getObject("created_at", LocalDateTime.class)), orderNo, merchantId, merchantId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "order not found"));
    }

    public Map<String, Object> receipt(String orderNo, Long merchantId) {
        OrderView order = order(orderNo, merchantId);
        String json = tradeJdbcTemplate.query("SELECT receipt_snapshot FROM electronic_receipts WHERE order_id = ?",
                (rs, row) -> rs.getString(1), order.orderId()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "receipt not found"));
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("receipt snapshot is invalid", exception);
        }
    }

    public List<TraceView> traceability(String orderNo, Long merchantId) {
        OrderView order = order(orderNo, merchantId);
        return tradeJdbcTemplate.query("""
                SELECT item.product_name_snapshot, allocation.batch_id, batch.batch_no, allocation.warehouse_id,
                       allocation.allocated_grams, allocation.batch_promotion_id, allocation.markdown_rate_snapshot,
                       allocation.discount_amount, batch.expires_on
                FROM order_items item JOIN order_item_batch_allocations allocation ON allocation.order_item_id = item.id
                LEFT JOIN freshmart_merchant.inventory_batches batch ON batch.id = allocation.batch_id
                WHERE item.order_id = ? ORDER BY item.id, allocation.id
                """, (rs, row) -> new TraceView(rs.getString("product_name_snapshot"), rs.getLong("batch_id"),
                rs.getString("batch_no"), rs.getLong("warehouse_id"), rs.getInt("allocated_grams"),
                (Long) rs.getObject("batch_promotion_id"), rs.getBigDecimal("markdown_rate_snapshot"),
                rs.getBigDecimal("discount_amount"), rs.getObject("expires_on", LocalDate.class)), order.orderId());
    }

    public record InventoryView(long batchId, long productId, String productName, long merchantId, long warehouseId,
            String batchNo, int availableGrams, int reservedGrams, LocalDate expiresOn) { }
    public record OrderView(long orderId, String orderNo, long userId, long merchantId, long warehouseId,
            long deliveryZoneId, String status, java.math.BigDecimal goodsAmount, java.math.BigDecimal freightAmount,
            java.math.BigDecimal discountAmount, java.math.BigDecimal payableAmount, LocalDateTime createdAt) { }
    public record TraceView(String productName, long batchId, String batchNo, long warehouseId, int allocatedGrams,
            Long batchPromotionId, java.math.BigDecimal markdownRate, java.math.BigDecimal discountAmount,
            LocalDate expiresOn) { }
}
