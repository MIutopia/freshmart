package com.freshmart.payment;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentAdapter {
    PaymentResult createPayment(long tradeId, String tradeNo, BigDecimal amount, String idempotencyKey);
    PaymentResult submitProof(String paymentNo, String proofUrl, String remarkText, long operatorId);
    PaymentResult verifyPayment(String paymentNo, long operatorId, boolean approved, String note);
    PaymentResult matchBill(String paymentNo, String billTransactionId, BigDecimal billAmount, String direction, String remarkText, long operatorId);
    PaymentBillImportResult importBill(String fileName, long operatorId, List<PaymentBillEntry> entries);
}
