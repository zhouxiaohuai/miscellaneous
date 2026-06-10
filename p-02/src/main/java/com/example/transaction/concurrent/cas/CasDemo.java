package com.example.transaction.concurrent.cas;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * CAS 原理深度解析 — 并发编程第十站
 *
 * 知识点：
 * 1. CAS 基础概念 — Compare-And-Swap 是什么
 * 2. CAS 底层原理 — CPU 指令级实现
 * 3. CAS vs synchronized — 无锁 vs 有锁
 * 4. CAS 自旋重试 — 失败了怎么办
 * 5. ABA 问题 — CAS 的经典陷阱
 * 6. CAS 实战应用 — 手写自旋锁、无锁栈
 */
@Slf4j
@Component
public class CasDemo {

    private final ExecutorService pool = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(50)
    );

    // ==================== 1. CAS 基础概念 ====================

    /**
     * CAS 基础 — 什么是 Compare-And-Swap
     *
     * 核心思想：比较并交换，CPU 指令级的原子操作
     */
    public Map<String, Object> casBasics() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- 1. CAS 三要素 ----
        result.put("CAS 三要素", """
                CAS(V, A, B)
                  V = 内存中的值（Value）
                  A = 预期的旧值（Expected）
                  B = 要设置的新值（New Value）

                伪代码：
                  if (V == A) {
                      V = B;     // 更新成功
                      return true;
                  } else {
                      return false; // 更新失败，说明被其他线程改过了
                  }
                """);

        // ---- 2. 模拟 CAS 过程 ----
        AtomicInteger sharedValue = new AtomicInteger(100);
        List<String> casLog = new ArrayList<>();

        // 线程1：CAS 从 100 → 200
        Thread t1 = new Thread(() -> {
            int expected = 100;
            int newValue = 200;
            boolean success = sharedValue.compareAndSet(expected, newValue);
            synchronized (casLog) {
                casLog.add("线程1: CAS(" + expected + " → " + newValue + ") = " + success +
                        " | 当前值: " + sharedValue.get());
            }
        });

        // 线程2：CAS 从 100 → 300（和线程1竞争）
        Thread t2 = new Thread(() -> {
            int expected = 100;
            int newValue = 300;
            boolean success = sharedValue.compareAndSet(expected, newValue);
            synchronized (casLog) {
                casLog.add("线程2: CAS(" + expected + " → " + newValue + ") = " + success +
                        " | 当前值: " + sharedValue.get());
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        result.put("CAS 竞争演示", casLog);
        result.put("结果分析", """
                两个线程同时 CAS(100 → ?)
                只有一个成功，另一个失败
                失败的线程需要重试（自旋）
                """);

        // ---- 3. CAS 的本质 ----
        result.put("CAS 本质", """
                ┌─────────────────────────────────────────────────┐
                │  CAS 不是一行 Java 代码，而是一条 CPU 指令！      │
                │                                                 │
                │  x86: cmpxchg 指令                              │
                │  ARM: ldrex/strex 指令对                        │
                │                                                 │
                │  这条指令是「原子的」— 执行期间不会被打断         │
                │  所以不需要加锁，不会有上下文切换                 │
                └─────────────────────────────────────────────────┘
                """);

        return result;
    }

    // ==================== 2. CAS 底层原理 ====================

    /**
     * CAS 底层 — CPU 指令级实现
     */
    public Map<String, Object> casInternals() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- 1. Java 到 CPU 的调用链 ----
        result.put("调用链", """
                Java 层：
                  AtomicInteger.incrementAndGet()
                      ↓
                JDK 层：
                  Unsafe.getAndAddInt()
                      ↓
                JNI 层（C++）：
                  Atomic::cmpxchg()
                      ↓
                CPU 层：
                  x86: lock cmpxchg 指令
                  （lock 前缀保证多核原子性）
                """);

        // ---- 2. Unsafe 类（CAS 的基石）----
        result.put("Unsafe 类", """
                sun.misc.Unsafe — JDK 内部 API，CAS 的底层实现

                核心方法：
                  compareAndSwapInt(obj, offset, expect, update)
                  compareAndSwapLong(obj, offset, expect, update)
                  compareAndSwapObject(obj, offset, expect, update)

                工作原理：
                  1. 获取字段在对象中的内存偏移量（offset）
                  2. 直接操作内存地址，调用 CPU CAS 指令
                  3. 如果内存值 == 预期值，原子更新为新值

                ⚠️ 为什么叫 Unsafe？
                  - 可以直接操作内存，绕过 JVM 安全检查
                  - 可以分配堆外内存（不受 GC 管理）
                  - 可以调用底层系统调用
                  - 普通开发者不应该直接使用
                """);

        // ---- 3. AtomicInteger 源码解析 ----
        result.put("AtomicInteger 源码解析", """
                public class AtomicInteger {
                    // 存储实际值，volatile 保证可见性
                    private volatile int value;

                    // 获取值在对象中的偏移量
                    private static final long valueOffset;
                    static {
                        valueOffset = Unsafe.objectFieldOffset(
                            AtomicInteger.class.getDeclaredField("value")
                        );
                    }

                    // CAS 更新
                    public final boolean compareAndSet(int expect, int update) {
                        return UNSAFE.compareAndSwapInt(this, valueOffset, expect, update);
                    }

                    // 原子递增（CAS 自旋）
                    public final int incrementAndGet() {
                        return UNSAFE.getAndAddInt(this, valueOffset, 1) + 1;
                    }
                }

                关键点：
                1. value 用 volatile 修饰 → 保证可见性
                2. valueOffset 是字段偏移量 → 定位内存地址
                3. CAS 是 CPU 指令 → 原子性
                volatile + CAS = 线程安全！
                """);

        // ---- 4. volatile 的作用 ----
        result.put("volatile 的作用", """
                CAS 只保证「原子性」，还需要 volatile 保证「可见性」

                没有 volatile 会怎样？
                  线程1 修改了值，线程2 可能看不到（CPU 缓存）
                  线程1: value = 100 → 200（写入 L1 缓存，未刷新主存）
                  线程2: 读到 value = 100（从自己的 L1 缓存读）

                有 volatile 后：
                  1. 写入时立即刷新到主存
                  2. 读取时直接从主存读
                  3. 禁止指令重排

                volatile 保证可见性 + CAS 保证原子性 = 线程安全
                """);

        return result;
    }

    // ==================== 3. CAS vs synchronized ====================

    /**
     * CAS vs synchronized — 无锁 vs 有锁
     */
    public Map<String, Object> casVsSynchronized() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        int threadCount = 10;
        int iterations = 100_000;

        // ---- synchronized 方式 ----
        Object lock = new Object();
        int[] syncCounter = {0};
        long start = System.currentTimeMillis();

        Thread[] syncThreads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            syncThreads[i] = new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    synchronized (lock) {
                        syncCounter[0]++;
                    }
                }
            });
            syncThreads[i].start();
        }
        for (Thread t : syncThreads) t.join();
        long syncCost = System.currentTimeMillis() - start;

        // ---- CAS 方式 ----
        AtomicInteger casCounter = new AtomicInteger(0);
        start = System.currentTimeMillis();

        Thread[] casThreads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            casThreads[i] = new Thread(() -> {
                for (int j = 0; j < iterations; j++) {
                    casCounter.incrementAndGet();
                }
            });
            casThreads[i].start();
        }
        for (Thread t : casThreads) t.join();
        long casCost = System.currentTimeMillis() - start;

        // ---- 性能对比 ----
        Map<String, String> perfCompare = new LinkedHashMap<>();
        perfCompare.put("synchronized", syncCounter[0] + " | 耗时: " + syncCost + "ms");
        perfCompare.put("CAS (AtomicInteger)", casCounter.get() + " | 耗时: " + casCost + "ms");
        perfCompare.put("CAS 优势", casCost > 0 ? ((syncCost * 100 / casCost) - 100) + "% 更快" : "N/A");
        result.put("性能对比（" + threadCount + " 线程 × " + iterations + " 次）", perfCompare);

        // ---- 原理对比 ----
        Map<String, String> principleCompare = new LinkedHashMap<>();
        principleCompare.put("synchronized", """
                有锁（悲观锁）
                获取锁 → 执行 → 释放锁
                问题：
                  1. 线程阻塞 → 上下文切换（开销大）
                  2. 线程调度 → 内核态切换
                  3. 锁竞争激烈时性能急剧下降
                """);
        principleCompare.put("CAS", """
                无锁（乐观锁）
                读取 → 修改 → CAS 更新
                优势：
                  1. 不阻塞线程 → 无上下文切换
                  2. 用户态操作 → 不需要内核态
                  3. 适合「读多写少」或「竞争不激烈」
                """);
        result.put("原理对比", principleCompare);

        // ---- 适用场景 ----
        result.put("适用场景", """
                ┌──────────────────┬─────────────────────────────────┐
                │ 场景             │ 推荐                            │
                ├──────────────────┼─────────────────────────────────┤
                │ 竞争不激烈       │ CAS（无锁，性能好）              │
                │ 竞争激烈         │ synchronized（避免 CAS 空转）    │
                │ 简单同步         │ synchronized（代码简洁）         │
                │ 需要高级特性     │ ReentrantLock                   │
                │ 计数器/统计      │ LongAdder（分段减少竞争）        │
                └──────────────────┴─────────────────────────────────┘
                """);

        return result;
    }

    // ==================== 4. CAS 自旋重试 ====================

    /**
     * CAS 自旋 — 失败了怎么办？
     */
    public Map<String, Object> casSpin() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- 1. 手动实现 CAS 自旋 ----
        AtomicInteger counter = new AtomicInteger(0);
        List<String> spinLog = new ArrayList<>();

        // 模拟 CAS 自旋：失败了就重试
        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            pool.execute(() -> {
                int retryCount = 0;
                while (true) {
                    int current = counter.get();        // 1. 读取当前值
                    int next = current + 1;             // 2. 计算新值
                    boolean success = counter.compareAndSet(current, next);  // 3. CAS 更新

                    if (success) {
                        synchronized (spinLog) {
                            spinLog.add("线程" + threadId + ": 成功（重试 " + retryCount + " 次）");
                        }
                        break;
                    } else {
                        retryCount++;
                        // CAS 失败，自旋重试
                    }
                }
                latch.countDown();
            });
        }

        latch.await();
        result.put("手动 CAS 自旋", spinLog);
        result.put("最终值", counter.get() + "（正确: " + threadCount + "）");

        // ---- 2. CAS 自旋的代价 ----
        result.put("CAS 自旋的代价", """
                问题：CAS 失败 → 重试 → 再失败 → 再重试...

                极端情况：
                  100 个线程同时 CAS
                  线程1 成功，99 个失败重试
                  线程2 成功，98 个失败重试
                  ...
                  总共 CAS 操作 = 100 + 99 + 98 + ... + 1 = 5050 次
                  实际只需要 100 次！

                这就是 CAS 的「自旋开销」
                """);

        // ---- 3. 减少自旋的策略 ----
        result.put("减少自旋的策略", """
                1. LongAdder：分段累加，每个线程操作自己的 Cell，减少竞争
                2. 分段锁：把一个大锁拆成多个小锁
                3. 退避策略：CAS 失败后 sleep 一下再重试
                4. 放弃重试：超过 N 次失败后走其他逻辑
                """);

        // ---- 4. LongAdder 原理 ----
        result.put("LongAdder 原理", """
                ┌─────────────────────────────────────────────────┐
                │  AtomicLong：所有线程竞争同一个 value            │
                │    线程1 ──→ CAS(value) ──┐                    │
                │    线程2 ──→ CAS(value) ──┤ 竞争激烈，大量重试  │
                │    线程3 ──→ CAS(value) ──┘                    │
                │                                                 │
                │  LongAdder：分散到多个 Cell                     │
                │    线程1 ──→ CAS(Cell[0]) ──→ 无竞争            │
                │    线程2 ──→ CAS(Cell[1]) ──→ 无竞争            │
                │    线程3 ──→ CAS(Cell[2]) ──→ 无竞争            │
                │    sum() = base + Cell[0] + Cell[1] + Cell[2]  │
                └─────────────────────────────────────────────────┘
                """);

        return result;
    }

    // ==================== 5. ABA 问题 ====================

    /**
     * ABA 问题 — CAS 的经典陷阱
     */
    public Map<String, Object> abaProblem() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- 1. 演示 ABA 问题 ----
        AtomicInteger abaValue = new AtomicInteger(100);
        List<String> abaLog = new ArrayList<>();

        // 线程1：准备 CAS（读到 100，想改成 200）
        Thread slowThread = new Thread(() -> {
            int expected = abaValue.get();  // 读到 100
            abaLog.add("线程1: 读取值 = " + expected + "，准备慢一点再 CAS...");
            try { Thread.sleep(200); } catch (InterruptedException e) {}

            // 此时值已经被改过又改回来了，但 CAS 仍然成功！
            boolean success = abaValue.compareAndSet(expected, 200);
            abaLog.add("线程1: CAS(" + expected + " → 200) = " + success +
                    " | 当前值: " + abaValue.get());
        });

        // 线程2：快速完成 ABA
        Thread fastThread = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            abaValue.compareAndSet(100, 999);  // 100 → 999
            abaLog.add("线程2: 100 → 999");
            abaValue.compareAndSet(999, 100);  // 999 → 100（改回来了）
            abaLog.add("线程2: 999 → 100（改回来了）");
        });

        slowThread.start();
        fastThread.start();
        slowThread.join();
        fastThread.join();

        result.put("ABA 问题演示", abaLog);
        result.put("问题分析", """
                线程1 读到 100，想改成 200
                线程2 把 100 改成 999，又改回 100
                线程1 CAS(100 → 200) 成功了！

                问题：值虽然看起来没变（都是 100），但中间已经被改过了
                在某些场景下这是有问题的（比如链表、栈）
                """);

        // ---- 2. 解决方案：AtomicStampedReference ----
        result.put("解决方案：AtomicStampedReference", """
                给值加一个「版本号」（stamp）

                CAS 时不仅比较值，还比较版本号
                即使值改回来了，版本号也变了，CAS 就会失败

                AtomicStampedReference<String> ref = new AtomicStampedReference<>("A", 0);

                // 获取当前版本号
                int[] stampHolder = new int[1];
                String value = ref.get(stampHolder);
                int stamp = stampHolder[0];

                // CAS：值 == "A" 且 版本号 == stamp 才更新
                ref.compareAndSet("A", "B", stamp, stamp + 1);
                """);

        // ---- 3. AtomicStampedReference 演示 ----
        AtomicStampedReference<Integer> stampedRef = new AtomicStampedReference<>(100, 0);
        List<String> stampedLog = new ArrayList<>();

        // 线程1：慢线程
        Thread slowStamped = new Thread(() -> {
            int[] stampHolder = new int[1];
            int value = stampedRef.get(stampHolder);
            int stamp = stampHolder[0];
            stampedLog.add("线程1: 读取值 = " + value + ", 版本号 = " + stamp);
            try { Thread.sleep(200); } catch (InterruptedException e) {}

            // CAS：值 == 100 且 版本号 == 0 才成功
            boolean success = stampedRef.compareAndSet(100, 200, stamp, stamp + 1);
            stampedLog.add("线程1: CAS(100→200, stamp " + stamp + "→" + (stamp + 1) + ") = " + success);
        });

        // 线程2：快速 ABA
        Thread fastStamped = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            int stamp = stampedRef.getStamp();
            stampedRef.compareAndSet(100, 999, stamp, stamp + 1);  // stamp: 0 → 1
            stampedLog.add("线程2: 100 → 999 (stamp: 0 → 1)");
            stamp = stampedRef.getStamp();
            stampedRef.compareAndSet(999, 100, stamp, stamp + 1);  // stamp: 1 → 2
            stampedLog.add("线程2: 999 → 100 (stamp: 1 → 2)");
        });

        slowStamped.start();
        fastStamped.start();
        slowStamped.join();
        fastStamped.join();

        result.put("AtomicStampedReference 演示", stampedLog);
        result.put("结果分析", """
                线程1 读到 stamp=0
                线程2 ABA 后 stamp 变成了 2
                线程1 CAS(100→200, stamp=0) 失败！因为 stamp 不匹配

                版本号解决了 ABA 问题 ✓
                """);

        // ---- 4. AtomicMarkableReference ----
        result.put("AtomicMarkableReference", """
                AtomicMarkableReference — 布尔标记版本

                和 AtomicStampedReference 类似，但用 boolean 代替 int
                适合只需要知道「有没有被改过」的场景

                AtomicMarkableReference<String> ref = new AtomicMarkableReference<>("A", false);

                // CAS：值 == "A" 且 标记 == false 才更新
                ref.compareAndSet("A", "B", false, true);

                区别：
                  AtomicStampedReference — 版本号，知道改了几次
                  AtomicMarkableReference — 布尔标记，只知道有没有改过
                """);

        return result;
    }

    // ==================== 6. CAS 实战应用 ====================

    /**
     * CAS 实战 — 手写自旋锁、无锁栈
     */
    public Map<String, Object> casPractice() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // ---- 1. 手写自旋锁 ----
        result.put("手写自旋锁", """
                public class SpinLock {
                    private final AtomicBoolean locked = new AtomicBoolean(false);

                    public void lock() {
                        while (!locked.compareAndSet(false, true)) {
                            // 自旋等待
                        }
                    }

                    public void unlock() {
                        locked.set(false);
                    }
                }

                特点：
                  - 不阻塞线程，自旋等待
                  - 适合锁持有时间极短的场景
                  - 缺点：CPU 空转
                """);

        // 演示自旋锁
        SpinLock spinLock = new SpinLock();
        int[] spinCounter = {0};
        int threadCount = 10;
        CountDownLatch spinLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.execute(() -> {
                try {
                    spinLock.lock();
                    try {
                        spinCounter[0]++;
                    } finally {
                        spinLock.unlock();
                    }
                } finally {
                    spinLatch.countDown();
                }
            });
        }
        spinLatch.await();
        result.put("自旋锁演示", threadCount + " 线程递增结果: " + spinCounter[0] + "（正确: " + threadCount + "）");

        // ---- 2. 无锁队列（简化版）----
        result.put("无锁数据结构原理", """
                无锁队列 / 无锁栈 — 用 CAS 替代锁

                栈的 push 操作（CAS 版）：
                  Node<T> oldHead = top.get();
                  Node<T> newHead = new Node<>(value, oldHead);
                  while (!top.compareAndSet(oldHead, newHead)) {
                      oldHead = top.get();  // 重新读取
                      newHead = new Node<>(value, oldHead);
                  }

                栈的 pop 操作（CAS 版）：
                  Node<T> oldHead = top.get();
                  while (oldHead != null && !top.compareAndSet(oldHead, oldHead.next)) {
                      oldHead = top.get();  // 重新读取
                  }
                  return oldHead == null ? null : oldHead.value;

                原理：
                  1. 读取当前头节点
                  2. 创建新节点，next 指向旧头节点
                  3. CAS 更新头节点
                  4. 如果失败（被其他线程抢先），重试
                """);

        // ---- 3. CAS 的局限性 ----
        result.put("CAS 的局限性", """
                1. ABA 问题
                   → 用 AtomicStampedReference（版本号）

                2. 自旋开销
                   → 竞争激烈时用 synchronized 或 LongAdder

                3. 只能保证单个变量的原子性
                   → 多个变量用锁，或用 AtomicReference 包装对象

                4. CPU 空转
                   → 自旋锁适合锁持有时间极短的场景
                   → 长时间等待应该用 synchronized（阻塞）
                """);

        // ---- 4. CAS 使用建议 ----
        result.put("使用建议", """
                ┌─────────────────────────────────────────────────────┐
                │ 什么时候用 CAS？                                    │
                ├─────────────────────────────────────────────────────┤
                │ ✅ 计数器 / 统计（AtomicLong / LongAdder）          │
                │ ✅ 状态标志（AtomicBoolean）                        │
                │ ✅ 对象引用更新（AtomicReference）                  │
                │ ✅ 锁持有时间极短（自旋锁）                         │
                │ ❌ 锁持有时间长（用 synchronized）                  │
                │ ❌ 多变量原子操作（用锁）                           │
                │ ❌ 竞争极其激烈（用 LongAdder 分段）                │
                └─────────────────────────────────────────────────────┘
                """);

        return result;
    }

    // ==================== 内部类：自旋锁 ====================

    /**
     * 自旋锁 — CAS 实现
     */
    static class SpinLock {
        private final AtomicBoolean locked = new AtomicBoolean(false);

        public void lock() {
            while (!locked.compareAndSet(false, true)) {
                // 自旋等待（CPU 空转）
            }
        }

        public void unlock() {
            locked.set(false);
        }
    }
}
