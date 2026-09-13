package com.freshmart.payment;

import java.math.BigDecimal;

public record RefundResult(String refundNo, RefundStatus status, BigDecimal amount, String message) {
}
