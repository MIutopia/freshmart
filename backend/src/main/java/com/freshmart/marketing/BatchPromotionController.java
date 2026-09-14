package com.freshmart.marketing;

import com.freshmart.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/marketing/batch-promotions")
@PreAuthorize("hasRole('ADMIN')")
public class BatchPromotionController {
    private final BatchPromotionService service;

    public BatchPromotionController(BatchPromotionService service) {
        this.service = service;
    }

    @GetMapping("/candidates")
    public List<BatchPromotionService.CandidateView> candidates() {
        return service.candidates();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BatchPromotionService.PromotionView confirm(@AuthenticationPrincipal CurrentUser admin,
            @Valid @RequestBody Request request, HttpServletRequest servletRequest) {
        String forwardedFor = servletRequest.getHeader("X-Forwarded-For");
        String sourceIp = forwardedFor == null ? servletRequest.getRemoteAddr() : forwardedFor.split(",")[0].trim();
        return service.confirm(admin, request.batchId(), request.markdownRate(), request.startsAt(), request.endsAt(), sourceIp);
    }

    public record Request(long batchId,
            @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "99.99") BigDecimal markdownRate,
            @NotNull LocalDateTime startsAt, @NotNull LocalDateTime endsAt) {
    }
}
