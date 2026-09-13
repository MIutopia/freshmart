package com.freshmart.operations;

import com.freshmart.auth.CurrentUser;
import com.freshmart.platform.PlatformRuleService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class MerchantDashboardService {
    private final JdbcTemplate merchantJdbcTemplate;
    private final JdbcTemplate tradeJdbcTemplate;
    private final JdbcTemplate deliveryJdbcTemplate;
    private final PlatformRuleService platformRuleService;

    public MerchantDashboardService(@Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate,
            @Qualifier("tradeJdbcTemplate") JdbcTemplate tradeJdbcTemplate,
            @Qualifier("deliveryJdbcTemplate") JdbcTemplate deliveryJdbcTemplate,
            PlatformRuleService platformRuleService) {
        this.merchantJdbcTemplate = merchantJdbcTemplate;
        this.tradeJdbcTemplate = tradeJdbcTemplate;
        this.deliveryJdbcTemplate = deliveryJdbcTemplate;
        this.platformRuleService = platformRuleService;
    }

    public DashboardView dashboard(CurrentUser user, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ResponseStatusException(BAD_REQUEST, "dashboard date range is invalid");
        }
        long merchantId = merchantId(user);
        SalesSummary sales = tradeJdbcTemplate.query("""
                SELECT COUNT(*) order_count, COALESCE(SUM(orders.payable_amount), 0) paid_amount,
                       COALESCE(SUM(CASE WHEN orders.status = 'DELIVERED' THEN 1 ELSE 0 END), 0) delivered_count
                FROM orders JOIN trade_orders trade ON trade.id = orders.trade_id AND trade.status = 'PAID'
                WHERE orders.merchant_id = ? AND DATE(orders.created_at) >= ? AND DATE(orders.created_at) <= ?
                """, (rs, row) -> new SalesSummary(rs.getInt("order_count"), rs.getBigDecimal("paid_amount"),
                rs.getInt("delivered_count")), merchantId, from, to).stream().findFirst().orElse(new SalesSummary(0, BigDecimal.ZERO, 0));
        BigDecimal refundAmount = tradeJdbcTemplate.query("""
                SELECT COALESCE(SUM(refund.amount), 0) FROM refund_orders refund
                JOIN orders ON orders.id = refund.order_id
                WHERE orders.merchant_id = ? AND refund.status = 'REFUND_SUCCESS'
                  AND DATE(refund.refunded_at) >= ? AND DATE(refund.refunded_at) <= ?
                """, (rs, row) -> rs.getBigDecimal(1), merchantId, from, to).stream().findFirst().orElse(BigDecimal.ZERO);
        int warningThreshold = platformRuleService.integerOrDefault("inventory.warning.threshold.grams", 2000);
        List<LowStockView> lowStock = merchantJdbcTemplate.query("""
                SELECT product.id, product.name, batch.warehouse_id, SUM(GREATEST(batch.available_grams - batch.reserved_grams, 0)) available_grams
                FROM products product JOIN inventory_batches batch ON batch.product_id = product.id
                WHERE product.merchant_id = ? GROUP BY product.id, product.name, batch.warehouse_id
                HAVING SUM(GREATEST(batch.available_grams - batch.reserved_grams, 0)) <= ?
                ORDER BY available_grams, product.id LIMIT 10
                """, (rs, row) -> new LowStockView(rs.getLong("id"), rs.getString("name"), rs.getLong("warehouse_id"),
                rs.getInt("available_grams")), merchantId, warningThreshold);
        List<TopProductView> topProducts = tradeJdbcTemplate.query("""
                SELECT item.product_id, item.product_name_snapshot, SUM(item.weight_grams) sold_grams,
                       SUM(item.user_goods_amount - item.batch_promotion_discount_amount) sales_amount
                FROM order_items item JOIN orders ON orders.id = item.order_id
                JOIN trade_orders trade ON trade.id = orders.trade_id AND trade.status = 'PAID'
                WHERE orders.merchant_id = ? AND DATE(orders.created_at) >= ? AND DATE(orders.created_at) <= ?
                GROUP BY item.product_id, item.product_name_snapshot ORDER BY sold_grams DESC, item.product_id LIMIT 10
                """, (rs, row) -> new TopProductView(rs.getLong("product_id"), rs.getString("product_name_snapshot"),
                rs.getInt("sold_grams"), rs.getBigDecimal("sales_amount")), merchantId, from, to);
        // orders 与 delivery_tasks 都有 status 列，必须显式限定为 task.status
        DeliverySummary delivery = deliveryJdbcTemplate.query("""
                SELECT COUNT(*) task_count, COALESCE(SUM(task.status = 'DELIVERED'), 0) delivered_count,
                       COALESCE(SUM(task.status = 'WAITING_ASSIGNMENT'), 0) waiting_assignment_count,
                       COALESCE(SUM(task.status IN ('ASSIGNED', 'ACCEPTED', 'PICKED')), 0) in_progress_count
                FROM delivery_tasks task JOIN freshmart_trade.orders orders ON orders.id = task.order_id
                WHERE task.merchant_id = ? AND DATE(orders.created_at) >= ? AND DATE(orders.created_at) <= ?
                """, (rs, row) -> new DeliverySummary(rs.getInt("task_count"), rs.getInt("delivered_count"),
                rs.getInt("waiting_assignment_count"), rs.getInt("in_progress_count")), merchantId, from, to)
                .stream().findFirst().orElse(new DeliverySummary(0, 0, 0, 0));
        BigDecimal averageTicket = sales.orderCount() == 0 ? BigDecimal.ZERO
                : sales.paidAmount().divide(BigDecimal.valueOf(sales.orderCount()), 2, java.math.RoundingMode.HALF_UP);
        return new DashboardView(from, to, sales.orderCount(), sales.paidAmount(), refundAmount, averageTicket,
                sales.deliveredCount(), lowStock, topProducts, delivery);
    }

    private long merchantId(CurrentUser user) {
        return merchantJdbcTemplate.query("SELECT id FROM merchants WHERE owner_user_id = ? AND status = 'ACTIVE'",
                (rs, row) -> rs.getLong(1), user.userId()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "active merchant profile is required"));
    }

    private record SalesSummary(int orderCount, BigDecimal paidAmount, int deliveredCount) { }
    public record DashboardView(LocalDate from, LocalDate to, int paidOrderCount, BigDecimal paidAmount,
            BigDecimal refundedAmount, BigDecimal averageTicket, int deliveredOrderCount, List<LowStockView> lowStock,
            List<TopProductView> topProducts, DeliverySummary delivery) { }
    public record LowStockView(long productId, String productName, long warehouseId, int availableGrams) { }
    public record TopProductView(long productId, String productName, int soldGrams, BigDecimal salesAmount) { }
    public record DeliverySummary(int taskCount, int deliveredCount, int waitingAssignmentCount, int inProgressCount) { }
}
