package com.freshmart.fulfillment;

import java.util.Collection;
import java.util.Objects;

public final class WarehouseBoundaryValidator {
    private WarehouseBoundaryValidator() {
    }

    public static Boundary validate(Collection<Line> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("at least one order line is required");
        }
        Boundary boundary = null;
        for (Line line : lines) {
            if (line == null || line.merchantId() == null || line.warehouseId() == null || line.zoneId() == null) {
                throw new IllegalArgumentException("merchant, warehouse and zone are required");
            }
            Boundary current = new Boundary(line.merchantId(), line.warehouseId(), line.zoneId());
            if (boundary == null) {
                boundary = current;
            } else if (!boundary.equals(current)) {
                throw new IllegalArgumentException("cross-merchant, cross-warehouse and cross-zone splitting is not allowed");
            }
        }
        return boundary;
    }

    public record Line(Long merchantId, Long warehouseId, Long zoneId) {
    }

    public record Boundary(Long merchantId, Long warehouseId, Long zoneId) {
        public Boundary {
            Objects.requireNonNull(merchantId, "merchantId");
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(zoneId, "zoneId");
        }
    }
}
