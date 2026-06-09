package com.example.transaction.concurrent.utility;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 并发工具类 — 由浅入深第七站
 *
 * 知识点：
 * 1. CountDownLatch — 倒计数器（等 N 个任务完成）
 * 2. CyclicBarrier — 循环屏障（N 个线程互相等待）
 * 3. Semaphore — 信号量（控制并发数）
 */
@Slf4j
@Component
public class ConcurrentUtilityDemo {

    private final ExecutorService pool = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(50)
    );

    // ==================== 1. CountDownLatch ====================

    /**
     * CountDownLatch — 倒计数器
     *
     * 场景：主线程等 N 个子任务完成后再继续
     * 类比：裁判等所有运动员就位后，发令起跑
     *
     * 特点：一次性，不可重置
     */
    public Map<String, Object> countDownLatch() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        int taskCount = 5;
        CountDownLatch latch = new CountDownLatch(taskCount);
        List<String> completed = new ArrayList<>();

        long start = System.currentTimeMillis();

        // 模拟 5 个并行任务
        for (int i = 1; i <= taskCount; i++) {
            final int taskId = i;
            pool.execute(() -> {
                try {
                    Thread.sleep(50 + taskId * 20); // 不同耗时
                    synchronized (completed) {
                        completed.add("任务" + taskId + " 完成 (" + (50 + taskId * 20) + "ms)");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown(); // 计数 -1（必须在 finally 中！）
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS); // 主线程等待（最多 5 秒）
        long cost = System.currentTimeMillis() - start;

        result.put("CountDownLatch", "计数器从 " + taskCount + " 递减到 0");
        result.put("已完成任务", completed);
        result.put("主线程等待耗时", cost + "ms（取最慢任务的时间）");
        result.put("API 速查", """
                new CountDownLatch(count) — 创建计数器
                latch.countDown()         — 计数 -1（每个任务完成后调用）
                latch.await()             — 阻塞等待计数归零
                latch.await(timeout, unit) — 超时等待
                latch.getCount()          — 查看当前计数
                """);
        result.put("典型场景", """
                1. 主线程等所有子任务完成后再汇总结果
                2. 多个服务启动完成后再开放流量
                3. N 个线程同时开始（计数器设为 1，所有线程 await，主线程 countDown）
                """);

        return result;
    }

    // ==================== 2. CyclicBarrier ====================

    /**
     * CyclicBarrier — 循环屏障
     *
     * 场景：N 个线程互相等待，全部到达后一起继续
     * 类比：团队集合 — 所有人到齐后才出发
     *
     * 特点：可重用（reset），可设 barrierAction
     */
    public Map<String, Object> cyclicBarrier() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        int parties = 3;
        List<String> phases = new ArrayList<>();

        // barrierAction：所有线程到达后执行
        CyclicBarrier barrier = new CyclicBarrier(parties, () -> {
            synchronized (phases) {
                phases.add(">>> 所有人到齐，开始下一轮！");
            }
            log.info("所有线程到达屏障点，执行 barrierAction");
        });

        List<String> executionLog = new ArrayList<>();

        // 3 个线程模拟 3 轮同步
        for (int round = 1; round <= 2; round++) {
            for (int i = 1; i <= parties; i++) {
                final int threadId = i;
                final int r = round;
                pool.execute(() -> {
                    try {
                        // 每个线程做自己的事
                        Thread.sleep(threadId * 30);
                        synchronized (executionLog) {
                            executionLog.add("第" + r + "轮 | 线程" + threadId + " 到达屏障");
                        }

                        barrier.await(5, TimeUnit.SECONDS); // 等待其他线程

                        synchronized (executionLog) {
                            executionLog.add("第" + r + "轮 | 线程" + threadId + " 通过屏障");
                        }
                    } catch (Exception e) {
                        log.error("线程 {} 异常: {}", threadId, e.getMessage());
                    }
                });
            }
            Thread.sleep(500); // 等一轮完成再开始下一轮
            barrier.reset(); // 重用屏障
        }

        Thread.sleep(2000);
        result.put("CyclicBarrier", "N 个线程互相等待，全部到达后继续");
        result.put("执行日志", executionLog);
        result.put("phases", phases);
        result.put("API 速查", """
                new CyclicBarrier(parties)            — 创建屏障（N 个线程到达才放行）
                new CyclicBarrier(parties, action)    — 到达后执行 action
                barrier.await()                       — 等待其他线程
                barrier.await(timeout, unit)          — 超时等待
                barrier.reset()                       — 重置屏障（可重用）
                barrier.getParties()                  — 需要到达的线程数
                barrier.getNumberWaiting()            — 当前等待的线程数
                """);
        result.put("CountDownLatch vs CyclicBarrier", """
                CountDownLatch — 一次性，主线程等子任务，计数减到 0 结束
                CyclicBarrier  — 可重用，线程互相等待，全部到达后一起继续
                """);

        return result;
    }

    // ==================== 3. Semaphore ====================

    /**
     * Semaphore — 信号量
     *
     * 场景：控制同时访问某个资源的线程数
     * 类比：停车位 — 只有 N 个车位，停满了就排队等
     */
    public Map<String, Object> semaphore() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        int permits = 3; // 同时最多 3 个线程
        Semaphore semaphore = new Semaphore(permits);
        List<String> accessLog = new ArrayList<>();

        // 模拟 8 个线程竞争 3 个许可
        for (int i = 1; i <= 8; i++) {
            final int taskId = i;
            pool.execute(() -> {
                try {
                    semaphore.acquire(); // 获取许可（可能阻塞）
                    synchronized (accessLog) {
                        accessLog.add("任务" + taskId + " 获得许可 | 可用: " + semaphore.availablePermits());
                    }
                    Thread.sleep(100); // 模拟使用资源
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    semaphore.release(); // 释放许可
                    synchronized (accessLog) {
                        accessLog.add("任务" + taskId + " 释放许可 | 可用: " + semaphore.availablePermits());
                    }
                }
            });
        }

        Thread.sleep(1500);
        result.put("Semaphore", permits + " 个许可 | 8 个线程竞争");
        result.put("执行日志", accessLog);
        result.put("API 速查", """
                new Semaphore(permits)      — 创建信号量（许可数）
                new Semaphore(permits, fair) — fair=true 公平（FIFO），性能较低
                semaphore.acquire()         — 获取许可（阻塞）
                semaphore.acquire(n)        — 获取 N 个许可
                semaphore.tryAcquire()      — 非阻塞，返回 boolean
                semaphore.tryAcquire(timeout, unit) — 超时等待
                semaphore.release()         — 释放许可
                semaphore.availablePermits() — 当前可用许可数
                """);
        result.put("典型场景", """
                1. 数据库连接池 — 限制同时连接数
                2. 接口限流 — 限制并发请求数
                3. 资源池 — 对象池、线程池
                4. 读写锁的读锁 — 允许 N 个读者同时读
                """);

        return result;
    }
}
