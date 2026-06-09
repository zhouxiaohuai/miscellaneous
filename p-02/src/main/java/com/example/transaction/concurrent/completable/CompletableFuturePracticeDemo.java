package com.example.transaction.concurrent.completable;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CompletableFuture 实战 — 由浅入深第六站
 *
 * 知识点：
 * 1. 并行查询（多数据源聚合）— 直接对应秒杀异步化
 * 2. 超时控制 + 重试机制
 * 3. 秒杀流程异步化改造示例
 */
@Slf4j
@Component
public class CompletableFuturePracticeDemo {

    private final ExecutorService pool = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(50),
            r -> new Thread(r, "cf-practice-" + r.hashCode())
    );

    // ==================== 1. 并行查询 ====================

    /**
     * 并行查询 — 多数据源聚合（最常用场景）
     *
     * 场景：用户下单页面需要同时查询：
     *   - 用户信息（MySQL，100ms）
     *   - 订单列表（MySQL，80ms）
     *   - 积分余额（Redis，20ms）
     *   - 推荐商品（ES，150ms）
     *
     * 串行：100 + 80 + 20 + 150 = 350ms
     * 并行：max(100, 80, 20, 150) = 150ms
     */
    public Map<String, Object> parallelQuery() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // 模拟 4 个数据源查询
        CompletableFuture<String> userFuture = CompletableFuture.supplyAsync(() -> {
            sleep(100); return "用户: 张三, 手机: 138****1234";
        }, pool);

        CompletableFuture<List<String>> orderFuture = CompletableFuture.supplyAsync(() -> {
            sleep(80); return List.of("订单A: ¥299", "订单B: ¥599", "订单C: ¥1299");
        }, pool);

        CompletableFuture<Long> scoreFuture = CompletableFuture.supplyAsync(() -> {
            sleep(20); return 10000L;
        }, pool);

        CompletableFuture<List<String>> recommendFuture = CompletableFuture.supplyAsync(() -> {
            sleep(150); return List.of("商品X: ¥99", "商品Y: ¥199", "商品Z: ¥399");
        }, pool);

        // 并行等待全部完成
        long start = System.currentTimeMillis();
        CompletableFuture.allOf(userFuture, orderFuture, scoreFuture, recommendFuture).join();
        long cost = System.currentTimeMillis() - start;

        // 聚合结果
        Map<String, Object> pageData = new LinkedHashMap<>();
        pageData.put("用户信息", userFuture.get());
        pageData.put("订单列表", orderFuture.get());
        pageData.put("积分余额", scoreFuture.get());
        pageData.put("推荐商品", recommendFuture.get());

        result.put("页面数据", pageData);
        result.put("性能对比",
                "串行耗时: 100 + 80 + 20 + 150 = 350ms\n" +
                "并行耗时: " + cost + "ms (取最慢的 150ms)\n" +
                "提升: " + (350 - cost) + "ms (" + (350 * 100 / Math.max(cost, 1) - 100) + "%)");

        result.put("核心代码", """
                CompletableFuture<User> userFuture = supplyAsync(() -> queryUser(id));
                CompletableFuture<List<Order>> orderFuture = supplyAsync(() -> queryOrders(id));
                CompletableFuture.allOf(userFuture, orderFuture).join();
                // 然后分别 get() 获取结果
                """);

        return result;
    }

    // ==================== 2. 超时控制 + 重试 ====================

    /**
     * 超时控制 + 重试机制
     */
    public Map<String, Object> timeoutAndRetry() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- 超时控制 ----
        AtomicInteger callCount = new AtomicInteger(0);

        CompletableFuture<String> withTimeout = CompletableFuture
                .supplyAsync(() -> {
                    callCount.incrementAndGet();
                    sleep(200); // 模拟慢任务
                    return "慢结果";
                }, pool)
                .completeOnTimeout("超时降级值", 50, TimeUnit.MILLISECONDS);

        result.put("超时控制", "completeOnTimeout(默认值, 50ms) | 结果: " + withTimeout.get());

        // ---- 重试机制（指数退避）----
        CompletableFuture<String> withRetry = retryWithBackoff(
                () -> {
                    int attempt = callCount.incrementAndGet();
                    if (attempt < 3) { // 前两次失败
                        throw new RuntimeException("模拟失败 #" + attempt);
                    }
                    return "重试成功（第 " + attempt + " 次）";
                },
                3,          // 最大重试次数
                50,         // 初始等待时间 ms
                pool
        );

        result.put("重试机制", "指数退避 | 最大 3 次 | 初始 50ms → 100ms → 200ms");
        result.put("重试结果", withRetry.get());

        // ---- 超时 + 重试组合 ----
        result.put("✅ 最佳实践", """
                1. 超时控制：防止慢任务阻塞整个流程
                2. 重试机制：应对偶发性失败（网络抖动、数据库短暂不可用）
                3. 指数退避：避免重试风暴（第一次 50ms，第二次 100ms，第三次 200ms）
                4. 重试 + 超时：每次重试也有超时限制
                """);

        return result;
    }

    // ==================== 3. 秒杀异步化 ====================

    /**
     * 秒杀流程异步化改造示例
     *
     * 当前秒杀流程（串行）：
     *   Redis预检(5ms) → DB扣减(50ms) → 写订单(30ms) → 发通知(100ms)
     *   总耗时: 5 + 50 + 30 + 100 = 185ms
     *
     * 异步化改造：
     *   Redis预检(5ms) → DB扣减+写订单(50ms) → 并行[发通知(100ms) + 更新统计(80ms)]
     *   总耗时: 5 + 50 + max(100, 80) = 155ms（通知和统计不阻塞主流程）
     */
    public Map<String, Object> seckillAsync() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        long start = System.currentTimeMillis();
        List<String> steps = new ArrayList<>();

        // Step 1: Redis 预检（必须同步，决定是否继续）
        steps.add("1. Redis 预检: 库存充足 ✓ (5ms)");
        sleep(5);

        // Step 2: DB 扣减 + 创建订单（必须同步，核心业务）
        CompletableFuture<String> dbFuture = CompletableFuture.supplyAsync(() -> {
            sleep(50); // DB 操作
            return "2. DB 扣减成功 + 订单创建: ORD-20260609-001";
        }, pool);
        String orderResult = dbFuture.get();
        steps.add(orderResult);

        // Step 3: 后续操作异步化（不阻塞返回给用户）
        CompletableFuture<Void> notifyFuture = CompletableFuture.runAsync(() -> {
            sleep(100); // 发送通知
            log.info("3. 通知已发送: 用户收到秒杀成功消息");
        }, pool);

        CompletableFuture<Void> statsFuture = CompletableFuture.runAsync(() -> {
            sleep(80); // 更新统计
            log.info("4. 统计已更新: 库存-1, 销量+1");
        }, pool);

        long mainCost = System.currentTimeMillis() - start;
        steps.add("3. 通知发送中...（异步，不阻塞返回）");
        steps.add("4. 统计更新中...（异步，不阻塞返回）");

        // 等异步任务完成（实际可以不等，这里为了演示）
        CompletableFuture.allOf(notifyFuture, statsFuture).join();
        long totalCost = System.currentTimeMillis() - start;

        result.put("异步化流程", steps);
        result.put("主流程耗时", mainCost + "ms（用户感知的响应时间）");
        result.put("总耗时（含异步）", totalCost + "ms");
        result.put("优化效果", """
                串行: Redis(5) + DB(50) + 订单(30) + 通知(100) + 统计(80) = 265ms（用户等待）
                异步: Redis(5) + DB(50) = 55ms（用户感知） + 通知/统计异步完成
                用户响应时间减少: """ + (265 - mainCost) + "ms");

        result.put("异步化原则", """
                ✅ 可以异步的：通知、日志、统计、缓存更新、积分累加
                ❌ 必须同步的：库存扣减、订单创建、支付操作（核心业务，需要立即返回结果）
                """);

        return result;
    }

    // ==================== 辅助方法 ====================

    /**
     * 指数退避重试
     */
    private <T> CompletableFuture<T> retryWithBackoff(Callable<T> task, int maxRetries,
                                                        long initialDelayMs, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            Exception lastException = null;
            long delay = initialDelayMs;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    return task.call();
                } catch (Exception e) {
                    lastException = e;
                    log.warn("重试 {}/{}: {}", attempt, maxRetries, e.getMessage());
                    if (attempt < maxRetries) {
                        try { Thread.sleep(delay); } catch (InterruptedException ie) { }
                        delay *= 2; // 指数退避
                    }
                }
            }
            throw new CompletionException("重试 " + maxRetries + " 次后仍失败", lastException);
        }, executor);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { }
    }
}
