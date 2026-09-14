package com.freshmart.refund;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshmart.auth.CurrentUser;
import com.freshmart.platform.PlatformRuleService;
import com.freshmart.media.MediaStorageService;
import com.freshmart.payment.PaymentAdapterFactory;
import com.freshmart.payment.FinancialStatusLogService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class RefundService {
    private final JdbcTemplate tradeJdbcTemplate;
    private final JdbcTemplate deliveryJdbcTemplate;
    private final JdbcTemplate userJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PlatformRuleService platformRuleService;
    private final int windowMinutes;
    private final PaymentAdapterFactory paymentAdapterFactory;
    private final FinancialStatusLogService statusLogService;
    private final MediaStorageService mediaStorageService;
    private final TransactionTemplate userTransactionTemplate;

    public RefundService(@Qualifier("tradeJdbcTemplate") JdbcTemplate tradeJdbcTemplate,
            @Qualifier("deliveryJdbcTemplate") JdbcTemplate deliveryJdbcTemplate,
            @Qualifier("userJdbcTemplate") JdbcTemplate userJdbcTemplate, ObjectMapper objectMapper,
            PlatformRuleService platformRuleService,
            @Value("${commerce.refund.default-window-minutes:1440}") int windowMinutes,
            PaymentAdapterFactory paymentAdapterFactory, FinancialStatusLogService statusLogService,
            MediaStorageService mediaStorageService,
            @Qualifier("userTransactionManager") PlatformTransactionManager userTransactionManager) {
        this.tradeJdbcTemplate = tradeJdbcTemplate;
        this.deliveryJdbcTemplate = deliveryJdbcTemplate;
        this.userJdbcTemplate = userJdbcTemplate;
        this.objectMapper = objectMapper;
        this.platformRuleService = platformRuleService;
        this.windowMinutes = windowMinutes;
        this.paymentAdapterFactory = paymentAdapterFactory;
        this.statusLogService = statusLogService;
        this.mediaStorageService = mediaStorageService;
        this.userTransactionTemplate = new TransactionTemplate(userTransactionManager);
    }

    @Transactional("tradeTransactionManager")
    public RefundView apply(CurrentUser user, long orderId, String issueType, String description,
            List<String> images, String idempotencyKey) {
        RefundView existing = findByIdempotencyKey(user.userId(), idempotencyKey);
        if (existing != null) {
            return existing;
        }
        if (!"OUT_OF_STOCK".equals(issueType) && !"QUALITY".equals(issueType)) {
            throw new ResponseStatusException(BAD_REQUEST, "issue type must be OUT_OF_STOCK or QUALITY");
        }
        if (description == null || description.isBlank() || images == null || images.isEmpty()
                || images.stream().anyMatch(image -> image == null || image.isBlank())) {
            throw new ResponseStatusException(BAD_REQUEST, "refund evidence image and description are required");
        }
        for (String image : images) {
            MediaStorageService.MediaAssetView evidence = mediaStorageService.describeOwned(user, image);
            if (!evidence.contentType().startsWith("image/")) {
                throw new ResponseStatusException(BAD_REQUEST, "refund evidence must be an image");
            }
        }
        OrderPayment order = tradeJdbcTemplate.query("""
                SELECT orders.id, orders.payable_amount, orders.user_id, payment.id AS payment_id
                FROM orders JOIN payment_orders payment ON payment.trade_id = orders.trade_id AND payment.status = 'PAID'
                WHERE orders.id = ? AND orders.user_id = ?
                ORDER BY payment.id DESC LIMIT 1
                """, (rs, row) -> new OrderPayment(rs.getLong("id"), rs.getBigDecimal("payable_amount"),
                rs.getLong("user_id"), rs.getLong("payment_id")), orderId, user.userId()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "paid order not found"));
        LocalDateTime deliveredAt = deliveryJdbcTemplate.query("""
                SELECT delivered_at FROM delivery_tasks WHERE order_id = ? AND status = 'DELIVERED'
                """, (rs, row) -> rs.getObject(1, LocalDateTime.class), orderId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "order has not been delivered"));
        int configuredWindowMinutes = refundWindowMinutes(orderId);
        if (deliveredAt.plusMinutes(configuredWindowMinutes).isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(CONFLICT, "refund window has expired");
        }
        String refundNo = "R" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
        tradeJdbcTemplate.update("""
                INSERT INTO refund_orders (refund_no, payment_id, order_id, reason, issue_type,
                                           evidence_description, evidence_images_json, amount, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                """, refundNo, order.paymentId(), order.id(), description.trim(), issueType,
                description.trim(), json(images), order.amount(), idempotencyKey);
        long id = tradeJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        statusLogService.record("REFUND", refundNo, null, "PENDING", "REFUND_APPLIED", user.userId(), "用户提交售后申请", "BUSINESS");
        return new RefundView(id, refundNo, order.id(), issueType, order.amount(), "PENDING", deliveredAt);
    }

    /**
     * 售后窗口按订单商品所属分类的品类取平台规则：水果与蔬菜的保鲜期差异很大，取其中最长的一条，
     * 避免混合订单被较短的窗口提前卡住；全部为 OTHER 或查不到分类时回落到全局默认窗口。
     */
    private int refundWindowMinutes(long orderId) {
        int defaultWindow = platformRuleService.integerOrDefault("refund.default.window.minutes", windowMinutes);
        List<String> scopes = tradeJdbcTemplate.query("""
                SELECT DISTINCT category.product_scope
                FROM order_items item
                JOIN freshmart_merchant.products product ON product.id = item.product_id
                JOIN freshmart_merchant.product_categories category ON category.id = product.category_id
                WHERE item.order_id = ?
                """, (rs, row) -> rs.getString(1), orderId);
        int resolved = 0;
        for (String scope : scopes) {
            resolved = Math.max(resolved, switch (scope == null ? "OTHER" : scope) {
                case "FRUIT" -> platformRuleService.integerOrDefault("refund.fruit.window.minutes", defaultWindow);
                case "VEGETABLE" -> platformRuleService.integerOrDefault("refund.vegetable.window.minutes", defaultWindow);
                default -> defaultWindow;
            });
        }
        return resolved > 0 ? resolved : defaultWindow;
    }

    @Transactional("tradeTransactionManager")
    public RefundView review(CurrentUser admin, long refundId, boolean approved, String reviewNote) {
        RefundDetail refund = tradeJdbcTemplate.query("""
                SELECT refund.id, refund.refund_no, refund.order_id, refund.issue_type, refund.amount, refund.status,
                       payment.provider, payment.trade_id, payment.id AS payment_id, orders.user_id, trade.payable_amount AS trade_payable_amount,
                       delivery.delivered_at
                FROM refund_orders refund
                JOIN payment_orders payment ON payment.id = refund.payment_id
                JOIN orders ON orders.id = refund.order_id
                JOIN trade_orders trade ON trade.id = payment.trade_id
                LEFT JOIN freshmart_delivery.delivery_tasks delivery ON delivery.order_id = refund.order_id
                    AND delivery.status = 'DELIVERED'
                WHERE refund.id = ? FOR UPDATE
                """, (rs, row) -> new RefundDetail(rs.getLong("id"), rs.getString("refund_no"),
                rs.getLong("order_id"), rs.getString("issue_type"), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getString("provider"), rs.getLong("trade_id"), rs.getLong("user_id"),
                rs.getBigDecimal("trade_payable_amount"), rs.getObject("delivered_at", LocalDateTime.class), rs.getLong("payment_id")), refundId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "refund request not found"));
        if (!"PENDING".equals(refund.status())) {
            throw new ResponseStatusException(CONFLICT, "refund request has already been reviewed");
        }
        if (!approved) {
            tradeJdbcTemplate.update("""
                    UPDATE refund_orders SET status = 'REJECTED', reason = CONCAT(reason, '\nReview: ', ?),
                        reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'PENDING'
                    """, reviewNote.trim(), admin.userId(), refund.id());
            statusLogService.record("REFUND", refund.refundNo(), "PENDING", "REJECTED", "REFUND_REVIEW_REJECTED", admin.userId(), reviewNote, "MANUAL");
            return refund.toView("REJECTED");
        }
        if ("BALANCE".equals(refund.provider())) {
            refundWallet(refund);
            completeRefund(refund, reviewNote, admin.userId());
            return refund.toView("REFUND_SUCCESS");
        }
        // 必须复用申请时写入的幂等键：退款适配器会校验它与 refund_orders.idempotency_key 一致
        String idempotencyKey = tradeJdbcTemplate.queryForObject(
                "SELECT idempotency_key FROM refund_orders WHERE id = ?", String.class, refund.id());
        paymentAdapterFactory.refund().createRefund(refund.paymentId(), refund.orderId(), refund.refundNo(),
                refund.amount(), idempotencyKey, admin.userId());
        tradeJdbcTemplate.update("UPDATE refund_orders SET reason = CONCAT(reason, '\nReview: ', ?), reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'MANUAL_PROCESS'", reviewNote.trim(), admin.userId(), refund.id());
        return refund.toView("MANUAL_PROCESS");
    }

    @Transactional("tradeTransactionManager")
    public RefundView completeManualRefund(CurrentUser admin, String refundNo) {
        paymentAdapterFactory.refund().completeManualRefund(refundNo, admin.userId());
        RefundDetail refund = findRefund(refundNo);
        completeRefund(refund, "人工退款完成", admin.userId());
        return refund.toView("REFUND_SUCCESS");
    }

    @Transactional("tradeTransactionManager")
    public RefundView failManualRefund(CurrentUser admin, String refundNo, String reason) {
        paymentAdapterFactory.refund().failManualRefund(refundNo, admin.userId(), reason);
        RefundDetail refund = findRefund(refundNo);
        return refund.toView("REFUND_FAIL");
    }

    @Transactional("tradeTransactionManager")
    public RefundView retryManualRefund(CurrentUser admin, String refundNo, String reason) {
        paymentAdapterFactory.refund().retryManualRefund(refundNo, admin.userId(), reason);
        return findRefund(refundNo).toView("MANUAL_PROCESS");
    }

    @Transactional("tradeTransactionManager")
    public List<RefundView> listMine(CurrentUser user) {
        return tradeJdbcTemplate.query("""
                SELECT refund.id, refund.refund_no, refund.order_id, refund.issue_type, refund.amount, refund.status,
                       delivery.delivered_at
                FROM refund_orders refund
                JOIN orders ON orders.id = refund.order_id
                LEFT JOIN freshmart_delivery.delivery_tasks delivery
                       ON delivery.order_id = refund.order_id AND delivery.status = 'DELIVERED'
                WHERE orders.user_id = ?
                ORDER BY refund.id DESC LIMIT 100
                """, (rs, row) -> refundView(rs), user.userId());
    }

    public List<RefundView> listAll(String status) {
        return tradeJdbcTemplate.query("""
                SELECT refund.id, refund.refund_no, refund.order_id, refund.issue_type, refund.amount, refund.status,
                       delivery.delivered_at
                FROM refund_orders refund
                LEFT JOIN freshmart_delivery.delivery_tasks delivery
                       ON delivery.order_id = refund.order_id AND delivery.status = 'DELIVERED'
                WHERE (? IS NULL OR refund.status = ?)
                ORDER BY refund.id DESC LIMIT 200
                """, (rs, row) -> refundView(rs), status, status);
    }

    private RefundView refundView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RefundView(rs.getLong("id"), rs.getString("refund_no"), rs.getLong("order_id"),
                rs.getString("issue_type"), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getObject("delivered_at", LocalDateTime.class));
    }

    public InventoryDispositionView processInventoryDisposition(CurrentUser operator, String refundNo, String disposition, String note) {
        if (!List.of("RESTOCKED", "DISCARDED").contains(disposition)) {
            throw new ResponseStatusException(BAD_REQUEST, "disposition must be RESTOCKED or DISCARDED");
        }
        RefundDetail refund = findRefund(refundNo);
        InventoryDisposition current = tradeJdbcTemplate.query("SELECT id, order_id, disposition, reason FROM refund_inventory_dispositions WHERE refund_id = ? FOR UPDATE",
                (rs, row) -> new InventoryDisposition(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4)), refund.id()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "inventory disposition not found"));
        if (!"PENDING_INSPECTION".equals(current.disposition())) {
            throw new ResponseStatusException(CONFLICT, "inventory disposition has already been processed");
        }
        if ("RESTOCKED".equals(disposition)) {
            restockOrderBatches(refund.orderId());
        }
        String cleanNote = note == null ? null : note.trim();
        tradeJdbcTemplate.update("UPDATE refund_inventory_dispositions SET disposition = ?, processed_by = ?, processed_at = CURRENT_TIMESTAMP, processing_note = ? WHERE id = ? AND disposition = 'PENDING_INSPECTION'", disposition, operator.userId(), cleanNote, current.id());
        statusLogService.record("REFUND_INVENTORY", refundNo, "PENDING_INSPECTION", disposition, "REFUND_INVENTORY_DISPOSITION_PROCESSED", operator.userId(), cleanNote, "MANUAL");
        return new InventoryDispositionView(refundNo, refund.orderId(), disposition, current.reason(), operator.userId(), LocalDateTime.now(), cleanNote);
    }

    /**
     * 退货回库：以实际出库克数回补批次。逐项称重过的订单项按实际净重回补，未称重的回落到预占克数，
     * 否则实重与预估不一致时会造成批次账实偏离。同一订单项跨多个批次时按各自预占克数比例分摊，
     * 最后一批兜底剩余，避免逐项抹零；同一批次的多项回补在内存中合并后只写一次。
     */
    private void restockOrderBatches(long orderId) {
        List<RestockAllocation> allocations = tradeJdbcTemplate.query("""
                SELECT allocation.batch_id, allocation.warehouse_id, allocation.order_item_id,
                       allocation.allocated_grams, item.actual_weight_grams
                FROM order_item_batch_allocations allocation
                JOIN order_items item ON item.id = allocation.order_item_id
                WHERE item.order_id = ?
                ORDER BY allocation.order_item_id, allocation.batch_id
                """, (rs, row) -> new RestockAllocation(rs.getLong("batch_id"), rs.getLong("warehouse_id"),
                rs.getLong("order_item_id"), rs.getInt("allocated_grams"),
                (Integer) rs.getObject("actual_weight_grams")), orderId);
        if (allocations.isEmpty()) {
            return;
        }
        Map<Long, List<RestockAllocation>> byItem = new LinkedHashMap<>();
        for (RestockAllocation allocation : allocations) {
            byItem.computeIfAbsent(allocation.orderItemId(), key -> new ArrayList<>()).add(allocation);
        }
        Map<String, RestockTarget> targets = new LinkedHashMap<>();
        for (List<RestockAllocation> itemAllocations : byItem.values()) {
            int allocatedTotal = itemAllocations.stream().mapToInt(RestockAllocation::allocatedGrams).sum();
            if (allocatedTotal <= 0) {
                continue;
            }
            Integer actualWeight = itemAllocations.get(0).actualWeightGrams();
            int effectiveGrams = actualWeight != null && actualWeight > 0 ? actualWeight : allocatedTotal;
            if (effectiveGrams <= 0) {
                continue;
            }
            int remaining = effectiveGrams;
            for (int index = 0; index < itemAllocations.size(); index++) {
                RestockAllocation allocation = itemAllocations.get(index);
                int share = index == itemAllocations.size() - 1
                        ? remaining
                        : (int) Math.round((double) allocation.allocatedGrams() / allocatedTotal * effectiveGrams);
                share = Math.min(share, remaining);
                if (share <= 0) {
                    continue;
                }
                String key = allocation.batchId() + ":" + allocation.warehouseId();
                targets.merge(key, new RestockTarget(allocation.batchId(), allocation.warehouseId(), share),
                        (left, right) -> new RestockTarget(left.batchId(), left.warehouseId(), left.grams() + right.grams()));
                remaining -= share;
            }
        }
        for (RestockTarget target : targets.values()) {
            if (tradeJdbcTemplate.update("""
                    UPDATE freshmart_merchant.inventory_batches SET available_grams = available_grams + ?
                    WHERE id = ? AND warehouse_id = ?
                    """, target.grams(), target.batchId(), target.warehouseId()) == 0) {
                throw new ResponseStatusException(CONFLICT, "inventory batch no longer exists");
            }
        }
    }

    private void completeRefund(RefundDetail refund, String reviewNote, long operatorId) {
        Integer existingLedger = tradeJdbcTemplate.queryForObject("SELECT COUNT(*) FROM fee_ledgers WHERE idempotency_key = ?", Integer.class, "REFUND-" + refund.refundNo());
        if (existingLedger != null && existingLedger > 0) {
            return;
        }
        int updated = tradeJdbcTemplate.update("""
                UPDATE refund_orders SET status = 'REFUND_SUCCESS', reason = CONCAT(reason, '\nReview: ', ?),
                    reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP, refunded_at = CURRENT_TIMESTAMP,
                    manual_refund_status = CASE WHEN ? = 'PERSONAL_WECHAT_QR' THEN 'COMPLETED' ELSE manual_refund_status END,
                    manual_refund_completed_at = CASE WHEN ? = 'PERSONAL_WECHAT_QR' THEN CURRENT_TIMESTAMP ELSE manual_refund_completed_at END,
                    manual_refund_operator_id = CASE WHEN ? = 'PERSONAL_WECHAT_QR' THEN ? ELSE manual_refund_operator_id END
                WHERE id = ? AND status IN ('PENDING','MANUAL_PROCESS')
                """, reviewNote.trim(), operatorId, refund.provider(), refund.provider(), refund.provider(), operatorId, refund.id());
        if (updated == 0 && !"REFUND_SUCCESS".equals(refund.status())) {
            return;
        }
        if (updated > 0) {
            statusLogService.record("REFUND", refund.refundNo(), refund.status(), "REFUND_SUCCESS", "REFUND_COMPLETED", operatorId, reviewNote, "MANUAL");
        }
        tradeJdbcTemplate.update("UPDATE orders SET status = 'REFUNDED' WHERE id = ?", refund.orderId());
        tradeJdbcTemplate.update("""
                INSERT INTO fee_ledgers (trade_id, order_id, fee_type, amount, direction, reference_type, reference_id, idempotency_key)
                VALUES (?, ?, 'REFUND', ?, 'DEBIT', 'REFUND', ?, ?)
                """, refund.tradeId(), refund.orderId(), refund.amount(), refund.refundNo(), "REFUND-" + refund.refundNo());
        reverseSettlement(refund);
        recordInventoryDisposition(refund);
        rollbackPoints(refund);
        markPaymentRefundedWhenTradeFullyRefunded(refund, operatorId);
    }

    private void markPaymentRefundedWhenTradeFullyRefunded(RefundDetail refund, long operatorId) {
        Integer remaining = tradeJdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE trade_id = ? AND status <> 'REFUNDED'", Integer.class, refund.tradeId());
        if (remaining != null && remaining == 0) {
            int updated = tradeJdbcTemplate.update("UPDATE payment_orders SET status = 'REFUNDED' WHERE id = ? AND status = 'PAID'", refund.paymentId());
            if (updated > 0) {
                String paymentNo = tradeJdbcTemplate.queryForObject("SELECT payment_no FROM payment_orders WHERE id = ?", String.class, refund.paymentId());
                statusLogService.record("PAYMENT", paymentNo, "PAID", "REFUNDED", "PAYMENT_FULLY_REFUNDED", operatorId, "交易全部子订单退款成功", "MANUAL");
            }
        }
    }

    private RefundDetail findRefund(String refundNo) {
        return tradeJdbcTemplate.query("""
                SELECT refund.id, refund.refund_no, refund.order_id, refund.issue_type, refund.amount, refund.status,
                       payment.provider, payment.trade_id, orders.user_id, trade.payable_amount AS trade_payable_amount,
                       payment.id AS payment_id, delivery.delivered_at
                FROM refund_orders refund JOIN payment_orders payment ON payment.id = refund.payment_id
                JOIN orders ON orders.id = refund.order_id JOIN trade_orders trade ON trade.id = payment.trade_id
                LEFT JOIN freshmart_delivery.delivery_tasks delivery ON delivery.order_id = refund.order_id AND delivery.status = 'DELIVERED'
                WHERE refund.refund_no = ?
                """, (rs, row) -> new RefundDetail(rs.getLong("id"), rs.getString("refund_no"), rs.getLong("order_id"), rs.getString("issue_type"), rs.getBigDecimal("amount"), rs.getString("status"), rs.getString("provider"), rs.getLong("trade_id"), rs.getLong("user_id"), rs.getBigDecimal("trade_payable_amount"), rs.getObject("delivered_at", LocalDateTime.class), rs.getLong("payment_id")), refundNo).stream().findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "refund request not found"));
    }

    private void reverseSettlement(RefundDetail refund) {
        Settlement settlement = tradeJdbcTemplate.query("""
                SELECT id, commission_amount FROM merchant_settlements
                WHERE order_id = ? AND status <> 'REVERSED' FOR UPDATE
                """, (rs, row) -> new Settlement(rs.getLong("id"), rs.getBigDecimal("commission_amount")),
                refund.orderId()).stream().findFirst().orElse(null);
        if (settlement == null) {
            return;
        }
        int updated = tradeJdbcTemplate.update("""
                UPDATE merchant_settlements SET status = 'REVERSED', reversed_at = CURRENT_TIMESTAMP,
                    reversal_reason = 'FULL_ORDER_REFUND'
                WHERE id = ? AND status <> 'REVERSED'
                """, settlement.id());
        if (updated > 0) {
            tradeJdbcTemplate.update("""
                    INSERT INTO fee_ledgers (trade_id, order_id, merchant_id, fee_type, amount, direction,
                        reference_type, reference_id, idempotency_key)
                    SELECT ?, ?, merchant_id, 'PLATFORM_COMMISSION_REVERSAL', ?, 'DEBIT', 'REFUND', ?, ?
                    FROM merchant_settlements WHERE id = ?
                    """, refund.tradeId(), refund.orderId(), settlement.commissionAmount(), refund.refundNo(),
                    "COMMISSION-REVERSAL-" + refund.refundNo(), settlement.id());
        }
    }

    private void recordInventoryDisposition(RefundDetail refund) {
        tradeJdbcTemplate.update("""
                INSERT IGNORE INTO refund_inventory_dispositions (refund_id, order_id, disposition, reason)
                VALUES (?, ?, 'PENDING_INSPECTION', ?)
                """, refund.id(), refund.orderId(), refund.issueType() + "_DELIVERED_REFUND");
    }

    private void rollbackPoints(RefundDetail refund) {
        userTransactionTemplate.executeWithoutResult(status -> {
            Integer awarded = userJdbcTemplate.query("""
                    SELECT change_amount FROM points_transactions
                    WHERE user_id = ? AND trade_id = ? AND reason = 'TRADE_PAYMENT'
                    """, (rs, row) -> rs.getInt(1), refund.userId(), refund.tradeId()).stream().findFirst().orElse(0);
            String idempotencyKey = "POINTS-REFUND-" + refund.refundNo();
            userJdbcTemplate.update("INSERT IGNORE INTO user_point_accounts (user_id) VALUES (?)", refund.userId());
            Integer available = userJdbcTemplate.queryForObject(
                    "SELECT available_points FROM user_point_accounts WHERE user_id = ? FOR UPDATE", Integer.class, refund.userId());
            Integer existing = userJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM points_transactions WHERE idempotency_key = ?", Integer.class, idempotencyKey);
            if (existing != null && existing > 0) {
                return;
            }
            int actualRollback = com.freshmart.marketing.PointsCalculator.refundRollback(awarded, refund.amount(),
                    refund.tradePayableAmount(), Math.max(0, available == null ? 0 : available));
            if (actualRollback <= 0) {
                return;
            }
            int balanceAfter = available - actualRollback;
            userJdbcTemplate.update("UPDATE user_point_accounts SET available_points = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                    balanceAfter, refund.userId());
            userJdbcTemplate.update("""
                    INSERT INTO points_transactions (user_id, trade_id, change_amount, balance_after, reason, idempotency_key)
                    VALUES (?, ?, ?, ?, 'REFUND_ROLLBACK', ?)
                    """, refund.userId(), refund.tradeId(), -actualRollback, balanceAfter, idempotencyKey);
        });
    }

    private void refundWallet(RefundDetail refund) {
        userJdbcTemplate.update("UPDATE wallet_accounts SET balance = balance + ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                refund.amount(), refund.userId());
        BigDecimal balance = userJdbcTemplate.query("SELECT balance FROM wallet_accounts WHERE user_id = ?",
                (rs, row) -> rs.getBigDecimal(1), refund.userId()).stream().findFirst().orElseThrow();
        userJdbcTemplate.update("""
                INSERT INTO wallet_transactions (wallet_id, trade_id, transaction_type, amount, balance_after, idempotency_key)
                SELECT id, ?, 'REFUND', ?, ?, ? FROM wallet_accounts WHERE user_id = ?
                """, refund.tradeId(), refund.amount(), balance, "WALLET-REFUND-" + refund.refundNo(), refund.userId());
    }

    private RefundView findByIdempotencyKey(long userId, String key) {
        return tradeJdbcTemplate.query("""
                SELECT refund.id, refund.refund_no, refund.order_id, refund.issue_type, refund.amount,
                       refund.status, delivery.delivered_at
                FROM refund_orders refund JOIN orders ON orders.id = refund.order_id
                LEFT JOIN freshmart_delivery.delivery_tasks delivery ON delivery.order_id = refund.order_id
                    AND delivery.status = 'DELIVERED'
                WHERE refund.idempotency_key = ? AND orders.user_id = ?
                """, (rs, row) -> new RefundView(rs.getLong("id"), rs.getString("refund_no"), rs.getLong("order_id"),
                rs.getString("issue_type"), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getObject("delivered_at", LocalDateTime.class)), key, userId).stream().findFirst().orElse(null);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "refund evidence cannot be serialized");
        }
    }

    private record OrderPayment(long id, BigDecimal amount, long userId, long paymentId) {
    }

    private record RestockAllocation(long batchId, long warehouseId, long orderItemId, int allocatedGrams,
            Integer actualWeightGrams) { }
    private record RestockTarget(long batchId, long warehouseId, int grams) { }
    private record InventoryDisposition(long id, long orderId, String disposition, String reason) { }

    private record RefundDetail(long id, String refundNo, long orderId, String issueType, BigDecimal amount, String status,
            String provider, long tradeId, long userId, BigDecimal tradePayableAmount, LocalDateTime deliveredAt, long paymentId) {
        private RefundView toView(String targetStatus) {
            return new RefundView(id, refundNo, orderId, issueType, amount, targetStatus, deliveredAt);
        }
    }

    private record Settlement(long id, BigDecimal commissionAmount) {
    }

    public record RefundView(long id, String refundNo, long orderId, String issueType, BigDecimal amount,
            String status, LocalDateTime deliveredAt) {
    }

    public record InventoryDispositionView(String refundNo, long orderId, String disposition, String reason,
            long processedBy, LocalDateTime processedAt, String processingNote) { }
}
