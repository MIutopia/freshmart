package com.freshmart.refund;

import com.freshmart.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
public class RefundController {
    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/api/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CONSUMER')")
    public RefundService.RefundView apply(@AuthenticationPrincipal CurrentUser user,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody RefundRequest request) {
        return refundService.apply(user, request.orderId(), request.issueType(), request.description(),
                request.evidenceImages(), idempotencyKey);
    }

    @PutMapping("/api/admin/refunds/{refundId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public RefundService.RefundView review(@AuthenticationPrincipal CurrentUser admin, @PathVariable long refundId,
            @Valid @RequestBody ReviewRequest request) {
        return refundService.review(admin, refundId, request.approved(), request.reviewNote());
    }

    public record RefundRequest(@Positive long orderId, @NotBlank String issueType,
            @NotBlank String description, @NotEmpty List<@NotBlank String> evidenceImages) {
    }

    public record ReviewRequest(boolean approved, @NotBlank String reviewNote) {
    }
}
