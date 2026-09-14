package com.freshmart.ai;

import com.freshmart.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiAssistantController {
    private final AiAssistantService service;

    public AiAssistantController(AiAssistantService service) {
        this.service = service;
    }

    @PostMapping("/api/ai/assistant/messages")
    @PreAuthorize("hasRole('CONSUMER')")
    public AiAssistantService.AssistantReply reply(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody MessageRequest request) {
        return service.reply(user, request.message());
    }

    public record MessageRequest(@NotBlank String message) { }
}
