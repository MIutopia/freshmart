package com.freshmart.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/open-api/v1")
public class OpenApiReadController {
    private final OpenApiClientService clientService;
    private final OpenApiReadService readService;

    public OpenApiReadController(OpenApiClientService clientService, OpenApiReadService readService) {
        this.clientService = clientService;
        this.readService = readService;
    }

    @GetMapping("/inventory")
    public List<OpenApiReadService.InventoryView> inventory(@RequestHeader("X-Api-Key") String key,
            @RequestHeader("X-Api-Secret") String secret, @RequestHeader("X-Api-Timestamp") String timestamp,
            @RequestHeader("X-Api-Nonce") String nonce, @RequestHeader("X-Api-Signature") String signature,
            @RequestParam(required = false) Long productId, HttpServletRequest request) {
        OpenApiClientService.ClientAccess client = authenticate(key, secret, timestamp, nonce, signature, request, "inventory:read");
        return logged(client, request, () -> readService.inventory(client.merchantId(), productId));
    }

    @GetMapping("/orders/{orderNo}")
    public OpenApiReadService.OrderView order(@PathVariable String orderNo, @RequestHeader("X-Api-Key") String key,
            @RequestHeader("X-Api-Secret") String secret, @RequestHeader("X-Api-Timestamp") String timestamp,
            @RequestHeader("X-Api-Nonce") String nonce, @RequestHeader("X-Api-Signature") String signature,
            HttpServletRequest request) {
        OpenApiClientService.ClientAccess client = authenticate(key, secret, timestamp, nonce, signature, request, "orders:read");
        return logged(client, request, () -> readService.order(orderNo, client.merchantId()));
    }

    @GetMapping("/orders/{orderNo}/receipt")
    public Map<String, Object> receipt(@PathVariable String orderNo, @RequestHeader("X-Api-Key") String key,
            @RequestHeader("X-Api-Secret") String secret, @RequestHeader("X-Api-Timestamp") String timestamp,
            @RequestHeader("X-Api-Nonce") String nonce, @RequestHeader("X-Api-Signature") String signature,
            HttpServletRequest request) {
        OpenApiClientService.ClientAccess client = authenticate(key, secret, timestamp, nonce, signature, request, "receipts:read");
        return logged(client, request, () -> readService.receipt(orderNo, client.merchantId()));
    }

    @GetMapping("/orders/{orderNo}/traceability")
    public List<OpenApiReadService.TraceView> traceability(@PathVariable String orderNo, @RequestHeader("X-Api-Key") String key,
            @RequestHeader("X-Api-Secret") String secret, @RequestHeader("X-Api-Timestamp") String timestamp,
            @RequestHeader("X-Api-Nonce") String nonce, @RequestHeader("X-Api-Signature") String signature,
            HttpServletRequest request) {
        OpenApiClientService.ClientAccess client = authenticate(key, secret, timestamp, nonce, signature, request, "traceability:read");
        return logged(client, request, () -> readService.traceability(orderNo, client.merchantId()));
    }

    private OpenApiClientService.ClientAccess authenticate(String key, String secret, String timestamp, String nonce,
            String signature, HttpServletRequest request, String scope) {
        return clientService.authenticateSigned(key, secret, timestamp, nonce, signature, canonical(request, timestamp, nonce), scope);
    }

    private <T> T logged(OpenApiClientService.ClientAccess client, HttpServletRequest request, java.util.function.Supplier<T> action) {
        long started = System.currentTimeMillis();
        int responseStatus = 500;
        try {
            T result = action.get();
            responseStatus = 200;
            return result;
        } catch (ResponseStatusException exception) {
            responseStatus = exception.getStatusCode().value();
            throw exception;
        } finally {
            clientService.log(client, request.getMethod(), request.getRequestURI(), responseStatus,
                    System.currentTimeMillis() - started, requestId(request));
        }
    }

    private String canonical(HttpServletRequest request, String timestamp, String nonce) {
        String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
        return request.getMethod() + "\n" + request.getRequestURI() + query + "\n" + timestamp + "\n" + nonce;
    }

    private String requestId(HttpServletRequest request) {
        String value = request.getHeader("X-Request-Id");
        return value == null || value.isBlank() ? java.util.UUID.randomUUID().toString() : value;
    }
}
