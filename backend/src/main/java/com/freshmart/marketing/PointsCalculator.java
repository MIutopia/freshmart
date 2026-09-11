package com.freshmart.marketing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PointsCalculator {
    private PointsCalculator() {
    }

    public static int awardablePoints(BigDecimal paidAmount, BigDecimal pointsPerCurrency) {
        if (paidAmount == null || pointsPerCurrency == null || paidAmount.signum() < 0 || pointsPerCurrency.signum() < 0) {
            throw new IllegalArgumentException("paid amount and points rate must not be negative");
        }
        return paidAmount.multiply(pointsPerCurrency).setScale(0, RoundingMode.DOWN).intValueExact();
    }
}
