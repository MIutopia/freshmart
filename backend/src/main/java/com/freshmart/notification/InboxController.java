package com.freshmart.notification;

import com.freshmart.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InboxController {
    private final InboxService inboxService;

    public InboxController(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping("/api/inbox/messages")
    @PreAuthorize("hasAnyRole('CONSUMER', 'MERCHANT', 'RIDER', 'ADMIN')")
    public List<InboxService.MessageView> list(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return inboxService.list(user, unreadOnly);
    }

    @PostMapping("/api/inbox/messages/{messageId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('CONSUMER', 'MERCHANT', 'RIDER', 'ADMIN')")
    public void markRead(@AuthenticationPrincipal CurrentUser user, @PathVariable @Positive long messageId) {
        inboxService.markRead(user, messageId);
    }

    @PostMapping("/api/admin/inbox/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public InboxService.MessageView send(@Valid @RequestBody SendRequest request) {
        return inboxService.send(request.userId(), request.messageType(), request.title(), request.body(),
                request.cardSvgUrl(), request.businessType(), request.businessId());
    }

    public record SendRequest(@Positive long userId, @NotBlank String messageType, @NotBlank String title,
            @NotBlank String body, String cardSvgUrl, String businessType, Long businessId) { }
}
