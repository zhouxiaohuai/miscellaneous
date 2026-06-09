package com.example.transaction.concurrent.basic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * 线程基础 — 由浅入深第一站
 *
 * 知识点：
 * 1. 线程创建 3 种方式（Thread / Runnable / Callable+FutureTask）
 * 2. 线程生命周期 6 种状态
 * 3. 线程控制（sleep / join / interrupt / 守护线程）
 */
@Slf4j
@Component
public class ThreadBasicDemo {

    // ==================== 1. 线程创建 ====================

    /**
     * 线程创建 3 种方式对比
     */
    public Map<String, Object> threadCreation() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- 方式 1：继承 Thread ----
        StringBuilder threadResult = new StringBuilder();
        Thread t1 = new Thread("demo-thread-1") {
            @Override
            public void run() {
                threadResult.append("Thread 子类执行 | 线程名: ").append(getName());
            }
        };
        t1.start();
        t1.join();
        result.put("方式1：继承 Thread", threadResult.toString());

        // ---- 方式 2：实现 Runnable ----
        StringBuilder runnableResult = new StringBuilder();
        Runnable runnable = () -> {
            runnableResult.append("Runnable 执行 | 线程名: ").append(Thread.currentThread().getName());
        };
        Thread t2 = new Thread(runnable, "demo-runnable-1");
        t2.start();
        t2.join();
        result.put("方式2：实现 Runnable", runnableResult.toString());

        // ---- 方式 3：Callable + FutureTask（有返回值）----
        Callable<Integer> callable = () -> {
            log.info("Callable 执行中...");
            int sum = 0;
            for (int i = 1; i <= 100; i++) sum += i;
            return sum;
        };
        FutureTask<Integer> futureTask = new FutureTask<>(callable);
        Thread t3 = new Thread(futureTask, "demo-callable-1");
        t3.start();
        Integer callResult = futureTask.get(); // 阻塞等待结果
        result.put("方式3：Callable + FutureTask", "计算 1~100 的和 = " + callResult);

        // ---- 对比总结 ----
        Map<String, String> comparison = new LinkedHashMap<>();
        comparison.put("继承 Thread", "简单直接，但 Java 单继承限制，无法继承其他类");
        comparison.put("实现 Runnable", "推荐！接口实现，不影响继承，任务与线程分离");
        comparison.put("Callable+FutureTask", "有返回值！可抛异常，适合需要结果的异步任务");
        result.put("三种方式对比", comparison);

        result.put("核心区别",
                "Thread = 线程本身 | Runnable = 线程要执行的任务 | Callable = 有返回值的任务");

        return result;
    }

    // ==================== 2. 线程生命周期 ====================

    /**
     * 线程生命周期 6 种状态
     *
     * NEW          → start() →
     * RUNNABLE     → synchronized 获取锁失败 → BLOCKED
     *              → wait()/join() → WAITING
     *              → sleep(n)/wait(n) → TIMED_WAITING
     *              → run() 结束 → TERMINATED
     */
    public Map<String, Object> threadLifecycle() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- 演示每种状态 ----
        Object lock = new Object();

        // 1. NEW 状态
        Thread newThread = new Thread(() -> {}, "state-demo");
        result.put("1. NEW（新建）", "new Thread() 之后，start() 之前 → 状态: " + newThread.getState());

        // 2. RUNNABLE 状态
        Thread runnableThread = new Thread(() -> {
            // 空循环，保持 RUNNABLE
            for (int i = 0; i < 1_000_000; i++) {}
        }, "runnable-demo");
        runnableThread.start();
        TimeUnit.MILLISECONDS.sleep(1);
        result.put("2. RUNNABLE（可运行）", "start() 之后 → 状态: " + runnableThread.getState());
        runnableThread.join();

        // 3. TIMED_WAITING 状态
        Thread sleepingThread = new Thread(() -> {
            try { TimeUnit.SECONDS.sleep(10); } catch (InterruptedException e) { }
        }, "sleep-demo");
        sleepingThread.start();
        TimeUnit.MILLISECONDS.sleep(10);
        result.put("3. TIMED_WAITING（限时等待）", "sleep(10s) 中 → 状态: " + sleepingThread.getState());
        sleepingThread.interrupt();
        sleepingThread.join();

        // 4. WAITING 状态
        Thread waitingThread = new Thread(() -> {
            synchronized (lock) {
                try { lock.wait(); } catch (InterruptedException e) { }
            }
        }, "waiting-demo");
        waitingThread.start();
        TimeUnit.MILLISECONDS.sleep(10);
        result.put("4. WAITING（无限等待）", "wait() 中 → 状态: " + waitingThread.getState());
        synchronized (lock) { lock.notifyAll(); }
        waitingThread.join();

        // 5. BLOCKED 状态
        Thread blocker = new Thread(() -> {
            synchronized (lock) {
                try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException e) { }
            }
        }, "blocker");
        Thread blocked = new Thread(() -> {
            synchronized (lock) { /* 获取锁 */ }
        }, "blocked");
        blocker.start();
        TimeUnit.MILLISECONDS.sleep(10);
        blocked.start();
        TimeUnit.MILLISECONDS.sleep(10);
        result.put("5. BLOCKED（阻塞）", "等待 synchronized 锁 → 状态: " + blocked.getState());
        blocker.join();
        blocked.join();

        // 6. TERMINATED 状态
        Thread terminated = new Thread(() -> {}, "terminated-demo");
        terminated.start();
        terminated.join();
        result.put("6. TERMINATED（终止）", "run() 执行完毕 → 状态: " + terminated.getState());

        // ---- 状态流转图 ----
        result.put("状态流转图", """
                NEW ──start()──→ RUNNABLE ──run()结束──→ TERMINATED
                                  │    ↑
                    synchronized  │    │ 获得锁
                    获取锁失败    ↓    │
                               BLOCKED
                                  │
                    wait()/join() │    │ notify()/目标线程结束
                                  ↓    │
                               WAITING ←──→ TIMED_WAITING
                                              (sleep(n)/wait(n))
                """);

        return result;
    }

    // ==================== 3. 线程控制 ====================

    /**
     * 线程控制：sleep / join / interrupt / 守护线程
     */
    public Map<String, Object> threadControl() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- sleep：让出 CPU，不释放锁 ----
        long sleepStart = System.currentTimeMillis();
        Thread.sleep(100);
        result.put("sleep(100ms)", "实际耗时: " + (System.currentTimeMillis() - sleepStart) + "ms" +
                " | 让出 CPU 时间片，不释放锁");

        // ---- join：等待目标线程结束 ----
        Thread joinTarget = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) { }
        }, "join-target");
        joinTarget.start();
        long joinStart = System.currentTimeMillis();
        joinTarget.join(); // 主线程等待 joinTarget 结束
        result.put("join()", "等待子线程结束，耗时: " + (System.currentTimeMillis() - joinStart) + "ms");

        // ---- interrupt：中断机制 ----
        Thread interruptTarget = new Thread(() -> {
            try {
                Thread.sleep(5000); // 长睡眠
            } catch (InterruptedException e) {
                log.info("线程被中断，清理资源...");
            }
        }, "interrupt-target");
        interruptTarget.start();
        TimeUnit.MILLISECONDS.sleep(10);
        interruptTarget.interrupt(); // 发送中断信号
        interruptTarget.join(1000);
        result.put("interrupt()", "发送中断信号 → sleep 抛出 InterruptedException → 线程退出");

        // ---- isInterrupted vs interrupted ----
        Map<String, String> interruptCompare = new LinkedHashMap<>();
        interruptCompare.put("isInterrupted()", "实例方法，查询中断状态，不清除标志");
        interruptCompare.put("Thread.interrupted()", "静态方法，查询并清除中断标志（一次性）");
        result.put("中断方法对比", interruptCompare);

        // ---- 守护线程 ----
        Thread daemon = new Thread(() -> {
            while (true) { /* 后台守护 */ }
        }, "daemon-thread");
        daemon.setDaemon(true); // 设为守护线程
        result.put("守护线程（Daemon）",
                "setDaemon(true) → JVM 退出时自动终止 | 典型：GC 线程、监控线程");
        result.put("用户线程 vs 守护线程",
                "用户线程全部结束 → JVM 退出 → 守护线程被强制终止");

        // ---- 注意事项 ----
        result.put("⚠️ 注意事项", """
                1. stop() 已废弃 — 强制终止线程，资源不释放，可能导致数据不一致
                2. suspend()/resume() 已废弃 — 容易导致死锁
                3. 正确终止线程 — interrupt() + 检查中断标志 / volatile 标志位
                4. sleep 不释放锁 — sleep 期间其他线程无法获取该锁
                5. join 底层是 wait — join() 内部调用 wait(0)，目标线程结束时自动 notifyAll
                """);

        return result;
    }
}
