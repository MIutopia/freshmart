package com.freshmart.weighing;

import com.freshmart.auth.CurrentUser;
import com.freshmart.auth.AuditLogService;
import com.freshmart.order.WeighingSettlementPolicy;
import com.freshmart.platform.PlatformRuleService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final JdbcTemplate userJdbcTemplate;
    private final PlatformRuleService platformRuleService;
    private final AuditLogService auditLogService;

    public WeighingAdjustmentService(@Qualifier("tradeJdbcTemplate") JdbcTemplate tradeJdbcTemplate,
            @Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate, PlatformRuleService platformRuleService,
            @Qualifier("userJdbcTemplate") JdbcTemplate userJdbcTemplate, AuditLogService auditLogService) {
        this.tradeJdbcTemplate = tradeJdbcTemplate;
        this.merchantJdbcTemplate = merchantJdbcTemplate;
        this.userJdbcTemplate = userJdbcTemplate;
        this.platformRuleService = platformRuleService;
        this.auditLogService = auditLogService;
    }

    @Transactional("tradeTransactionManager")
    public AdjustmentView submit(CurrentUser operator, long orderId, BigDecimal actualGoodsAmount, Integer actualGrams,
            List<ItemWeighing> items, String note, String idempotencyKey, String sourceIp) {
        if (!operator.hasRole("ADMIN") && !operator.hasRole("MERCHANT")) {
            throw new ResponseStatusException(FORBIDDEN, "merchant or admin role is required");
        }
        AdjustmentView existing = findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return existing;
        }
        OrderSnapshot order = requireAdjustableOrder(operator, orderId);
        List<ItemWeighing> itemWeighings = items == null ? List.of() : items;
        // 逐项称重优先：整单实际金额与克数由各项汇总得出，逐项结果同时落到订单项与批次库存
        BigDecimal resolvedAmount = actualGoodsAmount;
        Integer resolvedGrams = actualGrams;
        List<ItemSnapshot> snapshots = List.of();
        if (!itemWeighings.isEmpty()) {
            snapshots = resolveItemSnapshots(order.id(), itemWeighings);
            resolvedAmount = snapshots.stream().map(ItemSnapshot::actualGoodsAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            resolvedGrams = snapshots.stream().mapToInt(ItemSnapshot::actualGrams).sum();
        }
        if (resolvedAmount == null || resolvedAmount.signum() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "actual goods amount must not be negative");
        }
        BigDecimal limit = platformRuleService.decimalOrDefault("weighing.platform.absorb.limit", BigDecimal.valueOf(1.50));
        WeighingSettlementPolicy.Settlement settlement = WeighingSettlementPolicy.settle(order.goodsAmount(), resolvedAmount, limit);
        String adjustmentId = "WEIGH-" + order.id() + "-" + idempotencyKey;
        try {
            tradeJdbcTemplate.update("""
                    INSERT INTO weighing_adjustments (order_id, merchant_id, prepaid_goods_amount, actual_goods_amount,
                        actual_grams, difference_amount, action, refund_amount, absorbed_amount, note, idempotency_key, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, order.id(), order.merchantId(), order.goodsAmount(), resolvedAmount, resolvedGrams,
                    resolvedAmount.subtract(order.goodsAmount()), settlement.action().name(), settlement.refundAmount(),
                    settlement.absorbedAmount(), note == null ? null : note.trim(), idempotencyKey, operator.userId());
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new ResponseStatusException(CONFLICT, "an adjustment already exists for this order");
        }
        // 克数回补放在插入成功之后：唯一键冲突会直接抛出并回滚，不会出现库存已改而调整未落库的情况
        int inventoryAdjustGrams;
        if (snapshots.isEmpty()) {
            inventoryAdjustGrams = resolvedGrams == null ? 0 : adjustBatchGrams(order.id(), resolvedGrams, idempotencyKey);
        } else {
            inventoryAdjustGrams = applyItemWeighings(order.id(), snapshots, adjustmentId);
        }
        settle(order, settlement, adjustmentId);
        auditLogService.record(operator.userId(), "WEIGHING_ADJUSTMENT_SUBMITTED", "ORDER", Long.toString(order.id()), sourceIp);
        return new AdjustmentView(adjustmentId, order.id(), order.merchantId(), order.goodsAmount(), resolvedAmount,
                resolvedGrams, inventoryAdjustGrams, settlement.action().name(), settlement.refundAmount(),
                settlement.absorbedAmount(), LocalDateTime.now());
    }

    /** 商家称重前需要看到该订单的订单项与预估克数，逐项录入实际结果 */
    public WeighingSheetView weighingSheet(CurrentUser operator, long orderId) {
        if (!operator.hasRole("ADMIN") && !operator.hasRole("MERCHANT")) {
            throw new ResponseStatusException(FORBIDDEN, "merchant or admin role is required");
        }
        OrderSnapshot order = requireAdjustableOrder(operator, orderId);
        List<WeighingSheetItem> items = tradeJdbcTemplate.query("""
                SELECT id, product_name_snapshot, weight_grams, user_goods_amount,
                       actual_weight_grams, actual_goods_amount, weighed_at
                FROM order_items WHERE order_id = ? ORDER BY id
                """, (rs, row) -> new WeighingSheetItem(rs.getLong("id"), rs.getString("product_name_snapshot"),
                rs.getInt("weight_grams"), rs.getBigDecimal("user_goods_amount"),
                (Integer) rs.getObject("actual_weight_grams"), rs.getBigDecimal("actual_goods_amount"),
                rs.getObject("weighed_at", LocalDateTime.class)), orderId);
        return new WeighingSheetView(order.id(), order.status(), order.goodsAmount(), items);
    }

    private OrderSnapshot requireAdjustableOrder(CurrentUser operator, long orderId) {
        OrderSnapshot order = tradeJdbcTemplate.query("""
                SELECT id, merchant_id, user_id, trade_id, goods_amount, status FROM orders WHERE id = ?
                """, (rs, row) -> new OrderSnapshot(rs.getLong("id"), rs.getLong("merchant_id"),
                rs.getLong("user_id"), rs.getLong("trade_id"), rs.getBigDecimal("goods_amount"), rs.getString("status")), orderId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "order not found"));
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
        return order;
    }

    /**
     * 校验逐项称重记录：订单项必须属于该订单，实际克数为正、实际金额非负，
     * 并连同预估值一起形成后续落库与回补所需的快照。
     */
    private List<ItemSnapshot> resolveItemSnapshots(long orderId, List<ItemWeighing> items) {
        List<ItemSnapshot> snapshots = new ArrayList<>();
        for (ItemWeighing item : items) {
            ItemBaseline baseline = tradeJdbcTemplate.query("""
                    SELECT id, weight_grams, user_goods_amount FROM order_items WHERE id = ? AND order_id = ?
                    """, (rs, row) -> new ItemBaseline(rs.getLong("id"), rs.getInt("weight_grams"),
                    rs.getBigDecimal("user_goods_amount")), item.orderItemId(), orderId).stream().findFirst()
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "order item not found in this order"));
            if (item.actualGrams() <= 0) {
                throw new ResponseStatusException(BAD_REQUEST, "actual grams must be positive");
            }
            if (item.actualGoodsAmount() == null || item.actualGoodsAmount().signum() < 0) {
                throw new ResponseStatusException(BAD_REQUEST, "actual goods amount must not be negative");
            }
            snapshots.add(new ItemSnapshot(baseline.id(), baseline.prepaidGrams(), baseline.prepaidGoodsAmount(),
                    item.actualGrams(), item.actualGoodsAmount()));
        }
        return snapshots;
    }

    private AdjustmentView findByIdempotencyKey(String key) {
        return tradeJdbcTemplate.query("""
                SELECT id, order_id, merchant_id, prepaid_goods_amount, actual_goods_amount, actual_grams,
                       inventory_adjust_grams, action, refund_amount, absorbed_amount, created_at
                FROM weighing_adjustments WHERE idempotency_key = ?
                """, (rs, row) -> new AdjustmentView("WEIGH-" + rs.getLong("order_id") + "-" + key,
                rs.getLong("order_id"), rs.getLong("merchant_id"), rs.getBigDecimal("prepaid_goods_amount"),
                rs.getBigDecimal("actual_goods_amount"), (Integer) rs.getObject("actual_grams"),
                rs.getInt("inventory_adjust_grams"), rs.getString("action"), rs.getBigDecimal("refund_amount"),
                rs.getBigDecimal("absorbed_amount"), rs.getObject("created_at", LocalDateTime.class)), key)
                .stream().findFirst().orElse(null);
    }

    /**
     * 整单称重：按订单全部批次的预占克数分摊差额。调用方未提供逐项结果时走这条路径，
     * 订单没有批次分配记录时不调整，保持既有非批次库存场景可用。
     */
    private int adjustBatchGrams(long orderId, int actualGrams, String idempotencyKey) {
        List<BatchAllocation> allocations = tradeJdbcTemplate.query("""
                SELECT allocation.batch_id, allocation.warehouse_id, SUM(allocation.allocated_grams) grams
                FROM order_item_batch_allocations allocation
                JOIN order_items item ON item.id = allocation.order_item_id
                WHERE item.order_id = ?
                GROUP BY allocation.batch_id, allocation.warehouse_id
                ORDER BY allocation.batch_id
                """, (rs, row) -> new BatchAllocation(rs.getLong("batch_id"), rs.getLong("warehouse_id"),
                rs.getInt("grams")), orderId);
        if (allocations.isEmpty()) {
            return 0;
        }
        int prepaidGrams = allocations.stream().mapToInt(BatchAllocation::grams).sum();
        tradeJdbcTemplate.update("UPDATE weighing_adjustments SET prepaid_grams = ? WHERE idempotency_key = ?",
                prepaidGrams, idempotencyKey);
        int difference = applyGramDifference(prepaidGrams, actualGrams, allocations);
        tradeJdbcTemplate.update("""
                UPDATE weighing_adjustments SET inventory_adjust_grams = ?, inventory_adjusted_at = CURRENT_TIMESTAMP
                WHERE idempotency_key = ?
                """, difference, idempotencyKey);
        return difference;
    }

    /**
     * 逐项称重：每个订单项按自身批次预占比回补差额，并把实际值与项级差额落到订单项与明细表。
     * 同一订单的不同商品偏差方向可能相反，逐项处理比整单统一分摊更贴近实际拣货结果。
     */
    private int applyItemWeighings(long orderId, List<ItemSnapshot> snapshots, String adjustmentId) {
        int totalAdjustGrams = 0;
        for (ItemSnapshot snapshot : snapshots) {
            List<BatchAllocation> allocations = tradeJdbcTemplate.query("""
                    SELECT allocation.batch_id, allocation.warehouse_id, allocation.allocated_grams grams
                    FROM order_item_batch_allocations allocation
                    WHERE allocation.order_item_id = ?
                    ORDER BY allocation.batch_id
                    """, (rs, row) -> new BatchAllocation(rs.getLong("batch_id"), rs.getLong("warehouse_id"),
                    rs.getInt("grams")), snapshot.orderItemId());
            int adjustGrams = applyGramDifference(snapshot.prepaidGrams(), snapshot.actualGrams(), allocations);
            tradeJdbcTemplate.update("""
                    UPDATE order_items SET actual_weight_grams = ?, actual_goods_amount = ?, weighed_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, snapshot.actualGrams(), snapshot.actualGoodsAmount(), snapshot.orderItemId());
            tradeJdbcTemplate.update("""
                    INSERT INTO order_item_weighings (order_id, order_item_id, adjustment_id, prepaid_grams, actual_grams,
                        prepaid_goods_amount, actual_goods_amount, inventory_adjust_grams)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE actual_grams = VALUES(actual_grams),
                        actual_goods_amount = VALUES(actual_goods_amount),
                        inventory_adjust_grams = VALUES(inventory_adjust_grams)
                    """, orderId, snapshot.orderItemId(), adjustmentId, snapshot.prepaidGrams(), snapshot.actualGrams(),
                    snapshot.prepaidGoodsAmount(), snapshot.actualGoodsAmount(), adjustGrams);
            totalAdjustGrams += adjustGrams;
        }
        return totalAdjustGrams;
    }

    /**
     * 把克数差额按各批次预占克数比例分摊到批次可用克数：实际更重则补扣，更轻则退回。
     * 最后一批兜底剩余，保证分摊总量与差额完全一致；批次不足或不存在时抛出并回滚整单。
     */
    private int applyGramDifference(int prepaidGrams, int actualGrams, List<BatchAllocation> allocations) {
        if (allocations.isEmpty() || prepaidGrams <= 0) {
            return 0;
        }
        long difference = (long) actualGrams - prepaidGrams;
        if (difference == 0) {
            return 0;
        }
        long remaining = Math.abs(difference);
        for (int index = 0; index < allocations.size(); index++) {
            BatchAllocation allocation = allocations.get(index);
            long share = index == allocations.size() - 1
                    ? remaining
                    : Math.round((double) allocation.grams() / prepaidGrams * Math.abs(difference));
            share = Math.min(share, remaining);
            if (share <= 0) {
                continue;
            }
            int updated;
            if (difference > 0) {
                updated = tradeJdbcTemplate.update("""
                        UPDATE freshmart_merchant.inventory_batches SET available_grams = available_grams - ?
                        WHERE id = ? AND warehouse_id = ? AND available_grams >= ?
                        """, share, allocation.batchId(), allocation.warehouseId(), share);
                if (updated == 0) {
                    throw new ResponseStatusException(CONFLICT, "inventory is insufficient for the heavier weighing result");
                }
            } else {
                updated = tradeJdbcTemplate.update("""
                        UPDATE freshmart_merchant.inventory_batches SET available_grams = available_grams + ?
                        WHERE id = ? AND warehouse_id = ?
                        """, share, allocation.batchId(), allocation.warehouseId());
                if (updated == 0) {
                    throw new ResponseStatusException(CONFLICT, "inventory batch no longer exists");
                }
            }
            remaining -= share;
        }
        return (int) difference;
    }

    private void settle(OrderSnapshot order, WeighingSettlementPolicy.Settlement settlement, String adjustmentId) {
        // 金额无差额时不存在资金动作：既不退款也不记账，避免产生 0 元流水与无谓的钱包依赖
        if (settlement.refundAmount().signum() == 0 && settlement.absorbedAmount().signum() == 0) {
            markSettled(order.id(), "WEIGH-NO-DIFF-" + order.id());
            return;
        }
        if (settlement.action() == WeighingSettlementPolicy.SettlementAction.REFUND_USER) {
            String reference = "WEIGH-REFUND-" + order.id();
            userJdbcTemplate.update("""
                    UPDATE wallet_accounts SET balance = balance + ?, updated_at = CURRENT_TIMESTAMP
                    WHERE user_id = ? AND status = 'ACTIVE'
                    """, settlement.refundAmount(), order.userId());
            BigDecimal balance = userJdbcTemplate.query("SELECT balance FROM wallet_accounts WHERE user_id = ?",
                    (rs, row) -> rs.getBigDecimal(1), order.userId()).stream().findFirst()
                    .orElseThrow(() -> new ResponseStatusException(CONFLICT, "user wallet is unavailable"));
            userJdbcTemplate.update("""
                    INSERT IGNORE INTO wallet_transactions (wallet_id, trade_id, transaction_type, amount, balance_after, idempotency_key)
                    SELECT id, ?, 'WEIGHING_REFUND', ?, ?, ? FROM wallet_accounts WHERE user_id = ?
                    """, order.tradeId(), settlement.refundAmount(), balance, reference, order.userId());
            markSettled(order.id(), reference);
        } else if (settlement.action() == WeighingSettlementPolicy.SettlementAction.PLATFORM_ABSORB) {
            String reference = "WEIGH-ABSORB-" + order.id();
            tradeJdbcTemplate.update("""
                    INSERT IGNORE INTO fee_ledgers (trade_id, order_id, merchant_id, fee_type, amount, direction,
                        reference_type, reference_id, idempotency_key)
                    VALUES (?, ?, ?, 'WEIGHING_PLATFORM_ABSORB', ?, 'DEBIT', 'WEIGHING', ?, ?)
                    """, order.tradeId(), order.id(), order.merchantId(), settlement.absorbedAmount(), adjustmentId, reference);
            markSettled(order.id(), reference);
        }
    }

    private void markSettled(long orderId, String reference) {
        tradeJdbcTemplate.update("""
                UPDATE weighing_adjustments SET settled_at = CURRENT_TIMESTAMP, settlement_reference = ?
                WHERE order_id = ? AND settled_at IS NULL
                """, reference, orderId);
    }

    private record OrderSnapshot(long id, long merchantId, long userId, long tradeId, BigDecimal goodsAmount, String status) {
    }

    private record BatchAllocation(long batchId, long warehouseId, int grams) {
    }

    private record ItemBaseline(long id, int prepaidGrams, BigDecimal prepaidGoodsAmount) {
    }

    private record ItemSnapshot(long orderItemId, int prepaidGrams, BigDecimal prepaidGoodsAmount,
            int actualGrams, BigDecimal actualGoodsAmount) {
    }

    /** 调用方提交的单项称重结果 */
    public record ItemWeighing(long orderItemId, int actualGrams, BigDecimal actualGoodsAmount) {
    }

    public record WeighingSheetItem(long orderItemId, String productName, int prepaidGrams,
            BigDecimal prepaidGoodsAmount, Integer actualGrams, BigDecimal actualGoodsAmount, LocalDateTime weighedAt) {
    }

    public record WeighingSheetView(long orderId, String status, BigDecimal prepaidGoodsAmount,
            List<WeighingSheetItem> items) {
    }

    public record AdjustmentView(String adjustmentId, long orderId, long merchantId, BigDecimal prepaidGoodsAmount,
            BigDecimal actualGoodsAmount, Integer actualGrams, int inventoryAdjustGrams, String action,
            BigDecimal refundAmount, BigDecimal absorbedAmount, LocalDateTime createdAt) {
    }
}
