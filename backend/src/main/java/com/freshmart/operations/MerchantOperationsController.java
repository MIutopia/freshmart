package com.freshmart.operations;

import com.freshmart.auth.AuditLogService;
import com.freshmart.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MerchantOperationsController {
    private final MerchantDashboardService dashboardService;
    private final MerchantSettlementService settlementService;
    private final AuditLogService auditLogService;

    public MerchantOperationsController(MerchantDashboardService dashboardService, MerchantSettlementService settlementService,
            AuditLogService auditLogService) {
        this.dashboardService = dashboardService;
        this.settlementService = settlementService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/api/merchant/dashboard")
    @PreAuthorize("hasRole('MERCHANT')")
    public MerchantDashboardService.DashboardView dashboard(@AuthenticationPrincipal CurrentUser merchant,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.dashboard(merchant, from, to);
    }

    @GetMapping("/api/merchant/settlements")
    @PreAuthorize("hasRole('MERCHANT')")
    public List<MerchantSettlementService.SettlementView> settlements(@AuthenticationPrincipal CurrentUser merchant) {
        return settlementService.list(merchant);
    }

    @PostMapping("/api/admin/commissions/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public List<MerchantSettlementService.SettlementView> generate(@AuthenticationPrincipal CurrentUser admin,
            HttpServletRequest request) {
        List<MerchantSettlementService.SettlementView> settlements = settlementService.generatePending();
        auditLogService.record(admin.userId(), "MERCHANT_SETTLEMENT_GENERATED", "MERCHANT_SETTLEMENT", null, sourceIp(request));
        return settlements;
    }

    @GetMapping("/api/admin/commissions")
    @PreAuthorize("hasRole('ADMIN')")
    public List<MerchantSettlementService.SettlementView> listAll() {
        return settlementService.listAll();
    }

    @PutMapping("/api/admin/commissions/{settlementId}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    public MerchantSettlementService.SettlementView confirmSettlement(@AuthenticationPrincipal CurrentUser operator,
            @PathVariable long settlementId, @Valid @RequestBody SettlementConfirmationRequest request,
            HttpServletRequest servletRequest) {
        MerchantSettlementService.SettlementView settlement = settlementService.confirmSettlement(settlementId,
                operator.userId(), request.note());
        auditLogService.record(operator.userId(), "MERCHANT_SETTLEMENT_CONFIRMED", "MERCHANT_SETTLEMENT",
                Long.toString(settlementId), sourceIp(servletRequest));
        return settlement;
    }

    @GetMapping("/api/admin/platform-costs")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','FINANCE')")
    public List<PlatformCostView> platformCosts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "date range is invalid");
        }
        return settlementService.platformCosts(from, to);
    }

    public record PlatformCostView(String feeType, String direction, BigDecimal amount, long entryCount) { }
    public record SettlementConfirmationRequest(@jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 500) String note) { }

    private String sourceIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        return forwardedFor == null ? request.getRemoteAddr() : forwardedFor.split(",")[0].trim();
    }
}
