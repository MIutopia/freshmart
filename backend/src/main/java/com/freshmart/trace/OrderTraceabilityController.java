package com.freshmart.trace;

import com.freshmart.auth.CurrentUser;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('CONSUMER', 'MERCHANT', 'ADMIN')")
public class OrderTraceabilityController {
    private final OrderTraceabilityService service;

    public OrderTraceabilityController(OrderTraceabilityService service) {
        this.service = service;
    }

    @GetMapping("/api/orders/{orderId}/receipt")
    public OrderTraceabilityService.ReceiptView receipt(@AuthenticationPrincipal CurrentUser user,
            @PathVariable long orderId) {
        return service.receipt(user, orderId);
    }

    @GetMapping("/api/orders/{orderId}/traceability")
    public List<OrderTraceabilityService.TraceView> traceability(@AuthenticationPrincipal CurrentUser user,
            @PathVariable long orderId) {
        return service.traceability(user, orderId);
    }
}
