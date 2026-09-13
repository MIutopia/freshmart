package com.freshmart.delivery;

import com.freshmart.auth.CurrentUser;
import com.freshmart.platform.PlatformRuleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeliveryController {
    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/api/admin/delivery-zones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public IdResponse createZone(@Valid @RequestBody CreateZoneRequest request) {
        return new IdResponse(deliveryService.createZone(request.name(), request.areaCode(), request.boundaryJson()));
    }

    @PutMapping("/api/admin/delivery-zones/{zoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void updateZone(@PathVariable long zoneId, @Valid @RequestBody UpdateZoneRequest request) {
        deliveryService.updateZone(zoneId, request.name(), request.boundaryJson(), request.status());
    }

    @GetMapping("/api/admin/delivery-zones")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DeliveryService.DeliveryZoneView> listZones() {
        return deliveryService.listZones();
    }

    @GetMapping("/api/delivery/tasks")
    @PreAuthorize("hasRole('RIDER')")
    public List<DeliveryService.DeliveryTaskView> listMyTasks(@AuthenticationPrincipal CurrentUser rider) {
        return deliveryService.listMyTasks(rider);
    }

    @GetMapping("/api/delivery/performance")
    @PreAuthorize("hasRole('RIDER')")
    public List<DeliveryService.RiderPerformanceView> listMyPerformance(
            @AuthenticationPrincipal CurrentUser rider,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return deliveryService.listMyPerformance(rider, from, to);
    }

    @PostMapping("/api/admin/delivery-tasks/{taskId}/assign")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void assign(@PathVariable long taskId, @Valid @RequestBody AssignRequest request) {
        deliveryService.assign(taskId, request.riderUserId());
    }

    @PutMapping("/api/delivery/tasks/{taskId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('RIDER')")
    public void accept(@AuthenticationPrincipal CurrentUser rider, @PathVariable long taskId) {
        deliveryService.accept(rider, taskId);
    }

    @PutMapping("/api/delivery/tasks/{taskId}/pick")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('RIDER')")
    public void pick(@AuthenticationPrincipal CurrentUser rider, @PathVariable long taskId) {
        deliveryService.pick(rider, taskId);
    }

    @PutMapping("/api/delivery/tasks/{taskId}/deliver")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('RIDER')")
    public void deliver(@AuthenticationPrincipal CurrentUser rider, @PathVariable long taskId,
            @Valid @RequestBody DeliverRequest request) {
        deliveryService.deliver(rider, taskId, request.proofUrl());
    }

    public record IdResponse(long id) { }
    public record CreateZoneRequest(@NotBlank String name, @NotBlank String areaCode, String boundaryJson) { }
    public record UpdateZoneRequest(@NotBlank String name, String boundaryJson,
            @Pattern(regexp = "ACTIVE|INACTIVE") String status) { }
    public record AssignRequest(@Positive long riderUserId) { }
    public record DeliverRequest(@NotBlank String proofUrl) { }
}
