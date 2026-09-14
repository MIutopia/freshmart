package com.freshmart.media;

import java.util.Locale;
import java.util.Map;

public final class MediaUploadPolicy {
    public static final long MAX_SIZE_BYTES = 30L * 1024 * 1024;
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png", "image/png",
            ".webp", "image/webp",
            ".mp4", "video/mp4",
            ".mov", "video/quicktime");

    private MediaUploadPolicy() {
    }

    public static void validate(long sizeBytes) {
        if (sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("media size must be between 1 byte and 30 MB");
        }
    }

    public static String contentType(String originalFileName, String contentType) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("media file name is required");
        }
        int extensionStart = originalFileName.lastIndexOf('.');
        String extension = extensionStart < 0 ? "" : originalFileName.substring(extensionStart).toLowerCase(Locale.ROOT);
        String expectedContentType = ALLOWED_TYPES.get(extension);
        if (expectedContentType == null) {
            throw new IllegalArgumentException("only JPEG, PNG, WebP, MP4 and MOV media files are allowed");
        }
        if (contentType == null || contentType.isBlank() || "application/octet-stream".equalsIgnoreCase(contentType.trim())) {
            return expectedContentType;
        }
        if (!expectedContentType.equalsIgnoreCase(contentType.trim())) {
            throw new IllegalArgumentException("media content type does not match the file extension");
        }
        return expectedContentType;
    }

    public static String extension(String originalFileName) {
        int extensionStart = originalFileName.lastIndexOf('.');
        return originalFileName.substring(extensionStart).toLowerCase(Locale.ROOT);
    }
}
