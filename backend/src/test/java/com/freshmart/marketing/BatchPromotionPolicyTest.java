package com.freshmart.marketing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BatchPromotionPolicyTest {
    @Test
    void calculatesDiscountUsingAllocatedWeight() {
        assertEquals(new BigDecimal("2.00"), BatchPromotionPolicy.discountAmount(500,
                new BigDecimal("20.00"), new BigDecimal("20.00")));
    }

    @Test
    void rejectsAFullOrNegativeMarkdownRate() {
        assertThrows(IllegalArgumentException.class, () -> BatchPromotionPolicy.discountAmount(500,
                new BigDecimal("20.00"), new BigDecimal("100.00")));
    }
}
