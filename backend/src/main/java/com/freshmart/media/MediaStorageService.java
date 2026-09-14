package com.freshmart.media;

import com.freshmart.auth.CurrentUser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class MediaStorageService {
    private final Path root;
    private final JdbcTemplate logJdbcTemplate;

    public MediaStorageService(@Value("${storage.media.root:./data/media}") String root,
            @Qualifier("logJdbcTemplate") JdbcTemplate logJdbcTemplate) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.logJdbcTemplate = logJdbcTemplate;
    }

    public MediaAssetView store(CurrentUser user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("media file is required");
        }
        MediaUploadPolicy.validate(file.getSize());
        String original = Path.of(file.getOriginalFilename()).getFileName().toString();
        String contentType = MediaUploadPolicy.contentType(original, file.getContentType());
        String storageKey = UUID.randomUUID().toString().replace("-", "");
        String storageFileName = storageKey + MediaUploadPolicy.extension(original);
        Path target = root.resolve(storageFileName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("media path is invalid");
        }
        try {
            Files.createDirectories(root);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            logJdbcTemplate.update("""
                    INSERT INTO media_assets (storage_key, storage_file_name, uploader_user_id, original_file_name, content_type, size_bytes)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, storageKey, storageFileName, user.userId(), original, contentType, file.getSize());
            return new MediaAssetView("/api/media/" + storageKey, contentType, file.getSize());
        } catch (IOException exception) {
            throw new IllegalStateException("media storage failed", exception);
        }
    }

    public Resource load(CurrentUser user, String storageKey) {
        MediaAsset asset = find(storageKey);
        if (asset.uploaderUserId() != user.userId() && !canReviewEvidence(user.roles())) {
            throw new ResponseStatusException(FORBIDDEN, "media is not accessible to the current user");
        }
        Path target = root.resolve(asset.storageFileName()).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            throw new ResponseStatusException(NOT_FOUND, "media file is not available");
        }
        return new FileSystemResource(target);
    }

    public MediaAssetView describeOwned(CurrentUser user, String url) {
        String prefix = "/api/media/";
        if (url == null || !url.startsWith(prefix) || url.length() != prefix.length() + 32) {
            throw new ResponseStatusException(BAD_REQUEST, "evidence must be an uploaded media URL");
        }
        MediaAsset asset = find(url.substring(prefix.length()));
        if (asset.uploaderUserId() != user.userId()) {
            throw new ResponseStatusException(FORBIDDEN, "evidence must belong to the current user");
        }
        return new MediaAssetView(url, asset.contentType(), asset.sizeBytes());
    }

    public String contentType(String storageKey) {
        return find(storageKey).contentType();
    }

    private MediaAsset find(String storageKey) {
        if (!storageKey.matches("[a-fA-F0-9]{32}")) {
            throw new ResponseStatusException(NOT_FOUND, "media asset not found");
        }
        return logJdbcTemplate.query("""
                SELECT storage_key, storage_file_name, uploader_user_id, content_type, size_bytes
                FROM media_assets WHERE storage_key = ?
                """, (rs, row) -> new MediaAsset(rs.getString("storage_key"), rs.getString("storage_file_name"),
                rs.getLong("uploader_user_id"), rs.getString("content_type"), rs.getLong("size_bytes")), storageKey)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "media asset not found"));
    }

    private boolean canReviewEvidence(Set<String> roles) {
        return roles.contains("ADMIN") || roles.contains("OPERATIONS") || roles.contains("FINANCE");
    }

    public record MediaAssetView(String url, String contentType, long sizeBytes) {
    }

    private record MediaAsset(String storageKey, String storageFileName, long uploaderUserId, String contentType, long sizeBytes) {
    }
}
