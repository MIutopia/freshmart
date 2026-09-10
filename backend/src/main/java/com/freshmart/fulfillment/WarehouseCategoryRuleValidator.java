package com.freshmart.fulfillment;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

public final class WarehouseCategoryRuleValidator {
    private WarehouseCategoryRuleValidator() {
    }

    public static WarehouseRule select(
            long merchantId,
            long categoryId,
            long deliveryZoneId,
            Collection<WarehouseRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("an enabled category warehouse rule is required");
        }
        return rules.stream()
                .filter(Objects::nonNull)
                .filter(WarehouseRule::enabled)
                .filter(rule -> rule.merchantId() == merchantId)
                .filter(rule -> rule.warehouseMerchantId() == merchantId)
                .filter(rule -> rule.categoryId() == categoryId)
                .filter(rule -> rule.deliveryZoneId() == deliveryZoneId)
                .filter(WarehouseRule::warehouseAllowsCategory)
                .min(Comparator.comparingInt(WarehouseRule::priority).thenComparingLong(WarehouseRule::warehouseId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "no warehouse matches the merchant, category and delivery-zone boundary"));
    }

    public record WarehouseRule(
            long merchantId,
            long warehouseMerchantId,
            long categoryId,
            long warehouseId,
            long deliveryZoneId,
            boolean warehouseAllowsCategory,
            boolean enabled,
            int priority) {
        public WarehouseRule {
            if (merchantId <= 0 || warehouseMerchantId <= 0 || categoryId <= 0 || warehouseId <= 0
                    || deliveryZoneId <= 0 || priority < 0) {
                throw new IllegalArgumentException("warehouse rule identifiers and priority must be valid");
            }
        }
    }
}
