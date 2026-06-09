package com.example.transaction.concurrent.basic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * synchronized 关键字 — 由浅入深第二站
 *
 * 知识点：
 * 1. 对象锁 vs 类锁
 * 2. synchronized 方法 vs 代码块
 * 3. 可重入性
 * 4. wait/notify 机制
 * 5. 生产者消费者模式
 */
@Slf4j
@Component
public class SynchronizedDemo {

    // 共享计数器（用于演示线程安全问题）
    private int unsafeCounter = 0;
    private int safeCounter = 0;
    private final Object counterLock = new Object();

    // ==================== 1. synchronized 基础 ====================

    /**
     * synchronized 三种用法 + 可重入性
     */
    public Map<String, Object> synchronizedBasics() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- 1. 演示线程安全问题 ----
        unsafeCounter = 0;
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) unsafeCounter++;
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        result.put("1. 无锁（线程不安全）", "10 线程 × 1000 次 = 期望 10000，实际: " + unsafeCounter);

        // ---- 2. 对象锁 — synchronized 代码块 ----
        safeCounter = 0;
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    synchronized (counterLock) {
                        safeCounter++;
                    }
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        result.put("2. 对象锁（代码块）", "synchronized(lock) { counter++ } → 结果: " + safeCounter);

        // ---- 3. 对象锁 — synchronized 方法 ----
        result.put("3. 对象锁（实例方法）",
                "public synchronized void add() → 锁的是 this 对象");

        // ---- 4. 类锁 ----
        result.put("4. 类锁",
                "public static synchronized void add() → 锁的是 Class 对象（SynchronizedDemo.class）");

        // ---- 5. 可重入性 ----
        result.put("5. 可重入性",
                "同一线程可以重复获取同一把锁 → synchronized 是可重入锁 → 避免自己死锁");

        // ---- 对比总结 ----
        Map<String, String> lockTypes = new LinkedHashMap<>();
        lockTypes.put("synchronized(this) {}", "对象锁 — 同一实例互斥");
        lockTypes.put("synchronized(类名.class) {}", "类锁 — 所有实例互斥");
        lockTypes.put("public synchronized void m()", "对象锁 — 锁 this");
        lockTypes.put("public static synchronized void m()", "类锁 — 锁 Class");
        result.put("锁类型速查", lockTypes);

        result.put("⚠️ 最佳实践", """
                1. 锁的范围要最小 — 只锁共享变量的操作，不锁整个方法
                2. 避免锁字符串常量 — intern() 导致不同代码段锁同一对象
                3. 避免锁 Integer/Long — 值缓存导致锁对象相同
                4. 优先用代码块 — 粒度更细，性能更好
                """);

        return result;
    }

    // ==================== 2. wait/notify ====================

    /**
     * wait/notify 机制 — 线程间通信
     */
    public Map<String, Object> waitNotify() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        Object monitor = new Object();
        List<String> messages = new ArrayList<>();
        boolean[] dataReady = {false};

        // 生产者
        Thread producer = new Thread(() -> {
            synchronized (monitor) {
                try { Thread.sleep(50); } catch (InterruptedException e) { }
                messages.add("数据已生产");
                dataReady[0] = true;
                monitor.notifyAll(); // 通知消费者
                log.info("生产者: 已通知");
            }
        }, "producer");

        // 消费者
        Thread consumer = new Thread(() -> {
            synchronized (monitor) {
                while (!dataReady[0]) { // 用 while 防止虚假唤醒
                    try {
                        monitor.wait(); // 释放锁并等待
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                log.info("消费者: 收到数据 → {}", messages.get(0));
            }
        }, "consumer");

        consumer.start();
        producer.start();
        producer.join();
        consumer.join();

        result.put("wait()", "释放锁 → 进入 WAITING 状态 → 被 notify 唤醒后重新竞争锁");
        result.put("notify()", "随机唤醒一个等待线程 | notifyAll() 唤醒所有等待线程");
        result.put("⚠️ 虚假唤醒", """
                wait() 可能在没有 notify 的情况下被唤醒（OS 层面）
                正确写法：while (!condition) { wait(); }  ← 用 while，不用 if
                """);
        result.put("⚠️ 必须在 synchronized 内", """
                wait/notify 必须在 synchronized 块中调用
                原因：调用 wait 前需要先获取锁，wait 内部会释放锁
                不在 synchronized 中调用 → 抛 IllegalMonitorStateException
                """);

        result.put("执行结果", messages);
        return result;
    }

    // ==================== 3. 生产者消费者 ====================

    /**
     * 经典生产者消费者 — 有界阻塞队列
     */
    public Map<String, Object> producerConsumer() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // 有界缓冲区
        int capacity = 5;
        List<Integer> buffer = new ArrayList<>();
        Object bufferLock = new Object();
        int[] producedCount = {0};
        int[] consumedCount = {0};
        List<String> log2 = new ArrayList<>();

        // 生产者（生产 10 个）
        Thread producer = new Thread(() -> {
            for (int i = 1; i <= 10; i++) {
                synchronized (bufferLock) {
                    while (buffer.size() == capacity) { // 缓冲区满 → 等待
                        try { bufferLock.wait(); } catch (InterruptedException e) { }
                    }
                    buffer.add(i);
                    producedCount[0]++;
                    log2.add("生产: " + i + " | 缓冲区: " + buffer.size());
                    bufferLock.notifyAll(); // 通知消费者
                }
                try { Thread.sleep(10); } catch (InterruptedException e) { }
            }
        }, "bounded-producer");

        // 消费者（消费 10 个）
        Thread consumer = new Thread(() -> {
            for (int i = 1; i <= 10; i++) {
                synchronized (bufferLock) {
                    while (buffer.isEmpty()) { // 缓冲区空 → 等待
                        try { bufferLock.wait(); } catch (InterruptedException e) { }
                    }
                    int item = buffer.remove(0);
                    consumedCount[0]++;
                    log2.add("消费: " + item + " | 缓冲区: " + buffer.size());
                    bufferLock.notifyAll(); // 通知生产者
                }
                try { Thread.sleep(15); } catch (InterruptedException e) { }
            }
        }, "bounded-consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        result.put("模型", "有界缓冲区（容量=" + capacity + "）— 生产者消费者共享");
        result.put("生产总数", producedCount[0]);
        result.put("消费总数", consumedCount[0]);
        result.put("执行日志（前10条）", log2.subList(0, Math.min(10, log2.size())));
        result.put("核心要点", """
                1. 缓冲区满 → 生产者 wait() → 消费者消费后 notifyAll()
                2. 缓冲区空 → 消费者 wait() → 生产者生产后 notifyAll()
                3. while 循环检查条件（防虚假唤醒）
                4. notifyAll() 优于 notify()（避免信号丢失）
                """);
        result.put("实际开发替代方案", """
                BlockingQueue — JDK 提供的阻塞队列，内部已封装 wait/notify
                ├── ArrayBlockingQueue — 有界（推荐）
                ├── LinkedBlockingQueue — 可选有界
                └── SynchronousQueue — 无缓冲（直接传递）
                """);

        return result;
    }
}
