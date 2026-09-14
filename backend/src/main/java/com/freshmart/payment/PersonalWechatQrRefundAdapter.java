package com.freshmart.payment;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PersonalWechatQrRefundAdapter implements RefundAdapter {
    private final JdbcTemplate jdbc;
    private final FinancialStatusLogService statusLogService;

    public PersonalWechatQrRefundAdapter(@Qualifier("tradeJdbcTemplate") JdbcTemplate jdbc,
            FinancialStatusLogService statusLogService) {
        this.jdbc = jdbc;
        this.statusLogService = statusLogService;
    }

    @Override
    @Transactional("tradeTransactionManager")
    public RefundResult createRefund(long paymentId, long orderId, String refundNo, BigDecimal amount, String idempotencyKey, long operatorId) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("refund amount must be positive");
        RefundState request = jdbc.query("SELECT payment_id, order_id, amount, status, idempotency_key FROM refund_orders WHERE refund_no = ? FOR UPDATE",
                (rs, row) -> new RefundState(rs.getLong(1), rs.getLong(2), rs.getBigDecimal(3), rs.getString(4), rs.getString(5)), refundNo)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("refund request not found"));
        if (request.paymentId() != paymentId || request.orderId() != orderId || request.amount().compareTo(amount) != 0
                || !request.idempotencyKey().equals(idempotencyKey)) {
            throw new IllegalArgumentException("refund request does not match adapter arguments");
        }
        BigDecimal paid = jdbc.query("SELECT amount FROM payment_orders WHERE id = ? AND status = 'PAID'", (rs, row) -> rs.getBigDecimal(1), paymentId)
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("payment is not paid"));
        if (amount.compareTo(paid) > 0) throw new IllegalArgumentException("refund amount exceeds paid amount");
        if ("MANUAL_PROCESS".equals(request.status())) return new RefundResult(refundNo, RefundStatus.MANUAL_PROCESS, amount, "待人工微信转账");
        if (!"PENDING".equals(request.status())) throw new IllegalStateException("refund request cannot enter manual processing");
        jdbc.update("UPDATE refund_orders SET status = 'MANUAL_PROCESS', manual_refund_status = 'PENDING' WHERE refund_no = ? AND status = 'PENDING'", refundNo);
        statusLogService.record("REFUND", refundNo, "PENDING", "MANUAL_PROCESS", "REFUND_MANUAL_PROCESS_CREATED", operatorId, "审核通过，等待人工转账", "MANUAL");
        return new RefundResult(refundNo, RefundStatus.MANUAL_PROCESS, amount, "待人工微信转账");
    }

    @Override
    @Transactional("tradeTransactionManager")
    public RefundResult completeManualRefund(String refundNo, long operatorId) {
        String status = jdbc.query("SELECT status FROM refund_orders WHERE refund_no = ?", (rs, row) -> rs.getString(1), refundNo).stream().findFirst().orElseThrow();
        if ("REFUND_SUCCESS".equals(status)) return find(refundNo, RefundStatus.REFUND_SUCCESS, "人工退款已确认");
        if (!"MANUAL_PROCESS".equals(status)) throw new IllegalStateException("refund is not awaiting manual completion");
        BigDecimal amount = jdbc.queryForObject("SELECT amount FROM refund_orders WHERE refund_no = ?", BigDecimal.class, refundNo);
        int updated = jdbc.update("UPDATE refund_orders SET status = 'REFUND_SUCCESS', manual_refund_status = 'COMPLETED', manual_refund_completed_at = CURRENT_TIMESTAMP, manual_refund_operator_id = ?, refunded_at = CURRENT_TIMESTAMP WHERE refund_no = ? AND status = 'MANUAL_PROCESS'", operatorId, refundNo);
        if (updated == 0) throw new IllegalStateException("refund completion was not persisted");
        statusLogService.record("REFUND", refundNo, "MANUAL_PROCESS", "REFUND_SUCCESS", "REFUND_MANUAL_COMPLETED", operatorId, "人工退款已确认", "MANUAL");
        return new RefundResult(refundNo, RefundStatus.REFUND_SUCCESS, amount, "人工退款已确认");
    }

    @Override
    @Transactional("tradeTransactionManager")
    public RefundResult failManualRefund(String refundNo, long operatorId, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("failure reason is required");
        int updated = jdbc.update("UPDATE refund_orders SET status = 'REFUND_FAIL', manual_refund_status = 'FAILED', manual_refund_operator_id = ?, manual_refund_failure_reason = ? WHERE refund_no = ? AND status = 'MANUAL_PROCESS'", operatorId, reason, refundNo);
        if (updated == 0) {
            String status = jdbc.query("SELECT status FROM refund_orders WHERE refund_no = ?", (rs, row) -> rs.getString(1), refundNo).stream().findFirst().orElseThrow();
            if ("REFUND_FAIL".equals(status)) return find(refundNo, RefundStatus.REFUND_FAIL, reason);
            throw new IllegalStateException("refund is not awaiting manual completion");
        }
        statusLogService.record("REFUND", refundNo, "MANUAL_PROCESS", "REFUND_FAIL", "REFUND_MANUAL_FAILED", operatorId, reason, "MANUAL");
        BigDecimal amount = jdbc.queryForObject("SELECT amount FROM refund_orders WHERE refund_no = ?", BigDecimal.class, refundNo);
        return new RefundResult(refundNo, RefundStatus.REFUND_FAIL, amount, reason);
    }

    @Override
    @Transactional("tradeTransactionManager")
    public RefundResult retryManualRefund(String refundNo, long operatorId, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("retry reason is required");
        int updated = jdbc.update("UPDATE refund_orders SET status = 'MANUAL_PROCESS', manual_refund_status = 'PENDING', manual_refund_operator_id = ?, manual_refund_failure_reason = NULL WHERE refund_no = ? AND status = 'REFUND_FAIL'", operatorId, refundNo);
        if (updated == 0) throw new IllegalStateException("refund is not eligible for retry");
        statusLogService.record("REFUND", refundNo, "REFUND_FAIL", "MANUAL_PROCESS", "REFUND_MANUAL_RETRY", operatorId, reason, "MANUAL");
        return find(refundNo, RefundStatus.MANUAL_PROCESS, "人工退款已重新发起");
    }

    private RefundResult find(String refundNo, RefundStatus status, String message) {
        BigDecimal amount = jdbc.queryForObject("SELECT amount FROM refund_orders WHERE refund_no = ?", BigDecimal.class, refundNo);
        return new RefundResult(refundNo, status, amount, message);
    }

    private record RefundState(long paymentId, long orderId, BigDecimal amount, String status, String idempotencyKey) {
    }
}
