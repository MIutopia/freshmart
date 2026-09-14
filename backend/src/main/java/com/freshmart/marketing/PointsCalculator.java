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

    public static Redemption redemption(int requestedPoints, int availablePoints, BigDecimal eligibleGoodsAmount,
            int pointsPerCurrency, BigDecimal maximumRate) {
        if (requestedPoints < 0 || availablePoints < 0 || eligibleGoodsAmount == null || eligibleGoodsAmount.signum() < 0
                || pointsPerCurrency <= 0 || maximumRate == null || maximumRate.signum() < 0
                || maximumRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("points redemption inputs are invalid");
        }
        if (requestedPoints == 0) {
            return new Redemption(0, BigDecimal.ZERO);
        }
        if (requestedPoints % 10 != 0) {
            throw new IllegalArgumentException("points redemption must be a multiple of 10");
        }
        BigDecimal maximumAmount = eligibleGoodsAmount.multiply(maximumRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        int maximumPoints = maximumAmount.multiply(BigDecimal.valueOf(pointsPerCurrency))
                .setScale(0, RoundingMode.DOWN).intValueExact();
        int usedPoints = Math.min(requestedPoints, Math.min(availablePoints, maximumPoints));
        usedPoints = usedPoints - usedPoints % 10;
        return new Redemption(usedPoints, BigDecimal.valueOf(usedPoints)
                .divide(BigDecimal.valueOf(pointsPerCurrency), 2, RoundingMode.UNNECESSARY));
    }

    public static int refundRollback(int awardedPoints, BigDecimal refundAmount, BigDecimal tradePaidAmount,
            int availablePoints) {
        if (awardedPoints < 0 || availablePoints < 0 || refundAmount == null || tradePaidAmount == null
                || refundAmount.signum() < 0 || tradePaidAmount.signum() < 0) {
            throw new IllegalArgumentException("refund points inputs are invalid");
        }
        if (awardedPoints == 0 || refundAmount.signum() == 0 || tradePaidAmount.signum() == 0) {
            return 0;
        }
        int proportionalPoints = refundAmount.multiply(BigDecimal.valueOf(awardedPoints))
                .divide(tradePaidAmount, 0, RoundingMode.DOWN).intValueExact();
        return Math.min(proportionalPoints, availablePoints);
    }

    public record Redemption(int usedPoints, BigDecimal discountAmount) {
    }
}
