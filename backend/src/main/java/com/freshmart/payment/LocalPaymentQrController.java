package com.freshmart.payment;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
public class LocalPaymentQrController {
    private final String qrFile;

    public LocalPaymentQrController(@Value("${commerce.payment.personal-wechat-qr-file:}") String qrFile) {
        this.qrFile = qrFile;
    }

    @GetMapping(value = "/public/payment-qr", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<Resource> paymentQr() {
        Path path = Path.of(qrFile).toAbsolutePath().normalize();
        if (qrFile.isBlank() || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(NOT_FOUND, "personal WeChat QR code is not available");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new FileSystemResource(path));
    }
}
