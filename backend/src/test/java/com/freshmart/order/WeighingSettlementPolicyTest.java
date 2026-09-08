package com.freshmart.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WeighingSettlementPolicyTest {
    @Test
    void platformAbsorbsSmallIncrease() {
        var result = WeighingSettlementPolicy.settle(
                new BigDecimal("20.00"), new BigDecimal("20.80"), new BigDecimal("1.00"));
        assertEquals(WeighingSettlementPolicy.SettlementAction.PLATFORM_ABSORB, result.action());
        assertEquals(new BigDecimal("0.80"), result.absorbedAmount());
    }

    @Test
    void refundsUserWhenActualAmountIsLower() {
        var result = WeighingSettlementPolicy.settle(
                new BigDecimal("20.00"), new BigDecimal("19.50"), new BigDecimal("1.00"));
        assertEquals(WeighingSettlementPolicy.SettlementAction.REFUND_USER, result.action());
        assertEquals(new BigDecimal("0.50"), result.refundAmount());
    }
}
