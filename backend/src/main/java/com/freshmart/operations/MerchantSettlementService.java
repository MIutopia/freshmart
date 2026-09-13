package com.freshmart.operations;

import com.freshmart.auth.CurrentUser;
import com.freshmart.platform.PlatformRuleService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class MerchantSettlementService {
    private final JdbcTemplate tradeJdbcTemplate;
    private final JdbcTemplate merchantJdbcTemplate;
    private final PlatformRuleService platformRuleService;

    public MerchantSettlementService(@Qualifier("tradeJdbcTemplate") JdbcTemplate tradeJdbcTemplate,
            @Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate, PlatformRuleService platformRuleService) {
        this.tradeJdbcTemplate = tradeJdbcTemplate;
        this.merchantJdbcTemplate = merchantJdbcTemplate;
        this.platformRuleService = platformRuleService;
    }

    @Transactional("tradeTransactionManager")
    public List<SettlementView> generatePending() {
        BigDecimal rate = platformRuleService.decimalOrDefault("commission.default.rate", BigDecimal.valueOf(10));
        List<OrderAmounts> orders = tradeJdbcTemplate.query("""
                SELECT orders.id, orders.merchant_id,
                       SUM(items.merchant_gross_amount - items.flash_sale_discount_amount) gross_amount,
                       SUM(items.platform_price_subsidy_amount) platform_subsidy_amount
                FROM orders JOIN order_items items ON items.order_id = orders.id
                LEFT JOIN merchant_settlements settlement ON settlement.order_id = orders.id
                WHERE orders.status = 'DELIVERED' AND settlement.id IS NULL
                GROUP BY orders.id, orders.merchant_id
                """, (rs, row) -> new OrderAmounts(rs.getLong("id"), rs.getLong("merchant_id"),
                rs.getBigDecimal("gross_amount"), rs.getBigDecimal("platform_subsidy_amount")));
        for (OrderAmounts order : orders) {
            MerchantSettlementPolicy.Settlement calculation = MerchantSettlementPolicy.calculate(
                    order.grossAmount(), order.platformSubsidyAmount(), rate);
            int inserted = tradeJdbcTemplate.update("""
                    INSERT IGNORE INTO merchant_settlements (merchant_id, order_id, gross_amount, commission_base_amount,
                        platform_price_subsidy_amount, commission_rate, commission_amount, net_amount)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, order.merchantId(), order.orderId(), order.grossAmount(), calculation.commissionBaseAmount(),
                    order.platformSubsidyAmount(), rate, calculation.commissionAmount(), calculation.netAmount());
            if (inserted > 0) {
                tradeJdbcTemplate.update("""
                        INSERT INTO fee_ledgers (order_id, merchant_id, fee_type, amount, direction, reference_type,
                            reference_id, idempotency_key)
                        VALUES (?, ?, 'PLATFORM_COMMISSION', ?, 'CREDIT', 'SETTLEMENT', ?, ?)
                        """, order.orderId(), order.merchantId(), calculation.commissionAmount(), Long.toString(order.orderId()),
                        "COMMISSION-" + order.orderId());
            }
        }
        return listByMerchant(null);
    }

    public List<SettlementView> list(CurrentUser user) {
        Long merchantId = user == null ? null : merchantId(user);
        return listByMerchant(merchantId);
    }

    public List<SettlementView> listAll() {
        return listByMerchant(null);
    }

    private List<SettlementView> listByMerchant(Long merchantId) {
        return tradeJdbcTemplate.query("""
                SELECT id, merchant_id, order_id, gross_amount, commission_base_amount, platform_price_subsidy_amount,
                       commission_rate, commission_amount, net_amount, status, settled_at, created_at
                FROM merchant_settlements WHERE (? IS NULL OR merchant_id = ?)
                ORDER BY id DESC
                """, (rs, row) -> new SettlementView(rs.getLong("id"), rs.getLong("merchant_id"), rs.getLong("order_id"),
                rs.getBigDecimal("gross_amount"), rs.getBigDecimal("commission_base_amount"),
                rs.getBigDecimal("platform_price_subsidy_amount"), rs.getBigDecimal("commission_rate"),
                rs.getBigDecimal("commission_amount"), rs.getBigDecimal("net_amount"), rs.getString("status"),
                rs.getObject("settled_at", LocalDateTime.class), rs.getObject("created_at", LocalDateTime.class)), merchantId, merchantId);
    }

    private long merchantId(CurrentUser user) {
        return merchantJdbcTemplate.query("SELECT id FROM merchants WHERE owner_user_id = ? AND status = 'ACTIVE'",
                (rs, row) -> rs.getLong(1), user.userId()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "active merchant profile is required"));
    }

    private record OrderAmounts(long orderId, long merchantId, BigDecimal grossAmount, BigDecimal platformSubsidyAmount) { }

    public record SettlementView(long id, long merchantId, long orderId, BigDecimal grossAmount,
            BigDecimal commissionBaseAmount, BigDecimal platformPriceSubsidyAmount, BigDecimal commissionRate,
            BigDecimal commissionAmount, BigDecimal netAmount, String status, LocalDateTime settledAt,
            LocalDateTime createdAt) { }
}
