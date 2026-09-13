package com.freshmart.notification;

import com.freshmart.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HolidayCardController {
    private final HolidayCardService service;

    public HolidayCardController(HolidayCardService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/ai/holiday-cards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public InboxService.MessageView send(@AuthenticationPrincipal CurrentUser admin,
            @Valid @RequestBody Request request, HttpServletRequest servletRequest) {
        String forwardedFor = servletRequest.getHeader("X-Forwarded-For");
        String sourceIp = forwardedFor == null ? servletRequest.getRemoteAddr() : forwardedFor.split(",")[0].trim();
        return service.send(admin.userId(), request.userId(), request.holidayKey(), request.greeting(), sourceIp);
    }

    @GetMapping("/api/holiday-cards/preview")
    @PreAuthorize("isAuthenticated()")
    public HolidayCardService.PreviewView preview(@RequestParam(required = false) String holidayKey,
            @RequestParam(required = false) String greeting) {
        return service.preview(holidayKey, greeting);
    }

    public record Request(@Positive long userId, @NotBlank @Size(max = 32) String holidayKey,
            @NotBlank @Size(max = 120) String greeting) { }
}
