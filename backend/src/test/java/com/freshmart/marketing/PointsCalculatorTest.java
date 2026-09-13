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

    @Test
    void capsRedemptionAtThreePercentOfDiscountedGoodsOnly() {
        PointsCalculator.Redemption redemption = PointsCalculator.redemption(1000, 5000,
                new BigDecimal("22.00"), 1000, new BigDecimal("3.00"));
        assertEquals(660, redemption.usedPoints());
        assertEquals(new BigDecimal("0.66"), redemption.discountAmount());
    }

    @Test
    void rejectsRedemptionThatCannotRepresentAWholeCent() {
        assertThrows(IllegalArgumentException.class, () -> PointsCalculator.redemption(11, 100,
                new BigDecimal("10.00"), 1000, new BigDecimal("3.00")));
    }

    @Test
    void rollsBackOnlyAwardedPointsThatRemainAvailable() {
        assertEquals(30, PointsCalculator.refundRollback(100, new BigDecimal("15.00"),
                new BigDecimal("50.00"), 30));
    }
}
