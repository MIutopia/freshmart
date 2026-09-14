package com.freshmart.marketing;

import com.freshmart.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MarketingController {
    private final MarketingService marketingService;

    public MarketingController(MarketingService marketingService) {
        this.marketingService = marketingService;
    }

    @PostMapping("/api/merchant/marketing/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MERCHANT')")
    public IdResponse createPromotion(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody PromotionRequest request) {
        return new IdResponse(marketingService.createPromotion(user, request.promotionType(), request.name(), request.rule(),
                request.startsAt(), request.endsAt(), request.stackable()));
    }

    @PostMapping("/api/merchant/marketing/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MERCHANT')")
    public IdResponse createCoupon(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody CouponRequest request) {
        return new IdResponse(marketingService.createCoupon(user, request.name(), request.couponType(),
                request.thresholdAmount(), request.discountAmount(), request.startsAt(), request.endsAt(),
                request.totalQuantity()));
    }

    @PostMapping("/api/merchant/marketing/flash-sales")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MERCHANT')")
    public IdResponse createFlashSale(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody FlashSaleRequest request) {
        return new IdResponse(marketingService.createFlashSale(user, request.productId(), request.salePricePerKg(),
                request.totalGrams(), request.perUserLimitGrams(), request.startsAt(), request.endsAt()));
    }

    @PostMapping("/api/admin/marketing/membership-levels")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public IdResponse createMembershipLevel(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody MembershipLevelRequest request) {
        return new IdResponse(marketingService.createMembershipLevel(user, request.name(), request.minPoints(),
                request.discountRate()));
    }

    public record IdResponse(long id) {
    }

    public record PromotionRequest(@NotBlank String promotionType, @NotBlank String name,
            @NotNull Map<String, Object> rule, @NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt,
            boolean stackable) {
    }

    public record CouponRequest(@NotBlank String name, @NotBlank String couponType,
            @NotNull @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal thresholdAmount,
            @NotNull @jakarta.validation.constraints.DecimalMin(value = "0.01") BigDecimal discountAmount,
            @NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt,
            @jakarta.validation.constraints.Positive int totalQuantity) {
    }

    public record FlashSaleRequest(@jakarta.validation.constraints.Positive long productId,
            @NotNull @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal salePricePerKg,
            @jakarta.validation.constraints.Positive int totalGrams, @jakarta.validation.constraints.Positive int perUserLimitGrams,
            @NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt) { }

    public record MembershipLevelRequest(@NotBlank String name,
            @jakarta.validation.constraints.PositiveOrZero int minPoints,
            @NotNull @jakarta.validation.constraints.DecimalMin("0.00") @jakarta.validation.constraints.DecimalMax("100.00") BigDecimal discountRate) {
    }
}
