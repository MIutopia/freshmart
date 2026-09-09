package com.freshmart.media;

public final class MediaUploadPolicy {
    public static final long MAX_SIZE_BYTES = 30L * 1024 * 1024;

    private MediaUploadPolicy() {
    }

    public static void validate(long sizeBytes) {
        if (sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("media size must be between 1 byte and 30 MB");
        }
    }
}
