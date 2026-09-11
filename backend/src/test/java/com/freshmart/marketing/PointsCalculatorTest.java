package com.freshmart.marketing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PointsCalculatorTest {
    @Test
    void awardsOnlyWholePointsFromPaidAmount() {
        assertEquals(18, PointsCalculator.awardablePoints(new BigDecimal("18.99"), BigDecimal.ONE));
    }

    @Test
    void rejectsNegativePaidAmount() {
        assertThrows(IllegalArgumentException.class, () -> PointsCalculator.awardablePoints(new BigDecimal("-1"), BigDecimal.ONE));
    }
}
