package com.freshmart.payment;

public interface PaymentAdapterFactory {
    PaymentAdapter payment();
    RefundAdapter refund();
}
