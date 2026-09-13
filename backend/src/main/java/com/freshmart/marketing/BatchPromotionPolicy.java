package com.freshmart.marketing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BatchPromotionPolicy {
    private static final BigDecimal GRAMS_PER_KILOGRAM = BigDecimal.valueOf(1000);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private BatchPromotionPolicy() {
    }

    public static BigDecimal discountAmount(int allocatedGrams, BigDecimal userPricePerKilogram,
            BigDecimal markdownRate) {
        if (allocatedGrams <= 0 || userPricePerKilogram == null || userPricePerKilogram.signum() < 0
                || markdownRate == null || markdownRate.signum() < 0
                || markdownRate.compareTo(ONE_HUNDRED) >= 0) {
            throw new IllegalArgumentException("batch promotion inputs are invalid");
        }
        return userPricePerKilogram.multiply(BigDecimal.valueOf(allocatedGrams))
                .divide(GRAMS_PER_KILOGRAM, 4, RoundingMode.HALF_UP)
                .multiply(markdownRate).divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
