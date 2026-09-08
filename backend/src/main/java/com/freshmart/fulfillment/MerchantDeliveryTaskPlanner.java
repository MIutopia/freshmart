package com.freshmart.fulfillment;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class MerchantDeliveryTaskPlanner {
    private MerchantDeliveryTaskPlanner() {
    }

    public static Map<Long, DeliveryTaskDraft> plan(Collection<MerchantOrder> orders) {
        Objects.requireNonNull(orders, "orders");
        Map<Long, DeliveryTaskDraft> tasks = new LinkedHashMap<>();
        for (MerchantOrder order : orders) {
            if (order == null || order.merchantId() == null || order.orderId() == null) {
                throw new IllegalArgumentException("merchant and order ids are required");
            }
            tasks.putIfAbsent(order.merchantId(), new DeliveryTaskDraft(order.merchantId(), order.orderId()));
        }
        return tasks;
    }

    public record MerchantOrder(Long merchantId, Long orderId) {
    }

    public record DeliveryTaskDraft(Long merchantId, Long firstOrderId) {
    }
}
