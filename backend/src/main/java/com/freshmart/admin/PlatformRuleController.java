package com.freshmart.admin;

import com.freshmart.auth.AuditLogService;
import com.freshmart.auth.CurrentUser;
import com.freshmart.platform.PlatformRuleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/platform-rules")
@PreAuthorize("hasRole('ADMIN')")
public class PlatformRuleController {
    private final PlatformRuleService platformRuleService;
    private final AuditLogService auditLogService;

    public PlatformRuleController(PlatformRuleService platformRuleService, AuditLogService auditLogService) {
        this.platformRuleService = platformRuleService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<PlatformRuleService.RuleView> list() {
        return platformRuleService.list();
    }

    @PutMapping("/{ruleKey}")
    public PlatformRuleService.RuleView update(@AuthenticationPrincipal CurrentUser admin,
            @PathVariable String ruleKey, @Valid @RequestBody UpdateRuleRequest request, HttpServletRequest servletRequest) {
        PlatformRuleService.RuleView updated = platformRuleService.update(admin.userId(), ruleKey, request.value());
        auditLogService.record(admin.userId(), "PLATFORM_RULE_UPDATED", "PLATFORM_RULE", ruleKey, sourceIp(servletRequest));
        return updated;
    }

    private String sourceIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        return forwardedFor == null ? request.getRemoteAddr() : forwardedFor.split(",")[0].trim();
    }

    public record UpdateRuleRequest(@NotBlank String value) {
    }
}
