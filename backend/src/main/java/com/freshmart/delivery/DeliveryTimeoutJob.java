package com.freshmart.delivery;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeliveryTimeoutJob {
    private final DeliveryService deliveryService;

    public DeliveryTimeoutJob(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @Scheduled(fixedDelayString = "${commerce.delivery.timeout-scan-interval-ms:60000}")
    @Transactional("deliveryTransactionManager")
    public void releaseTimedOutAssignments() {
        deliveryService.releaseTimedOutAssignments();
    }
}
