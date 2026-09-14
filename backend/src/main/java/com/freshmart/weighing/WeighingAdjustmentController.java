package com.freshmart.weighing;

import com.freshmart.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WeighingAdjustmentController {
    private final WeighingAdjustmentService service;

    public WeighingAdjustmentController(WeighingAdjustmentService service) {
        this.service = service;
    }

    @PostMapping("/api/weighing-adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public WeighingAdjustmentService.AdjustmentView submit(@AuthenticationPrincipal CurrentUser operator,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody Request request, HttpServletRequest servletRequest) {
        String forwardedFor = servletRequest.getHeader("X-Forwarded-For");
        String sourceIp = forwardedFor == null ? servletRequest.getRemoteAddr() : forwardedFor.split(",")[0].trim();
        return service.submit(operator, request.orderId(), request.actualGoodsAmount(), request.actualGrams(),
                toItemWeighings(request.items()), request.note(), idempotencyKey, sourceIp);
    }

    /** 逐项称重前先取订单项与预估克数，商家据此逐项录入实际结果 */
    @GetMapping("/api/merchant/orders/{orderId}/weighing-sheet")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    public WeighingAdjustmentService.WeighingSheetView weighingSheet(@AuthenticationPrincipal CurrentUser operator,
            @PathVariable long orderId) {
        return service.weighingSheet(operator, orderId);
    }

    private List<WeighingAdjustmentService.ItemWeighing> toItemWeighings(List<ItemRequest> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> new WeighingAdjustmentService.ItemWeighing(item.orderItemId(), item.actualGrams(),
                        item.actualGoodsAmount()))
                .toList();
    }

    /**
     * actualGrams 为整单实际称重克数，items 为逐项称重结果，两者任选其一。
     * 提供 items 时整单金额与克数由各项汇总得出，并按订单项各自回补批次库存。
     */
    public record Request(@Positive long orderId, BigDecimal actualGoodsAmount,
            @Positive Integer actualGrams, List<ItemRequest> items, @Size(max = 500) String note) {
    }

    public record ItemRequest(@Positive long orderItemId, @Positive int actualGrams,
            @NotNull @DecimalMin("0.00") BigDecimal actualGoodsAmount) {
    }
}
