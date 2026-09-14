package com.freshmart.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class WarehouseBoundaryValidatorTest {
    @Test
    void acceptsLinesInsideOneMerchantWarehouseAndZone() {
        var boundary = WarehouseBoundaryValidator.validate(List.of(
                new WarehouseBoundaryValidator.Line(1L, 10L, 100L),
                new WarehouseBoundaryValidator.Line(1L, 10L, 100L)));
        assertEquals(10L, boundary.warehouseId());
    }

    @Test
    void rejectsCrossWarehouseCheckout() {
        assertThrows(IllegalArgumentException.class, () -> WarehouseBoundaryValidator.validate(List.of(
                new WarehouseBoundaryValidator.Line(1L, 10L, 100L),
                new WarehouseBoundaryValidator.Line(1L, 11L, 100L))));
    }
}
