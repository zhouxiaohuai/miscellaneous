package com.example.transaction.concurrent.atomic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * 原子类与并发集合 — 由浅入深第九站
 *
 * 知识点：
 * 1. 原子类（AtomicInteger / AtomicReference / CAS 原理 / ABA 问题）
 * 2. LongAdder（高并发累加，分段思想）
 * 3. ConcurrentHashMap（JDK 7 → JDK 8 演进）
 */
@Slf4j
@Component
public class AtomicDemo {

    private final ExecutorService pool = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(50)
    );

    // ==================== 1. 原子类基础 ====================

    /**
     * 原子类 — CAS 原理 + ABA 问题
     */
    public Map<String, Object> atomicBasics() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- AtomicInteger 基本操作 ----
        AtomicInteger atomicInt = new AtomicInteger(0);

        // 10 线程各递增 10000 次
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10000; j++) {
                    atomicInt.incrementAndGet();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        result.put("AtomicInteger", "10 线程 × 10000 = " + atomicInt.get() + "（正确: 100000）");

        // 常用方法
        Map<String, String> methods = new LinkedHashMap<>();
        methods.put("incrementAndGet()", "先 +1 再返回（++i）");
        methods.put("getAndIncrement()", "先返回再 +1（i++）");
        methods.put("decrementAndGet()", "先 -1 再返回（--i）");
        methods.put("addAndGet(n)", "先 +n 再返回");
        methods.put("compareAndSet(expect, update)", "CAS：当前值 == expect 则更新为 update");
        methods.put("updateAndGet(fn)", "函数式更新（JDK 8+）");
        result.put("常用方法", methods);

        // ---- CAS 原理 ----
        result.put("CAS 原理", """
                Compare-And-Swap（比较并交换）
                内存值 V、预期值 A、新值 B
                if (V == A) { V = B; return true; }
                else { return false; }  → 重试

                CPU 指令级支持（cmpxchg），无锁，高性能
                """);

        // ---- ABA 问题 ----
        result.put("ABA 问题", """
                线程1 读到 A
                线程2 把 A 改成 B
                线程2 再把 B 改回 A
                线程1 CAS 成功 — 但实际上数据已经被改过了！

                解决方案：
                1. AtomicStampedReference — 加版本号（stamp）
                2. AtomicMarkableReference — 加布尔标记
                """);

        // ---- AtomicStampedReference 演示 ----
        AtomicStampedReference<String> stampedRef = new AtomicStampedReference<>("A", 0);

        // 线程1：读取
        int[] stampHolder = {stampedRef.getStamp()};
        String value1 = stampedRef.getReference();

        // 线程2：A → B → A
        pool.submit(() -> {
            stampedRef.compareAndSet("A", "B", 0, 1);
            stampedRef.compareAndSet("B", "A", 1, 2);
        }).get(1, TimeUnit.SECONDS);

        // 线程1：CAS（版本号不匹配，失败）
        boolean casResult = stampedRef.compareAndSet("A", "C", stampHolder[0], stampHolder[0] + 1);
        result.put("AtomicStampedReference", "CAS 结果: " + casResult + "（版本号已被线程2修改，CAS 失败 ✓）");

        return result;
    }

    // ==================== 2. LongAdder ====================

    /**
     * LongAdder vs AtomicLong — 高并发累加性能对比
     *
     * AtomicLong：所有线程竞争同一个变量 → CAS 重试 → 高并发下性能差
     * LongAdder：分段累加（Cell[]），最后汇总 → 减少竞争 → 高并发下性能好
     */
    public Map<String, Object> longAdder() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        int threadCount = 10;
        int increments = 100_000;

        // ---- AtomicLong 性能测试 ----
        AtomicLong atomicLong = new AtomicLong(0);
        long start = System.currentTimeMillis();
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < increments; j++) {
                    atomicLong.incrementAndGet();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        long atomicCost = System.currentTimeMillis() - start;

        // ---- LongAdder 性能测试 ----
        LongAdder longAdder = new LongAdder();
        start = System.currentTimeMillis();
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < increments; j++) {
                    longAdder.increment();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        long adderCost = System.currentTimeMillis() - start;

        Map<String, String> perfCompare = new LinkedHashMap<>();
        perfCompare.put("AtomicLong", atomicLong.get() + " | 耗时: " + atomicCost + "ms");
        perfCompare.put("LongAdder", longAdder.sum() + " | 耗时: " + adderCost + "ms");
        perfCompare.put("性能提升", atomicCost > 0 ? (atomicCost * 100 / Math.max(adderCost, 1) - 100) + "%" : "N/A");
        result.put("性能对比（" + threadCount + " 线程 × " + increments + " 次）", perfCompare);

        result.put("LongAdder 原理", """
                base（基础值）+ Cell[]（分段数组）
                每个线程映射到一个 Cell → 减少 CAS 竞争
                sum() 时才汇总：base + ΣCell[i]
                """);
        result.put("使用建议", """
                计数器 / 统计场景（高并发写入）→ LongAdder
                需要精确值（CAS 语义）        → AtomicLong
                读多写少                      → AtomicLong
                读少写多                      → LongAdder
                """);

        return result;
    }

    // ==================== 3. ConcurrentHashMap ====================

    /**
     * ConcurrentHashMap — 线程安全的 HashMap
     */
    public Map<String, Object> concurrentHashMap() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

        // ---- 多线程并发写入 ----
        int threadCount = 10;
        int writesPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            pool.execute(() -> {
                for (int j = 0; j < writesPerThread; j++) {
                    String key = "key-" + (threadId * writesPerThread + j);
                    map.put(key, threadId * writesPerThread + j);
                }
                latch.countDown();
            });
        }
        latch.await();

        result.put("并发写入", threadCount + " 线程 × " + writesPerThread + " = " + map.size() + " 个键值对");

        // ---- JDK 8 新方法 ----
        // computeIfAbsent — 不存在时计算
        map.computeIfAbsent("computed", k -> k.length() * 100);
        result.put("computeIfAbsent()", "key 不存在时执行函数并存入 | computed = " + map.get("computed"));

        // compute — 计算新值
        map.compute("computed", (k, v) -> v + 1);
        result.put("compute()", "对已有值进行计算 | computed = " + map.get("computed"));

        // merge — 合并值
        map.merge("merged", 1, Integer::sum);
        map.merge("merged", 2, Integer::sum);
        map.merge("merged", 3, Integer::sum);
        result.put("merge()", "merge(key, value, remappingFunction) | merged = " + map.get("merged") + " (1+2+3)");

        // ---- JDK 7 vs JDK 8 架构对比 ----
        Map<String, String> archCompare = new LinkedHashMap<>();
        archCompare.put("JDK 7（Segment 分段锁）", """
                ConcurrentHashMap → Segment[] → HashEntry[]
                每个 Segment 是一把 ReentrantLock
                并发度 = Segment 数量（默认 16）
                """);
        archCompare.put("JDK 8（CAS + synchronized）", """
                ConcurrentHashMap → Node[]（同 HashMap）
                链表头节点加 synchronized 锁
                空桶 CAS 插入，非空桶 synchronized
                """);
        result.put("JDK 7 vs JDK 8", archCompare);

        result.put("常用方法", """
                put(key, value)              — 线程安全写入
                get(key)                     — 无锁读取（volatile 保证可见性）
                putIfAbsent(key, value)      — 不存在才写入
                compute(key, remappingFn)    — 计算新值
                computeIfAbsent(key, mappingFn) — 不存在时计算（常用于缓存初始化）
                merge(key, value, remappingFn) — 合并值
                forEach(action)              — 并发安全遍历
                """);
        result.put("⚠️ 注意事项", """
                1. size() 是近似值（并发环境下不精确）
                2. 不允许 null key/value（与 HashMap 不同！）
                3. compute/merge 内部加锁，不要在函数内做耗时操作
                """);

        return result;
    }
}
