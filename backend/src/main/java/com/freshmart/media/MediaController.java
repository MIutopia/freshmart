package com.freshmart.media;

import com.freshmart.auth.CurrentUser;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final MediaStorageService storageService;

    public MediaController(MediaStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public UploadView upload(@AuthenticationPrincipal CurrentUser user, @RequestParam("file") MultipartFile file) {
        MediaStorageService.MediaAssetView asset = storageService.store(user, file);
        return new UploadView(asset.url(), asset.contentType(), asset.sizeBytes());
    }

    @GetMapping("/{storageKey}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal CurrentUser user, @PathVariable String storageKey) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storageService.contentType(storageKey)))
                .cacheControl(CacheControl.noStore())
                .body(storageService.load(user, storageKey));
    }

    public record UploadView(String url, String contentType, long sizeBytes) {
    }
}
