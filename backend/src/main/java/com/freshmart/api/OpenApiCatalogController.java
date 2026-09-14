package com.freshmart.api;

import com.freshmart.catalog.CatalogService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/open-api/v1")
public class OpenApiCatalogController {
    private final OpenApiClientService clientService;
    private final CatalogService catalogService;

    public OpenApiCatalogController(OpenApiClientService clientService, CatalogService catalogService) {
        this.clientService = clientService;
        this.catalogService = catalogService;
    }

    @GetMapping("/products")
    public List<CatalogService.ProductView> products(@RequestHeader("X-Api-Key") String apiKey,
            @RequestHeader("X-Api-Secret") String apiSecret, @RequestHeader("X-Api-Timestamp") String timestamp,
            @RequestHeader("X-Api-Nonce") String nonce, @RequestHeader("X-Api-Signature") String signature,
            @RequestParam(required = false) Long categoryId,
            HttpServletRequest request) {
        long started = System.currentTimeMillis();
        OpenApiClientService.ClientAccess client = clientService.authenticateSigned(apiKey, apiSecret, timestamp, nonce,
                signature, canonical(request, timestamp, nonce), "products:read");
        int responseStatus = 500;
        try {
            List<CatalogService.ProductView> products = catalogService.listProducts(categoryId);
            responseStatus = 200;
            if (client.merchantId() == null) return products;
            return products.stream().filter(product -> product.merchantId() == client.merchantId()).toList();
        } catch (ResponseStatusException exception) {
            responseStatus = exception.getStatusCode().value();
            throw exception;
        } finally {
            clientService.log(client, request.getMethod(), request.getRequestURI(), responseStatus,
                    System.currentTimeMillis() - started, request.getHeader("X-Request-Id") == null
                            ? UUID.randomUUID().toString() : request.getHeader("X-Request-Id"));
        }
    }

    private String canonical(HttpServletRequest request, String timestamp, String nonce) {
        String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
        return request.getMethod() + "\n" + request.getRequestURI() + query + "\n" + timestamp + "\n" + nonce;
    }
}
