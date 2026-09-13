package com.freshmart.catalog;

import com.freshmart.auth.CurrentUser;
import com.freshmart.order.MarketPriceSettlementPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class CatalogService {
    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate deliveryJdbcTemplate;
    private final com.freshmart.platform.PlatformRuleService platformRuleService;
    private final BigDecimal fallbackMaxMarkupRate;

    public CatalogService(
            @Qualifier("merchantJdbcTemplate") JdbcTemplate jdbcTemplate,
            @Qualifier("deliveryJdbcTemplate") JdbcTemplate deliveryJdbcTemplate,
            com.freshmart.platform.PlatformRuleService platformRuleService,
            @Value("${commerce.market-price.max-markup-rate:5.00}") BigDecimal fallbackMaxMarkupRate) {
        this.jdbcTemplate = jdbcTemplate;
        this.deliveryJdbcTemplate = deliveryJdbcTemplate;
        this.platformRuleService = platformRuleService;
        this.fallbackMaxMarkupRate = fallbackMaxMarkupRate;
    }

    @Transactional("merchantTransactionManager")
    public long createCategory(Long parentId, String name, int sortOrder) {
        if (parentId != null && !exists("SELECT 1 FROM product_categories WHERE id = ? AND status = 'ACTIVE'", parentId)) {
            throw new ResponseStatusException(NOT_FOUND, "parent category not found");
        }
        jdbcTemplate.update("INSERT INTO product_categories (parent_id, name, sort_order) VALUES (?, ?, ?)", parentId, name, sortOrder);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Transactional("merchantTransactionManager")
    public long createWarehouse(CurrentUser user, long deliveryZoneId, String name, String code, String address) {
        long merchantId = merchantId(user);
        if (!existsInDelivery("SELECT 1 FROM delivery_zones WHERE id = ? AND status = 'ACTIVE'", deliveryZoneId)) {
            throw new ResponseStatusException(BAD_REQUEST, "active delivery zone is required");
        }
        jdbcTemplate.update("INSERT INTO warehouses (merchant_id, delivery_zone_id, name, code, address) VALUES (?, ?, ?, ?, ?)",
                merchantId, deliveryZoneId, name, code, address);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Transactional("merchantTransactionManager")
    public void allowWarehouseCategory(CurrentUser user, long warehouseId, long categoryId) {
        requireOwnedWarehouse(user, warehouseId);
        requireCategory(categoryId);
        jdbcTemplate.update("""
                INSERT INTO warehouse_operable_categories (warehouse_id, category_id, status) VALUES (?, ?, 'ACTIVE')
                ON DUPLICATE KEY UPDATE status = 'ACTIVE'
                """, warehouseId, categoryId);
    }

    @Transactional("merchantTransactionManager")
    public void createWarehouseRule(CurrentUser user, long warehouseId, long categoryId, int priority) {
        if (priority < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "priority must not be negative");
        }
        long merchantId = requireOwnedWarehouse(user, warehouseId);
        requireCategory(categoryId);
        if (!exists("SELECT 1 FROM warehouse_operable_categories WHERE warehouse_id = ? AND category_id = ? AND status = 'ACTIVE'",
                warehouseId, categoryId)) {
            throw new ResponseStatusException(BAD_REQUEST, "warehouse does not allow this category");
        }
        jdbcTemplate.update("""
                INSERT INTO merchant_category_warehouse_rules (merchant_id, category_id, warehouse_id, priority, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                ON DUPLICATE KEY UPDATE priority = VALUES(priority), status = 'ACTIVE'
                """, merchantId, categoryId, warehouseId, priority);
    }

    @Transactional("merchantTransactionManager")
    public long createProduct(CurrentUser user, long categoryId, String name, String description,
            BigDecimal marketPricePerKg, BigDecimal merchantPricePerKg) {
        long merchantId = merchantId(user);
        requireCategory(categoryId);
        try {
            BigDecimal maxMarkupRate = platformRuleService.decimalOrDefault(
                    "product.market.price.max.markup.rate", fallbackMaxMarkupRate);
            MarketPriceSettlementPolicy.calculate(1000, marketPricePerKg, merchantPricePerKg, maxMarkupRate);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage());
        }
        jdbcTemplate.update("""
                INSERT INTO products (merchant_id, category_id, name, description, market_price_per_kg, merchant_price_per_kg)
                VALUES (?, ?, ?, ?, ?, ?)
                """, merchantId, categoryId, name, description, marketPricePerKg, merchantPricePerKg);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Transactional("merchantTransactionManager")
    public void publishProduct(CurrentUser user, long productId) {
        long merchantId = merchantId(user);
        ProductRef product = jdbcTemplate.query("SELECT merchant_id, category_id FROM products WHERE id = ?", (rs, row) ->
                new ProductRef(rs.getLong("merchant_id"), rs.getLong("category_id")), productId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "product not found"));
        if (product.merchantId() != merchantId) {
            throw new ResponseStatusException(FORBIDDEN, "product is not owned by this merchant");
        }
        if (!exists("""
                SELECT 1 FROM merchant_category_warehouse_rules rule
                JOIN warehouse_operable_categories category ON category.warehouse_id = rule.warehouse_id AND category.category_id = rule.category_id
                JOIN warehouses warehouse ON warehouse.id = rule.warehouse_id
                WHERE rule.merchant_id = ? AND rule.category_id = ? AND rule.status = 'ACTIVE'
                  AND category.status = 'ACTIVE' AND warehouse.status = 'ACTIVE'
                """, merchantId, product.categoryId())) {
            throw new ResponseStatusException(BAD_REQUEST, "an active warehouse category rule is required before publishing");
        }
        jdbcTemplate.update("UPDATE products SET status = 'ACTIVE' WHERE id = ?", productId);
    }

    public List<ProductView> listProducts(Long categoryId) {
        return jdbcTemplate.query("""
                SELECT product.id, product.merchant_id, product.category_id, product.name, product.description,
                       product.market_price_per_kg, product.merchant_price_per_kg,
                       COALESCE(SUM(GREATEST(batch.available_grams - batch.reserved_grams, 0)), 0) AS available_grams
                FROM products product
                LEFT JOIN inventory_batches batch ON batch.product_id = product.id
                JOIN merchants merchant ON merchant.id = product.merchant_id AND merchant.status = 'ACTIVE'
                WHERE product.status = 'ACTIVE' AND (? IS NULL OR product.category_id = ?)
                GROUP BY product.id, product.merchant_id, product.category_id, product.name, product.description,
                         product.market_price_per_kg, product.merchant_price_per_kg
                ORDER BY product.id DESC
                """, (rs, row) -> productView(rs), categoryId, categoryId);
    }

    public List<ProductView> listMerchantProducts(CurrentUser user) {
        long merchantId = merchantId(user);
        return jdbcTemplate.query("""
                SELECT product.id, product.merchant_id, product.category_id, product.name, product.description,
                       product.market_price_per_kg, product.merchant_price_per_kg,
                       COALESCE(SUM(GREATEST(batch.available_grams - batch.reserved_grams, 0)), 0) AS available_grams
                FROM products product
                LEFT JOIN inventory_batches batch ON batch.product_id = product.id
                WHERE product.merchant_id = ?
                GROUP BY product.id, product.merchant_id, product.category_id, product.name, product.description,
                         product.market_price_per_kg, product.merchant_price_per_kg
                ORDER BY product.id DESC
                """, (rs, row) -> productView(rs), merchantId);
    }

    public List<WarehouseView> listWarehouses(CurrentUser user) {
        long merchantId = merchantId(user);
        return jdbcTemplate.query("""
                SELECT id, merchant_id, delivery_zone_id, name, code, address, status
                FROM warehouses WHERE merchant_id = ? ORDER BY id DESC
                """, (rs, row) -> new WarehouseView(rs.getLong("id"), rs.getLong("merchant_id"),
                        rs.getLong("delivery_zone_id"), rs.getString("name"), rs.getString("code"),
                        rs.getString("address"), rs.getString("status")), merchantId);
    }

    @Transactional("merchantTransactionManager")
    public long createBatch(CurrentUser user, long productId, long warehouseId, String batchNo, int availableGrams, LocalDate expiresOn) {
        if (availableGrams <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "available grams must be positive");
        }
        long merchantId = requireOwnedWarehouse(user, warehouseId);
        ProductRef product = jdbcTemplate.query("SELECT merchant_id, category_id FROM products WHERE id = ?", (rs, row) ->
                new ProductRef(rs.getLong("merchant_id"), rs.getLong("category_id")), productId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "product not found"));
        if (product.merchantId() != merchantId) {
            throw new ResponseStatusException(FORBIDDEN, "product is not owned by this merchant");
        }
        if (!exists("""
                SELECT 1 FROM merchant_category_warehouse_rules rule
                JOIN warehouse_operable_categories category ON category.warehouse_id = rule.warehouse_id AND category.category_id = rule.category_id
                WHERE rule.merchant_id = ? AND rule.warehouse_id = ? AND rule.category_id = ?
                  AND rule.status = 'ACTIVE' AND category.status = 'ACTIVE'
                """, merchantId, warehouseId, product.categoryId())) {
            throw new ResponseStatusException(BAD_REQUEST, "active category warehouse rule is required before stock entry");
        }
        jdbcTemplate.update("""
                INSERT INTO inventory_batches (product_id, warehouse_id, batch_no, available_grams, expires_on)
                VALUES (?, ?, ?, ?, ?)
                """, productId, warehouseId, batchNo, availableGrams, expiresOn);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long merchantId(CurrentUser user) {
        return jdbcTemplate.query("SELECT id FROM merchants WHERE owner_user_id = ? AND status = 'ACTIVE'", (rs, row) -> rs.getLong(1), user.userId())
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(FORBIDDEN, "active merchant profile is required"));
    }

    private long requireOwnedWarehouse(CurrentUser user, long warehouseId) {
        long merchantId = merchantId(user);
        if (!exists("SELECT 1 FROM warehouses WHERE id = ? AND merchant_id = ? AND status = 'ACTIVE'", warehouseId, merchantId)) {
            throw new ResponseStatusException(FORBIDDEN, "warehouse is not owned by this merchant");
        }
        return merchantId;
    }

    private void requireCategory(long categoryId) {
        if (!exists("SELECT 1 FROM product_categories WHERE id = ? AND status = 'ACTIVE'", categoryId)) {
            throw new ResponseStatusException(NOT_FOUND, "category not found");
        }
    }

    private boolean exists(String sql, Object... args) {
        return !jdbcTemplate.query(sql, (rs, row) -> Boolean.TRUE, args).isEmpty();
    }

    private boolean existsInDelivery(String sql, Object... args) {
        return !deliveryJdbcTemplate.query(sql, (rs, row) -> Boolean.TRUE, args).isEmpty();
    }

    private ProductView productView(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new ProductView(
                resultSet.getLong("id"), resultSet.getLong("merchant_id"), resultSet.getLong("category_id"),
                resultSet.getString("name"), resultSet.getString("description"),
                resultSet.getBigDecimal("market_price_per_kg"), resultSet.getBigDecimal("merchant_price_per_kg"),
                resultSet.getInt("available_grams"));
    }

    private record ProductRef(long merchantId, long categoryId) {
    }

    public record ProductView(long id, long merchantId, long categoryId, String name, String description,
            BigDecimal marketPricePerKg, BigDecimal merchantPricePerKg, int availableGrams) {
    }

    public record WarehouseView(long id, long merchantId, long deliveryZoneId, String name, String code,
            String address, String status) {
    }
}
