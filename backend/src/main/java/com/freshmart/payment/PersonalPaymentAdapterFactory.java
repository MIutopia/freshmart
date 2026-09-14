package com.freshmart.payment;

import org.springframework.stereotype.Component;

@Component
public class PersonalPaymentAdapterFactory implements PaymentAdapterFactory {
    private final PersonalWechatQrPaymentAdapter payment;
    private final PersonalWechatQrRefundAdapter refund;

    public PersonalPaymentAdapterFactory(PersonalWechatQrPaymentAdapter payment, PersonalWechatQrRefundAdapter refund) {
        this.payment = payment;
        this.refund = refund;
    }

    @Override
    public PaymentAdapter payment() { return payment; }

    @Override
    public RefundAdapter refund() { return refund; }
}
