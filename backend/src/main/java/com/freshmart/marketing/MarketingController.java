package com.freshmart.marketing;

import com.freshmart.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
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

    public record IdResponse(long id) {
    }

    public record PromotionRequest(@NotBlank String promotionType, @NotBlank String name,
            @NotNull Map<String, Object> rule, @NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt,
            boolean stackable) {
    }
}
