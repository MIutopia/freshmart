package com.freshmart.admin;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台级总览指标。
 *
 * 只做跨卫星库的只读聚合，不写任何数据；统计口径固定，避免与商家看板口径漂移。
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','FINANCE')")
public class AdminDashboardController {
    private final JdbcTemplate tradeJdbcTemplate;
    private final JdbcTemplate userJdbcTemplate;
    private final JdbcTemplate merchantJdbcTemplate;
    private final JdbcTemplate deliveryJdbcTemplate;

    public AdminDashboardController(@Qualifier("tradeJdbcTemplate") JdbcTemplate tradeJdbcTemplate,
            @Qualifier("userJdbcTemplate") JdbcTemplate userJdbcTemplate,
            @Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate,
            @Qualifier("deliveryJdbcTemplate") JdbcTemplate deliveryJdbcTemplate) {
        this.tradeJdbcTemplate = tradeJdbcTemplate;
        this.userJdbcTemplate = userJdbcTemplate;
        this.merchantJdbcTemplate = merchantJdbcTemplate;
        this.deliveryJdbcTemplate = deliveryJdbcTemplate;
    }

    @GetMapping("/api/admin/dashboard")
    public DashboardView dashboard() {
        return new DashboardView(
                count(tradeJdbcTemplate, "SELECT COUNT(*) FROM orders WHERE created_at >= CURRENT_DATE"),
                amount(tradeJdbcTemplate, """
                        SELECT COALESCE(SUM(payable_amount), 0) FROM orders
                        WHERE created_at >= CURRENT_DATE AND status <> 'CANCELLED'
                        """),
                count(tradeJdbcTemplate, "SELECT COUNT(*) FROM refund_orders"),
                count(userJdbcTemplate, "SELECT COUNT(*) FROM users"),
                count(merchantJdbcTemplate, "SELECT COUNT(*) FROM merchants WHERE status = 'ACTIVE'"),
                count(merchantJdbcTemplate, "SELECT COUNT(*) FROM products WHERE status = 'ACTIVE'"),
                count(deliveryJdbcTemplate, "SELECT COUNT(*) FROM delivery_tasks"),
                count(deliveryJdbcTemplate, "SELECT COUNT(*) FROM delivery_tasks WHERE status = 'DELIVERED'"));
    }

    private int count(JdbcTemplate jdbcTemplate, String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private BigDecimal amount(JdbcTemplate jdbcTemplate, String sql) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    public record DashboardView(int todayOrderCount, BigDecimal todaySalesAmount, int refundOrderCount,
            int userCount, int activeMerchantCount, int activeProductCount, int deliveryTaskCount,
            int deliveredTaskCount) {
    }
}
