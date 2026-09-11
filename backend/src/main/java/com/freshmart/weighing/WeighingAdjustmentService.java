package com.freshmart.weighing;

import com.freshmart.auth.CurrentUser;
import com.freshmart.auth.AuditLogService;
import com.freshmart.order.WeighingSettlementPolicy;
import com.freshmart.platform.PlatformRuleService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class WeighingAdjustmentService {
    private final JdbcTemplate tradeJdbcTemplate;
    private final JdbcTemplate merchantJdbcTemplate;
    private final PlatformRuleService platformRuleService;
    private final AuditLogService auditLogService;

    public WeighingAdjustmentService(@Qualifier("tradeJdbcTemplate") JdbcTemplate tradeJdbcTemplate,
            @Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate, PlatformRuleService platformRuleService,
            AuditLogService auditLogService) {
        this.tradeJdbcTemplate = tradeJdbcTemplate;
        this.merchantJdbcTemplate = merchantJdbcTemplate;
        this.platformRuleService = platformRuleService;
        this.auditLogService = auditLogService;
    }

    @Transactional("tradeTransactionManager")
    public AdjustmentView submit(CurrentUser operator, long orderId, BigDecimal actualGoodsAmount,
            String note, String idempotencyKey, String sourceIp) {
        if (!operator.hasRole("ADMIN") && !operator.hasRole("MERCHANT")) {
            throw new ResponseStatusException(FORBIDDEN, "merchant or admin role is required");
        }
        AdjustmentView existing = findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return existing;
        }
        OrderSnapshot order = tradeJdbcTemplate.query("""
                SELECT id, merchant_id, goods_amount, status FROM orders WHERE id = ?
                """, (rs, row) -> new OrderSnapshot(rs.getLong("id"), rs.getLong("merchant_id"),
                rs.getBigDecimal("goods_amount"), rs.getString("status")), orderId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "order not found"));
        if (actualGoodsAmount == null || actualGoodsAmount.signum() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "actual goods amount must not be negative");
        }
        if (!List.of("WAITING_PICKING", "PICKED", "DELIVERED").contains(order.status())) {
            throw new ResponseStatusException(CONFLICT, "order is not ready for weighing adjustment");
        }
        if (operator.hasRole("MERCHANT")) {
            boolean owns = !merchantJdbcTemplate.query("SELECT id FROM merchants WHERE id = ? AND owner_user_id = ?",
                    (rs, row) -> rs.getLong(1), order.merchantId(), operator.userId()).isEmpty();
            if (!owns) {
                throw new ResponseStatusException(FORBIDDEN, "merchant cannot adjust this order");
            }
        }
        BigDecimal limit = platformRuleService.decimalOrDefault("weighing.platform.absorb.limit", BigDecimal.valueOf(1.50));
        WeighingSettlementPolicy.Settlement settlement = WeighingSettlementPolicy.settle(order.goodsAmount(), actualGoodsAmount, limit);
        String adjustmentId = "WEIGH-" + order.id() + "-" + idempotencyKey;
        try {
            tradeJdbcTemplate.update("""
                    INSERT INTO weighing_adjustments (order_id, merchant_id, prepaid_goods_amount, actual_goods_amount,
                        difference_amount, action, refund_amount, absorbed_amount, note, idempotency_key, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, order.id(), order.merchantId(), order.goodsAmount(), actualGoodsAmount,
                    actualGoodsAmount.subtract(order.goodsAmount()), settlement.action().name(), settlement.refundAmount(),
                    settlement.absorbedAmount(), note == null ? null : note.trim(), idempotencyKey, operator.userId());
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new ResponseStatusException(CONFLICT, "an adjustment already exists for this order");
        }
        auditLogService.record(operator.userId(), "WEIGHING_ADJUSTMENT_SUBMITTED", "ORDER", Long.toString(order.id()), sourceIp);
        return new AdjustmentView(adjustmentId, order.id(), order.merchantId(), order.goodsAmount(), actualGoodsAmount,
                settlement.action().name(), settlement.refundAmount(), settlement.absorbedAmount(), LocalDateTime.now());
    }

    private AdjustmentView findByIdempotencyKey(String key) {
        return tradeJdbcTemplate.query("""
                SELECT id, order_id, merchant_id, prepaid_goods_amount, actual_goods_amount, action,
                       refund_amount, absorbed_amount, created_at
                FROM weighing_adjustments WHERE idempotency_key = ?
                """, (rs, row) -> new AdjustmentView("WEIGH-" + rs.getLong("order_id") + "-" + key,
                rs.getLong("order_id"), rs.getLong("merchant_id"), rs.getBigDecimal("prepaid_goods_amount"),
                rs.getBigDecimal("actual_goods_amount"), rs.getString("action"), rs.getBigDecimal("refund_amount"),
                rs.getBigDecimal("absorbed_amount"), rs.getObject("created_at", LocalDateTime.class)), key)
                .stream().findFirst().orElse(null);
    }

    private record OrderSnapshot(long id, long merchantId, BigDecimal goodsAmount, String status) {
    }

    public record AdjustmentView(String adjustmentId, long orderId, long merchantId, BigDecimal prepaidGoodsAmount,
            BigDecimal actualGoodsAmount, String action, BigDecimal refundAmount, BigDecimal absorbedAmount,
            LocalDateTime createdAt) {
    }
}
