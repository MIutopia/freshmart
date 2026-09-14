package com.freshmart.operations;

import com.freshmart.auth.CurrentUser;
import com.freshmart.platform.PlatformRuleService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

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

    @Transactional("tradeTransactionManager")
    public SettlementView confirmSettlement(long settlementId, long operatorUserId, String note) {
        SettlementStatus settlement = tradeJdbcTemplate.query("""
                SELECT id, status FROM merchant_settlements WHERE id = ? FOR UPDATE
                """, (rs, row) -> new SettlementStatus(rs.getLong(1), rs.getString(2)), settlementId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "merchant settlement not found"));
        if (!"PENDING".equals(settlement.status())) {
            throw new ResponseStatusException(CONFLICT, "merchant settlement is not pending");
        }
        tradeJdbcTemplate.update("""
                UPDATE merchant_settlements
                SET status = 'SETTLED', settled_at = CURRENT_TIMESTAMP, settled_by = ?, settlement_note = ?
                WHERE id = ? AND status = 'PENDING'
                """, operatorUserId, note.trim(), settlement.id());
        return findById(settlement.id());
    }

    public List<MerchantOperationsController.PlatformCostView> platformCosts(LocalDate from, LocalDate to) {
        return tradeJdbcTemplate.query("""
                SELECT fee_type, direction, COALESCE(SUM(amount), 0) amount, COUNT(*) entry_count
                FROM fee_ledgers
                WHERE occurred_at >= ? AND occurred_at < DATE_ADD(?, INTERVAL 1 DAY)
                GROUP BY fee_type, direction ORDER BY fee_type, direction
                """, (rs, row) -> new MerchantOperationsController.PlatformCostView(rs.getString("fee_type"),
                rs.getString("direction"), rs.getBigDecimal("amount"), rs.getLong("entry_count")), from, to);
    }

    private List<SettlementView> listByMerchant(Long merchantId) {
        return tradeJdbcTemplate.query("""
                SELECT id, merchant_id, order_id, gross_amount, commission_base_amount, platform_price_subsidy_amount,
                       commission_rate, commission_amount, net_amount, status, settled_at, settled_by, settlement_note, created_at
                FROM merchant_settlements WHERE (? IS NULL OR merchant_id = ?)
                ORDER BY id DESC
                """, (rs, row) -> new SettlementView(rs.getLong("id"), rs.getLong("merchant_id"), rs.getLong("order_id"),
                rs.getBigDecimal("gross_amount"), rs.getBigDecimal("commission_base_amount"),
                rs.getBigDecimal("platform_price_subsidy_amount"), rs.getBigDecimal("commission_rate"),
                rs.getBigDecimal("commission_amount"), rs.getBigDecimal("net_amount"), rs.getString("status"),
                rs.getObject("settled_at", LocalDateTime.class), (Long) rs.getObject("settled_by"),
                rs.getString("settlement_note"), rs.getObject("created_at", LocalDateTime.class)), merchantId, merchantId);
    }

    private SettlementView findById(long settlementId) {
        return tradeJdbcTemplate.query("""
                SELECT id, merchant_id, order_id, gross_amount, commission_base_amount, platform_price_subsidy_amount,
                       commission_rate, commission_amount, net_amount, status, settled_at, settled_by, settlement_note, created_at
                FROM merchant_settlements WHERE id = ?
                """, (rs, row) -> new SettlementView(rs.getLong("id"), rs.getLong("merchant_id"), rs.getLong("order_id"),
                rs.getBigDecimal("gross_amount"), rs.getBigDecimal("commission_base_amount"),
                rs.getBigDecimal("platform_price_subsidy_amount"), rs.getBigDecimal("commission_rate"),
                rs.getBigDecimal("commission_amount"), rs.getBigDecimal("net_amount"), rs.getString("status"),
                rs.getObject("settled_at", LocalDateTime.class), (Long) rs.getObject("settled_by"),
                rs.getString("settlement_note"), rs.getObject("created_at", LocalDateTime.class)), settlementId).stream()
                .findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "merchant settlement not found"));
    }

    private long merchantId(CurrentUser user) {
        return merchantJdbcTemplate.query("SELECT id FROM merchants WHERE owner_user_id = ? AND status = 'ACTIVE'",
                (rs, row) -> rs.getLong(1), user.userId()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "active merchant profile is required"));
    }

    private record OrderAmounts(long orderId, long merchantId, BigDecimal grossAmount, BigDecimal platformSubsidyAmount) { }
    private record SettlementStatus(long id, String status) { }

    public record SettlementView(long id, long merchantId, long orderId, BigDecimal grossAmount,
            BigDecimal commissionBaseAmount, BigDecimal platformPriceSubsidyAmount, BigDecimal commissionRate,
            BigDecimal commissionAmount, BigDecimal netAmount, String status, LocalDateTime settledAt,
            Long settledBy, String settlementNote, LocalDateTime createdAt) { }
}
