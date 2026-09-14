package com.freshmart.trade;

import com.freshmart.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import com.freshmart.payment.PaymentBillEntry;
import com.freshmart.payment.PaymentBillImportResult;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TradeController {
    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @PostMapping("/api/trades")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CONSUMER')")
    public TradeService.TradeView createTrade(
            @AuthenticationPrincipal CurrentUser user,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody CreateTradeRequest request) {
        List<TradeService.CheckoutLine> lines = request.lines().stream()
                .map(line -> new TradeService.CheckoutLine(line.productId(), line.weightGrams()))
                .toList();
        return tradeService.create(user, request.deliveryZoneId(), request.addressSnapshot(), lines,
                request.couponIds(), request.pointsToRedeem(), idempotencyKey);
    }

    @PostMapping("/api/payments/{tradeNo}/prepay")
    @PreAuthorize("hasRole('CONSUMER')")
    public TradeService.PaymentView prepay(@AuthenticationPrincipal CurrentUser user, @PathVariable String tradeNo) {
        return tradeService.prepay(user, tradeNo);
    }

    @GetMapping("/api/trades/{tradeNo}/payment-code")
    @PreAuthorize("hasRole('CONSUMER')")
    public TradeService.PaymentView paymentCode(@AuthenticationPrincipal CurrentUser user, @PathVariable String tradeNo) {
        return tradeService.paymentCode(user, tradeNo);
    }

    @PostMapping("/api/payments/{tradeNo}/proof")
    @PreAuthorize("hasRole('CONSUMER')")
    public TradeService.PaymentView submitPaymentProof(@AuthenticationPrincipal CurrentUser user, @PathVariable String tradeNo,
            @Valid @RequestBody PaymentProofRequest request) {
        return tradeService.submitPaymentProof(user, tradeNo, request.proofUrl(), request.remarkText());
    }

    @PostMapping("/api/admin/payments/{tradeNo}/confirm-personal-wechat-qr")
    @PreAuthorize("hasAnyRole('OPERATIONS','FINANCE')")
    public TradeService.TradeView confirmPersonalWechatQrPayment(@AuthenticationPrincipal CurrentUser admin,
            @PathVariable String tradeNo) {
        return tradeService.confirmPersonalWechatQrPayment(admin, tradeNo);
    }

    @PostMapping("/api/admin/payments/{paymentNo}/verify-proof")
    @PreAuthorize("hasAnyRole('OPERATIONS','FINANCE')")
    public TradeService.PaymentView verifyPaymentProof(@AuthenticationPrincipal CurrentUser admin, @PathVariable String paymentNo,
            @Valid @RequestBody PaymentVerificationRequest request) {
        return tradeService.verifyPaymentProof(admin, paymentNo, request.approved(), request.note());
    }

    @PostMapping("/api/admin/payments/{paymentNo}/match-bill")
    @PreAuthorize("hasAnyRole('OPERATIONS','FINANCE')")
    public TradeService.PaymentView matchPaymentBill(@AuthenticationPrincipal CurrentUser admin, @PathVariable String paymentNo,
            @Valid @RequestBody BillMatchRequest request) {
        return tradeService.matchPaymentBill(admin, paymentNo, request.transactionId(), request.amount(), request.direction(), request.remarkText());
    }

    @PostMapping("/api/admin/payments/bill-imports")
    @PreAuthorize("hasAnyRole('OPERATIONS','FINANCE')")
    public PaymentBillImportResult importPaymentBill(@AuthenticationPrincipal CurrentUser admin,
            @Valid @RequestBody PaymentBillImportRequest request) {
        return tradeService.importPaymentBill(admin, request.fileName(), request.entries().stream()
                .map(PaymentBillEntryRequest::toEntry).toList());
    }

    @GetMapping("/api/admin/payments/reconciliation-differences")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TradeService.ReconciliationDifferenceView> listReconciliationDifferences(
    @RequestParam(defaultValue = "UNHANDLED") String status) {
        return tradeService.listReconciliationDifferences(status);
    }

    @PostMapping("/api/admin/payments/reconciliation-differences/{differenceId}/claim")
    @PreAuthorize("hasAnyRole('OPERATIONS','FINANCE')")
    public TradeService.ReconciliationDifferenceView claimReconciliationDifference(
            @AuthenticationPrincipal CurrentUser operator, @PathVariable long differenceId,
            @Valid @RequestBody ReconciliationNoteRequest request) {
        return tradeService.claimReconciliationDifference(operator, differenceId, request.note());
    }

    @PostMapping("/api/admin/payments/reconciliation-differences/{differenceId}/shelve")
    @PreAuthorize("hasAnyRole('OPERATIONS','FINANCE')")
    public TradeService.ReconciliationDifferenceView shelveReconciliationDifference(
            @AuthenticationPrincipal CurrentUser operator, @PathVariable long differenceId,
            @Valid @RequestBody ReconciliationNoteRequest request) {
        return tradeService.shelveReconciliationDifference(operator, differenceId, request.note());
    }

    @PostMapping("/api/admin/payments/reconciliation-differences/{differenceId}/resolve")
    @PreAuthorize("hasAnyRole('OPERATIONS','FINANCE')")
    public TradeService.ReconciliationDifferenceView resolveReconciliationDifference(
            @AuthenticationPrincipal CurrentUser operator, @PathVariable long differenceId,
            @Valid @RequestBody ReconciliationResolveRequest request) {
        return tradeService.resolveReconciliationDifference(operator, differenceId, request.resolution(), request.note());
    }

    @PostMapping("/api/payments/{tradeNo}/confirm-balance")
    @PreAuthorize("hasRole('CONSUMER')")
    public TradeService.TradeView confirmBalancePayment(@AuthenticationPrincipal CurrentUser user, @PathVariable String tradeNo) {
        return tradeService.confirmBalancePayment(user, tradeNo);
    }

    public record CreateTradeRequest(@Positive long deliveryZoneId, @NotNull Map<String, Object> addressSnapshot,
            @NotEmpty List<@Valid CheckoutLineRequest> lines, List<@Positive Long> couponIds,
            @PositiveOrZero int pointsToRedeem) {
        public CreateTradeRequest(long deliveryZoneId, Map<String, Object> addressSnapshot,
                List<CheckoutLineRequest> lines, List<Long> couponIds) {
            this(deliveryZoneId, addressSnapshot, lines, couponIds, 0);
        }
    }

    public record CheckoutLineRequest(@Positive long productId, @Positive int weightGrams) {
    }

    public record PaymentProofRequest(@NotBlank String proofUrl, @NotBlank String remarkText) {
    }

    public record PaymentVerificationRequest(boolean approved, @NotBlank String note) {
    }

    public record BillMatchRequest(@NotBlank String transactionId, @Positive BigDecimal amount, @NotBlank String direction, @NotBlank String remarkText) {
    }

    public record PaymentBillImportRequest(@NotBlank String fileName, @NotEmpty List<@Valid PaymentBillEntryRequest> entries) {
    }

    public record PaymentBillEntryRequest(@NotBlank String transactionId, LocalDateTime transactionTime,
            String remarkText, @Positive BigDecimal amount, @NotBlank String direction) {
        PaymentBillEntry toEntry() {
            return new PaymentBillEntry(transactionId, transactionTime, remarkText, amount, direction);
        }
    }

    public record ReconciliationNoteRequest(@NotBlank String note) {
    }

    public record ReconciliationResolveRequest(@NotBlank String resolution, @NotBlank String note) {
    }
}
