package com.freshmart.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class WeightPriceCalculator {
    private static final BigDecimal GRAMS_PER_KILOGRAM = BigDecimal.valueOf(1000);

    private WeightPriceCalculator() {
    }

    public static BigDecimal calculate(int weightGrams, BigDecimal marketPricePerKilogram) {
        if (weightGrams <= 0 || marketPricePerKilogram == null || marketPricePerKilogram.signum() < 0) {
            throw new IllegalArgumentException("weight and market price must be positive");
        }
        return marketPricePerKilogram
                .multiply(BigDecimal.valueOf(weightGrams))
                .divide(GRAMS_PER_KILOGRAM, 2, RoundingMode.HALF_UP);
    }
}
