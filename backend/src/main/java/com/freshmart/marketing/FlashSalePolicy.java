package com.freshmart.marketing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FlashSalePolicy {
    private static final BigDecimal GRAMS_PER_KILOGRAM = BigDecimal.valueOf(1000);

    private FlashSalePolicy() {
    }

    public static BigDecimal discountAmount(int grams, BigDecimal regularGoodsAmount, BigDecimal salePricePerKg) {
        if (grams <= 0 || regularGoodsAmount == null || regularGoodsAmount.signum() < 0
                || salePricePerKg == null || salePricePerKg.signum() < 0) {
            throw new IllegalArgumentException("flash sale inputs are invalid");
        }
        BigDecimal saleAmount = salePricePerKg.multiply(BigDecimal.valueOf(grams))
                .divide(GRAMS_PER_KILOGRAM, 2, RoundingMode.HALF_UP);
        return regularGoodsAmount.subtract(saleAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
