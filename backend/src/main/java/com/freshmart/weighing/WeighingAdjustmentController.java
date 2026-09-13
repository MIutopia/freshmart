package com.freshmart.weighing;

import com.freshmart.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
                request.note(), idempotencyKey, sourceIp);
    }

    /** actualGrams 为实际称重克数，缺省时只结算金额、不动批次库存 */
    public record Request(@Positive long orderId, @NotNull BigDecimal actualGoodsAmount,
            @Positive Integer actualGrams, @Size(max = 500) String note) {
    }
}
