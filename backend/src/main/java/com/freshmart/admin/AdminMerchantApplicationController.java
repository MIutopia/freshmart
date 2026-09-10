package com.freshmart.admin;

import com.freshmart.auth.CurrentUser;
import com.freshmart.merchant.MerchantApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/merchants")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMerchantApplicationController {
    private final MerchantApplicationService merchantApplicationService;

    public AdminMerchantApplicationController(MerchantApplicationService merchantApplicationService) {
        this.merchantApplicationService = merchantApplicationService;
    }

    @GetMapping("/applications")
    public List<MerchantApplicationService.ApplicationView> list(@RequestParam(required = false) String status) {
        return merchantApplicationService.list(status);
    }

    @PutMapping("/{merchantId}/review")
    public MerchantApplicationService.ApplicationView review(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable Long merchantId,
            @Valid @RequestBody ReviewApplicationRequest request,
            HttpServletRequest servletRequest) {
        return merchantApplicationService.review(admin, merchantId, request.approved(), request.reviewNote(), sourceIp(servletRequest));
    }

    private String sourceIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        return forwardedFor == null ? request.getRemoteAddr() : forwardedFor.split(",")[0].trim();
    }

    public record ReviewApplicationRequest(boolean approved, @Size(max = 500) String reviewNote) {
    }
}
