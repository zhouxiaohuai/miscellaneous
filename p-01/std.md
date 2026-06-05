# std.md（学习笔记：只追加、不覆盖）

说明：本文件用于记录 Java 学习笔记与进度。**以后每次学习/提问/复盘，都在末尾追加**新的日期小节，不改动既有内容。

---

## 2026-04-15 学习主题：Java 并发编程（起步）

### 本阶段目标（并发编程）
- **能写对**：掌握线程安全、可见性、有序性、原子性；写出正确且可维护的并发代码。
- **能写快**：理解锁/无锁、阻塞/非阻塞、线程池与队列的取舍，避免常见性能陷阱。
- **能排障**：会看线程 dump、理解死锁/活锁/饥饿、会用 JFR/VisualVM/日志定位并发问题。

### 学习节奏（建议 4 周，可按你时间伸缩）
说明：每天建议 60–120 分钟；每周 1 次复盘（把“概念 -> 代码 -> 反例 -> 排障方法”写进笔记）。

#### 第 0 周：准备与工具（0.5–1 天）
- **JDK 与工程**：确定 JDK 版本（建议 17 或 21），Maven/Gradle 任选其一。
- **排障工具**：会用 `jstack/jcmd`、IDEA 线程视图；了解 JFR/VisualVM（先会打开与看基本指标）。

#### 第 1 周：并发基础与 JMM（内存模型）
- **核心概念**：线程 vs 进程、上下文切换、竞态条件。
- **JMM 三性**：可见性/有序性/原子性；`happens-before` 基本规则。
- **关键语义**：`volatile`（可见性+禁止部分重排序）、`synchronized`（互斥+可见性+进入/退出屏障）。
- **练习方向**：用最小例子复现“指令重排/可见性问题”，再用 `volatile/synchronized` 修复（只写学习代码，不追求业务意义）。

#### 第 2 周：锁与并发容器
- **锁体系**：内置锁、`ReentrantLock`、读写锁、`StampedLock`（理解适用场景，不强求全掌握）。
- **等待/通知**：`wait/notify` vs `Condition`。
- **并发容器**：`ConcurrentHashMap`、`CopyOnWriteArrayList`、`ConcurrentLinkedQueue`、阻塞队列（`ArrayBlockingQueue/LinkedBlockingQueue` 等）。
- **原子类**：`Atomic*`、CAS 思想、ABA 问题（知道 `AtomicStampedReference` 的存在与用途）。

#### 第 3 周：线程池、Future、CompletableFuture
- **线程池参数**：核心线程数/最大线程数/队列/拒绝策略/线程工厂。
- **正确姿势**：为什么不要滥用 `Executors.newFixedThreadPool`（理解队列与 OOM 风险）；如何自定义线程池。
- **异步编排**：`Future` 局限、`CompletableFuture` 常用组合（`thenApply/thenCompose/allOf/anyOf/exceptionally/handle`）。
- **练习方向**：写一个“多任务并发 + 超时 + 失败回退 + 汇总结果”的小练习。

#### 第 4 周：并发排障与性能
- **问题类型**：死锁/活锁/饥饿、锁竞争、线程泄漏、队列堆积、上下游背压。
- **排查手段**：线程 dump、锁等待分析、线程池指标（活跃数、队列长度、完成数）、简单压测。
- **性能心法**：减少共享、缩小临界区、分段/分离锁、选择合适队列与拒绝策略、避免过度并行。

### 并发编程知识地图（大模块清单）
- **基础**：JMM、`volatile`、`final` 语义、`synchronized`
- **工具类**：`Lock/Condition`、`AQS`（理解思想即可）、`Semaphore/CountDownLatch/CyclicBarrier/Phaser`
- **原子与无锁**：CAS、原子类、LongAdder
- **容器与队列**：并发容器、阻塞队列
- **线程池与调度**：`ThreadPoolExecutor`、`ScheduledThreadPoolExecutor`
- **异步与反应式思维**：`CompletableFuture`、背压（概念）
- **排障与监控**：dump/JFR/指标

### 今日开始（你可以直接照这个模板追加）
- **今日学习时长**：
- **学习内容**（概念要点）：
- **代码练习**（写了什么小例子/结论是什么）：
- **踩坑与纠正**：
- **明日计划**：

---

## 2026-04-15 并发第 1 课：为什么会“线程不安全”

### 今日目标
- **建立直觉**：并发 bug 本质来自“共享可变状态 + 不正确的同步”。
- **掌握三性**：原子性、可见性、有序性分别是什么，分别用什么手段解决。
- **完成 1 个最小练习**：复现一个“可见性问题”，并用 `volatile` 修复（后续再用 `synchronized` 对照）。

### 核心概念（先记住这三句话）
- **原子性**：一个操作要么全部完成，要么完全不发生；`i++` 不是原子操作。
- **可见性**：一个线程对共享变量的修改，另一个线程何时能看到；没有同步时可能“永远看不到”。
- **有序性**：编译器/CPU 可能对指令重排；同步手段会建立“先行发生（happens-before）”关系来约束重排。

### 最小练习（今天只做可见性）
练习名：**stop-flag 可见性**（独立文件夹：`concurrency/ex01_stopflag`）
- **现象预期**：线程 A 在循环里读一个 `stop` 标志；线程 B 过一会儿把 `stop` 改成 `true`。如果没有正确同步，A 可能迟迟不退出（表现为卡住/不结束）。
- **修复方式**：把 `stop` 声明为 `volatile`（或用锁/原子类等方式建立可见性）。

> 你现在要做的事：运行 `com.aichat.study.concurrency.ex01_stopflag` 包下的两个 demo，观察现象并对照。  
> 你把运行结果（是否卡住、输出是什么、JDK 版本）发我，我们进入下一课：`i++` 的原子性与 `synchronized`。

---

## 2026-04-15 并发第 2 课：`i++` 为什么不安全（原子性）

### 你要记住的结论
- **`i++` 不是原子操作**，它大致等价于：读取 i → i+1 → 写回 i。多线程同时执行会发生“丢失更新”。
- **`volatile` 不能解决 `i++` 的线程安全**：`volatile` 只保证“可见性/部分有序性”，不保证复合操作的原子性。

### 这节课练什么
练习名：**counter race**
- **目标**：多个线程同时做 `counter++`，最终结果通常 **小于** 期望值（复现竞态条件）。
- **修复 1（互斥）**：用 `synchronized` 把 `counter++` 包进临界区，结果应正确。
- **修复 2（无锁原子）**：用 `AtomicInteger.incrementAndGet()`，结果也应正确。

### 你现在要做的事
在 `com.aichat.study.concurrency.ex02_counter` 包下运行这三个 demo（我已生成）：
- `CounterRaceDemo`（错误示例，观察“少加了”）
- `CounterSynchronizedDemo`（用 `synchronized` 修复）
- `CounterAtomicDemo`（用 `AtomicInteger` 修复）

你把三次输出贴给我，我们继续：**`synchronized` 的可见性与 happens-before**，以及它和 `ReentrantLock` 的差异。

---

## 2026-04-15 并发第 3 课：`synchronized` 的可见性与 happens-before

### 你要记住的结论（非常重要）
- **同一把锁**上：
  - 线程 A **释放锁（退出 synchronized）** 之前的写入，
  - 对线程 B **随后获取同一把锁（进入 synchronized）** 之后的读取，
  - 存在 **happens-before** 关系。
- 所以：`synchronized` 不只是“互斥”，还提供 **可见性 + 有序性约束**。

### 这节课练什么
练习名：**stop-flag（用 synchronized 而不是 volatile）**（独立文件夹：`concurrency/ex03_happensbefore`）
- **目标**：不使用 `volatile`，但通过 `synchronized` 的“加锁/解锁”建立 happens-before，使 worker 能及时看到 stop 变化并退出。
- **关键点**：读写 stop 都要通过同一把锁保护（例如 `isStop()`/`setStop()` 都 `synchronized`）。

### 你现在要做的事
运行 `com.aichat.study.concurrency.ex03_happensbefore.StopFlagSynchronizedDemo`，把输出贴给我。

---

## 2026-04-15 并发第 4 课：`ReentrantLock`（相对 `synchronized` 的优势）

### 你要记住的结论
- **共同点**：都能提供互斥，并且在正确使用时都能提供可见性/有序性（happens-before）。
- **`ReentrantLock` 的常用优势**：
  - **可中断地等锁**：`lockInterruptibly()`
  - **可超时/可尝试获取**：`tryLock()` / `tryLock(timeout)`
  - **更灵活的条件队列**：`Condition`（可多个条件队列，而 `wait/notify` 只有一个监视器条件队列）
  - （可选）**公平锁**：构造时 `new ReentrantLock(true)`（吞吐通常更低，但更“排队”）

### 这节课练什么
练习名：**可中断获取锁 + Condition 等待/唤醒**（独立文件夹：`concurrency/ex04_reentrantlock`）
- `LockInterruptiblyDemo`：演示“一个线程持锁不放”，另一个线程用 `lockInterruptibly()` 等锁时可以被 `interrupt()` 立刻打断。
- `ConditionSignalDemo`：演示 `Condition.await()` / `signal()`（类比 `wait/notify`，但更可控）。

### 你现在要做的事
依次运行：
- `com.aichat.study.concurrency.ex04_reentrantlock.LockInterruptiblyDemo`
- `com.aichat.study.concurrency.ex04_reentrantlock.ConditionSignalDemo`

把两段输出贴给我，我们进入下一课：线程池（为什么要线程池、核心参数、队列与拒绝策略）。

---

## 2026-04-15 并发第 5 课：线程池 `ThreadPoolExecutor`（参数、队列、拒绝策略）

### 你要记住的结论
- **线程池解决什么**：避免频繁创建/销毁线程；通过“队列 + 线程数”控制并发度与资源。
- **核心参数（先背会名字）**：
  - `corePoolSize`：核心线程数（常驻）
  - `maximumPoolSize`：最大线程数（忙时扩容上限）
  - `workQueue`：任务队列（决定“排队”还是“扩容”）
  - `RejectedExecutionHandler`：拒绝策略（队列满 + 线程到上限时怎么办）
  - `ThreadFactory`：线程命名/是否守护线程等
- **一个关键流程**（最常考/最常踩坑）：
  - 先用核心线程执行 → 核心满了就入队 → 队列满了再扩到最大线程 → 还满就触发拒绝策略

### 这节课练什么
练习名：**队列堆积 + 拒绝策略**（独立文件夹：`concurrency/ex05_threadpool`）
- `ThreadPoolQueueDemo`：用小队列制造“排队堆积”，打印线程池关键指标（active/queue/completed）。
- `ThreadPoolRejectionDemo`：把队列/线程数调小，强制触发拒绝策略（`AbortPolicy` vs `CallerRunsPolicy`）。

### 你现在要做的事
依次运行：
- `com.aichat.study.concurrency.ex05_threadpool.ThreadPoolQueueDemo`
- `com.aichat.study.concurrency.ex05_threadpool.ThreadPoolRejectionDemo`

把输出贴给我，我们继续下一课：**如何合理设置线程池参数**（CPU 密集/IO 密集的思路 + 常见线上坑）。

---

## 2026-04-15 并发第 6 课：线程池参数怎么配（CPU/IO、队列、背压、异常）

### 你要记住的“可操作规则”
- **CPU 密集型**（几乎不阻塞）：线程数一般接近 `CPU 核心数`（或 `核心数 + 1`），避免过多线程导致频繁上下文切换。
- **IO 密集型**（大量等待 IO）：线程数可以适当大于核心数（常见从 `2 * 核心数` 起试），但一定要配合**限流/队列/超时**，否则只是把拥塞搬到别处。
- **队列选择**：
  - **有界队列**（推荐线上默认）：可控，配合拒绝策略形成背压，避免无穷排队导致 OOM。
  - **无界队列**（谨慎）：吞吐看起来“很稳”，但高峰期会无限堆积，最终可能 OOM 或长尾延迟爆炸。
- **拒绝策略选型**：
  - `AbortPolicy`：快速失败（适合必须保护系统、让上游重试/降级）
  - `CallerRunsPolicy`：把压力反弹给提交方（背压），常用于保护系统并“自动降速”
- **任务异常（重要）**：
  - 在线程池里抛出的异常**不会冒泡到提交线程**（除非你用 `Future.get()`），要通过日志/统一包装/线程工厂等方式让异常“可见”。

### 这节课练什么
练习名：**背压与异常可见性**（独立文件夹：`concurrency/ex06_threadpool_tuning`）
- `CallerRunsBackpressureDemo`：观察 `CallerRunsPolicy` 如何让提交变慢（形成背压）。
- `ThreadPoolExceptionVisibilityDemo`：观察线程池中任务抛异常时，提交方通常看不到；以及如何让异常更可见。

### 你现在要做的事
依次运行：
- `com.aichat.study.concurrency.ex06_threadpool_tuning.CallerRunsBackpressureDemo`
- `com.aichat.study.concurrency.ex06_threadpool_tuning.ThreadPoolExceptionVisibilityDemo`

把输出贴给我，我们下一课进入：`CompletableFuture`（组合、异常、超时、allOf/anyOf）。

---

## 2026-04-15 并发第 7 课：`CompletableFuture`（组合、异常、超时、汇总）

### 你要记住的结论（先抓住“怎么用”）
- **两类常用创建**：
  - `supplyAsync`：有返回值
  - `runAsync`：无返回值
- **串行组合**：
  - `thenApply`：同步转换（把结果 map 一下）
  - `thenCompose`：扁平化（把“异步嵌套”摊平，避免 `Future<Future<T>>`）
- **并行汇总**：
  - `allOf`：等全部完成（常用于并行请求后汇总）
  - `anyOf`：任意一个完成就继续（竞速/取最快）
- **异常处理**：
  - `exceptionally`：出错时给一个兜底值
  - `handle`：不管成功/失败都处理一遍（同时能拿到异常）
- **超时**（JDK 9+）：
  - `orTimeout`：超时则异常
  - `completeOnTimeout`：超时给默认值

### 这节课练什么
练习名：**串行/并行组合 + 异常 + 超时**（独立文件夹：`concurrency/ex07_completablefuture`）
- `CfComposeDemo`：对比 `thenApply` vs `thenCompose`
- `CfAllOfAnyOfDemo`：演示 `allOf/anyOf` 汇总与竞速
- `CfTimeoutAndExceptionDemo`：演示超时与异常兜底（`orTimeout/completeOnTimeout/exceptionally`）

### 你现在要做的事
依次运行：
- `com.aichat.study.concurrency.ex07_completablefuture.CfComposeDemo`
- `com.aichat.study.concurrency.ex07_completablefuture.CfAllOfAnyOfDemo`
- `com.aichat.study.concurrency.ex07_completablefuture.CfTimeoutAndExceptionDemo`

把输出贴给我，我们下一课进入：并发工具类（`CountDownLatch/CyclicBarrier/Semaphore`）。

