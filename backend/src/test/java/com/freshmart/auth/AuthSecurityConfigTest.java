package com.freshmart.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthSecurityConfigTest {
    @Test
    void bcryptEncoderVerifiesPasswordWithoutStoringPlainText() {
        PasswordEncoder encoder = new AuthSecurityConfig().passwordEncoder();
        String encoded = encoder.encode("freshmart-local-password");
        assertTrue(encoder.matches("freshmart-local-password", encoded));
        assertFalse(encoder.matches("incorrect-password", encoded));
    }

    @Test
    void localTestAccountHashMatchesDocumentedPassword() {
        PasswordEncoder encoder = new AuthSecurityConfig().passwordEncoder();
        assertTrue(encoder.matches("password", "$2a$10$p/T1yo6nQC4vyFvYV7hHm.B.5dfw9s1GUwqAJnzs/ZDiyPi.Yb1Ym"));
    }
}
