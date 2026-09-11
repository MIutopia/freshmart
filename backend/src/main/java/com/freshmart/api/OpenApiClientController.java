package com.freshmart.api;

import com.freshmart.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/open-api/clients")
@PreAuthorize("hasRole('ADMIN')")
public class OpenApiClientController {
    private final OpenApiClientService service;

    public OpenApiClientController(OpenApiClientService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OpenApiClientService.CreatedClient create(@AuthenticationPrincipal CurrentUser admin,
            @Valid @RequestBody Request request) {
        return service.create(admin, request.merchantId(), request.name(), request.scopes(), request.expiresAt());
    }

    public record Request(Long merchantId, @NotBlank String name, @NotEmpty Set<String> scopes, LocalDateTime expiresAt) {
    }
}
