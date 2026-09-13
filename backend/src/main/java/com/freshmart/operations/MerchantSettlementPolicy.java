package com.freshmart.operations;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MerchantSettlementPolicy {
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private MerchantSettlementPolicy() {
    }

    public static Settlement calculate(BigDecimal merchantGrossAmount, BigDecimal platformPriceSubsidyAmount,
            BigDecimal commissionRate) {
        requireNonNegative(merchantGrossAmount, "merchant gross amount");
        requireNonNegative(platformPriceSubsidyAmount, "platform price subsidy amount");
        if (platformPriceSubsidyAmount.compareTo(merchantGrossAmount) > 0) {
            throw new IllegalArgumentException("platform price subsidy must not exceed merchant gross amount");
        }
        if (commissionRate == null || commissionRate.signum() < 0 || commissionRate.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("commission rate must be between 0 and 100");
        }
        BigDecimal base = merchantGrossAmount.subtract(platformPriceSubsidyAmount);
        BigDecimal commission = base.multiply(commissionRate).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        return new Settlement(base, commission, merchantGrossAmount.subtract(commission));
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    public record Settlement(BigDecimal commissionBaseAmount, BigDecimal commissionAmount, BigDecimal netAmount) {
    }
}
