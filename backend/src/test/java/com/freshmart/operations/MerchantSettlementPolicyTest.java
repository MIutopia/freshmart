package com.freshmart.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MerchantSettlementPolicyTest {
    @Test
    void excludesPlatformMarketPriceSubsidyFromCommissionBase() {
        var settlement = MerchantSettlementPolicy.calculate(new BigDecimal("10.50"), new BigDecimal("0.50"),
                new BigDecimal("10.00"));

        assertEquals(new BigDecimal("10.00"), settlement.commissionBaseAmount());
        assertEquals(new BigDecimal("1.00"), settlement.commissionAmount());
        assertEquals(new BigDecimal("9.50"), settlement.netAmount());
    }

    @Test
    void rejectsSubsidyThatExceedsMerchantGrossAmount() {
        assertThrows(IllegalArgumentException.class, () -> MerchantSettlementPolicy.calculate(
                new BigDecimal("10.00"), new BigDecimal("10.01"), new BigDecimal("10.00")));
    }
}
