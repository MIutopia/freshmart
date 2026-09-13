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
    private final RefundAiReviewService refundAiReviewService;

    public RefundController(RefundService refundService, RefundAiReviewService refundAiReviewService) {
        this.refundService = refundService;
        this.refundAiReviewService = refundAiReviewService;
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
    @PreAuthorize("hasAnyRole('OPERATIONS','FINANCE')")
    public RefundService.RefundView review(@AuthenticationPrincipal CurrentUser admin, @PathVariable long refundId,
            @Valid @RequestBody ReviewRequest request) {
        return refundService.review(admin, refundId, request.approved(), request.reviewNote());
    }

    @PostMapping("/api/admin/refunds/{refundNo}/manual-complete")
    @PreAuthorize("hasAnyRole('OPERATIONS','FINANCE')")
    public RefundService.RefundView completeManualRefund(@AuthenticationPrincipal CurrentUser admin, @PathVariable String refundNo) {
        return refundService.completeManualRefund(admin, refundNo);
    }

    @PostMapping("/api/admin/refunds/{refundNo}/manual-fail")
    @PreAuthorize("hasAnyRole('OPERATIONS','FINANCE')")
    public RefundService.RefundView failManualRefund(@AuthenticationPrincipal CurrentUser admin, @PathVariable String refundNo,
            @Valid @RequestBody ManualRefundFailureRequest request) {
        return refundService.failManualRefund(admin, refundNo, request.reason());
    }

    @PostMapping("/api/admin/refunds/{refundNo}/manual-retry")
    @PreAuthorize("hasAnyRole('OPERATIONS','FINANCE')")
    public RefundService.RefundView retryManualRefund(@AuthenticationPrincipal CurrentUser admin, @PathVariable String refundNo,
            @Valid @RequestBody ManualRefundFailureRequest request) {
        return refundService.retryManualRefund(admin, refundNo, request.reason());
    }

    @PutMapping("/api/admin/refunds/{refundNo}/inventory-disposition")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public RefundService.InventoryDispositionView processInventoryDisposition(@AuthenticationPrincipal CurrentUser operator,
            @PathVariable String refundNo, @Valid @RequestBody InventoryDispositionRequest request) {
        return refundService.processInventoryDisposition(operator, refundNo, request.disposition(), request.note());
    }

    @PostMapping("/api/admin/refunds/{refundNo}/ai-review-suggestion")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','FINANCE')")
    public RefundAiReviewService.SuggestionView aiReviewSuggestion(@AuthenticationPrincipal CurrentUser operator,
            @PathVariable String refundNo) {
        return refundAiReviewService.suggest(operator, refundNo);
    }

    public record RefundRequest(@Positive long orderId, @NotBlank String issueType,
            @NotBlank String description, @NotEmpty List<@NotBlank String> evidenceImages) {
    }

    public record ReviewRequest(boolean approved, @NotBlank String reviewNote) {
    }

    public record ManualRefundFailureRequest(@NotBlank String reason) {
    }

    public record InventoryDispositionRequest(@NotBlank String disposition, @NotBlank String note) {
    }
}
