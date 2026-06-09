package com.example.transaction.concurrent.controller;

import com.example.transaction.concurrent.atomic.AtomicDemo;
import com.example.transaction.concurrent.basic.SynchronizedDemo;
import com.example.transaction.concurrent.basic.ThreadBasicDemo;
import com.example.transaction.concurrent.completable.CompletableFutureDemo;
import com.example.transaction.concurrent.completable.CompletableFuturePracticeDemo;
import com.example.transaction.concurrent.lock.LockDemo;
import com.example.transaction.concurrent.pool.ThreadPoolDemo;
import com.example.transaction.concurrent.pool.ThreadPoolPracticeDemo;
import com.example.transaction.concurrent.utility.ConcurrentUtilityDemo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 并发编程模块 — REST 接口（约 30 个端点）
 *
 * 学习路径（由浅入深）：
 * 1. /basic/*         — 线程基础（创建、生命周期、控制）
 * 2. /sync/*          — synchronized（对象锁、wait/notify、生产者消费者）
 * 3. /pool/*          — 线程池（7参数、内置池、拒绝策略、监控、调优）
 * 4. /completable/*   — CompletableFuture（创建、链式、组合、异常、实战）
 * 5. /utility/*       — 并发工具（CountDownLatch、CyclicBarrier、Semaphore）
 * 6. /lock/*          — 锁机制（ReentrantLock、ReadWriteLock、StampedLock）
 * 7. /atomic/*        — 原子类与并发集合（CAS、LongAdder、ConcurrentHashMap）
 */
@Slf4j
@RestController
@RequestMapping("/api/concurrent")
@RequiredArgsConstructor
public class ConcurrentDemoController {

    // ---- 第一层：线程基础 ----
    private final ThreadBasicDemo threadBasicDemo;
    private final SynchronizedDemo synchronizedDemo;

    // ---- 第二层：线程池 ----
    private final ThreadPoolDemo threadPoolDemo;
    private final ThreadPoolPracticeDemo threadPoolPracticeDemo;

    // ---- 第三层：CompletableFuture ----
    private final CompletableFutureDemo completableFutureDemo;
    private final CompletableFuturePracticeDemo completableFuturePracticeDemo;

    // ---- 第四层：并发工具 ----
    private final ConcurrentUtilityDemo concurrentUtilityDemo;

    // ---- 第五层：锁 ----
    private final LockDemo lockDemo;

    // ---- 第六层：原子类 ----
    private final AtomicDemo atomicDemo;

    // ==================== 第一层：线程基础 ====================

    /** 线程创建 3 种方式 */
    @GetMapping("/basic/creation")
    public Map<String, Object> threadCreation() throws Exception {
        return threadBasicDemo.threadCreation();
    }

    /** 线程生命周期 6 种状态 */
    @GetMapping("/basic/lifecycle")
    public Map<String, Object> threadLifecycle() throws Exception {
        return threadBasicDemo.threadLifecycle();
    }

    /** 线程控制（sleep/join/interrupt） */
    @GetMapping("/basic/control")
    public Map<String, Object> threadControl() throws Exception {
        return threadBasicDemo.threadControl();
    }

    // ==================== synchronized ====================

    /** synchronized 基础（对象锁/类锁/可重入） */
    @GetMapping("/sync/basics")
    public Map<String, Object> synchronizedBasics() throws Exception {
        return synchronizedDemo.synchronizedBasics();
    }

    /** wait/notify 机制 */
    @GetMapping("/sync/wait-notify")
    public Map<String, Object> waitNotify() throws Exception {
        return synchronizedDemo.waitNotify();
    }

    /** 生产者消费者模式 */
    @GetMapping("/sync/producer-consumer")
    public Map<String, Object> producerConsumer() throws Exception {
        return synchronizedDemo.producerConsumer();
    }

    // ==================== 第二层：线程池 ====================

    /** ThreadPoolExecutor 7 大参数 */
    @GetMapping("/pool/parameters")
    public Map<String, Object> poolParameters() {
        return threadPoolDemo.sevenParameters();
    }

    /** 4 种内置线程池 */
    @GetMapping("/pool/built-in")
    public Map<String, Object> builtInPools() {
        return threadPoolDemo.fourBuiltInPools();
    }

    /** 4 种拒绝策略 */
    @GetMapping("/pool/rejection")
    public Map<String, Object> rejectionPolicies() throws Exception {
        return threadPoolDemo.rejectionPolicies();
    }

    /** 线程池运行时监控 */
    @GetMapping("/pool/monitoring")
    public Map<String, Object> poolMonitoring() throws Exception {
        return threadPoolDemo.poolMonitoring();
    }

    /** 线程池大小选择 */
    @GetMapping("/pool/sizing")
    public Map<String, Object> poolSizing() {
        return threadPoolPracticeDemo.poolSizing();
    }

    /** 优雅关闭 */
    @GetMapping("/pool/shutdown")
    public Map<String, Object> poolShutdown() throws Exception {
        return threadPoolPracticeDemo.gracefulShutdown();
    }

    /** 任务编排（submit/invokeAll/invokeAny） */
    @GetMapping("/pool/orchestration")
    public Map<String, Object> taskOrchestration() throws Exception {
        return threadPoolPracticeDemo.taskOrchestration();
    }

    // ==================== 第三层：CompletableFuture ====================

    /** CompletableFuture 创建方式 */
    @GetMapping("/completable/creation")
    public Map<String, Object> cfCreation() throws Exception {
        return completableFutureDemo.creation();
    }

    /** 链式调用（thenApply/thenAccept/thenRun/thenCompose） */
    @GetMapping("/completable/chaining")
    public Map<String, Object> cfChaining() throws Exception {
        return completableFutureDemo.chaining();
    }

    /** 组合编排（thenCombine/allOf/anyOf） */
    @GetMapping("/completable/combination")
    public Map<String, Object> cfCombination() throws Exception {
        return completableFutureDemo.combination();
    }

    /** 异常处理（exceptionally/handle/timeout） */
    @GetMapping("/completable/exception")
    public Map<String, Object> cfException() throws Exception {
        return completableFutureDemo.exceptionHandling();
    }

    /** 并行查询实战 */
    @GetMapping("/completable/parallel-query")
    public Map<String, Object> parallelQuery() throws Exception {
        return completableFuturePracticeDemo.parallelQuery();
    }

    /** 超时控制 + 重试 */
    @GetMapping("/completable/timeout-retry")
    public Map<String, Object> timeoutRetry() throws Exception {
        return completableFuturePracticeDemo.timeoutAndRetry();
    }

    /** 秒杀流程异步化 */
    @GetMapping("/completable/seckill-async")
    public Map<String, Object> seckillAsync() throws Exception {
        return completableFuturePracticeDemo.seckillAsync();
    }

    // ==================== 第四层：并发工具 ====================

    /** CountDownLatch（倒计数器） */
    @GetMapping("/utility/countdown-latch")
    public Map<String, Object> countdownLatch() throws Exception {
        return concurrentUtilityDemo.countDownLatch();
    }

    /** CyclicBarrier（循环屏障） */
    @GetMapping("/utility/cyclic-barrier")
    public Map<String, Object> cyclicBarrier() throws Exception {
        return concurrentUtilityDemo.cyclicBarrier();
    }

    /** Semaphore（信号量） */
    @GetMapping("/utility/semaphore")
    public Map<String, Object> semaphore() throws Exception {
        return concurrentUtilityDemo.semaphore();
    }

    // ==================== 第五层：锁 ====================

    /** ReentrantLock（可重入锁） */
    @GetMapping("/lock/reentrant")
    public Map<String, Object> reentrantLock() throws Exception {
        return lockDemo.reentrantLock();
    }

    /** ReadWriteLock（读写锁） */
    @GetMapping("/lock/read-write")
    public Map<String, Object> readWriteLock() throws Exception {
        return lockDemo.readWriteLock();
    }

    /** StampedLock（乐观读锁） */
    @GetMapping("/lock/stamped")
    public Map<String, Object> stampedLock() throws Exception {
        return lockDemo.stampedLock();
    }

    // ==================== 第六层：原子类 ====================

    /** 原子类基础（CAS/ABA） */
    @GetMapping("/atomic/basics")
    public Map<String, Object> atomicBasics() throws Exception {
        return atomicDemo.atomicBasics();
    }

    /** LongAdder 高并发累加 */
    @GetMapping("/atomic/long-adder")
    public Map<String, Object> longAdder() throws Exception {
        return atomicDemo.longAdder();
    }

    /** ConcurrentHashMap */
    @GetMapping("/atomic/concurrent-hashmap")
    public Map<String, Object> concurrentHashMap() throws Exception {
        return atomicDemo.concurrentHashMap();
    }

    // ==================== 接口索引 ====================

    /** 全部接口索引 */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("模块", "并发编程（由浅入深）");
        result.put("学习路径", """
                第一层：线程基础（/basic/* + /sync/*）→ 理解线程创建、同步、通信
                第二层：线程池（/pool/*）→ 掌握 ThreadPoolExecutor 7 参数、拒绝策略、调优
                第三层：CompletableFuture（/completable/*）→ 异步编程、并行查询、秒杀异步化
                第四层：并发工具（/utility/*）→ CountDownLatch、CyclicBarrier、Semaphore
                第五层：锁机制（/lock/*）→ ReentrantLock、ReadWriteLock、StampedLock
                第六层：原子类（/atomic/*）→ CAS、LongAdder、ConcurrentHashMap
                """);

        // 第一层
        Map<String, String> basic = new LinkedHashMap<>();
        basic.put("/basic/creation", "线程创建 3 种方式（Thread/Runnable/Callable）");
        basic.put("/basic/lifecycle", "线程生命周期 6 种状态");
        basic.put("/basic/control", "线程控制（sleep/join/interrupt/守护线程）");
        basic.put("/sync/basics", "synchronized 基础（对象锁/类锁/可重入）");
        basic.put("/sync/wait-notify", "wait/notify 机制");
        basic.put("/sync/producer-consumer", "生产者消费者模式");
        result.put("第一层：线程基础", basic);

        // 第二层
        Map<String, String> pool = new LinkedHashMap<>();
        pool.put("/pool/parameters", "ThreadPoolExecutor 7 大参数详解");
        pool.put("/pool/built-in", "4 种内置线程池 + OOM 隐患");
        pool.put("/pool/rejection", "4 种拒绝策略对比");
        pool.put("/pool/monitoring", "运行时监控指标");
        pool.put("/pool/sizing", "池大小选择（CPU密集型/IO密集型）");
        pool.put("/pool/shutdown", "优雅关闭（shutdown/shutdownNow）");
        pool.put("/pool/orchestration", "任务编排（submit/invokeAll/invokeAny）");
        result.put("第二层：线程池", pool);

        // 第三层
        Map<String, String> cf = new LinkedHashMap<>();
        cf.put("/completable/creation", "创建方式（supplyAsync/runAsync）");
        cf.put("/completable/chaining", "链式调用（thenApply/thenAccept/thenRun/thenCompose）");
        cf.put("/completable/combination", "组合编排（thenCombine/allOf/anyOf）");
        cf.put("/completable/exception", "异常处理（exceptionally/handle/timeout）");
        cf.put("/completable/parallel-query", "并行查询实战（多数据源聚合）");
        cf.put("/completable/timeout-retry", "超时控制 + 指数退避重试");
        cf.put("/completable/seckill-async", "秒杀流程异步化改造");
        result.put("第三层：CompletableFuture", cf);

        // 第四层
        Map<String, String> utility = new LinkedHashMap<>();
        utility.put("/utility/countdown-latch", "CountDownLatch（倒计数器）");
        utility.put("/utility/cyclic-barrier", "CyclicBarrier（循环屏障）");
        utility.put("/utility/semaphore", "Semaphore（信号量）");
        result.put("第四层：并发工具", utility);

        // 第五层
        Map<String, String> lock = new LinkedHashMap<>();
        lock.put("/lock/reentrant", "ReentrantLock（可重入锁/tryLock/公平锁）");
        lock.put("/lock/read-write", "ReadWriteLock（读写锁）");
        lock.put("/lock/stamped", "StampedLock（乐观读锁）");
        result.put("第五层：锁机制", lock);

        // 第六层
        Map<String, String> atomic = new LinkedHashMap<>();
        atomic.put("/atomic/basics", "原子类基础（CAS/ABA/AtomicStampedReference）");
        atomic.put("/atomic/long-adder", "LongAdder 高并发累加");
        atomic.put("/atomic/concurrent-hashmap", "ConcurrentHashMap（JDK7→8 演进/新 API）");
        result.put("第六层：原子类与并发集合", atomic);

        return result;
    }
}
