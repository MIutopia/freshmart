package com.freshmart.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.freshmart.weighing.WeighingAdjustmentService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

/**
 * 实验 E3：幂等性实验。
 *
 * <p>研究问题 RQ3 —— 在重复提交与并发操作的条件下，幂等设计与唯一约束是否足以避免重复入账？
 *
 * <p>三个子场景：
 * <ul>
 *   <li>E3-a 顺序重放：同一幂等键连续提交多次，预期命中幂等键缓存返回首次结果，库存只变一次；</li>
 *   <li>E3-b 并发同键：多线程用同一幂等键同时提交，预期唯一约束拦截，仅一次生效、其余转为冲突；</li>
 *   <li>E3-c 同实体异键：同一订单用不同幂等键提交，预期订单级唯一键拦截，仅一次生效。</li>
 * </ul>
 *
 * <p>度量指标为「有效变更次数」——称重记录条数与批次可用克数的实际变动量，二者都应为 1 与一次差额。
 *
 * <p>运行前提：需提供数据库连接（{@code DB_USERNAME} 与 {@code DB_PASSWORD}），未配置时整类跳过。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@DisplayName("E3 幂等性实验")
class IdempotencyExperimentTest {

    private static final int CONCURRENCY = 10;

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
     * E3-a：同一幂等键顺序重放。
     * 预期：每次都返回同一个调整标识，称重记录只有一条，批次克数只被扣减一次。
     */
    @Test
    @DisplayName("E3-a 顺序重放：同键多次提交只生效一次且返回一致结果")
    void sequentialReplayAppliesOnce() {
        String token = fixture.uniqueToken();
        createdTokens.add(token);
        Scenario scenario = prepareScenario(token, 300);
        String idempotencyKey = "EXP-E3-SEQ-" + token;

        List<String> returnedIds = new ArrayList<>();
        for (int round = 0; round < 5; round++) {
            var view = weighingService.submit(fixture.merchantOperator(), scenario.orderId(),
                    new BigDecimal("13.00"), scenario.actualGrams(), null, "E3 sequential replay",
                    idempotencyKey, "127.0.0.1");
            returnedIds.add(view.adjustmentId());
        }

        assertEquals(1, returnedIds.stream().distinct().count(), "同键重放应始终返回同一个调整标识");
        assertEquals(1, fixture.countWeighings(scenario.orderId()), "称重记录必须只有一条");
        assertEquals(scenario.availableBefore() - scenario.expectedAdjustGrams(), fixture.availableGrams(scenario.batchId()),
                "批次克数只能被扣减一次");
        assertTrue(fixture.checkBatchInvariants(List.of(scenario.batchId())).isEmpty(), "批次不变式仍应成立");
    }

    /**
     * E3-b：并发使用同一幂等键。
     * 预期：唯一约束使恰好一次成功，其余转为冲突；称重记录仍只有一条，库存只变一次。
     */
    @Test
    @DisplayName("E3-b 并发同键：唯一约束保证恰好一次生效")
    void concurrentSameKeyAppliesOnce() throws Exception {
        String token = fixture.uniqueToken();
        createdTokens.add(token);
        Scenario scenario = prepareScenario(token, 300);
        String idempotencyKey = "EXP-E3-CONC-" + token;

        Attempts attempts = runConcurrently(CONCURRENCY, () -> weighingService.submit(fixture.merchantOperator(),
                scenario.orderId(), new BigDecimal("13.00"), scenario.actualGrams(), null, "E3 concurrent same key",
                idempotencyKey, "127.0.0.1"));

        assertEquals(1, fixture.countWeighings(scenario.orderId()), "并发同键后称重记录必须只有一条");
        assertEquals(scenario.availableBefore() - scenario.expectedAdjustGrams(), fixture.availableGrams(scenario.batchId()),
                "并发同键下批次克数只能被扣减一次");
        assertTrue(attempts.succeeded() >= 1, "至少应有一次请求成功");
        assertEquals(CONCURRENCY, attempts.succeeded() + attempts.conflicted() + attempts.unexpected(),
                "每次尝试都应被归类为成功、冲突或异常");
        assertTrue(attempts.unexpected() == 0, "不应出现冲突与成功之外的异常，实际=" + attempts.unexpected());
        assertTrue(fixture.checkBatchInvariants(List.of(scenario.batchId())).isEmpty(), "批次不变式仍应成立");
    }

    /**
     * E3-c：同一订单使用不同幂等键。
     * 预期：订单级唯一键使第二次提交被拒绝，避免同一订单被重复结算。
     */
    @Test
    @DisplayName("E3-c 同实体异键：订单级唯一键拒绝重复结算")
    void differentKeysSameOrderRejected() {
        String token = fixture.uniqueToken();
        createdTokens.add(token);
        Scenario scenario = prepareScenario(token, 300);

        weighingService.submit(fixture.merchantOperator(), scenario.orderId(), new BigDecimal("13.00"),
                scenario.actualGrams(), null, "E3 first key", "EXP-E3-K1-" + token, "127.0.0.1");
        int afterFirst = fixture.availableGrams(scenario.batchId());

        boolean rejected = false;
        try {
            weighingService.submit(fixture.merchantOperator(), scenario.orderId(), new BigDecimal("13.00"),
                    scenario.actualGrams(), null, "E3 second key", "EXP-E3-K2-" + token, "127.0.0.1");
        } catch (ResponseStatusException exception) {
            rejected = true;
        }

        assertTrue(rejected, "同一订单用不同幂等键再次提交应被拒绝");
        assertEquals(1, fixture.countWeighings(scenario.orderId()), "称重记录仍只有一条");
        assertEquals(afterFirst, fixture.availableGrams(scenario.batchId()), "被拒绝的请求不得改变库存");
    }

    /** 构造一个批次 + 订单 + 订单项 + 分配的完整场景 */
    private Scenario prepareScenario(String token, int initialGrams) {
        long batchId = fixture.createBatch(token, initialGrams);
        int availableBefore = fixture.availableGrams(batchId);
        long orderId = fixture.createOrder(token, new BigDecimal("12.00"), "PICKED");
        long itemId = fixture.createOrderItem(token + "-1", orderId, 1000, new BigDecimal("12.00"));
        fixture.allocate(itemId, batchId, 1000);
        return new Scenario(batchId, orderId, itemId, availableBefore, 200, 1200);
    }

    /** 并发执行同一动作，把结果归类为成功、冲突与异常 */
    private Attempts runConcurrently(int threads, Callable<?> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < threads; index++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    try {
                        action.call();
                        succeeded.incrementAndGet();
                    } catch (ResponseStatusException exception) {
                        // 唯一键冲突与状态冲突都属于「正确的拒绝」
                        conflicted.incrementAndGet();
                    } catch (Exception exception) {
                        unexpected.incrementAndGet();
                    }
                    return null;
                }));
            }
            startGate.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        return new Attempts(succeeded.get(), conflicted.get(), unexpected.get());
    }

    private record Scenario(long batchId, long orderId, long itemId, int availableBefore,
            int expectedAdjustGrams, int actualGrams) {
    }

    private record Attempts(int succeeded, int conflicted, int unexpected) {
    }
}
