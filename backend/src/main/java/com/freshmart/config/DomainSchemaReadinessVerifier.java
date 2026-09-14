package com.freshmart.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DomainSchemaReadinessVerifier implements SmartInitializingSingleton {
    private final Map<String, JdbcTemplate> dataSources;

    public DomainSchemaReadinessVerifier(@Qualifier("userJdbcTemplate") JdbcTemplate userJdbcTemplate,
            @Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate,
            @Qualifier("deliveryJdbcTemplate") JdbcTemplate deliveryJdbcTemplate,
            @Qualifier("tradeJdbcTemplate") JdbcTemplate tradeJdbcTemplate,
            @Qualifier("logJdbcTemplate") JdbcTemplate logJdbcTemplate) {
        dataSources = Map.of(
                "freshmart_user", userJdbcTemplate,
                "freshmart_merchant", merchantJdbcTemplate,
                "freshmart_delivery", deliveryJdbcTemplate,
                "freshmart_trade", tradeJdbcTemplate,
                "freshmart_log", logJdbcTemplate);
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> missing = new ArrayList<>();
        requiredTables().forEach((schema, tables) -> tables.forEach(table -> {
            if (!exists(dataSources.get(schema), "tables", table, null)) {
                missing.add(schema + "." + table + " table");
            }
        }));
        requiredColumns().forEach(requirement -> {
            if (!exists(dataSources.get(requirement.schema()), "columns", requirement.table(), requirement.column())) {
                missing.add(requirement.schema() + "." + requirement.table() + "." + requirement.column() + " column");
            }
        });
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Domain schema is incomplete: " + String.join(", ", missing)
                    + ". Run backend/scripts/bootstrap-domain-databases.sql as MySQL administrator, then restart.");
        }
    }

    private boolean exists(JdbcTemplate jdbcTemplate, String metadataTable, String table, String column) {
        String sql = "columns".equals(metadataTable)
                ? "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?"
                : "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
        Integer count = "columns".equals(metadataTable)
                ? jdbcTemplate.queryForObject(sql, Integer.class, table, column)
                : jdbcTemplate.queryForObject(sql, Integer.class, table);
        return count != null && count > 0;
    }

    private Map<String, Set<String>> requiredTables() {
        return Map.of(
                "freshmart_user", Set.of("users", "user_role_assignments", "auth_sessions", "user_addresses", "wallet_accounts", "wallet_transactions", "user_memberships", "points_transactions", "user_point_accounts", "user_coupons", "user_notification_preferences"),
                "freshmart_merchant", Set.of("merchants", "merchant_applications", "product_categories", "products", "warehouses", "inventory_batches", "promotion_rules", "coupons", "batch_promotions", "flash_sale_items", "api_clients", "api_access_logs", "membership_levels", "warehouse_operable_categories", "merchant_category_warehouse_rules"),
                "freshmart_delivery", Set.of("delivery_zones", "rider_profiles", "delivery_tasks", "rider_performance_daily"),
                "freshmart_trade", Set.of("trade_orders", "orders", "order_items", "order_item_batch_allocations", "electronic_receipts", "inventory_reservations", "flash_sale_reservations", "payment_orders", "payment_verifications", "payment_bill_imports", "payment_bill_entries", "payment_reconciliation_differences", "refund_orders", "fee_ledgers", "merchant_settlements", "refund_inventory_dispositions", "weighing_adjustments", "financial_status_logs"),
                "freshmart_log", Set.of("audit_logs", "inbox_messages", "integration_outbox", "media_assets", "ai_interaction_logs", "holiday_card_tasks", "holiday_card_deliveries"));
    }

    private List<ColumnRequirement> requiredColumns() {
        return List.of(
                new ColumnRequirement("freshmart_user", "users", "login_name"),
                new ColumnRequirement("freshmart_user", "users", "password_hash"),
                new ColumnRequirement("freshmart_merchant", "products", "merchant_price_per_kg"),
                new ColumnRequirement("freshmart_merchant", "inventory_batches", "warehouse_id"),
                new ColumnRequirement("freshmart_delivery", "delivery_tasks", "timeout_at"),
                new ColumnRequirement("freshmart_delivery", "delivery_tasks", "accept_deadline_at"),
                new ColumnRequirement("freshmart_trade", "inventory_reservations", "order_id"),
                new ColumnRequirement("freshmart_trade", "inventory_reservations", "order_item_id"),
                new ColumnRequirement("freshmart_trade", "inventory_reservations", "warehouse_id"),
                new ColumnRequirement("freshmart_trade", "orders", "actual_goods_amount"),
                new ColumnRequirement("freshmart_trade", "orders", "platform_absorbed_amount"),
                new ColumnRequirement("freshmart_trade", "trade_orders", "redeemed_points"),
                new ColumnRequirement("freshmart_trade", "trade_orders", "points_discount_amount"),
                new ColumnRequirement("freshmart_trade", "orders", "redeemed_points"),
                new ColumnRequirement("freshmart_trade", "orders", "points_discount_amount"),
                new ColumnRequirement("freshmart_trade", "order_items", "batch_promotion_discount_amount"),
                new ColumnRequirement("freshmart_trade", "order_items", "merchant_price_per_kg"),
                new ColumnRequirement("freshmart_trade", "order_items", "user_goods_amount"),
                new ColumnRequirement("freshmart_trade", "payment_orders", "code_url"),
                new ColumnRequirement("freshmart_trade", "payment_orders", "remark_text"),
                new ColumnRequirement("freshmart_trade", "payment_orders", "payment_proof_url"),
                new ColumnRequirement("freshmart_trade", "payment_reconciliation_differences", "claimed_by"),
                new ColumnRequirement("freshmart_trade", "payment_reconciliation_differences", "resolution_note"),
                new ColumnRequirement("freshmart_trade", "refund_orders", "manual_refund_status"),
                new ColumnRequirement("freshmart_log", "inbox_messages", "card_svg_url"),
                new ColumnRequirement("freshmart_log", "inbox_messages", "card_svg_content"),
                new ColumnRequirement("freshmart_log", "inbox_messages", "idempotency_key"));
    }

    private record ColumnRequirement(String schema, String table, String column) {
    }
}
