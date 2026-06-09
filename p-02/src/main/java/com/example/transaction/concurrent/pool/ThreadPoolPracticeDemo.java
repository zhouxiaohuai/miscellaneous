package com.example.transaction.concurrent.pool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * 线程池实战 — 由浅入深第四站
 *
 * 知识点：
 * 1. 线程池大小选择（CPU 密集型 vs IO 密集型）
 * 2. 优雅关闭（shutdown vs shutdownNow）
 * 3. 任务编排（submit/execute/invokeAll/invokeAny）
 */
@Slf4j
@Component
public class ThreadPoolPracticeDemo {

    // ==================== 1. 池大小选择 ====================

    /**
     * 线程池大小选择 — 不是越大越好！
     */
    public Map<String, Object> poolSizing() {
        Map<String, Object> result = new LinkedHashMap<>();

        int cpuCores = Runtime.getRuntime().availableProcessors();

        result.put("CPU 核心数", cpuCores);

        // ---- CPU 密集型 ----
        Map<String, String> cpuIntensive = new LinkedHashMap<>();
        cpuIntensive.put("特点", "大量计算，很少 IO（加密、压缩、排序、正则）");
        cpuIntensive.put("公式", "线程数 = N + 1（N = CPU 核心数）");
        cpuIntensive.put("当前推荐", (cpuCores + 1) + " 个线程");
        cpuIntensive.put("原理", "CPU 密集型任务 CPU 不会空闲，多开线程反而增加上下文切换开销");
        cpuIntensive.put("+1 的原因", "当某个线程因为偶尔的缺页（page fault）等原因暂停时，额外线程可以利用空闲 CPU");
        result.put("CPU 密集型", cpuIntensive);

        // ---- IO 密集型 ----
        Map<String, String> ioIntensive = new LinkedHashMap<>();
        ioIntensive.put("特点", "大量等待 IO（网络请求、数据库查询、文件读写）");
        ioIntensive.put("公式", "线程数 = N × 2 或 N / (1 - 阻塞系数)");
        ioIntensive.put("当前推荐", (cpuCores * 2) + " 个线程（×2 简化公式）");
        ioIntensive.put("阻塞系数法", "假设 50% 时间在等 IO → N / 0.5 = 2N");
        ioIntensive.put("原理", "IO 等待期间 CPU 空闲，多开线程可以让 CPU 处理其他任务");
        result.put("IO 密集型", ioIntensive);

        // ---- 混合型 ----
        result.put("混合型任务", """
                公式：N × (1 + W/C)
                  N = CPU 核心数
                  W = 等待时间（IO）
                  C = 计算时间（CPU）
                例如：N=8, W=50ms, C=50ms → 8 × (1 + 1) = 16
                最终值需要通过压测调整！
                """);

        // ---- 实际调整建议 ----
        result.put("✅ 实际调整步骤", """
                1. 用公式计算初始值
                2. 压测观察 CPU 利用率、队列长度、响应时间
                3. CPU 利用率 < 70% → 可以增加线程
                4. CPU 利用率 > 80% → 减少线程
                5. 队列持续增长 → 线程不够或任务太慢
                """);

        result.put("⚠️ 常见误区", """
                ❌ 线程越多越快 → 上下文切换开销
                ❌ 直接用 Executors.newCachedThreadPool() → 线程数无上限
                ❌ 不考虑下游承受能力 → 数据库连接池、MQ 消费者数
                ✅ 线程池大小 ≤ 下游资源连接数
                """);

        return result;
    }

    // ==================== 2. 优雅关闭 ====================

    /**
     * 线程池优雅关闭 — shutdown vs shutdownNow
     */
    public Map<String, Object> gracefulShutdown() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- 演示 shutdown ----
        ThreadPoolExecutor pool1 = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10)
        );
        for (int i = 0; i < 6; i++) {
            pool1.execute(() -> {
                try { Thread.sleep(100); } catch (InterruptedException e) { }
            });
        }
        Thread.sleep(50);

        pool1.shutdown(); // 不再接受新任务，等待已提交任务完成
        boolean finished = pool1.awaitTermination(5, TimeUnit.SECONDS);

        Map<String, String> shutdownInfo = new LinkedHashMap<>();
        shutdownInfo.put("行为", "不再接受新任务，等待已提交任务（包括队列中的）执行完毕");
        shutdownInfo.put("是否阻塞", "调用本身不阻塞，需配合 awaitTermination");
        shutdownInfo.put("已完成", String.valueOf(finished));
        shutdownInfo.put("isShutdown()", String.valueOf(pool1.isShutdown()));
        shutdownInfo.put("isTerminated()", String.valueOf(pool1.isTerminated()));
        result.put("shutdown()", shutdownInfo);

        // ---- 演示 shutdownNow ----
        ThreadPoolExecutor pool2 = new ThreadPoolExecutor(
                2, 4, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10)
        );
        for (int i = 0; i < 6; i++) {
            pool2.execute(() -> {
                try { Thread.sleep(500); } catch (InterruptedException e) { }
            });
        }
        Thread.sleep(50);

        List<Runnable> unfinished = pool2.shutdownNow(); // 尝试中断所有线程

        Map<String, String> shutdownNowInfo = new LinkedHashMap<>();
        shutdownNowInfo.put("行为", "尝试中断所有正在执行的线程，返回队列中未执行的任务");
        shutdownNowInfo.put("未执行任务数", String.valueOf(unfinished.size()));
        shutdownNowInfo.put("⚠️ 注意", "shutdownNow 只发送中断信号，线程可能不会立即停止（取决于是否响应中断）");
        result.put("shutdownNow()", shutdownNowInfo);

        // ---- 对比 ----
        result.put("对比总结", """
                shutdown()     — 温和关闭：停止接受新任务，等待已有任务完成
                shutdownNow()  — 强制关闭：中断正在执行的线程，丢弃队列任务
                awaitTermination(timeout) — 等待指定时间，超时返回 false
                """);

        result.put("✅ 推荐关闭流程", """
                1. pool.shutdown()                    ← 停止接收新任务
                2. if (!pool.awaitTermination(30s))   ← 等待 30 秒
                3.     pool.shutdownNow()             ← 还没完成就强制中断
                4.     awaitTermination(10s)           ← 再等 10 秒确保线程退出
                """);

        return result;
    }

    // ==================== 3. 任务编排 ====================

    /**
     * 任务编排：submit/execute/invokeAll/invokeAny
     */
    public Map<String, Object> taskOrchestration() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                3, 6, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(20)
        );

        // ---- 1. execute vs submit ----
        Map<String, String> execVsSubmit = new LinkedHashMap<>();
        execVsSubmit.put("execute(Runnable)", "无返回值 | 异常直接抛出 | 简单任务");
        execVsSubmit.put("submit(Runnable)", "返回 Future<?> | 异常封装在 Future 中 | 需要检查结果");
        execVsSubmit.put("submit(Callable)", "返回 Future<T> | 可获取返回值 | 异步任务");
        result.put("execute vs submit", execVsSubmit);

        // 演示 submit 获取返回值
        Future<String> future = pool.submit(() -> {
            Thread.sleep(50);
            return "任务结果：Hello from pool!";
        });
        result.put("submit(Callable) 示例", future.get(5, TimeUnit.SECONDS));

        // ---- 2. invokeAll — 全部执行，等待所有完成 ----
        List<Callable<String>> tasks = List.of(
                () -> { Thread.sleep(100); return "任务A: 用户信息"; },
                () -> { Thread.sleep(80);  return "任务B: 订单列表"; },
                () -> { Thread.sleep(60);  return "任务C: 积分余额"; }
        );

        long start = System.currentTimeMillis();
        List<Future<String>> futures = pool.invokeAll(tasks); // 并行执行，全部完成才返回
        long cost = System.currentTimeMillis() - start;

        List<String> allResults = new ArrayList<>();
        for (Future<String> f : futures) {
            allResults.add(f.get());
        }
        result.put("invokeAll()", "并行执行 " + tasks.size() + " 个任务 | 总耗时: " + cost + "ms（而非 240ms）");
        result.put("invokeAll 结果", allResults);

        // ---- 3. invokeAny — 取最快的一个 ----
        Callable<String> fastTask = () -> { Thread.sleep(50);  return "快速任务（50ms）"; };
        Callable<String> slowTask = () -> { Thread.sleep(200); return "慢速任务（200ms）"; };

        start = System.currentTimeMillis();
        String fastest = pool.invokeAny(List.of(fastTask, slowTask)); // 只返回最快的那个
        cost = System.currentTimeMillis() - start;
        result.put("invokeAny()", "取最快完成的结果 | 耗时: " + cost + "ms | 结果: " + fastest);

        // ---- 实战场景 ----
        result.put("实战场景", """
                invokeAll — 多数据源聚合查询（用户+订单+积分+推荐），等所有结果返回后合并
                invokeAny — 多通道短信发送（阿里云+腾讯云+华为云），哪个先成功用哪个
                submit    — 单个异步任务，需要结果时 future.get()
                execute   — 火后不管（fire-and-forget），如异步日志写入
                """);

        pool.shutdown();
        return result;
    }
}
