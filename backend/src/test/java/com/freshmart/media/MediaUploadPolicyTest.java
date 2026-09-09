package com.freshmart.media;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MediaUploadPolicyTest {
    @Test
    void acceptsThirtyMegabytes() {
        assertDoesNotThrow(() -> MediaUploadPolicy.validate(30L * 1024 * 1024));
    }

    @Test
    void rejectsAnythingOverThirtyMegabytes() {
        assertThrows(IllegalArgumentException.class, () -> MediaUploadPolicy.validate(30L * 1024 * 1024 + 1));
    }
}
