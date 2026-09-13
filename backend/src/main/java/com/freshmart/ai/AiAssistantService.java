package com.freshmart.ai;

import com.freshmart.auth.CurrentUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AiAssistantService {
    private final JdbcTemplate merchantJdbcTemplate;
    private final JdbcTemplate tradeJdbcTemplate;

    public AiAssistantService(@Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate,
            @Qualifier("tradeJdbcTemplate") JdbcTemplate tradeJdbcTemplate) {
        this.merchantJdbcTemplate = merchantJdbcTemplate;
        this.tradeJdbcTemplate = tradeJdbcTemplate;
    }

    public AssistantReply reply(CurrentUser user, String message) {
        if (message == null || message.isBlank() || message.length() > 500) {
            throw new ResponseStatusException(BAD_REQUEST, "message must contain 1 to 500 characters");
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("订单") || normalized.contains("order")) {
            return orderReply(user);
        }
        List<ProductView> products = merchantJdbcTemplate.query("""
                SELECT product.id, product.name, product.description, product.market_price_per_kg,
                       product.merchant_price_per_kg, COALESCE(SUM(GREATEST(batch.available_grams - batch.reserved_grams, 0)), 0) available_grams
                FROM products product LEFT JOIN inventory_batches batch ON batch.product_id = product.id
                WHERE product.status = 'ACTIVE' GROUP BY product.id, product.name, product.description,
                    product.market_price_per_kg, product.merchant_price_per_kg
                HAVING available_grams > 0 ORDER BY product.id DESC LIMIT 5
                """, (rs, row) -> new ProductView(rs.getLong("id"), rs.getString("name"),
                rs.getString("description"), rs.getBigDecimal("market_price_per_kg"),
                rs.getBigDecimal("merchant_price_per_kg"), rs.getInt("available_grams")));
        String answer = products.isEmpty() ? "当前没有可推荐的在售商品。" : "根据你的需求，优先推荐当前有库存的商品。";
        return new AssistantReply("SHOPPING_GUIDE", answer, products, List.of());
    }

    private AssistantReply orderReply(CurrentUser user) {
        List<OrderView> orders = tradeJdbcTemplate.query("""
                SELECT order_no, status, payable_amount, created_at FROM orders
                WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 5
                """, (rs, row) -> new OrderView(rs.getString("order_no"), rs.getString("status"),
                rs.getBigDecimal("payable_amount"), rs.getTimestamp("created_at").toLocalDateTime()), user.userId());
        String answer = orders.isEmpty() ? "暂未查询到你的订单。" : "已返回你最近的订单状态。";
        return new AssistantReply("ORDER_QUERY", answer, List.of(), orders);
    }

    public record AssistantReply(String intent, String answer, List<ProductView> products, List<OrderView> orders) { }
    public record ProductView(long productId, String name, String description, BigDecimal marketPricePerKg,
            BigDecimal merchantPricePerKg, int availableGrams) { }
    public record OrderView(String orderNo, String status, BigDecimal payableAmount,
            java.time.LocalDateTime createdAt) { }
}
