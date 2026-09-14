package com.freshmart.payment;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PersonalWechatQrPaymentAdapter implements PaymentAdapter {
    private final JdbcTemplate jdbc;
    private final String qrUrl;
    private final FinancialStatusLogService statusLogService;

    public PersonalWechatQrPaymentAdapter(@Qualifier("tradeJdbcTemplate") JdbcTemplate jdbc,
            @Value("${commerce.payment.personal-wechat-qr-url:}") String qrUrl,
            FinancialStatusLogService statusLogService) {
        this.jdbc = jdbc;
        this.qrUrl = qrUrl;
        this.statusLogService = statusLogService;
    }

    @Override
    @Transactional("tradeTransactionManager")
    public PaymentResult createPayment(long tradeId, String tradeNo, BigDecimal amount, String idempotencyKey) {
        if (qrUrl == null || qrUrl.isBlank()) throw new IllegalStateException("personal WeChat QR code is not configured");
        var existing = jdbc.query("SELECT payment_no, status, amount, code_url, remark_text FROM payment_orders WHERE idempotency_key = ?",
                (rs, row) -> new PaymentResult(rs.getString(1), PaymentStatus.valueOf(rs.getString(2)), rs.getBigDecimal(3), rs.getString(4), rs.getString(5), ""), idempotencyKey);
        if (!existing.isEmpty()) return existing.get(0);
        String paymentNo = "P" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
        String remark = "FreshMart订单" + tradeNo;
        jdbc.update("INSERT INTO payment_orders (payment_no, trade_id, provider, payment_mode, code_url, amount, remark_text, idempotency_key) VALUES (?, ?, 'PERSONAL_WECHAT_QR', 'MANUAL_CONFIRMATION', ?, ?, ?, ?)",
                paymentNo, tradeId, qrUrl.trim(), amount, remark, idempotencyKey);
        statusLogService.record("PAYMENT", paymentNo, null, "PENDING", "PAYMENT_CREATED", null, "创建个人收款码支付单", "SYSTEM");
        return new PaymentResult(paymentNo, PaymentStatus.PENDING, amount, qrUrl.trim(), remark, "请转账时备注系统订单号");
    }

    @Override
    @Transactional("tradeTransactionManager")
    public PaymentResult submitProof(String paymentNo, String proofUrl, String remarkText, long operatorId) {
        if (proofUrl == null || proofUrl.isBlank() || remarkText == null || remarkText.isBlank()) {
            throw new IllegalArgumentException("payment proof and transfer remark are required");
        }
        int updated = jdbc.update("UPDATE payment_orders SET payment_proof_url = ?, remark_text = ?, status = 'PROOF_SUBMITTED' WHERE payment_no = ? AND status = 'PENDING'", proofUrl, remarkText, paymentNo);
        if (updated == 0) throw new IllegalStateException("payment is not awaiting proof");
        jdbc.update("INSERT INTO payment_verifications (payment_no, verification_type, proof_url, remark_text, result) VALUES (?, 'SCREENSHOT_REVIEW', ?, ?, 'PENDING')", paymentNo, proofUrl, remarkText);
        statusLogService.record("PAYMENT", paymentNo, "PENDING", "PROOF_SUBMITTED", "PAYMENT_PROOF_SUBMITTED", operatorId, "用户提交付款凭证", "BUSINESS");
        return find(paymentNo, PaymentStatus.PROOF_SUBMITTED, "付款凭证已提交，等待人工审核");
    }

    @Override
    @Transactional("tradeTransactionManager")
    public PaymentResult verifyPayment(String paymentNo, long operatorId, boolean approved, String note) {
        PaymentResult previous = find(paymentNo, null, "");
        String status = approved ? "PAID" : "ABNORMAL";
        int updated = jdbc.update("UPDATE payment_orders SET status = ?, verified_by = ?, verified_at = CURRENT_TIMESTAMP, paid_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE paid_at END, failure_code = ?, failure_message = ? WHERE payment_no = ? AND status IN ('PENDING','PROOF_SUBMITTED')", status, operatorId, approved, approved ? null : "VERIFICATION_FAILED", approved ? null : note, paymentNo);
        if (updated == 0) throw new IllegalStateException("payment cannot be verified in current state");
        int verificationUpdated = jdbc.update("UPDATE payment_verifications SET result = ?, verified_by = ?, verified_at = CURRENT_TIMESTAMP, failure_message = ? WHERE payment_no = ? AND result = 'PENDING' ORDER BY id DESC LIMIT 1", approved ? "APPROVED" : "REJECTED", operatorId, approved ? null : note, paymentNo);
        if (verificationUpdated == 0) {
            jdbc.update("INSERT INTO payment_verifications (payment_no, verification_type, result, failure_message, verified_by, verified_at) VALUES (?, 'MANUAL_CONFIRMATION', ?, ?, ?, CURRENT_TIMESTAMP)", paymentNo, approved ? "APPROVED" : "REJECTED", approved ? null : note, operatorId);
        }
        statusLogService.record("PAYMENT", paymentNo, previous.status().name(), status, "PAYMENT_PROOF_VERIFIED", operatorId, note, "MANUAL");
        return find(paymentNo, approved ? PaymentStatus.PAID : PaymentStatus.ABNORMAL, approved ? "付款已核验" : note);
    }

    @Override
    @Transactional("tradeTransactionManager")
    public PaymentResult matchBill(String paymentNo, String billTransactionId, BigDecimal billAmount, String direction, String remarkText, long operatorId) {
        if (billTransactionId == null || billTransactionId.isBlank()) {
            throw new IllegalArgumentException("bill transaction id is required");
        }
        if (billAmount == null || billAmount.signum() < 0) {
            throw new IllegalArgumentException("bill amount must not be negative");
        }
        PaymentResult current = find(paymentNo, null, "");
        if (current.status() == PaymentStatus.PAID) {
            return new PaymentResult(current.paymentNo(), current.status(), current.amount(), current.codeUrl(), current.remarkText(), "支付单已核验");
        }
        boolean duplicateTransaction = !jdbc.query("SELECT id FROM payment_verifications WHERE bill_transaction_id = ? LIMIT 1",
                (rs, row) -> rs.getLong(1), billTransactionId.trim()).isEmpty();
        if (duplicateTransaction) {
            markDifference(paymentNo, "DUPLICATE_TRANSACTION", "账单流水号已处理，禁止重复匹配");
            statusLogService.record("PAYMENT", paymentNo, current.status().name(), "ABNORMAL", "PAYMENT_BILL_MATCH", operatorId, "账单流水号已处理，禁止重复匹配", "MANUAL");
            return find(paymentNo, PaymentStatus.ABNORMAL, "账单流水号已处理，禁止重复匹配");
        }
        boolean amountOk = current.amount().compareTo(billAmount) == 0;
        boolean directionOk = isIncome(direction);
        boolean remarkOk = remarkText != null && remarkText.contains(tradeNoFor(current.paymentNo()));
        String result = amountOk && directionOk && remarkOk ? "MATCHED" : "DIFFERENCE";
        jdbc.update("INSERT INTO payment_verifications (payment_no, verification_type, bill_transaction_id, bill_amount, remark_text, result, failure_message, verified_at) VALUES (?, 'BILL_IMPORT_MATCH', ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)", paymentNo, billTransactionId, billAmount, remarkText, result,
                amountOk && directionOk && remarkOk ? null : differenceMessage(amountOk, directionOk, remarkOk));
        if (!amountOk) {
            markDifference(paymentNo, "AMOUNT_MISMATCH", "账单金额与支付单不一致");
        } else if (!directionOk) {
            markDifference(paymentNo, "DIRECTION_INVALID", "账单不是收入方向");
        } else if (!remarkOk) {
            markDifference(paymentNo, "REMARK_MISSING", "账单备注未包含系统订单号");
        } else {
            jdbc.update("UPDATE payment_orders SET status = 'PAID', paid_at = CURRENT_TIMESTAMP, failure_code = NULL, failure_message = NULL WHERE payment_no = ? AND status IN ('PENDING','PROOF_SUBMITTED')", paymentNo);
        }
        PaymentStatus status = amountOk && directionOk && remarkOk ? PaymentStatus.PAID : PaymentStatus.ABNORMAL;
        statusLogService.record("PAYMENT", paymentNo, current.status().name(), status.name(), "PAYMENT_BILL_MATCH", operatorId,
                amountOk && directionOk && remarkOk ? "账单匹配成功" : differenceMessage(amountOk, directionOk, remarkOk), "MANUAL");
        return find(paymentNo, status, result);
    }

    @Override
    @Transactional("tradeTransactionManager")
    public PaymentBillImportResult importBill(String fileName, long operatorId, List<PaymentBillEntry> entries) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("bill file name is required");
        }
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("bill must contain at least one entry");
        }
        Long importId = jdbc.query("SELECT id FROM payment_bill_imports WHERE file_name = ? AND imported_by = ? ORDER BY id DESC LIMIT 1",
                (rs, row) -> rs.getLong(1), fileName.trim(), operatorId).stream().findFirst().orElse(null);
        if (importId != null) {
            Integer total = jdbc.queryForObject("SELECT total_entries FROM payment_bill_imports WHERE id = ?", Integer.class, importId);
            Integer matched = jdbc.queryForObject("SELECT matched_entries FROM payment_bill_imports WHERE id = ?", Integer.class, importId);
            Integer differences = jdbc.queryForObject("SELECT difference_entries FROM payment_bill_imports WHERE id = ?", Integer.class, importId);
            return new PaymentBillImportResult(importId, total, matched, differences);
        }
        jdbc.update("INSERT INTO payment_bill_imports (file_name, imported_by) VALUES (?, ?)", fileName.trim(), operatorId);
        importId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        int matched = 0;
        int differences = 0;
        for (PaymentBillEntry entry : entries) {
            validateEntry(entry);
            boolean duplicate = !jdbc.query("SELECT id FROM payment_bill_entries WHERE transaction_id = ? LIMIT 1",
                    (rs, row) -> rs.getLong(1), entry.transactionId().trim()).isEmpty();
            jdbc.update("INSERT INTO payment_bill_entries (import_id, transaction_id, transaction_time, remark_text, amount, direction, match_result) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    importId, entry.transactionId().trim(), entry.transactionTime(), entry.remarkText(), entry.amount(), entry.direction(), duplicate ? "DUPLICATE" : "UNMATCHED");
            long entryId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            if (duplicate) {
                differences++;
                jdbc.update("INSERT INTO payment_reconciliation_differences (bill_entry_id, difference_type, description) VALUES (?, 'DUPLICATE_TRANSACTION', '账单流水号已导入，未重复匹配')", entryId);
                continue;
            }
            String paymentNo = findPaymentNo(entry.remarkText());
            if (paymentNo == null) {
                differences++;
                jdbc.update("INSERT INTO payment_reconciliation_differences (bill_entry_id, difference_type, description) VALUES (?, 'UNMATCHED_PAYMENT', '账单备注未找到对应系统订单号')", entryId);
                continue;
            }
            PaymentResult result = matchBill(paymentNo, entry.transactionId(), entry.amount(), entry.direction(), entry.remarkText(), operatorId);
            jdbc.update("UPDATE payment_bill_entries SET payment_no = ?, match_result = ? WHERE id = ?",
                    paymentNo, result.status() == PaymentStatus.PAID ? "MATCHED" : "DIFFERENCE", entryId);
            if (result.status() == PaymentStatus.PAID) {
                matched++;
            } else {
                differences++;
            }
        }
        jdbc.update("UPDATE payment_bill_imports SET total_entries = ?, matched_entries = ?, difference_entries = ? WHERE id = ?",
                entries.size(), matched, differences, importId);
        return new PaymentBillImportResult(importId, entries.size(), matched, differences);
    }

    private void validateEntry(PaymentBillEntry entry) {
        if (entry == null || entry.transactionId() == null || entry.transactionId().isBlank()
                || entry.amount() == null || entry.amount().signum() < 0 || entry.direction() == null || entry.direction().isBlank()) {
            throw new IllegalArgumentException("bill entry requires transaction id, non-negative amount and direction");
        }
    }

    private String findPaymentNo(String remarkText) {
        if (remarkText == null || remarkText.isBlank()) {
            return null;
        }
        return jdbc.query("SELECT p.payment_no FROM payment_orders p JOIN trade_orders t ON t.id = p.trade_id WHERE ? LIKE CONCAT('%', t.trade_no, '%') ORDER BY p.id DESC LIMIT 1",
                (rs, row) -> rs.getString(1), remarkText).stream().findFirst().orElse(null);
    }

    private boolean isIncome(String direction) {
        return direction != null && ("INCOME".equalsIgnoreCase(direction) || "收入".equals(direction) || "CREDIT".equalsIgnoreCase(direction));
    }

    private String differenceMessage(boolean amountOk, boolean directionOk, boolean remarkOk) {
        if (!amountOk) return "账单金额与支付单不一致";
        if (!directionOk) return "账单不是收入方向";
        return "账单备注未包含系统订单号";
    }

    private void markDifference(String paymentNo, String type, String description) {
        jdbc.update("UPDATE payment_orders SET status = 'ABNORMAL', failure_code = ?, failure_message = ? WHERE payment_no = ? AND status IN ('PENDING','PROOF_SUBMITTED')", type, description, paymentNo);
        jdbc.update("INSERT INTO payment_reconciliation_differences (payment_no, difference_type, description) VALUES (?, ?, ?)", paymentNo, type, description);
    }

    private String tradeNoFor(String paymentNo) {
        return jdbc.queryForObject("SELECT trade.trade_no FROM payment_orders p JOIN trade_orders trade ON trade.id = p.trade_id WHERE p.payment_no = ?", String.class, paymentNo);
    }

    private PaymentResult find(String no, PaymentStatus ignored, String message) {
        return jdbc.query("SELECT payment_no,status,amount,code_url,remark_text FROM payment_orders WHERE payment_no = ?", (rs, row) -> new PaymentResult(rs.getString(1), PaymentStatus.valueOf(rs.getString(2)), rs.getBigDecimal(3), rs.getString(4), rs.getString(5), message), no).stream().findFirst().orElseThrow();
    }
}
