package com.freshmart.payment;

import java.math.BigDecimal;

public interface RefundAdapter {
    RefundResult createRefund(long paymentId, long orderId, String refundNo, BigDecimal amount, String idempotencyKey, long operatorId);
    RefundResult completeManualRefund(String refundNo, long operatorId);
    RefundResult failManualRefund(String refundNo, long operatorId, String reason);
    RefundResult retryManualRefund(String refundNo, long operatorId, String reason);
}
