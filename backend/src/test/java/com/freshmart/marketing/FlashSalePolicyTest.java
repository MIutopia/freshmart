package com.freshmart.marketing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FlashSalePolicyTest {
    @Test
    void calculatesDiscountAgainstRegularGoodsAmount() {
        assertEquals(new BigDecimal("4.00"), FlashSalePolicy.discountAmount(500, new BigDecimal("10.00"), new BigDecimal("12.00")));
    }

    @Test
    void rejectsNonPositiveWeight() {
        assertThrows(IllegalArgumentException.class, () -> FlashSalePolicy.discountAmount(0, BigDecimal.ONE, BigDecimal.ONE));
    }
}
