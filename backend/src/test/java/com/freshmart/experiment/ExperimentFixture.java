package com.freshmart.experiment;

import com.freshmart.auth.CurrentUser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 实验数据构造与不变式校验工具。
 *
 * <p>实验只考察「称重结算与库存回补」这一机制本身，因此订单相关前置数据由本类直接构造，
 * 而不重新走一遍下单与支付链路——那条链路由 96 项端到端测试覆盖。这样做的收益是实验
 * 完全可控且可重复：同一随机种子能复现同样的批次分配与偏差组合。
 *
 * <p>构造的实体统一带 {@link #PREFIX} 前缀，便于实验结束后精确清理，不影响既有数据。
 */
@Component
public class ExperimentFixture {

    /** 实验数据前缀，用于标识与清理 */
    public static final String PREFIX = "EXP-";

    /** 复用的测试商家（merchant-test-01 拥有） */
    public static final long MERCHANT_ID = 1L;
    public static final long MERCHANT_USER_ID = 11L;
    public static final long WAREHOUSE_ID = 15L;
    public static final long DELIVERY_ZONE_ID = 15L;
    public static final long PRODUCT_ID = 19L;
    /** 实验订单的归属用户，需保证其钱包可用，否则变轻场景的退款会被拒绝 */
    public static final long EXPERIMENT_USER_ID = 1L;

    private final JdbcTemplate tradeJdbcTemplate;
    private final JdbcTemplate merchantJdbcTemplate;
    private final JdbcTemplate userJdbcTemplate;

    @Autowired
    public ExperimentFixture(@Qualifier("tradeJdbcTemplate") JdbcTemplate tradeJdbcTemplate,
            @Qualifier("merchantJdbcTemplate") JdbcTemplate merchantJdbcTemplate,
            @Qualifier("userJdbcTemplate") JdbcTemplate userJdbcTemplate) {
        this.tradeJdbcTemplate = tradeJdbcTemplate;
        this.merchantJdbcTemplate = merchantJdbcTemplate;
        this.userJdbcTemplate = userJdbcTemplate;
    }

    /**
     * 确保指定用户存在可用的钱包账户。
     *
     * <p>称重结果为「实际金额低于预估」时会把差额退回用户钱包，因此实验开始前必须有 ACTIVE 钱包，
     * 否则服务层会因为找不到钱包而拒绝整笔调整。
     */
    public void ensureWallet(long userId) {
        Integer count = userJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallet_accounts WHERE user_id = ?", Integer.class, userId);
        if (count != null && count > 0) {
            return;
        }
        userJdbcTemplate.update("""
                INSERT INTO wallet_accounts (user_id, balance, frozen_balance, status)
                VALUES (?, 0.00, 0.00, 'ACTIVE')
                """, userId);
    }

    /** 商家操作身份：与 merchant-test-01 的归属关系一致，否则服务层会拒绝 */
    public CurrentUser merchantOperator() {
        return new CurrentUser(MERCHANT_USER_ID, null, "merchant-test-01", Set.of("MERCHANT"));
    }

    /** 生成本次实验唯一标识，保证多次运行互不干扰 */
    public String uniqueToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * 新建一个实验批次。
     *
     * @param initialGrams 初始可用克数
     * @return 批次主键
     */
    public long createBatch(String token, int initialGrams) {
        String batchNo = PREFIX + "B-" + token;
        merchantJdbcTemplate.update("""
                INSERT INTO inventory_batches (product_id, warehouse_id, batch_no, available_grams, reserved_grams)
                VALUES (?, ?, ?, ?, 0)
                """, PRODUCT_ID, WAREHOUSE_ID, batchNo, initialGrams);
        Long id = merchantJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new IllegalStateException("failed to create experiment batch");
        }
        return id;
    }

    /**
     * 新建一个实验订单。状态取 PICKED，使其落在称重允许的状态集合内。
     *
     * @param goodsAmount 预估商品金额
     */
    public long createOrder(String token, BigDecimal goodsAmount, String status) {
        String orderNo = PREFIX + "O-" + token;
        // 称重结果为「实际低于预估」时需把钱退回用户钱包，因此这里先保证钱包存在
        ensureWallet(EXPERIMENT_USER_ID);
        tradeJdbcTemplate.update("""
                INSERT INTO orders (order_no, trade_id, user_id, merchant_id, warehouse_id, delivery_zone_id,
                    status, goods_amount, freight_amount, discount_amount, redeemed_points,
                    points_discount_amount, payable_amount, address_snapshot, pricing_snapshot)
                VALUES (?, 0, ?, ?, ?, ?, ?, ?, 0.00, 0.00, 0, 0.00, ?, '{}', '{}')
                """, orderNo, EXPERIMENT_USER_ID, MERCHANT_ID, WAREHOUSE_ID, DELIVERY_ZONE_ID, status,
                goodsAmount, goodsAmount);
        Long id = tradeJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new IllegalStateException("failed to create experiment order");
        }
        return id;
    }

    /** 新建一个订单项，返回订单项主键 */
    public long createOrderItem(String token, long orderId, int weightGrams, BigDecimal userGoodsAmount) {
        tradeJdbcTemplate.update("""
                INSERT INTO order_items (order_id, product_id, product_name_snapshot, warehouse_id, weight_grams,
                    market_price_per_kg, merchant_price_per_kg, user_price_per_kg, merchant_gross_amount,
                    user_goods_amount, platform_price_subsidy_amount, batch_promotion_discount_amount,
                    flash_sale_discount_amount)
                VALUES (?, ?, ?, ?, ?, 10.00, 9.50, 9.50, ?, ?, 0.00, 0.00, 0.00)
                """, orderId, PRODUCT_ID, PREFIX + "P-" + token, WAREHOUSE_ID, weightGrams,
                userGoodsAmount, userGoodsAmount);
        Long id = tradeJdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new IllegalStateException("failed to create experiment order item");
        }
        return id;
    }

    /** 把一个订单项的部分克数分配到批次上，模拟下单时的批次预占结果 */
    public void allocate(long orderItemId, long batchId, int grams) {
        tradeJdbcTemplate.update("""
                INSERT INTO order_item_batch_allocations (order_item_id, batch_id, warehouse_id,
                    markdown_rate_snapshot, allocated_grams, discount_amount)
                VALUES (?, ?, ?, 0.00, ?, 0.00)
                """, orderItemId, batchId, WAREHOUSE_ID, grams);
    }

    /** 读取批次当前可用克数 */
    public int availableGrams(long batchId) {
        Integer grams = merchantJdbcTemplate.queryForObject(
                "SELECT available_grams FROM inventory_batches WHERE id = ?", Integer.class, batchId);
        return grams == null ? -1 : grams;
    }

    /** 读取某个订单项的累计批次分配克数 */
    public int allocatedGrams(long orderItemId) {
        Integer grams = tradeJdbcTemplate.queryForObject("""
                SELECT IFNULL(SUM(allocated_grams), 0) FROM order_item_batch_allocations WHERE order_item_id = ?
                """, Integer.class, orderItemId);
        return grams == null ? 0 : grams;
    }

    /** 读取称重调整记录的关键字段，用于校验回补结果 */
    public WeighingRow findWeighing(long orderId) {
        List<WeighingRow> rows = tradeJdbcTemplate.query("""
                SELECT prepaid_grams, actual_grams, inventory_adjust_grams
                FROM weighing_adjustments WHERE order_id = ?
                """, (rs, row) -> new WeighingRow((Integer) rs.getObject("prepaid_grams"),
                (Integer) rs.getObject("actual_grams"), rs.getInt("inventory_adjust_grams")), orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 读取订单项的实际称重结果 */
    public ItemWeighingRow findItemWeighing(long orderItemId) {
        List<ItemWeighingRow> rows = tradeJdbcTemplate.query("""
                SELECT weight_grams, actual_weight_grams, inventory_adjust_grams
                FROM order_items item
                LEFT JOIN order_item_weighings weighing ON weighing.order_item_id = item.id
                WHERE item.id = ?
                """, (rs, row) -> new ItemWeighingRow(rs.getInt("weight_grams"),
                (Integer) rs.getObject("actual_weight_grams"), (Integer) rs.getObject("inventory_adjust_grams")),
                orderItemId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 统计某订单下的称重调整记录条数，用于幂等性判定 */
    public int countWeighings(long orderId) {
        Integer count = tradeJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM weighing_adjustments WHERE order_id = ?", Integer.class, orderId);
        return count == null ? 0 : count;
    }

    /** 统计某订单项下的逐项称重明细条数 */
    public int countItemWeighings(long orderItemId) {
        Integer count = tradeJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_item_weighings WHERE order_item_id = ?", Integer.class, orderItemId);
        return count == null ? 0 : count;
    }

    /** 统计某幂等键产生的费用台账记录数，用于判定是否重复入账 */
    public int countLedgerEntries(String referenceId) {
        Integer count = tradeJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fee_ledgers WHERE reference_id = ?", Integer.class, referenceId);
        return count == null ? 0 : count;
    }

    /** 清理本次实验产生的全部数据，避免污染后续运行 */
    public void cleanup(String token) {
        String batchNo = PREFIX + "B-" + token;
        String orderNo = PREFIX + "O-" + token;
        List<Long> orderIds = tradeJdbcTemplate.queryForList(
                "SELECT id FROM orders WHERE order_no = ?", Long.class, orderNo);
        List<Long> batchIds = merchantJdbcTemplate.queryForList(
                "SELECT id FROM inventory_batches WHERE batch_no = ?", Long.class, batchNo);
        if (!orderIds.isEmpty()) {
            long orderId = orderIds.get(0);
            List<Long> itemIds = tradeJdbcTemplate.queryForList(
                    "SELECT id FROM order_items WHERE order_id = ?", Long.class, orderId);
            for (Long itemId : itemIds) {
                tradeJdbcTemplate.update("DELETE FROM order_item_weighings WHERE order_item_id = ?", itemId);
                tradeJdbcTemplate.update("DELETE FROM order_item_batch_allocations WHERE order_item_id = ?", itemId);
            }
            tradeJdbcTemplate.update("DELETE FROM fee_ledgers WHERE order_id = ?", orderId);
            tradeJdbcTemplate.update("DELETE FROM weighing_adjustments WHERE order_id = ?", orderId);
            tradeJdbcTemplate.update("DELETE FROM order_items WHERE order_id = ?", orderId);
            tradeJdbcTemplate.update("DELETE FROM orders WHERE id = ?", orderId);
        }
        for (Long batchId : batchIds) {
            merchantJdbcTemplate.update("DELETE FROM inventory_batches WHERE id = ?", batchId);
        }
    }

    /**
     * 校验批次守恒不变式：可用克数不得为负，且预占不得超过可用。
     * 这是 I1 与 I2 的直接实现。
     *
     * @return 违反说明；全部成立时返回空列表
     */
    public List<String> checkBatchInvariants(List<Long> batchIds) {
        List<String> violations = new ArrayList<>();
        for (Long batchId : batchIds) {
            List<int[]> rows = merchantJdbcTemplate.query("""
                    SELECT available_grams, reserved_grams FROM inventory_batches WHERE id = ?
                    """, (rs, row) -> new int[] { rs.getInt("available_grams"), rs.getInt("reserved_grams") }, batchId);
            if (rows.isEmpty()) {
                violations.add("I1 violated: batch " + batchId + " no longer exists");
                continue;
            }
            int available = rows.get(0)[0];
            int reserved = rows.get(0)[1];
            if (available < 0) {
                violations.add("I1 violated: batch " + batchId + " available=" + available);
            }
            if (reserved < 0) {
                violations.add("I1 violated: batch " + batchId + " reserved=" + reserved);
            }
            if (reserved > available) {
                violations.add("I2 violated: batch " + batchId + " reserved=" + reserved
                        + " exceeds available=" + available);
            }
        }
        return violations;
    }

    /** 批次上的称重结果行 */
    public record WeighingRow(Integer prepaidGrams, Integer actualGrams, int inventoryAdjustGrams) {
    }

    /** 订单项上的称重结果行 */
    public record ItemWeighingRow(int prepaidGrams, Integer actualGrams, Integer inventoryAdjustGrams) {
    }
}
