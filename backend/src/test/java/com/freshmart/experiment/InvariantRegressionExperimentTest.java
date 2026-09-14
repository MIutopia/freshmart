package com.freshmart.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.freshmart.weighing.WeighingAdjustmentService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 实验 E1：不变式随机回归。
 *
 * <p>研究问题 RQ1 —— 在多样的订单结构与偏差组合下，双维度一致性机制是否始终保持成立？
 *
 * <p>方法：以固定随机种子生成订单结构（订单项数、每项跨批次数、各项偏差方向与幅度），
 * 逐项提交称重后校验四条不变式：
 * <ul>
 *   <li>I1 非负性：批次可用克数不得为负；</li>
 *   <li>I2 预占约束：预占克数不得超过可用克数；</li>
 *   <li>I6 粒度一致性：各项回补克数之和等于整单记录的回补克数；</li>
 *   <li>I7 分摊无损：各批次可用克数的实际变化量之和等于整单差额。</li>
 * </ul>
 *
 * <p>随机种子固定，因此任何一次失败都可被完整复现；这也是实验可重复性的前提。
 *
 * <p>运行前提：需提供数据库连接（{@code DB_USERNAME} 与 {@code DB_PASSWORD}），未配置时整类跳过。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@DisplayName("E1 不变式随机回归")
class InvariantRegressionExperimentTest {

    /** 固定种子：保证结果可复现，失败可回溯 */
    private static final long SEED = 20260914L;
    private static final int ROUNDS = 20;
    /** 每个批次的初始可用克数，取足够大以避免因库存不足干扰不变式判定 */
    private static final int BATCH_INITIAL_GRAMS = 50_000;

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
     * 主实验：随机结构与随机偏差下，全部不变式必须成立。
     * 任何一次违反都会带上轮次、项数与批次变化明细，便于定位。
     */
    @Test
    @DisplayName("随机订单结构与偏差组合下，I1 / I2 / I6 / I7 全部成立")
    void randomSequencesPreserveAllInvariants() {
        Random random = new Random(SEED);
        List<String> violations = new ArrayList<>();
        int executedRounds = 0;

        for (int round = 0; round < ROUNDS; round++) {
            String token = fixture.uniqueToken();
            createdTokens.add(token);
            int itemCount = 1 + random.nextInt(4);          // 1 至 4 个订单项
            int batchesPerItem = 1 + random.nextInt(3);     // 每项跨 1 至 3 个批次

            List<Long> batchIds = new ArrayList<>();
            for (int index = 0; index < batchesPerItem; index++) {
                batchIds.add(fixture.createBatch(token + index, BATCH_INITIAL_GRAMS));
            }
            Map<Long, Integer> before = new HashMap<>();
            for (Long batchId : batchIds) {
                before.put(batchId, fixture.availableGrams(batchId));
            }

            long orderId = fixture.createOrder(token, new BigDecimal("10.00"), "PICKED");
            int totalPrepaid = 0;
            int totalActual = 0;
            List<WeighingAdjustmentService.ItemWeighing> items = new ArrayList<>();

            for (int itemIndex = 0; itemIndex < itemCount; itemIndex++) {
                int prepaidGrams = 500 + random.nextInt(501);   // 500 至 1000 克
                long itemId = fixture.createOrderItem(token + "-" + itemIndex, orderId, prepaidGrams,
                        new BigDecimal("10.00"));
                // 把该项克数轮流分到各批次上，保证每项至少跨 1 个批次
                int remaining = prepaidGrams;
                for (int batchIndex = 0; batchIndex < batchesPerItem; batchIndex++) {
                    int share = batchIndex == batchesPerItem - 1
                            ? remaining
                            : Math.max(1, remaining / (batchesPerItem - batchIndex));
                    share = Math.min(share, remaining);
                    fixture.allocate(itemId, batchIds.get(batchIndex), share);
                    remaining -= share;
                }
                // 偏差方向随机：变重或变轻，幅度不超过预占克数的 20%
                int maxShift = Math.max(1, prepaidGrams / 5);
                int shift = random.nextInt(2 * maxShift + 1) - maxShift;
                int actualGrams = prepaidGrams + shift;
                if (actualGrams <= 0) {
                    actualGrams = 1;
                }
                totalPrepaid += prepaidGrams;
                totalActual += actualGrams;
                items.add(new WeighingAdjustmentService.ItemWeighing(itemId, actualGrams,
                        new BigDecimal("10.00")));
            }

            var view = weighingService.submit(fixture.merchantOperator(), orderId, null, null, items,
                    "E1 round " + round, "EXP-E1-" + token, "127.0.0.1");
            executedRounds++;

            // I7：实际称重总量等于各项之和
            if (view.actualGrams() == null || view.actualGrams() != totalActual) {
                violations.add("round " + round + " actual grams mismatch: expected=" + totalActual
                        + " actual=" + view.actualGrams());
            }

            // I6：整单回补克数等于各项回补之和
            int sumOfItems = 0;
            for (var item : items) {
                var row = fixture.findItemWeighing(item.orderItemId());
                if (row == null || row.inventoryAdjustGrams() == null) {
                    violations.add("round " + round + " missing item weighing for item " + item.orderItemId());
                    continue;
                }
                sumOfItems += row.inventoryAdjustGrams();
            }
            if (sumOfItems != view.inventoryAdjustGrams()) {
                violations.add("I6 violated at round " + round + ": items sum=" + sumOfItems
                        + " order adjust=" + view.inventoryAdjustGrams());
            }

            // I7：批次变化之和等于整单差额，且方向与差额一致
            int totalDelta = 0;
            for (Long batchId : batchIds) {
                totalDelta += before.get(batchId) - fixture.availableGrams(batchId);
            }
            if (totalDelta != totalActual - totalPrepaid) {
                violations.add("I7 violated at round " + round + ": batch delta=" + totalDelta
                        + " expected=" + (totalActual - totalPrepaid));
            }

            // I1 与 I2
            int currentRound = round;
            violations.addAll(fixture.checkBatchInvariants(batchIds).stream()
                    .map(message -> "round " + currentRound + " " + message).toList());
        }

        assertEquals(ROUNDS, executedRounds, "所有轮次都应执行完毕");
        assertTrue(violations.isEmpty(),
                "随机回归不得出现不变式违反，实际违反 " + violations.size() + " 处：\n"
                        + String.join("\n", violations));
    }

    /**
     * 边界实验：所有订单项都变轻时，回补方向必须为「退回」，且退回总量与差额一致。
     * 这一场景与「全部变重」互为对照，用于验证方向判定不依赖绝对值。
     */
    @Test
    @DisplayName("全部变轻时回补方向为退回，且退回量与差额一致")
    void allLighterItemsReturnGrams() {
        String token = fixture.uniqueToken();
        createdTokens.add(token);

        long batchA = fixture.createBatch(token + "a", BATCH_INITIAL_GRAMS);
        long batchB = fixture.createBatch(token + "b", BATCH_INITIAL_GRAMS);
        List<Long> batchIds = List.of(batchA, batchB);
        Map<Long, Integer> before = new HashMap<>();
        for (Long batchId : batchIds) {
            before.put(batchId, fixture.availableGrams(batchId));
        }

        long orderId = fixture.createOrder(token, new BigDecimal("20.00"), "PICKED");
        long item1 = fixture.createOrderItem(token + "-1", orderId, 1000, new BigDecimal("10.00"));
        long item2 = fixture.createOrderItem(token + "-2", orderId, 1000, new BigDecimal("10.00"));
        fixture.allocate(item1, batchA, 1000);
        fixture.allocate(item2, batchB, 1000);

        // 两项都变轻共 300 克
        var items = List.of(
                new WeighingAdjustmentService.ItemWeighing(item1, 900, new BigDecimal("9.00")),
                new WeighingAdjustmentService.ItemWeighing(item2, 800, new BigDecimal("8.00")));
        var view = weighingService.submit(fixture.merchantOperator(), orderId, null, null, items,
                "E1 all lighter", "EXP-E1-LIGHT-" + token, "127.0.0.1");

        assertEquals(-300, view.inventoryAdjustGrams(), "全部变轻时整单回补量应为 −300");
        // 逐项回补按各订单项自身批次执行，而不是把 300 克在批次之间均摊：
        // 项 1 变轻 100 克只影响 batchA，项 2 变轻 200 克只影响 batchB。
        assertEquals(before.get(batchA) + 100, fixture.availableGrams(batchA),
                "项 1 的 100 克差额应只退回到它自己的批次");
        assertEquals(before.get(batchB) + 200, fixture.availableGrams(batchB),
                "项 2 的 200 克差额应只退回到它自己的批次");
        assertTrue(fixture.checkBatchInvariants(batchIds).isEmpty(), "退回后不变式仍应成立");
    }

    /**
     * 边界实验：实际克数等于预估时，不应对库存做任何调整，也不应留下非零回补量。
     */
    @Test
    @DisplayName("实际等于预估时回补量为零且库存不变")
    void equalGramsLeaveInventoryUntouched() {
        String token = fixture.uniqueToken();
        createdTokens.add(token);

        long batchId = fixture.createBatch(token, BATCH_INITIAL_GRAMS);
        int before = fixture.availableGrams(batchId);
        long orderId = fixture.createOrder(token, new BigDecimal("10.00"), "PICKED");
        long itemId = fixture.createOrderItem(token + "-1", orderId, 1000, new BigDecimal("10.00"));
        fixture.allocate(itemId, batchId, 1000);

        var view = weighingService.submit(fixture.merchantOperator(), orderId, new BigDecimal("10.00"), 1000,
                null, "E1 equal grams", "EXP-E1-EQ-" + token, "127.0.0.1");

        assertEquals(0, view.inventoryAdjustGrams(), "无偏差时回补量应为 0");
        assertEquals(before, fixture.availableGrams(batchId), "无偏差时库存不得变化");
    }
}
