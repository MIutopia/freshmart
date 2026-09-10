package com.freshmart.auth;

import java.util.Set;

public record CurrentUser(Long userId, Long sessionId, String loginName, Set<String> roles) {
    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
