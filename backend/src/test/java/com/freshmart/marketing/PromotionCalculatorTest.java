package com.freshmart.marketing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromotionCalculatorTest {
    @Test
    void appliesMembershipAndStackablePromotionsWithoutNegativePayable() {
        var result = PromotionCalculator.calculate(new BigDecimal("100.00"), new BigDecimal("95.00"), List.of(
                new PromotionCalculator.Promotion("FULL100_MINUS10", new BigDecimal("100"), new BigDecimal("10"), true, 10),
                new PromotionCalculator.Promotion("FLASH5", new BigDecimal("0"), new BigDecimal("5"), true, 20)));
        assertEquals(new BigDecimal("20.00"), result.discountAmount());
        assertEquals(new BigDecimal("80.00"), result.payableGoodsAmount());
    }

    @Test
    void rejectsInvalidMembershipRate() {
        assertThrows(IllegalArgumentException.class, () -> PromotionCalculator.calculate(
                new BigDecimal("100"), new BigDecimal("101"), List.of()));
    }
}
