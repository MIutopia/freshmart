package com.freshmart.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthTokenCodecTest {
    @Test
    void createsDistinctUrlSafeTokensAndStableHashes() {
        String first = AuthTokenCodec.newToken();
        String second = AuthTokenCodec.newToken();
        assertNotEquals(first, second);
        assertTrue(first.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(AuthTokenCodec.hash(first), AuthTokenCodec.hash(first));
        assertNotEquals(first, AuthTokenCodec.hash(first));
    }
}
