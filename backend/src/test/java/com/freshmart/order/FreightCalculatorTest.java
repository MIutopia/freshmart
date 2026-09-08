package com.freshmart.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FreightCalculatorTest {
    @Test
    void chargesStandardFeeBelowThreshold() {
        assertEquals(new BigDecimal("6.00"), FreightCalculator.calculate(
                new BigDecimal("58.99"), new BigDecimal("59.00"), new BigDecimal("6.00")));
    }

    @Test
    void waivesFeeAtThreshold() {
        assertEquals(BigDecimal.ZERO, FreightCalculator.calculate(
                new BigDecimal("59.00"), new BigDecimal("59.00"), new BigDecimal("6.00")));
    }
}
