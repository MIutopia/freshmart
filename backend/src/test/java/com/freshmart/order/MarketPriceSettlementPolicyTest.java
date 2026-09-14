package com.freshmart.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MarketPriceSettlementPolicyTest {
    @Test
    void capsUserPriceAtMarketPriceAndSubsidizesCompliantMarkup() {
        var result = MarketPriceSettlementPolicy.calculate(1000, new BigDecimal("10.00"), new BigDecimal("10.50"), new BigDecimal("5.00"));
        assertEquals(new BigDecimal("10.00"), result.userGoodsAmount());
        assertEquals(new BigDecimal("0.50"), result.platformSubsidyAmount());
        assertEquals(new BigDecimal("10.50"), result.merchantGrossAmount());
    }

    @Test
    void rejectsMerchantPriceBeyondConfiguredMarkup() {
        assertThrows(IllegalArgumentException.class, () -> MarketPriceSettlementPolicy.calculate(
                1000, new BigDecimal("10.00"), new BigDecimal("10.51"), new BigDecimal("5.00")));
    }
}
