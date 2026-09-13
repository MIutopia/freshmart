package com.freshmart.catalog;

import com.freshmart.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {
    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping("/api/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public IdResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return new IdResponse(catalogService.createCategory(request.parentId(), request.name(), request.sortOrder()));
    }

    @PostMapping("/api/merchant/catalog/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MERCHANT')")
    public IdResponse createWarehouse(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody WarehouseRequest request) {
        return new IdResponse(catalogService.createWarehouse(user, request.deliveryZoneId(), request.name(), request.code(), request.address()));
    }

    @PostMapping("/api/merchant/catalog/warehouse-categories")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MERCHANT')")
    public void allowWarehouseCategory(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody WarehouseCategoryRequest request) {
        catalogService.allowWarehouseCategory(user, request.warehouseId(), request.categoryId());
    }

    @PostMapping("/api/merchant/catalog/warehouse-rules")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MERCHANT')")
    public void createWarehouseRule(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody WarehouseRuleRequest request) {
        catalogService.createWarehouseRule(user, request.warehouseId(), request.categoryId(), request.priority());
    }

    @PostMapping("/api/merchant/catalog/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MERCHANT')")
    public IdResponse createProduct(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ProductRequest request) {
        return new IdResponse(catalogService.createProduct(user, request.categoryId(), request.name(), request.description(),
                request.marketPricePerKg(), request.merchantPricePerKg()));
    }

    @PutMapping("/api/merchant/catalog/products/{productId}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MERCHANT')")
    public void publishProduct(@AuthenticationPrincipal CurrentUser user, @PathVariable long productId) {
        catalogService.publishProduct(user, productId);
    }

    @PostMapping("/api/merchant/catalog/batches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MERCHANT')")
    public IdResponse createBatch(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody BatchRequest request) {
        return new IdResponse(catalogService.createBatch(user, request.productId(), request.warehouseId(), request.batchNo(),
                request.availableGrams(), request.expiresOn()));
    }

    @GetMapping("/api/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CatalogService.CategoryView> listCategories() {
        return catalogService.listCategories();
    }

    @GetMapping("/api/catalog/products")
    public List<CatalogService.ProductView> listProducts(@RequestParam(required = false) Long categoryId) {
        return catalogService.listProducts(categoryId);
    }

    @GetMapping("/api/merchant/catalog/products")
    @PreAuthorize("hasRole('MERCHANT')")
    public List<CatalogService.ProductView> listMerchantProducts(@AuthenticationPrincipal CurrentUser user) {
        return catalogService.listMerchantProducts(user);
    }

    @GetMapping("/api/merchant/warehouses")
    @PreAuthorize("hasRole('MERCHANT')")
    public List<CatalogService.WarehouseView> listWarehouses(@AuthenticationPrincipal CurrentUser user) {
        return catalogService.listWarehouses(user);
    }

    public record IdResponse(long id) { }
    public record CategoryRequest(Long parentId, @NotBlank String name, @PositiveOrZero int sortOrder) { }
    public record WarehouseRequest(@Positive long deliveryZoneId, @NotBlank String name, @NotBlank String code, @NotBlank String address) { }
    public record WarehouseCategoryRequest(@Positive long warehouseId, @Positive long categoryId) { }
    public record WarehouseRuleRequest(@Positive long warehouseId, @Positive long categoryId, @PositiveOrZero int priority) { }
    public record ProductRequest(@Positive long categoryId, @NotBlank String name, String description,
            @NotNull @DecimalMin(value = "0.01") BigDecimal marketPricePerKg,
            @NotNull @DecimalMin(value = "0.01") BigDecimal merchantPricePerKg) { }
    public record BatchRequest(@Positive long productId, @Positive long warehouseId, @NotBlank String batchNo,
            @Positive int availableGrams, LocalDate expiresOn) { }
}
