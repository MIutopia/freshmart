package com.freshmart.trade;

import com.freshmart.auth.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class OrderQueryService {
    private final JdbcTemplate tradeJdbcTemplate;
    private final JdbcTemplate merchantJdbcTemplate;

    public OrderQueryService(@Qualifier("tradeJdbcTemplate") JdbcTemplate tradeJdbcTemplate,
            @Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate) {
        this.tradeJdbcTemplate = tradeJdbcTemplate;
        this.merchantJdbcTemplate = merchantJdbcTemplate;
    }

    public List<OrderView> listMine(CurrentUser user) {
        if (user.hasRole("ADMIN")) {
            return query(null, null);
        }
        if (user.hasRole("MERCHANT")) {
            long merchantId = merchantJdbcTemplate.query("SELECT id FROM merchants WHERE owner_user_id = ? AND status = 'ACTIVE'",
                    (rs, row) -> rs.getLong(1), user.userId()).stream().findFirst()
                    .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "active merchant profile is required"));
            return query(null, merchantId);
        }
        return query(user.userId(), null);
    }

    public OrderView detail(CurrentUser user, long orderId) {
        OrderView order = tradeJdbcTemplate.query("""
                SELECT id, order_no, user_id, merchant_id, warehouse_id, delivery_zone_id, status, goods_amount,
                       freight_amount, discount_amount, payable_amount, actual_goods_amount, platform_absorbed_amount, created_at
                FROM orders WHERE id = ?
                """, (rs, row) -> map(rs), orderId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "order not found"));
        if (user.hasRole("ADMIN") || order.userId() == user.userId()) {
            return order;
        }
        if (user.hasRole("MERCHANT") && merchantJdbcTemplate.query("""
                SELECT id FROM merchants WHERE id = ? AND owner_user_id = ? AND status = 'ACTIVE'
                """, (rs, row) -> rs.getLong(1), order.merchantId(), user.userId()).stream().findFirst().isPresent()) {
            return order;
        }
        throw new ResponseStatusException(FORBIDDEN, "order is outside the current account scope");
    }

    private List<OrderView> query(Long userId, Long merchantId) {
        return tradeJdbcTemplate.query("""
                SELECT id, order_no, user_id, merchant_id, warehouse_id, delivery_zone_id, status, goods_amount,
                       freight_amount, discount_amount, payable_amount, actual_goods_amount, platform_absorbed_amount, created_at
                FROM orders WHERE (? IS NULL OR user_id = ?) AND (? IS NULL OR merchant_id = ?)
                ORDER BY created_at DESC, id DESC LIMIT 100
                """, (rs, row) -> map(rs), userId, userId, merchantId, merchantId);
    }

    private OrderView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OrderView(rs.getLong("id"), rs.getString("order_no"), rs.getLong("user_id"), rs.getLong("merchant_id"),
                rs.getLong("warehouse_id"), rs.getLong("delivery_zone_id"), rs.getString("status"),
                rs.getBigDecimal("goods_amount"), rs.getBigDecimal("freight_amount"), rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("payable_amount"), rs.getBigDecimal("actual_goods_amount"),
                rs.getBigDecimal("platform_absorbed_amount"), rs.getObject("created_at", LocalDateTime.class));
    }

    public record OrderView(long id, String orderNo, long userId, long merchantId, long warehouseId,
            long deliveryZoneId, String status, BigDecimal goodsAmount, BigDecimal freightAmount,
            BigDecimal discountAmount, BigDecimal payableAmount, BigDecimal actualGoodsAmount,
            BigDecimal platformAbsorbedAmount, LocalDateTime createdAt) { }
}
