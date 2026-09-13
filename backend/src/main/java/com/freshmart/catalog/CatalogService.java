package com.freshmart.catalog;

import com.freshmart.auth.CurrentUser;
import com.freshmart.order.MarketPriceSettlementPolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
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
    public long createCategory(Long parentId, String name, int sortOrder, String productScope) {
        requireProductScope(productScope);
        if (parentId != null && !exists("SELECT 1 FROM product_categories WHERE id = ? AND status = 'ACTIVE'", parentId)) {
            throw new ResponseStatusException(NOT_FOUND, "parent category not found");
        }
        jdbcTemplate.update("INSERT INTO product_categories (parent_id, name, sort_order, product_scope) VALUES (?, ?, ?, ?)",
                parentId, name, sortOrder, productScope);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public List<CategoryView> listCategories() {
        return jdbcTemplate.query("""
                SELECT id, parent_id, name, sort_order, product_scope, status FROM product_categories ORDER BY sort_order, id
                """, (rs, row) -> new CategoryView(rs.getLong("id"), (Long) rs.getObject("parent_id"),
                rs.getString("name"), rs.getInt("sort_order"), rs.getString("product_scope"), rs.getString("status")));
    }

    @Transactional("merchantTransactionManager")
    public CategoryView updateCategory(long categoryId, String name, int sortOrder, String productScope, String status) {
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new ResponseStatusException(BAD_REQUEST, "status must be ACTIVE or INACTIVE");
        }
        requireProductScope(productScope);
        if (!exists("SELECT 1 FROM product_categories WHERE id = ?", categoryId)) {
            throw new ResponseStatusException(NOT_FOUND, "category not found");
        }
        // 停用前必须没有在架商品继续引用该分类，否则商品与分类状态会不一致
        if ("INACTIVE".equals(status)
                && exists("SELECT 1 FROM products WHERE category_id = ? AND status = 'ACTIVE'", categoryId)) {
            throw new ResponseStatusException(CONFLICT, "category still has active products");
        }
        jdbcTemplate.update("""
                UPDATE product_categories SET name = ?, sort_order = ?, product_scope = ?, status = ? WHERE id = ?
                """, name, sortOrder, productScope, status, categoryId);
        return findCategory(categoryId);
    }

    /** 品类决定售后窗口：FRUIT 与 VEGETABLE 各自匹配平台规则，其余走默认窗口 */
    private void requireProductScope(String productScope) {
        if (!List.of("FRUIT", "VEGETABLE", "OTHER").contains(productScope)) {
            throw new ResponseStatusException(BAD_REQUEST, "product scope must be FRUIT, VEGETABLE or OTHER");
        }
    }

    private CategoryView findCategory(long categoryId) {
        return jdbcTemplate.query("""
                SELECT id, parent_id, name, sort_order, product_scope, status FROM product_categories WHERE id = ?
                """, (rs, row) -> new CategoryView(rs.getLong("id"), (Long) rs.getObject("parent_id"),
                rs.getString("name"), rs.getInt("sort_order"), rs.getString("product_scope"), rs.getString("status")), categoryId)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "category not found"));
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
        requireStockEntryAllowed(user, productId, warehouseId);
        jdbcTemplate.update("""
                INSERT INTO inventory_batches (product_id, warehouse_id, batch_no, available_grams, expires_on)
                VALUES (?, ?, ?, ?, ?)
                """, productId, warehouseId, batchNo, availableGrams, expiresOn);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /**
     * 称收入库：仓库按实际称重克数入库，净重（receivedGrams）才是库存增量，毛重与皮重仅用于留痕。
     * 同一商品+仓库+批次号已存在时累加到可用克数，避免同一批次被重复建号导致库存分散。
     * 幂等键重复时直接返回首次结果，不重复加库存。
     */
    @Transactional("merchantTransactionManager")
    public ReceiptView receiveStock(CurrentUser user, long productId, long warehouseId, String batchNo,
            int receivedGrams, Integer grossGrams, Integer tareGrams, String note, LocalDate expiresOn,
            String idempotencyKey) {
        ReceiptView existing = findReceiptByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return existing;
        }
        if (receivedGrams <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "received grams must be positive");
        }
        if (grossGrams != null && grossGrams < receivedGrams) {
            throw new ResponseStatusException(BAD_REQUEST, "gross grams must not be less than received grams");
        }
        requireStockEntryAllowed(user, productId, warehouseId);
        Long batchId = jdbcTemplate.query("""
                SELECT id FROM inventory_batches WHERE product_id = ? AND warehouse_id = ? AND batch_no = ?
                """, (rs, row) -> rs.getLong(1), productId, warehouseId, batchNo).stream().findFirst().orElse(null);
        if (batchId == null) {
            jdbcTemplate.update("""
                    INSERT INTO inventory_batches (product_id, warehouse_id, batch_no, available_grams, expires_on)
                    VALUES (?, ?, ?, ?, ?)
                    """, productId, warehouseId, batchNo, receivedGrams, expiresOn);
            batchId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            jdbcTemplate.update("UPDATE inventory_batches SET available_grams = available_grams + ? WHERE id = ?",
                    receivedGrams, batchId);
        }
        String receiptNo = "RC" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        jdbcTemplate.update("""
                INSERT INTO inventory_receipts (receipt_no, batch_id, product_id, warehouse_id, received_grams,
                    gross_grams, tare_grams, note, idempotency_key, received_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, receiptNo, batchId, productId, warehouseId, receivedGrams, grossGrams, tareGrams,
                cleanNote, idempotencyKey, user.userId());
        Integer totalGrams = jdbcTemplate.queryForObject(
                "SELECT available_grams FROM inventory_batches WHERE id = ?", Integer.class, batchId);
        return new ReceiptView(receiptNo, batchId, productId, warehouseId, receivedGrams, grossGrams, tareGrams,
                cleanNote, totalGrams == null ? receivedGrams : totalGrams, LocalDateTime.now());
    }

    private ReceiptView findReceiptByIdempotencyKey(String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT receipt.receipt_no, receipt.batch_id, receipt.product_id, receipt.warehouse_id,
                       receipt.received_grams, receipt.gross_grams, receipt.tare_grams, receipt.note,
                       receipt.created_at, batch.available_grams
                FROM inventory_receipts receipt
                JOIN inventory_batches batch ON batch.id = receipt.batch_id
                WHERE receipt.idempotency_key = ?
                """, (rs, row) -> new ReceiptView(rs.getString("receipt_no"), rs.getLong("batch_id"),
                rs.getLong("product_id"), rs.getLong("warehouse_id"), rs.getInt("received_grams"),
                (Integer) rs.getObject("gross_grams"), (Integer) rs.getObject("tare_grams"), rs.getString("note"),
                rs.getInt("available_grams"), rs.getObject("created_at", LocalDateTime.class)), idempotencyKey)
                .stream().findFirst().orElse(null);
    }

    /** 批次入库（含称收入库）的统一前置条件：仓库与商品都归属该商家，且分类仓配规则有效 */
    private ProductRef requireStockEntryAllowed(CurrentUser user, long productId, long warehouseId) {
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
        return product;
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

    public record CategoryView(long id, Long parentId, String name, int sortOrder, String productScope, String status) {
    }

    /** 称收入库结果：batchAvailableGrams 是入库后该批次的可用总克数，便于前端直接回显 */
    public record ReceiptView(String receiptNo, long batchId, long productId, long warehouseId, int receivedGrams,
            Integer grossGrams, Integer tareGrams, String note, int batchAvailableGrams, LocalDateTime createdAt) {
    }
}
