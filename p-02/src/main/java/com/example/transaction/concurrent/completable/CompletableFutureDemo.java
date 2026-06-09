package com.example.transaction.concurrent.completable;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * CompletableFuture 核心 — 由浅入深第五站
 *
 * 知识点：
 * 1. 创建方式（supplyAsync / runAsync）
 * 2. 链式调用（thenApply / thenAccept / thenRun / thenCompose）
 * 3. 组合编排（thenCombine / allOf / anyOf）
 * 4. 异常处理（exceptionally / handle / whenComplete / timeout）
 */
@Slf4j
@Component
public class CompletableFutureDemo {

    private final ExecutorService pool = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(50),
            r -> new Thread(r, "cf-demo-" + r.hashCode())
    );

    // ==================== 1. 创建方式 ====================

    /**
     * CompletableFuture 创建 — 有返回值 vs 无返回值
     */
    public Map<String, Object> creation() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- supplyAsync：有返回值 ----
        CompletableFuture<String> supplyFuture = CompletableFuture.supplyAsync(() -> {
            log.info("supplyAsync 执行中...");
            return "Hello from supplyAsync";
        }, pool);

        result.put("supplyAsync()", "有返回值 → CompletableFuture<T> | 结果: " + supplyFuture.get());

        // ---- runAsync：无返回值 ----
        StringBuilder sb = new StringBuilder();
        CompletableFuture<Void> runFuture = CompletableFuture.runAsync(() -> {
            sb.append("Hello from runAsync");
        }, pool);

        runFuture.get(); // 等待完成
        result.put("runAsync()", "无返回值 → CompletableFuture<Void> | 结果: " + sb);

        // ---- 自定义线程池 ----
        result.put("线程池选择", """
                supplyAsync(task)        — 使用 ForkJoinPool.commonPool()（全局共享，慎用！）
                supplyAsync(task, pool)  — 使用自定义线程池（推荐！任务隔离）
                """);

        result.put("⚠️ 为什么不推荐默认线程池", """
                ForkJoinPool.commonPool() 是全局共享的：
                1. 所有 CompletableFuture 共用 → 互相影响
                2. 线程数 = CPU 核心数 - 1 → IO 密集型任务会排队
                3. 一个慢任务阻塞 → 影响所有其他异步任务
                """);

        return result;
    }

    // ==================== 2. 链式调用 ====================

    /**
     * 链式调用 — 上一步的结果是下一步的输入
     */
    public Map<String, Object> chaining() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- thenApply：转换结果（有入参，有返回值）----
        CompletableFuture<String> thenApply = CompletableFuture
                .supplyAsync(() -> "张三", pool)
                .thenApply(name -> "用户: " + name)                    // 同步转换
                .thenApply(info -> info + " | 状态: 正常");             // 继续转换
        result.put("thenApply()", "同步转换 | R apply(T t) | 结果: " + thenApply.get());

        // ---- thenApplyAsync：异步转换 ----
        CompletableFuture<String> thenApplyAsync = CompletableFuture
                .supplyAsync(() -> "张三", pool)
                .thenApplyAsync(name -> {
                    // 在自定义线程池中执行
                    return "异步处理: " + name;
                }, pool);
        result.put("thenApplyAsync()", "异步转换 | 在指定线程池执行 | 结果: " + thenApplyAsync.get());

        // ---- thenAccept：消费结果（有入参，无返回值）----
        StringBuilder consumed = new StringBuilder();
        CompletableFuture<Void> thenAccept = CompletableFuture
                .supplyAsync(() -> "订单数据", pool)
                .thenAccept(data -> consumed.append("消费: ").append(data));
        thenAccept.get();
        result.put("thenAccept()", "消费结果 | void accept(T t) | " + consumed);

        // ---- thenRun：执行动作（无入参，无返回值）----
        CompletableFuture<Void> thenRun = CompletableFuture
                .supplyAsync(() -> "计算完成", pool)
                .thenRun(() -> log.info("后续动作：发送通知"));
        thenRun.get();
        result.put("thenRun()", "无参动作 | void run() | 不关心上一步结果");

        // ---- thenCompose：flatMap（上一步结果作为下一步的输入，返回新的 CF）----
        CompletableFuture<String> thenCompose = CompletableFuture
                .supplyAsync(() -> 1001L, pool)                           // 查到 userId
                .thenCompose(userId ->                                   // 用 userId 查详情
                        CompletableFuture.supplyAsync(() -> "用户详情: id=" + userId, pool)
                );
        result.put("thenCompose()", "flatMap | 两个异步操作串行 | 结果: " + thenCompose.get());

        // ---- 链式方法对比 ----
        Map<String, String> compare = new LinkedHashMap<>();
        compare.put("thenApply(f)", "T → R | 同步转换 | 类似 Stream.map()");
        compare.put("thenAccept(f)", "T → void | 同步消费 | 类似 Stream.forEach()");
        compare.put("thenRun(f)", "() → void | 执行动作 | 不关心上一步结果");
        compare.put("thenCompose(f)", "T → CF<R> | 异步串行 | 类似 Stream.flatMap()");
        result.put("链式方法速查", compare);

        return result;
    }

    // ==================== 3. 组合编排 ====================

    /**
     * 组合编排 — 多个 CompletableFuture 的关系
     */
    public Map<String, Object> combination() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- thenCombine：两个任务都完成后，合并结果 ----
        CompletableFuture<String> combineFuture = CompletableFuture
                .supplyAsync(() -> "张三", pool)                    // 任务1：查用户
                .thenCombine(
                        CompletableFuture.supplyAsync(() -> 1000L, pool), // 任务2：查积分
                        (name, score) -> name + " | 积分: " + score       // 合并函数
                );
        result.put("thenCombine()", "两任务并行 → 合并结果 | " + combineFuture.get());

        // ---- thenAcceptBoth：两个任务都完成后，消费结果 ----
        StringBuilder bothResult = new StringBuilder();
        CompletableFuture<Void> bothFuture = CompletableFuture
                .supplyAsync(() -> "用户A", pool)
                .thenAcceptBoth(
                        CompletableFuture.supplyAsync(() -> "订单B", pool),
                        (user, order) -> bothResult.append(user).append(" + ").append(order)
                );
        bothFuture.get();
        result.put("thenAcceptBoth()", "两任务并行 → 消费结果 | " + bothResult);

        // ---- allOf：等待所有任务完成 ----
        CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> sleepReturn(50, "用户信息"), pool);
        CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> sleepReturn(80, "订单列表"), pool);
        CompletableFuture<String> f3 = CompletableFuture.supplyAsync(() -> sleepReturn(30, "积分余额"), pool);

        long start = System.currentTimeMillis();
        CompletableFuture.allOf(f1, f2, f3).get(); // 等全部完成
        long cost = System.currentTimeMillis() - start;

        result.put("allOf()", "等待所有任务完成 | 耗时: " + cost + "ms（取最慢的）");
        result.put("allOf 注意", "allOf 返回 CompletableFuture<Void>，需要单独 get 每个子任务获取结果");

        // ---- anyOf：取最先完成的 ----
        CompletableFuture<String> fast = CompletableFuture.supplyAsync(() -> sleepReturn(100, "慢结果"), pool);
        CompletableFuture<String> slow = CompletableFuture.supplyAsync(() -> sleepReturn(20, "快结果"), pool);

        Object anyResult = CompletableFuture.anyOf(fast, slow).get();
        result.put("anyOf()", "取最先完成的结果 | 结果: " + anyResult);

        // ---- 方法对比 ----
        Map<String, String> combineCompare = new LinkedHashMap<>();
        combineCompare.put("thenCombine(a, b)", "两个 CF 并行 → 合并结果 → 返回 CF<R>");
        combineCompare.put("thenAcceptBoth(a, b)", "两个 CF 并行 → 消费结果 → 返回 CF<Void>");
        combineCompare.put("runAfterBoth(a, b)", "两个 CF 并行 → 执行动作 → 返回 CF<Void>");
        combineCompare.put("allOf(cf...)", "N 个 CF → 全部完成 → 返回 CF<Void>");
        combineCompare.put("anyOf(cf...)", "N 个 CF → 最先完成 → 返回 CF<Object>");
        result.put("组合方法速查", combineCompare);

        return result;
    }

    // ==================== 4. 异常处理 ====================

    /**
     * 异常处理 — exceptionally / handle / whenComplete / timeout
     */
    public Map<String, Object> exceptionHandling() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- 1. exceptionally：异常时的降级处理 ----
        CompletableFuture<String> exceptional = CompletableFuture
                .supplyAsync(() -> {
                    if (true) throw new RuntimeException("数据库连接失败");
                    return "正常结果";
                }, pool)
                .exceptionally(ex -> {
                    log.warn("异常降级: {}", ex.getMessage());
                    return "默认值（降级）";
                });
        result.put("exceptionally()", "异常时执行 | 返回降级值 | 结果: " + exceptional.get());

        // ---- 2. handle：无论成功或失败都处理 ----
        CompletableFuture<String> handled = CompletableFuture
                .supplyAsync(() -> {
                    if (true) throw new RuntimeException("业务异常");
                    return "正常";
                }, pool)
                .handle((data, ex) -> {
                    if (ex != null) return "handle 降级: " + ex.getMessage();
                    return "handle 正常: " + data;
                });
        result.put("handle()", "成功或失败都处理 | 类似 try-catch-finally | 结果: " + handled.get());

        // ---- 3. whenComplete：监听结果（不改变结果）----
        CompletableFuture<String> completed = CompletableFuture
                .supplyAsync(() -> "原始结果", pool)
                .whenComplete((data, ex) -> {
                    if (ex != null) log.error("异常: {}", ex.getMessage());
                    else log.info("完成: {}", data);
                });
        result.put("whenComplete()", "监听完成事件，不改变结果 | 用于记录日志/监控");

        // ---- 4. completeOnTimeout：超时降级 ----
        CompletableFuture<String> timeoutFuture = CompletableFuture
                .supplyAsync(() -> {
                    sleepReturn(5000, "慢结果"); // 模拟 5 秒慢任务
                    return "慢结果";
                }, pool)
                .completeOnTimeout("超时降级值", 100, TimeUnit.MILLISECONDS);
        result.put("completeOnTimeout()", "超时返回默认值 | 结果: " + timeoutFuture.get());

        // ---- 5. orTimeout：超时抛异常 ----
        CompletableFuture<String> orTimeoutFuture = CompletableFuture
                .supplyAsync(() -> {
                    sleepReturn(5000, "慢结果");
                    return "慢结果";
                }, pool)
                .orTimeout(100, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> "orTimeout 捕获: " + ex.getClass().getSimpleName());
        result.put("orTimeout()", "超时抛 TimeoutException | 结果: " + orTimeoutFuture.get());

        // ---- 异常处理速查 ----
        Map<String, String> exCompare = new LinkedHashMap<>();
        exCompare.put("exceptionally(f)", "异常时降级 | 类似 catch");
        exCompare.put("handle(f)", "成功或失败都处理 | 类似 try-catch");
        exCompare.put("whenComplete(f)", "监听结果，不改变 | 类似 finally");
        exCompare.put("completeOnTimeout(v, t)", "超时返回默认值 | JDK 9+");
        exCompare.put("orTimeout(t)", "超时抛 TimeoutException | JDK 9+");
        result.put("异常处理速查", exCompare);

        return result;
    }

    // ==================== 辅助方法 ====================

    private String sleepReturn(long ms, String value) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { }
        return value;
    }
}
