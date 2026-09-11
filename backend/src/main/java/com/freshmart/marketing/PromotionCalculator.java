package com.freshmart.marketing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

public final class PromotionCalculator {
    private PromotionCalculator() {
    }

    public static DiscountResult calculate(BigDecimal goodsAmount, BigDecimal membershipDiscountRate,
            List<Promotion> promotions) {
        if (goodsAmount == null || goodsAmount.signum() < 0 || membershipDiscountRate == null
                || membershipDiscountRate.signum() < 0 || membershipDiscountRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("promotion amounts and membership rate must be valid");
        }
        BigDecimal membershipDiscount = goodsAmount.multiply(BigDecimal.valueOf(100).subtract(membershipDiscountRate))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal promotionDiscount = BigDecimal.ZERO;
        if (promotions != null) {
            for (Promotion promotion : promotions.stream().filter(Promotion::eligible).sorted(Comparator.comparing(Promotion::priority)).toList()) {
                if (goodsAmount.compareTo(promotion.thresholdAmount()) < 0) {
                    continue;
                }
                BigDecimal remaining = goodsAmount.subtract(membershipDiscount).subtract(promotionDiscount).max(BigDecimal.ZERO);
                BigDecimal candidate = promotion.fixedDiscount().min(remaining);
                promotionDiscount = promotion.stackable() ? promotionDiscount.add(candidate) : promotionDiscount.max(candidate);
            }
        }
        BigDecimal total = membershipDiscount.add(promotionDiscount).min(goodsAmount);
        return new DiscountResult(total.setScale(2, RoundingMode.HALF_UP), goodsAmount.subtract(total).setScale(2, RoundingMode.HALF_UP));
    }

    public record Promotion(String code, BigDecimal thresholdAmount, BigDecimal fixedDiscount, boolean stackable, int priority) {
        public boolean eligible() {
            return thresholdAmount != null && fixedDiscount != null && thresholdAmount.signum() >= 0
                    && fixedDiscount.signum() >= 0 && priority >= 0;
        }
    }

    public record DiscountResult(BigDecimal discountAmount, BigDecimal payableGoodsAmount) {
    }
}
