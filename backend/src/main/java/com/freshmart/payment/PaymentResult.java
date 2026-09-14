package com.freshmart.payment;

import java.math.BigDecimal;

public record PaymentResult(String paymentNo, PaymentStatus status, BigDecimal amount, String codeUrl, String remarkText,
        String message) {
}
