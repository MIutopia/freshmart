package com.freshmart.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class WeighingSettlementPolicy {
    private WeighingSettlementPolicy() {
    }

    public static Settlement settle(
            BigDecimal prepaidGoodsAmount,
            BigDecimal actualGoodsAmount,
            BigDecimal platformAbsorbLimit) {
        requireNonNegative(prepaidGoodsAmount, "prepaid goods amount");
        requireNonNegative(actualGoodsAmount, "actual goods amount");
        requireNonNegative(platformAbsorbLimit, "platform absorb limit");

        BigDecimal difference = actualGoodsAmount.subtract(prepaidGoodsAmount).setScale(2, RoundingMode.HALF_UP);
        if (difference.signum() <= 0) {
            return new Settlement(SettlementAction.REFUND_USER, difference.abs(), BigDecimal.ZERO);
        }
        if (difference.compareTo(platformAbsorbLimit) <= 0) {
            return new Settlement(SettlementAction.PLATFORM_ABSORB, BigDecimal.ZERO, difference);
        }
        return new Settlement(SettlementAction.REQUIRES_REVIEW, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    public enum SettlementAction {
        REFUND_USER,
        PLATFORM_ABSORB,
        REQUIRES_REVIEW
    }

    public record Settlement(SettlementAction action, BigDecimal refundAmount, BigDecimal absorbedAmount) {
    }
}
