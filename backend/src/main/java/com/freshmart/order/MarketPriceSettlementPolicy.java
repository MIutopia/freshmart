package com.freshmart.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MarketPriceSettlementPolicy {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal GRAMS_PER_KILOGRAM = BigDecimal.valueOf(1000);

    private MarketPriceSettlementPolicy() {
    }

    public static PriceBreakdown calculate(
            int weightGrams,
            BigDecimal marketPricePerKilogram,
            BigDecimal merchantPricePerKilogram,
            BigDecimal maxMarkupRate) {
        requirePositive(weightGrams, marketPricePerKilogram, merchantPricePerKilogram, maxMarkupRate);
        BigDecimal maximumMerchantPrice = marketPricePerKilogram
                .multiply(BigDecimal.ONE.add(maxMarkupRate.divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP)));
        if (merchantPricePerKilogram.compareTo(maximumMerchantPrice) > 0) {
            throw new IllegalArgumentException("merchant price exceeds the market-price markup limit");
        }

        BigDecimal userPrice = merchantPricePerKilogram.min(marketPricePerKilogram);
        BigDecimal subsidyPerKilogram = merchantPricePerKilogram.subtract(userPrice);
        return new PriceBreakdown(
                money(merchantPricePerKilogram),
                money(userPrice),
                money(subsidyPerKilogram),
                amount(weightGrams, merchantPricePerKilogram),
                amount(weightGrams, userPrice),
                amount(weightGrams, subsidyPerKilogram));
    }

    private static BigDecimal amount(int weightGrams, BigDecimal pricePerKilogram) {
        return money(pricePerKilogram.multiply(BigDecimal.valueOf(weightGrams)).divide(GRAMS_PER_KILOGRAM, 4, RoundingMode.HALF_UP));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static void requirePositive(
            int weightGrams,
            BigDecimal marketPricePerKilogram,
            BigDecimal merchantPricePerKilogram,
            BigDecimal maxMarkupRate) {
        if (weightGrams <= 0 || marketPricePerKilogram == null || merchantPricePerKilogram == null
                || maxMarkupRate == null || marketPricePerKilogram.signum() < 0
                || merchantPricePerKilogram.signum() < 0 || maxMarkupRate.signum() < 0) {
            throw new IllegalArgumentException("weight, prices and markup rate must not be negative");
        }
    }

    public record PriceBreakdown(
            BigDecimal merchantPricePerKilogram,
            BigDecimal userPricePerKilogram,
            BigDecimal platformSubsidyPerKilogram,
            BigDecimal merchantGrossAmount,
            BigDecimal userGoodsAmount,
            BigDecimal platformSubsidyAmount) {
    }
}
