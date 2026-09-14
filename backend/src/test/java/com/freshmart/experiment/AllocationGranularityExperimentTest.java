package com.freshmart.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.freshmart.weighing.WeighingAdjustmentService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 实验 E2：分摊粒度对照实验。
 *
 * <p>研究问题 RQ2 —— 称重差额按「整单分摊」与按「订单项逐项回补」，两种粒度在克数误差上有何差异？
 *
 * <p>对照条件：同一个订单结构分别以两种粒度提交称重，比较
 * <ul>
 *   <li>整单记录的 {@code inventory_adjust_grams} 是否等于理论差额（I6、I7）；</li>
 *   <li>各批次可用克数的实际变化量之和是否恰好等于差额（分摊无损）。</li>
 * </ul>
 *
 * <p>假设 H2：逐项回补的误差为零；整单分摊在「订单项跨多批次且偏差方向相反」时出现可测量偏差。
 *
 * <p>运行前提：需提供数据库连接（{@code DB_USERNAME} 与 {@code DB_PASSWORD}）。未配置时整类跳过，
 * 以保证 {@code mvn test} 在无库环境下依然通过。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@DisplayName("E2 分摊粒度对照实验")
class AllocationGranularityExperimentTest {

    @Autowired
    private WeighingAdjustmentService weighingService;

    @Autowired
    private ExperimentFixture fixture;

    private final List<String> createdTokens = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String token : createdTokens) {
            fixture.cleanup(token);
        }
        createdTokens.clear();
    }

    /**
     * 对照项一：整单分摊。订单含两个订单项，各跨两个批次，提交一个整体实际克数。
     * 预期：整单记录中的回补克数等于「实际 − 预估」，且各批次可用克数变化之和与之一致。
     */
    @Test
    @DisplayName("整单分摊：回补克数等于实际与预估之差，且批次变化之和一致")
    void wholeOrderAllocationIsLossless() {
        String token = fixture.uniqueToken();
        createdTokens.add(token);

        long batch1 = fixture.createBatch(token + "a", 10_000);
        long batch2 = fixture.createBatch(token + "b", 10_000);
        long batch3 = fixture.createBatch(token + "c", 10_000);
        List<Long> batches = List.of(batch1, batch2, batch3);
        List<Integer> before = batches.stream().map(fixture::availableGrams).toList();

        // 两个订单项共预占 2000 克，分别跨 2 个与 2 个批次
        long orderId = fixture.createOrder(token, new BigDecimal("20.00"), "PICKED");
        long item1 = fixture.createOrderItem(token + "-1", orderId, 1000, new BigDecimal("10.00"));
        long item2 = fixture.createOrderItem(token + "-2", orderId, 1000, new BigDecimal("10.00"));
        fixture.allocate(item1, batch1, 600);
        fixture.allocate(item1, batch2, 400);
        fixture.allocate(item2, batch2, 500);
        fixture.allocate(item2, batch3, 500);

        // 预估 2000 克，实际 2200 克，差额 +200 应被补扣
        var view = weighingService.submit(fixture.merchantOperator(), orderId, new BigDecimal("21.00"), 2200,
                null, "E2 whole order", "EXP-E2-WHOLE-" + token, "127.0.0.1");

        assertEquals(200, view.inventoryAdjustGrams(), "整单回补克数应等于实际与预估之差");

        List<Integer> after = batches.stream().map(fixture::availableGrams).toList();
        int totalDelta = 0;
        for (int index = 0; index < batches.size(); index++) {
            totalDelta += before.get(index) - after.get(index);
        }
        assertEquals(200, totalDelta, "I7：各批次被扣减的克数之和必须等于总差额，不得因逐批取整而损失");
        assertTrue(fixture.checkBatchInvariants(batches).isEmpty(), "称重后批次不变式仍应成立");
    }

    /**
     * 对照项二：逐项回补。同样的订单结构，但按订单项分别给出实际克数。
     * 预期：整单记录的回补量等于各项之和（I6），且每个订单项自身也留有明细（I4）。
     */
    @Test
    @DisplayName("逐项回补：项级回补之和等于整单回补，且逐项明细齐备")
    void itemWeighingIsConsistentWithOrderTotals() {
        String token = fixture.uniqueToken();
        createdTokens.add(token);

        long batch1 = fixture.createBatch(token + "a", 10_000);
        long batch2 = fixture.createBatch(token + "b", 10_000);
        long batch3 = fixture.createBatch(token + "c", 10_000);
        List<Long> batches = List.of(batch1, batch2, batch3);
        List<Integer> before = batches.stream().map(fixture::availableGrams).toList();

        long orderId = fixture.createOrder(token, new BigDecimal("20.00"), "PICKED");
        long item1 = fixture.createOrderItem(token + "-1", orderId, 1000, new BigDecimal("10.00"));
        long item2 = fixture.createOrderItem(token + "-2", orderId, 1000, new BigDecimal("10.00"));
        fixture.allocate(item1, batch1, 600);
        fixture.allocate(item1, batch2, 400);
        fixture.allocate(item2, batch2, 500);
        fixture.allocate(item2, batch3, 500);

        // 项 1 变重 150 克，项 2 变轻 50 克：偏差方向相反，正是整单分摊最容易失真的场景
        var items = List.of(
                new WeighingAdjustmentService.ItemWeighing(item1, 1150, new BigDecimal("10.95")),
                new WeighingAdjustmentService.ItemWeighing(item2, 950, new BigDecimal("9.03")));
        var view = weighingService.submit(fixture.merchantOperator(), orderId, null, null,
                items, "E2 per item", "EXP-E2-ITEM-" + token, "127.0.0.1");

        assertEquals(2100, view.actualGrams(), "整单实际克数应由各项汇总得出");
        assertEquals(100, view.inventoryAdjustGrams(), "项级差额之和应为 150 − 50 = 100");

        var item1Row = fixture.findItemWeighing(item1);
        var item2Row = fixture.findItemWeighing(item2);
        assertNotNull(item1Row, "订单项 1 应留下称重明细");
        assertNotNull(item2Row, "订单项 2 应留下称重明细");
        assertEquals(150, item1Row.inventoryAdjustGrams(), "项 1 应补扣 150 克");
        assertEquals(-50, item2Row.inventoryAdjustGrams(), "项 2 应退回 50 克");
        assertEquals(1, fixture.countItemWeighings(item1), "每个订单项只允许一条逐项称重明细");

        // I6：项级回补之和必须等于整单记录
        int sumOfItems = item1Row.inventoryAdjustGrams() + item2Row.inventoryAdjustGrams();
        assertEquals(view.inventoryAdjustGrams(), sumOfItems, "I6：项级回补之和应等于整单回补");

        List<Integer> after = batches.stream().map(fixture::availableGrams).toList();
        int totalDelta = 0;
        for (int index = 0; index < batches.size(); index++) {
            totalDelta += before.get(index) - after.get(index);
        }
        assertEquals(100, totalDelta, "逐项回补同样必须无损：批次变化之和等于总差额");
        assertTrue(fixture.checkBatchInvariants(batches).isEmpty(), "逐项称重后批次不变式仍应成立");
    }

    /**
     * 直接对照项：同一订单结构、同一组偏差，分别以两种粒度提交，比较批次级分布。
     *
     * <p>结论：两种粒度在「总量」上都无损（回补克数都等于差额），但在「批次归属」上不同 ——
     * 逐项模式下每个批次的变化只由使用它的订单项决定，与真实拣货结果一致；
     * 整单模式下偏差会按订单整体预占比例在各批次之间重新分配，从而把某一项的偏差摊到其他项的批次上。
     * 这一点在两项偏差方向相反时尤为明显。
     */
    @Test
    @DisplayName("对照：两种粒度的总量一致，但批次级分布不同")
    void granularitiesDifferInBatchLevelDistribution() {
        // 场景一：整单分摊。项 1 只占 batchA，项 2 只占 batchB，项 1 变重 100、项 2 变轻 100
        String wholeToken = fixture.uniqueToken();
        createdTokens.add(wholeToken);
        long wholeBatchA = fixture.createBatch(wholeToken + "a", 50_000);
        long wholeBatchB = fixture.createBatch(wholeToken + "b", 50_000);
        int wholeABefore = fixture.availableGrams(wholeBatchA);
        int wholeBBefore = fixture.availableGrams(wholeBatchB);

        long wholeOrder = fixture.createOrder(wholeToken, new BigDecimal("20.00"), "PICKED");
        long wholeItem1 = fixture.createOrderItem(wholeToken + "-1", wholeOrder, 1000, new BigDecimal("10.00"));
        long wholeItem2 = fixture.createOrderItem(wholeToken + "-2", wholeOrder, 1000, new BigDecimal("10.00"));
        fixture.allocate(wholeItem1, wholeBatchA, 1000);
        fixture.allocate(wholeItem2, wholeBatchB, 1000);

        // 净差额为 0，因此整单模式下两个批次都不应发生变化
        var wholeView = weighingService.submit(fixture.merchantOperator(), wholeOrder, new BigDecimal("20.00"), 2000,
                null, "E2 whole distribution", "EXP-E2-DIST-W-" + wholeToken, "127.0.0.1");
        assertEquals(0, wholeView.inventoryAdjustGrams(), "净差额为 0 时整单回补量应为 0");
        assertEquals(wholeABefore, fixture.availableGrams(wholeBatchA), "整单模式下净差额为 0，批次 A 不应变化");
        assertEquals(wholeBBefore, fixture.availableGrams(wholeBatchB), "整单模式下净差额为 0，批次 B 不应变化");

        // 场景二：逐项回补。同样的偏差方向，但按项分别提交
        String itemToken = fixture.uniqueToken();
        createdTokens.add(itemToken);
        long itemBatchA = fixture.createBatch(itemToken + "a", 50_000);
        long itemBatchB = fixture.createBatch(itemToken + "b", 50_000);
        int itemABefore = fixture.availableGrams(itemBatchA);
        int itemBBefore = fixture.availableGrams(itemBatchB);

        long itemOrder = fixture.createOrder(itemToken, new BigDecimal("20.00"), "PICKED");
        long item1 = fixture.createOrderItem(itemToken + "-1", itemOrder, 1000, new BigDecimal("10.00"));
        long item2 = fixture.createOrderItem(itemToken + "-2", itemOrder, 1000, new BigDecimal("10.00"));
        fixture.allocate(item1, itemBatchA, 1000);
        fixture.allocate(item2, itemBatchB, 1000);

        var itemView = weighingService.submit(fixture.merchantOperator(), itemOrder, null, null,
                List.of(new WeighingAdjustmentService.ItemWeighing(item1, 1100, new BigDecimal("11.00")),
                        new WeighingAdjustmentService.ItemWeighing(item2, 900, new BigDecimal("9.00"))),
                "E2 per item distribution", "EXP-E2-DIST-I-" + itemToken, "127.0.0.1");

        assertEquals(0, itemView.inventoryAdjustGrams(), "项级正负相抵后净差额同样为 0");
        // 关键差异：逐项模式下偏差被如实回补到各自的批次，即使净差额为 0
        assertEquals(itemABefore - 100, fixture.availableGrams(itemBatchA),
                "项 1 变重应只补扣它自己的批次，而不会被净差额抵消");
        assertEquals(itemBBefore + 100, fixture.availableGrams(itemBatchB),
                "项 2 变轻应只退回它自己的批次，而不会被净差额抵消");
    }

    /**
     * 边界项：订单项跨越多批次且差额需按比例分摊时，最后一批兜底剩余。
     * 这里用 7 个批次制造大量取整机会，验证分摊总量依然精确等于差额。
     */
    @Test
    @DisplayName("跨多批次分摊：取整误差被最后一批兜底吸收，总量仍精确")
    void allocationAcrossManyBatchesRemainsExact() {
        String token = fixture.uniqueToken();
        createdTokens.add(token);

        List<Long> batches = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            batches.add(fixture.createBatch(token + index, 5_000));
        }
        List<Integer> before = batches.stream().map(fixture::availableGrams).toList();

        long orderId = fixture.createOrder(token, new BigDecimal("10.00"), "PICKED");
        long itemId = fixture.createOrderItem(token + "-1", orderId, 1000, new BigDecimal("10.00"));
        // 1000 克按 7 个批次均分：每批 142 或 143 克，必然产生取整
        int base = 1000 / 7;
        int remainder = 1000 - base * 7;
        for (int index = 0; index < batches.size(); index++) {
            fixture.allocate(itemId, batches.get(index), index < remainder ? base + 1 : base);
        }
        assertEquals(1000, fixture.allocatedGrams(itemId), "前置分配总量应为 1000 克");

        // 实际 1100 克，差额 +100 需按 7 个批次的比例分摊
        var view = weighingService.submit(fixture.merchantOperator(), orderId, new BigDecimal("11.00"), 1100,
                null, "E2 many batches", "EXP-E2-MANY-" + token, "127.0.0.1");
        assertEquals(100, view.inventoryAdjustGrams(), "回补克数应为 100");

        List<Integer> after = batches.stream().map(fixture::availableGrams).toList();
        int totalDelta = 0;
        for (int index = 0; index < batches.size(); index++) {
            totalDelta += before.get(index) - after.get(index);
        }
        assertEquals(100, totalDelta, "7 个批次的分摊之和必须精确等于 100，取整余数由最后一批兜底");
        assertTrue(fixture.checkBatchInvariants(batches).isEmpty(), "多批次分摊后不变式仍应成立");
    }
}
