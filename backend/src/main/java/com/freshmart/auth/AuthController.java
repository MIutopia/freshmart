package com.freshmart.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthService.LoginResult result = authService.login(request.loginName(), request.password(), sourceIp(servletRequest));
        return new LoginResponse(result.accessToken(), result.expiresAt(), result.user().userId(), result.user().loginName(), result.user().roles());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal CurrentUser user, HttpServletRequest servletRequest) {
        authService.logout(user, sourceIp(servletRequest));
    }

    @GetMapping("/me")
    public ProfileResponse me(@AuthenticationPrincipal CurrentUser user) {
        return new ProfileResponse(user.userId(), user.loginName(), user.roles());
    }

    private String sourceIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        return forwardedFor == null ? request.getRemoteAddr() : forwardedFor.split(",")[0].trim();
    }

    public record LoginRequest(@NotBlank String loginName, @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, LocalDateTime expiresAt, Long userId, String loginName, Set<String> roles) {
    }

    public record ProfileResponse(Long userId, String loginName, Set<String> roles) {
    }
}
