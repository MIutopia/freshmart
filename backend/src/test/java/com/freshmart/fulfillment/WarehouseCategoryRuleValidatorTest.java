package com.freshmart.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class WarehouseCategoryRuleValidatorTest {
    @Test
    void selectsTheHighestPriorityEligibleWarehouse() {
        var selected = WarehouseCategoryRuleValidator.select(1L, 10L, 100L, List.of(
                new WarehouseCategoryRuleValidator.WarehouseRule(1L, 1L, 10L, 20L, 100L, true, true, 20),
                new WarehouseCategoryRuleValidator.WarehouseRule(1L, 1L, 10L, 21L, 100L, true, true, 10)));
        assertEquals(21L, selected.warehouseId());
    }

    @Test
    void rejectsWarehouseWithoutTheCategoryAuthorization() {
        assertThrows(IllegalArgumentException.class, () -> WarehouseCategoryRuleValidator.select(1L, 10L, 100L, List.of(
                new WarehouseCategoryRuleValidator.WarehouseRule(1L, 1L, 10L, 20L, 100L, false, true, 10))));
    }

    @Test
    void rejectsRulePointingToAnotherMerchantsWarehouse() {
        assertThrows(IllegalArgumentException.class, () -> WarehouseCategoryRuleValidator.select(1L, 10L, 100L, List.of(
                new WarehouseCategoryRuleValidator.WarehouseRule(1L, 2L, 10L, 20L, 100L, true, true, 10))));
    }
}
