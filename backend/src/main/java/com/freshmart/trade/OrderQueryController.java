package com.freshmart.trade;

import com.freshmart.auth.CurrentUser;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('CONSUMER', 'MERCHANT', 'ADMIN')")
public class OrderQueryController {
    private final OrderQueryService service;

    public OrderQueryController(OrderQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/orders")
    public List<OrderQueryService.OrderView> list(@AuthenticationPrincipal CurrentUser user) {
        return service.listMine(user);
    }

    @GetMapping("/api/orders/{orderId}")
    public OrderQueryService.OrderView detail(@AuthenticationPrincipal CurrentUser user, @PathVariable long orderId) {
        return service.detail(user, orderId);
    }
}
