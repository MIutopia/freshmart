package com.freshmart.merchant;

import com.freshmart.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant/applications")
public class MerchantApplicationController {
    private final MerchantApplicationService merchantApplicationService;

    public MerchantApplicationController(MerchantApplicationService merchantApplicationService) {
        this.merchantApplicationService = merchantApplicationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CONSUMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantApplicationService.ApplicationView submit(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody SubmitApplicationRequest request,
            HttpServletRequest servletRequest) {
        return merchantApplicationService.submit(user, request.merchantName(), request.businessLicenseUrl(), sourceIp(servletRequest));
    }

    private String sourceIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        return forwardedFor == null ? request.getRemoteAddr() : forwardedFor.split(",")[0].trim();
    }

    public record SubmitApplicationRequest(
            @NotBlank @Size(max = 128) String merchantName,
            @Size(max = 512) String businessLicenseUrl) {
    }
}
