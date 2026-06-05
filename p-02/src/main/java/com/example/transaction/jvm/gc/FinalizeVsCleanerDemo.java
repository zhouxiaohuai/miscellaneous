package com.example.transaction.jvm.gc;

import org.springframework.stereotype.Component;

import java.lang.ref.Cleaner;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对象回收钩子：finalize vs Cleaner 演示
 *
 * 对象被 GC 回收时，有时需要清理非 GC 资源（文件句柄、网络连接等）。
 *
 * 三种方案演进：
 *
 * 1. finalize()（JDK 1.0，已废弃）
 *    - Object 类的 protected 方法
 *    - GC 回收前调用
 *    - 问题：不确定何时执行、可能复活对象、性能差
 *
 * 2. PhantomReference + ReferenceQueue（JDK 1.2）
 *    - 虚引用 + 引用队列
 *    - 对象被 GC 后收到通知
 *    - 无法复活对象，确定性回收
 *
 * 3. Cleaner（JDK 9+）
 *    - 基于 PhantomReference 的高级封装
 *    - 注册清理动作，GC 后自动执行
 *    - 推荐替代 finalize()
 *
 * ┌─────────────┬────────────┬────────────┬────────────┐
 * │  对比项       │ finalize() │ PhantomRef │ Cleaner    │
 * ├─────────────┼────────────┼────────────┼────────────┤
 * │ 确定性       │ 不确定      │ 确定       │ 确定       │
 * │ 复活对象      │ 可以        │ 不可以     │ 不可以     │
 * │ 性能         │ 差          │ 好         │ 好         │
 * │ 线程         │ Finalizer  │ 引用队列线程│ Cleaner线程│
 * │ 推荐度       │ 已废弃      │ 低级API    │ 推荐       │
 * └─────────────┴────────────┴────────────┴────────────┘
 */
@Component
public class FinalizeVsCleanerDemo {

    /**
     * 演示 finalize() 的问题
     *
     * 问题1：不确定何时执行
     * - GC 回收对象后，finalize() 不一定立即执行
     * - Finalizer 线程优先级低，可能被延迟
     * - 程序退出时不一定执行
     *
     * 问题2：可能复活对象
     * - finalize() 中可以重新建立引用 → 对象复活
     * - 但 finalize() 只执行一次 → 复活后不再调用
     *
     * 问题3：性能差
     * - 每个有 finalize() 的对象都要入队 Finalizer 链表
     * - 需要额外的 Finalizer 线程执行
     * - 增加 GC 停顿时间
     */
    public Map<String, Object> demonstrateFinalizeProblems() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("finalize() 的问题", """
            问题1: 不确定何时执行
            - GC 回收后，finalize() 入队 Finalizer 链表
            - Finalizer 线程（优先级低）异步执行
            - 可能延迟几秒甚至几分钟

            问题2: 可能复活对象
            protected void finalize() throws Throwable {
                // 重新建立引用 → 对象复活！
                InstanceKeeper.instance = this;
            }
            但 finalize() 只执行一次，复活后不再调用

            问题3: 性能差
            - 每个对象入队 Finalizer 链表（同步操作）
            - Finalizer 线程执行 finalize()（异步）
            - 增加 GC 停顿时间

            问题4: 异常被忽略
            - finalize() 中的异常不会打印，不会终止线程
            - 难以调试
            """);

        result.put("finalize() 执行流程", """
            对象不可达
              ↓
            GC 标记为可回收
              ↓
            检查是否重写了 finalize()
              ↓
            没有 → 直接回收
            有   → 加入 Finalizer 链表
              ↓
            Finalizer 线程取出，执行 finalize()
              ↓
            对象真正可回收（下次 GC 回收）
            """);

        result.put("结论", """
            JDK 9+: finalize() 已废弃（@Deprecated(forRemoval=true)）
            不要在任何新代码中使用 finalize()
            已有代码应尽快迁移到 Cleaner 或 PhantomReference
            """);

        return result;
    }

    /**
     * 演示 Cleaner（推荐方案）
     *
     * Cleaner 基于 PhantomReference 实现：
     * 1. 创建 Cleaner 实例
     * 2. 注册清理动作（Cleaner.clean(Runnable)）
     * 3. 当对象不可达时，Cleaner 线程自动执行清理动作
     *
     * 优势：
     * - 确定性：对象被 GC 后一定会执行清理
     * - 安全：无法复活对象
     * - 高效：不增加 GC 停顿
     * - 线程安全：Cleaner 有自己的线程池
     */
    public Map<String, Object> demonstrateCleaner() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 创建 Cleaner
        Cleaner cleaner = Cleaner.create();

        // 创建资源持有者
        byte[] resource = new byte[1024 * 1024]; // 1MB 模拟资源
        int resourceId = System.identityHashCode(resource);

        // 注册清理动作
        cleaner.register(resource, new ResourceCleanupTask(resourceId));

        result.put("代码示例", """
            Cleaner cleaner = Cleaner.create();

            // 资源持有者
            byte[] resource = new byte[1024 * 1024];

            // 注册清理动作（当 resource 不可达时执行）
            cleaner.register(resource, () -> {
                System.out.println("清理资源: 关闭文件/释放内存");
            });

            // 释放强引用
            resource = null;
            System.gc();
            // Cleaner 线程会自动执行清理动作
            """);

        result.put("Cleaner 的优势", """
            1. 确定性回收：对象不可达后一定会执行清理
            2. 安全性：清理动作在独立线程执行，不影响 GC
            3. 高效性：基于 PhantomReference，不增加 GC 停顿
            4. 简洁性：API 简单易用
            5. 线程安全：Cleaner 内部管理线程池
            """);

        result.put("使用场景", """
            1. 文件句柄清理
            2. 网络连接关闭
            3. 堆外内存释放（DirectByteBuffer）
            4. 本地资源释放（JNI）
            5. 临时文件删除
            """);

        // 释放资源，触发清理
        resource = null;
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        return result;
    }

    /**
     * 演示 PhantomReference 跟踪回收
     *
     * 虚引用的唯一目的：在对象被 GC 回收时收到通知
     * get() 始终返回 null，只能通过 ReferenceQueue 知道对象被回收
     */
    public Map<String, Object> demonstratePhantomReferenceTracking() {
        Map<String, Object> result = new LinkedHashMap<>();

        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        Object target = new Object();
        int id = System.identityHashCode(target);

        // 创建虚引用
        PhantomReference<Object> phantom = new PhantomReference<>(target, queue);

        result.put("GC 前", Map.of(
            "对象 ID", id,
            "phantom.get()", phantom.get(), // 始终 null
            "队列中", queue.poll() != null
        ));

        // 释放强引用，触发 GC
        target = null;
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        result.put("GC 后", Map.of(
            "队列中", queue.poll() != null ? "是（对象已被回收）" : "否"
        ));

        result.put("PhantomReference 用途", """
            1. 跟踪对象回收时机
            2. 管理堆外内存（DirectByteBuffer 底层实现）
            3. 实现 Cleaner（JDK 9+）
            4. 替代 finalize()
            """);

        return result;
    }

    /**
     * 最佳实践总结
     */
    public Map<String, Object> showBestPractices() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("资源清理方案选择", """
            ┌─────────────────┬───────────────────────────────────┐
            │ 场景             │ 方案                               │
            ├─────────────────┼───────────────────────────────────┤
            │ 文件/流          │ try-with-resources（最优先）       │
            │ 连接池           │ 框架管理（Spring/HikariCP）        │
            │ 堆外内存         │ Cleaner（JDK 9+）                 │
            │ JNI 资源         │ Cleaner                           │
            │ 监听器注册       │ WeakHashMap / 显式注销             │
            │ 定时任务         │ shutdown() / cancel()             │
            └─────────────────┴───────────────────────────────────┘
            """);

        result.put("原则", """
            1. 优先使用 try-with-resources（确定性关闭）
            2. 依赖框架管理资源（连接池、线程池）
            3. Cleaner 作为兜底方案（非 GC 资源清理）
            4. 永远不要用 finalize()（已废弃）
            5. 监控资源泄漏（文件描述符、连接数）
            """);

        return result;
    }

    // 清理任务
    static class ResourceCleanupTask implements Runnable {
        private final int resourceId;

        ResourceCleanupTask(int resourceId) {
            this.resourceId = resourceId;
        }

        @Override
        public void run() {
            System.out.println("[Cleaner] 清理资源 ID: " + resourceId + " — 关闭文件/释放内存");
        }
    }
}
