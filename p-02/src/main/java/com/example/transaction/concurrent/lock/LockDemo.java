package com.example.transaction.concurrent.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * 锁机制 — 由浅入深第八站
 *
 * 知识点：
 * 1. ReentrantLock — 可重入锁（synchronized 的增强版）
 * 2. ReadWriteLock — 读写锁（读共享写独占）
 * 3. StampedLock — 乐观读锁（JDK 8 新增）
 */
@Slf4j
@Component
public class LockDemo {

    private final ExecutorService pool = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(50)
    );

    // ==================== 1. ReentrantLock ====================

    /**
     * ReentrantLock — synchronized 的增强版
     */
    public Map<String, Object> reentrantLock() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- 1. 基本用法 ----
        ReentrantLock lock = new ReentrantLock();
        int[] counter = {0};

        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    lock.lock();
                    try {
                        counter[0]++;
                    } finally {
                        lock.unlock(); // 必须在 finally 中释放！
                    }
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        result.put("1. 基本用法", "10 线程 × 1000 次 = " + counter[0] + "（正确: 10000）");

        // ---- 2. tryLock（非阻塞）----
        ReentrantLock tryLock = new ReentrantLock();
        List<String> tryResults = new ArrayList<>();

        // 线程1 先获取锁
        Thread holder = new Thread(() -> {
            tryLock.lock();
            try {
                Thread.sleep(200); // 持锁 200ms
            } catch (InterruptedException e) { } finally {
                tryLock.unlock();
            }
        });
        holder.start();
        Thread.sleep(10);

        // 线程2 tryLock — 非阻塞
        boolean acquired = tryLock.tryLock();
        tryResults.add("tryLock()（无超时）: " + acquired + " — 锁被占用，立即返回 false");

        // 线程3 tryLock(timeout) — 超时等待
        boolean acquiredWithTimeout = tryLock.tryLock(300, TimeUnit.MILLISECONDS);
        tryResults.add("tryLock(300ms): " + acquiredWithTimeout + " — 等待 300ms 后获取到锁");

        holder.join();
        result.put("2. tryLock（非阻塞）", tryResults);

        // ---- 3. 可中断锁 ----
        ReentrantLock interruptLock = new ReentrantLock();
        interruptLock.lock(); // 主线程持有锁

        Thread interruptThread = new Thread(() -> {
            try {
                interruptLock.lockInterruptibly(); // 可中断的获取锁
            } catch (InterruptedException e) {
                log.info("线程被中断，未获取到锁");
            }
        });
        interruptThread.start();
        Thread.sleep(10);
        interruptThread.interrupt(); // 中断等待
        interruptThread.join(1000);
        interruptLock.unlock();

        result.put("3. 可中断锁", "lockInterruptibly() — 等待锁时可被 interrupt() 中断");

        // ---- 4. 公平锁 vs 非公平锁 ----
        Map<String, String> fairCompare = new LinkedHashMap<>();
        fairCompare.put("非公平锁（默认）", "new ReentrantLock(false) | 允许插队 | 吞吐量高 | 可能饥饿");
        fairCompare.put("公平锁", "new ReentrantLock(true) | FIFO 排队 | 吞吐量低 | 不会饥饿");
        result.put("4. 公平 vs 非公平", fairCompare);

        // ---- synchronized vs ReentrantLock ----
        Map<String, String> vsCompare = new LinkedHashMap<>();
        vsCompare.put("synchronized", "JVM 内置 | 自动释放 | 不可中断 | 非公平 | 简单");
        vsCompare.put("ReentrantLock", "JDK API | 手动释放（finally） | 可中断 | 可公平 | 灵活");
        result.put("synchronized vs ReentrantLock", vsCompare);

        result.put("✅ 使用建议", """
                优先用 synchronized（简单、JVM 优化）
                需要以下功能时用 ReentrantLock：
                1. tryLock（非阻塞/超时）
                2. lockInterruptibly（可中断）
                3. 公平锁
                4. 多个 Condition（精确唤醒）
                """);

        return result;
    }

    // ==================== 2. ReadWriteLock ====================

    /**
     * ReadWriteLock — 读写锁（读共享写独占）
     *
     * 场景：读多写少（缓存、配置中心）
     * 原理：多个读可以同时进行，写时独占
     */
    public Map<String, Object> readWriteLock() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        Lock readLock = rwLock.readLock();
        Lock writeLock = rwLock.writeLock();

        // 共享数据
        int[] sharedData = {0};
        List<String> accessLog = new ArrayList<>();

        // ---- 演示读读并行 ----
        long start = System.currentTimeMillis();

        // 3 个读线程并行
        for (int i = 1; i <= 3; i++) {
            final int readerId = i;
            pool.execute(() -> {
                readLock.lock();
                try {
                    Thread.sleep(100);
                    synchronized (accessLog) {
                        accessLog.add("读者" + readerId + " 读取: " + sharedData[0]);
                    }
                } catch (InterruptedException e) { } finally {
                    readLock.unlock();
                }
            });
        }

        Thread.sleep(200);
        long readCost = System.currentTimeMillis() - start;
        result.put("读读并行", "3 个读者同时读取 | 耗时: " + readCost + "ms（≈100ms，而非 300ms）");

        // ---- 演示写互斥 ----
        start = System.currentTimeMillis();

        for (int i = 1; i <= 3; i++) {
            final int writerId = i;
            pool.execute(() -> {
                writeLock.lock();
                try {
                    sharedData[0]++;
                    synchronized (accessLog) {
                        accessLog.add("写者" + writerId + " 写入: " + sharedData[0]);
                    }
                    Thread.sleep(100);
                } catch (InterruptedException e) { } finally {
                    writeLock.unlock();
                }
            });
        }

        Thread.sleep(500);
        long writeCost = System.currentTimeMillis() - start;
        result.put("写写互斥", "3 个写者串行写入 | 耗时: " + writeCost + "ms（≈300ms）");

        result.put("执行日志", accessLog);
        result.put("ReadWriteLock 规则", """
                读-读：✅ 并行（共享锁）
                读-写：❌ 互斥（写锁等待所有读锁释放）
                写-写：❌ 互斥（独占锁）
                """);
        result.put("典型场景", """
                缓存更新 — 多个读线程同时读缓存，写线程更新时独占
                配置中心 — 多个服务读配置，管理员修改配置时独占
                数据库连接池 — 多个线程读连接状态，创建/销毁连接时独占
                """);

        return result;
    }

    // ==================== 3. StampedLock ====================

    /**
     * StampedLock — 乐观读锁（JDK 8 新增）
     *
     * 比 ReadWriteLock 更进一步：读多写少时，先乐观读（不加锁），发现数据被改了再转悲观读
     */
    public Map<String, Object> stampedLock() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        StampedLock stampedLock = new StampedLock();

        // 共享数据
        double[] point = {1.0, 2.0}; // x, y 坐标
        List<String> accessLog = new ArrayList<>();

        // ---- 写者：移动坐标 ----
        pool.execute(() -> {
            for (int i = 0; i < 3; i++) {
                long stamp = stampedLock.writeLock();
                try {
                    point[0] += 1.0;
                    point[1] += 1.0;
                    synchronized (accessLog) {
                        accessLog.add("写者: 移动到 (" + point[0] + ", " + point[1] + ")");
                    }
                    Thread.sleep(50);
                } catch (InterruptedException e) { } finally {
                    stampedLock.unlockWrite(stamp);
                }
                try { Thread.sleep(100); } catch (InterruptedException e) { }
            }
        });

        // ---- 读者：读取坐标（乐观读）----
        for (int i = 0; i < 5; i++) {
            final int readerId = i;
            pool.execute(() -> {
                // 1. 乐观读（不加锁，高性能）
                long stamp = stampedLock.tryOptimisticRead();
                double x = point[0];
                double y = point[1];

                // 2. 验证：期间有没有写操作
                if (!stampedLock.validate(stamp)) {
                    // 3. 有写操作 → 升级为悲观读锁
                    synchronized (accessLog) {
                        accessLog.add("读者" + readerId + ": 乐观读失败，升级悲观读");
                    }
                    stamp = stampedLock.readLock();
                    try {
                        x = point[0];
                        y = point[1];
                    } finally {
                        stampedLock.unlockRead(stamp);
                    }
                } else {
                    synchronized (accessLog) {
                        accessLog.add("读者" + readerId + ": 乐观读成功 (" + x + ", " + y + ")");
                    }
                }
            });
        }

        Thread.sleep(1000);
        result.put("StampedLock 三种模式", """
                1. 乐观读（tryOptimisticRead）— 不加锁，验证时检查是否有写操作
                2. 悲观读（readLock）— 和 ReadWriteLock 的读锁一样
                3. 写锁（writeLock）— 和 ReadWriteLock 的写锁一样
                """);
        result.put("执行日志", accessLog);
        result.put("乐观读原理", """
                tryOptimisticRead() → 返回一个 stamp（版本号）
                执行期间不加锁，不阻塞写者
                validate(stamp) → 检查 stamp 是否还有效
                  有效 → 数据没被修改，直接使用（零开销！）
                  无效 → 数据被修改了，升级为悲观读锁重新读取
                """);
        result.put("三种锁对比", """
                synchronized     — 简单，无读写区分
                ReadWriteLock    — 读共享写独占，读写互斥
                StampedLock      — 乐观读 + 悲观读 + 写锁，读多写少性能最优
                """);
        result.put("✅ 使用建议", """
                读 >> 写（99% 读 1% 写）→ StampedLock 乐观读
                读 > 写（80% 读 20% 写）→ ReadWriteLock
                读 ≈ 写               → ReentrantLock 或 synchronized
                """);

        return result;
    }
}
