package com.freshmart.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WeightPriceCalculatorTest {
    @Test
    void calculatesAmountByGramsWithHalfUpRounding() {
        assertEquals(new BigDecimal("9.95"), WeightPriceCalculator.calculate(650, new BigDecimal("15.30")));
    }
}
