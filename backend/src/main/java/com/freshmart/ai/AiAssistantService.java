package com.freshmart.ai;

import com.freshmart.auth.CurrentUser;
import com.freshmart.auth.AuditLogService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class AiAssistantService {
    private final JdbcTemplate merchantJdbcTemplate;
    private final JdbcTemplate tradeJdbcTemplate;
    private final DeepSeekClient deepSeekClient;
    private final AuditLogService auditLogService;

    public AiAssistantService(@Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate,
            @Qualifier("tradeJdbcTemplate") JdbcTemplate tradeJdbcTemplate,
            DeepSeekClient deepSeekClient, AuditLogService auditLogService) {
        this.merchantJdbcTemplate = merchantJdbcTemplate;
        this.tradeJdbcTemplate = tradeJdbcTemplate;
        this.deepSeekClient = deepSeekClient;
        this.auditLogService = auditLogService;
    }

    public AssistantReply reply(CurrentUser user, String message) {
        if (message == null || message.isBlank() || message.length() > 500) {
            throw new ResponseStatusException(BAD_REQUEST, "message must contain 1 to 500 characters");
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("订单") || normalized.contains("order")) {
            AssistantReply reply = orderReply(user, message);
            auditLogService.record(user.userId(), "AI_ORDER_QUERY", "USER", user.userId().toString(), null);
            return reply;
        }
        List<ProductView> products = merchantJdbcTemplate.query("""
                SELECT product.id, product.name, product.description, product.market_price_per_kg,
                       product.merchant_price_per_kg, COALESCE(SUM(GREATEST(batch.available_grams - batch.reserved_grams, 0)), 0) available_grams
                FROM products product
                JOIN merchants merchant ON merchant.id = product.merchant_id AND merchant.status = 'ACTIVE'
                LEFT JOIN inventory_batches batch ON batch.product_id = product.id
                WHERE product.status = 'ACTIVE' GROUP BY product.id, product.name, product.description,
                    product.market_price_per_kg, product.merchant_price_per_kg
                HAVING available_grams > 0 ORDER BY product.id DESC LIMIT 5
                """, (rs, row) -> new ProductView(rs.getLong("id"), rs.getString("name"),
                rs.getString("description"), rs.getBigDecimal("market_price_per_kg"),
                rs.getBigDecimal("merchant_price_per_kg"), rs.getInt("available_grams")));
        String context = products.stream()
                .map(product -> "%s|库存%d克|用户价%.2f元/千克".formatted(product.name(), product.availableGrams(), product.userPricePerKg()))
                .collect(Collectors.joining("; "));
        String answer = deepSeekClient.chat(
                "你是生鲜商城导购。只能基于给定商品数据回答，不能编造库存、价格、优惠或承诺下单。回答使用简体中文，控制在120字以内。",
                "用户需求：" + message.trim() + "\n可推荐商品：" + (context.isBlank() ? "无" : context));
        auditLogService.record(user.userId(), "AI_SHOPPING_GUIDE", "USER", user.userId().toString(), null);
        return new AssistantReply("SHOPPING_GUIDE", answer, products, List.of());
    }

    private AssistantReply orderReply(CurrentUser user, String message) {
        List<OrderView> orders = tradeJdbcTemplate.query("""
                SELECT order_no, status, payable_amount, created_at FROM orders
                WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 5
                """, (rs, row) -> new OrderView(rs.getString("order_no"), rs.getString("status"),
                rs.getBigDecimal("payable_amount"), rs.getTimestamp("created_at").toLocalDateTime()), user.userId());
        String context = orders.stream()
                .map(order -> "%s|状态%s|金额%.2f元".formatted(order.orderNo(), order.status(), order.payableAmount()))
                .collect(Collectors.joining("; "));
        String answer = deepSeekClient.chat(
                "你是生鲜商城订单助手。只能根据给定的当前用户订单回答，不得查询或推测其他用户数据。回答使用简体中文，控制在120字以内。",
                "用户询问：" + message.trim() + "\n当前用户最近订单：" + (context.isBlank() ? "无" : context));
        return new AssistantReply("ORDER_QUERY", answer, List.of(), orders);
    }

    public record AssistantReply(String intent, String answer, List<ProductView> products, List<OrderView> orders) { }
    public record ProductView(long productId, String name, String description, BigDecimal marketPricePerKg,
            BigDecimal merchantPricePerKg, int availableGrams) {
        public BigDecimal userPricePerKg() {
            return merchantPricePerKg.min(marketPricePerKg);
        }
    }
    public record OrderView(String orderNo, String status, BigDecimal payableAmount,
            java.time.LocalDateTime createdAt) { }
}
