package com.freshmart.order;

import java.math.BigDecimal;

public final class FreightCalculator {
    private FreightCalculator() {
    }

    public static BigDecimal calculate(
            BigDecimal discountedGoodsAmount,
            BigDecimal freeThreshold,
            BigDecimal standardFee) {
        requireNonNegative(discountedGoodsAmount, "discounted goods amount");
        requireNonNegative(freeThreshold, "free threshold");
        requireNonNegative(standardFee, "standard fee");
        return discountedGoodsAmount.compareTo(freeThreshold) >= 0 ? BigDecimal.ZERO : standardFee;
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
