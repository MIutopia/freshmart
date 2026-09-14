package com.freshmart.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentBillEntry(String transactionId, LocalDateTime transactionTime, String remarkText,
        BigDecimal amount, String direction) {
}
