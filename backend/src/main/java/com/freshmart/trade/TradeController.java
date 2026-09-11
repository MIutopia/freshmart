package com.freshmart.trade;

import com.freshmart.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
        return tradeService.create(user, request.deliveryZoneId(), request.addressSnapshot(), lines, request.couponIds(), idempotencyKey);
    }

    @PostMapping("/api/payments/{tradeNo}/prepay")
    @PreAuthorize("hasRole('CONSUMER')")
    public TradeService.PaymentView prepay(@AuthenticationPrincipal CurrentUser user, @PathVariable String tradeNo) {
        return tradeService.prepay(user, tradeNo);
    }

    @PostMapping("/api/payments/{tradeNo}/confirm-simulated")
    @PreAuthorize("hasRole('CONSUMER')")
    public TradeService.TradeView confirmSimulatedPayment(@AuthenticationPrincipal CurrentUser user, @PathVariable String tradeNo) {
        return tradeService.confirmSimulatedPayment(user, tradeNo);
    }

    @PostMapping("/api/payments/{tradeNo}/confirm-balance")
    @PreAuthorize("hasRole('CONSUMER')")
    public TradeService.TradeView confirmBalancePayment(@AuthenticationPrincipal CurrentUser user, @PathVariable String tradeNo) {
        return tradeService.confirmBalancePayment(user, tradeNo);
    }

    public record CreateTradeRequest(@Positive long deliveryZoneId, @NotNull Map<String, Object> addressSnapshot,
            @NotEmpty List<@Valid CheckoutLineRequest> lines, List<@Positive Long> couponIds) {
    }

    public record CheckoutLineRequest(@Positive long productId, @Positive int weightGrams) {
    }
}
