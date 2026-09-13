package com.freshmart.notification;

import com.freshmart.auth.AuditLogService;
import com.freshmart.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('CONSUMER')")
public class NotificationPreferenceController {
    private final NotificationPreferenceService preferenceService;
    private final AuditLogService auditLogService;

    public NotificationPreferenceController(NotificationPreferenceService preferenceService, AuditLogService auditLogService) {
        this.preferenceService = preferenceService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/api/notification-preferences")
    public NotificationPreferenceService.PreferenceView find(@AuthenticationPrincipal CurrentUser user) {
        return preferenceService.find(user.userId());
    }

    @PutMapping("/api/notification-preferences")
    public NotificationPreferenceService.PreferenceView update(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody UpdateRequest request, HttpServletRequest servletRequest) {
        NotificationPreferenceService.PreferenceView updated = preferenceService.update(user.userId(), request.seasonalCardEnabled());
        auditLogService.record(user.userId(), "NOTIFICATION_PREFERENCE_UPDATED", "NOTIFICATION_PREFERENCE",
                user.userId().toString(), sourceIp(servletRequest));
        return updated;
    }

    private String sourceIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        return forwardedFor == null ? request.getRemoteAddr() : forwardedFor.split(",")[0].trim();
    }

    public record UpdateRequest(@NotNull Boolean seasonalCardEnabled) {
    }
}
