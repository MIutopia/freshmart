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
            @RequestHeader("X-Api-Secret") String apiSecret, @RequestParam(required = false) Long categoryId,
            HttpServletRequest request) {
        long started = System.currentTimeMillis();
        OpenApiClientService.ClientAccess client = clientService.authenticate(apiKey, apiSecret, "products:read");
        try {
            List<CatalogService.ProductView> products = catalogService.listProducts(categoryId);
            if (client.merchantId() == null) return products;
            return products.stream().filter(product -> product.merchantId() == client.merchantId()).toList();
        } finally {
            clientService.log(client, request.getMethod(), request.getRequestURI(), 200,
                    System.currentTimeMillis() - started, request.getHeader("X-Request-Id") == null
                            ? UUID.randomUUID().toString() : request.getHeader("X-Request-Id"));
        }
    }
}
