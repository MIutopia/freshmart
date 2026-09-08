package com.freshmart.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class MerchantDeliveryTaskPlannerTest {
    @Test
    void createsOneTaskPerMerchant() {
        var tasks = MerchantDeliveryTaskPlanner.plan(List.of(
                new MerchantDeliveryTaskPlanner.MerchantOrder(10L, 100L),
                new MerchantDeliveryTaskPlanner.MerchantOrder(10L, 101L),
                new MerchantDeliveryTaskPlanner.MerchantOrder(20L, 200L)));
        assertEquals(2, tasks.size());
        assertEquals(100L, tasks.get(10L).firstOrderId());
        assertEquals(200L, tasks.get(20L).firstOrderId());
    }
}
