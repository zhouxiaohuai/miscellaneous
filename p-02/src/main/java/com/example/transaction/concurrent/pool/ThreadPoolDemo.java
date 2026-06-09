package com.example.transaction.concurrent.pool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池核心 — 由浅入深第三站
 *
 * 知识点：
 * 1. ThreadPoolExecutor 7 大参数
 * 2. 4 种内置线程池（及其隐患）
 * 3. 4 种拒绝策略
 * 4. 线程池运行时监控
 */
@Slf4j
@Component
public class ThreadPoolDemo {

    // ==================== 1. 七大参数 ====================

    /**
     * ThreadPoolExecutor 7 大参数详解
     *
     * 类比餐厅：
     *   corePoolSize    = 正式员工数（即使空闲也不解雇）
     *   maximumPoolSize = 最大员工数（正式 + 临时）
     *   keepAliveTime   = 临时员工空闲多久后解雇
     *   unit            = 时间单位
     *   workQueue       = 等候区（顾客排队的地方）
     *   threadFactory   = 员工招聘方式（命名、优先级）
     *   handler         = 满员时的处理方式（拒绝策略）
     */
    public Map<String, Object> sevenParameters() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("ThreadPoolExecutor 7 大参数", """
                corePoolSize     — 核心线程数（常驻线程，即使空闲也不回收）
                maximumPoolSize  — 最大线程数（核心 + 非核心）
                keepAliveTime    — 非核心线程空闲存活时间
                unit             — keepAliveTime 的时间单位
                workQueue        — 任务队列（核心线程满时，任务入队等待）
                threadFactory    — 线程工厂（自定义线程名、优先级等）
                handler          — 拒绝策略（队列满 + 最大线程时触发）
                """);

        result.put("餐厅类比", """
                顾客来吃饭：
                ① 正式员工（core）空闲 → 直接服务
                ② 正式员工全忙 → 顾客到等候区排队（workQueue）
                ③ 等候区满 → 招临时工（创建非核心线程，直到 maximumPoolSize）
                ④ 临时工也满 → 执行拒绝策略（handler）
                ⑤ 临时工空闲超过 keepAliveTime → 解雇（线程回收）
                """);

        // 实际创建演示
        AtomicInteger threadId = new AtomicInteger(0);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2,                                    // 核心线程数
                4,                                    // 最大线程数
                60L,                                  // 空闲存活时间
                TimeUnit.SECONDS,                     // 时间单位
                new ArrayBlockingQueue<>(10),          // 有界队列
                r -> new Thread(r, "demo-pool-" + threadId.incrementAndGet()), // 线程工厂
                new ThreadPoolExecutor.AbortPolicy()  // 拒绝策略
        );

        result.put("创建示例", "new ThreadPoolExecutor(2, 4, 60s, ArrayBlockingQueue(10), AbortPolicy)");

        // 任务执行流程
        result.put("任务执行流程", """
                提交任务
                  → 当前线程数 < corePoolSize？创建核心线程执行
                  → 当前线程数 >= corePoolSize？任务入 workQueue
                  → workQueue 满？创建非核心线程（直到 maximumPoolSize）
                  → 线程数 = maximumPoolSize 且队列满？触发拒绝策略
                """);

        executor.shutdown();
        return result;
    }

    // ==================== 2. 四种内置线程池 ====================

    /**
     * Executors 工厂方法 — 4 种内置线程池及隐患
     */
    public Map<String, Object> fourBuiltInPools() {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- 1. FixedThreadPool ----
        Map<String, String> fixed = new LinkedHashMap<>();
        fixed.put("创建", "Executors.newFixedThreadPool(n)");
        fixed.put("特点", "固定 n 个核心线程，无超时，LinkedBlockingQueue（无界）");
        fixed.put("⚠️ 隐患", "队列无界 → 任务堆积 → OOM！生产环境慎用");
        fixed.put("适用", "已知并发量、任务执行时间短");
        result.put("1. FixedThreadPool（固定线程池）", fixed);

        // ---- 2. SingleThreadExecutor ----
        Map<String, String> single = new LinkedHashMap<>();
        single.put("创建", "Executors.newSingleThreadExecutor()");
        single.put("特点", "1 个核心线程，保证任务顺序执行");
        single.put("⚠️ 隐患", "同 FixedThreadPool，队列无界 → OOM");
        single.put("适用", "需要顺序执行的场景（日志写入）");
        result.put("2. SingleThreadExecutor（单线程池）", single);

        // ---- 3. CachedThreadPool ----
        Map<String, String> cached = new LinkedHashMap<>();
        cached.put("创建", "Executors.newCachedThreadPool()");
        cached.put("特点", "无核心线程，最大线程数 Integer.MAX_VALUE，60s 超时");
        cached.put("⚠️ 隐患", "最大线程数无上限 → 线程暴增 → OOM！");
        cached.put("适用", "大量短生命周期的异步任务");
        result.put("3. CachedThreadPool（缓存线程池）", cached);

        // ---- 4. ScheduledThreadPool ----
        Map<String, String> scheduled = new LinkedHashMap<>();
        scheduled.put("创建", "Executors.newScheduledThreadPool(n)");
        scheduled.put("特点", "支持定时/周期执行，DelayedWorkQueue");
        scheduled.put("⚠️ 隐患", "同 FixedThreadPool，队列无界 → OOM");
        scheduled.put("适用", "定时任务、周期性任务");
        result.put("4. ScheduledThreadPool（定时线程池）", scheduled);

        // ---- 生产环境建议 ----
        result.put("✅ 生产环境建议", """
                禁止使用 Executors 创建线程池！
                原因：FixedThreadPool/SingleThreadExecutor/ScheduledThreadPool 使用无界队列
                      CachedThreadPool 最大线程数无上限
                      都可能导致 OOM

                正确做法：手动 new ThreadPoolExecutor(...)，明确指定队列大小和拒绝策略
                """);

        return result;
    }

    // ==================== 3. 四种拒绝策略 ====================

    /**
     * 4 种拒绝策略对比
     */
    public Map<String, Object> rejectionPolicies() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // 创建一个"小而满"的线程池来演示拒绝
        // 核心 1 + 最大 2 + 队列 2 → 最多同时处理 4 个任务
        AtomicInteger counter = new AtomicInteger(0);

        // ---- 1. AbortPolicy（默认）----
        ThreadPoolExecutor abortPool = createDemoPool(1, 2, 2, new ThreadPoolExecutor.AbortPolicy());
        int abortRejected = submitAndWait(abortPool, 5, counter);
        result.put("1. AbortPolicy（默认）", "抛 RejectedExecutionException | 调用方可捕获处理 | 已拒绝: " + abortRejected);

        // ---- 2. CallerRunsPolicy ----
        ThreadPoolExecutor callerPool = createDemoPool(1, 2, 2, new ThreadPoolExecutor.CallerRunsPolicy());
        int callerRejected = submitAndWait(callerPool, 5, counter);
        result.put("2. CallerRunsPolicy", "由调用线程（主线程）自己执行任务 | 不丢弃、不抛异常 | 已拒绝: " + callerRejected);

        // ---- 3. DiscardPolicy ----
        ThreadPoolExecutor discardPool = createDemoPool(1, 2, 2, new ThreadPoolExecutor.DiscardPolicy());
        int discardRejected = submitAndWait(discardPool, 5, counter);
        result.put("3. DiscardPolicy", "静默丢弃任务，不抛异常 | 适合允许丢失的场景 | 已拒绝: " + discardRejected);

        // ---- 4. DiscardOldestPolicy ----
        ThreadPoolExecutor oldestPool = createDemoPool(1, 2, 2, new ThreadPoolExecutor.DiscardOldestPolicy());
        int oldestRejected = submitAndWait(oldestPool, 5, counter);
        result.put("4. DiscardOldestPolicy", "丢弃队列中最老的任务，然后重试 | 已拒绝: " + oldestRejected);

        result.put("拒绝策略速查", """
                AbortPolicy       — 抛异常（默认，推荐！调用方感知到拒绝）
                CallerRunsPolicy  — 调用者执行（降速，不丢任务，但阻塞调用线程）
                DiscardPolicy     — 静默丢弃（危险！任务悄悄丢失）
                DiscardOldestPolicy — 丢弃最老（危险！可能丢失重要任务）
                """);

        result.put("✅ 最佳实践", """
                推荐 AbortPolicy + 监控告警
                或自定义拒绝策略：记录日志 + 写入降级队列 + 发送告警
                """);

        return result;
    }

    // ==================== 4. 运行时监控 ====================

    /**
     * 线程池运行时监控指标
     */
    public Map<String, Object> poolMonitoring() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                3, 6, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                r -> new Thread(r, "monitor-demo-" + r.hashCode())
        );

        // 提交一些任务
        for (int i = 0; i < 8; i++) {
            pool.execute(() -> {
                try { Thread.sleep(200); } catch (InterruptedException e) { }
            });
        }
        Thread.sleep(50); // 等任务开始执行

        // 采集监控指标
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("getPoolSize()", pool.getPoolSize() + " — 当前线程数（含核心+非核心）");
        metrics.put("getActiveCount()", pool.getActiveCount() + " — 正在执行任务的线程数");
        metrics.put("getCorePoolSize()", pool.getCorePoolSize() + " — 核心线程数");
        metrics.put("getMaximumPoolSize()", pool.getMaximumPoolSize() + " — 最大线程数");
        metrics.put("getLargestPoolSize()", pool.getLargestPoolSize() + " — 历史最大线程数（峰值）");
        metrics.put("getTaskCount()", pool.getTaskCount() + " — 已提交任务总数（含已完成+执行中+排队）");
        metrics.put("getCompletedTaskCount()", pool.getCompletedTaskCount() + " — 已完成任务数");
        metrics.put("getQueue().size()", pool.getQueue().size() + " — 队列中等待的任务数");
        metrics.put("getQueue().remainingCapacity()", pool.getQueue().remainingCapacity() + " — 队列剩余容量");
        result.put("监控指标", metrics);

        // 计算派生指标
        long pending = pool.getTaskCount() - pool.getCompletedTaskCount() - pool.getActiveCount();
        result.put("派生指标", """
                待处理任务数 = taskCount - completedTaskCount - activeCount
                队列使用率   = queue.size / (queue.size + queue.remainingCapacity)
                线程利用率   = activeCount / poolSize
                完成率       = completedTaskCount / taskCount
                """);

        pool.shutdown();
        return result;
    }

    // ==================== 辅助方法 ====================

    private ThreadPoolExecutor createDemoPool(int core, int max, int queueSize,
                                               RejectedExecutionHandler handler) {
        return new ThreadPoolExecutor(
                core, max, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize),
                r -> new Thread(r, "reject-demo"),
                handler
        );
    }

    private int submitAndWait(ThreadPoolExecutor pool, int taskCount, AtomicInteger counter) throws Exception {
        int rejected = 0;
        for (int i = 0; i < taskCount; i++) {
            try {
                pool.execute(() -> {
                    try { Thread.sleep(100); } catch (InterruptedException e) { }
                });
            } catch (RejectedExecutionException e) {
                rejected++;
            }
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        return rejected;
    }
}
