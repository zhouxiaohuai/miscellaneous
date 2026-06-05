package com.example.transaction.jvm.memory;

import org.springframework.stereotype.Component;

import java.lang.ref.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 四种引用类型演示
 *
 * Java 提供 4 种引用类型，控制对象的生命周期和 GC 行为：
 *
 * ┌──────────┬────────────┬────────────┬────────────────────┐
 * │ 引用类型  │ GC 回收条件  │ 用途        │ 典型场景            │
 * ├──────────┼────────────┼────────────┼────────────────────┤
 * │ 强引用    │ 永不回收    │ 普通对象    │ Object o = new     │
 * │ 软引用    │ 内存不足时  │ 缓存        │ 图片缓存            │
 * │ 弱引用    │ 下次 GC    │ 缓存/映射   │ WeakHashMap        │
 * │ 虚引用    │ 随时可能    │ 跟踪回收    │ Cleaner/DirectBuf  │
 * └──────────┴────────────┴────────────┴────────────────────┘
 *
 * 引用强度：强 > 软 > 弱 > 虚
 *
 * 对象可达性分析（GC Roots）：
 * ┌─────────────────────────────────────────────┐
 * │ GC Roots:                                    │
 * │  - 虚拟机栈中引用的对象（局部变量）              │
 * │  - 方法区中静态变量引用的对象                    │
 * │  - 方法区中常量引用的对象                       │
 * │  - 本地方法栈中 JNI 引用的对象                  │
 * │                                               │
 * │ 从 GC Roots 出发，不可达的对象 → 可回收          │
 * └─────────────────────────────────────────────┘
 */
@Component
public class ReferenceTypeDemo {

    /**
     * 演示强引用 — 永不回收
     *
     * 强引用是最常见的引用类型，只要强引用存在，GC 永远不会回收对象。
     * 即使内存不足，宁可抛出 OOM 也不回收强引用对象。
     */
    public Map<String, Object> demonstrateStrongReference() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 强引用
        Object strong = new Object();
        int hashCode = System.identityHashCode(strong);

        result.put("代码", "Object strong = new Object();");
        result.put("对象 hashCode", hashCode);
        result.put("GC 行为", "只要 strong 变量存在，对象永远不会被 GC 回收");
        result.put("释放方式", "strong = null; 或超出作用域");
        result.put("OOM 风险", "如果忘记置 null，大对象可能无法回收");

        strong = null;
        return result;
    }

    /**
     * 演示软引用 — 内存不足时回收
     *
     * 软引用适合做缓存：内存够用就保留，不够就回收。
     * JDK 提供 SoftReference 类。
     *
     * 回收时机：内存不足时（接近 -Xmx 上限）
     * 回收前会调用 ReferenceQueue（如果注册了）
     */
    public Map<String, Object> demonstrateSoftReference() {
        Map<String, Object> result = new LinkedHashMap<>();

        ReferenceQueue<Object> refQueue = new ReferenceQueue<>();
        byte[] bigData = new byte[10 * 1024 * 1024]; // 10MB
        SoftReference<byte[]> softRef = new SoftReference<>(bigData, refQueue);

        result.put("代码", """
            byte[] bigData = new byte[10 * 1024 * 1024];
            SoftReference<byte[]> softRef = new SoftReference<>(bigData, refQueue);
            """);
        result.put("引用对象存在", softRef.get() != null);
        result.put("GC 行为", "内存充足时保留，内存不足时回收");
        result.put("用途", "适合做内存敏感的缓存（如图片缓存）");
        result.put("回收时机", "JVM 在抛出 OOM 之前会回收所有软引用对象");
        result.put("注意", "软引用回收后 get() 返回 null，需要重新加载");

        // 验证软引用回收
        bigData = null; // 释放强引用
        System.gc();    // 建议 GC（不保证执行）
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        result.put("GC 后引用存在", softRef.get() != null);
        result.put("说明", "内存充足时 System.gc() 不会回收软引用");

        return result;
    }

    /**
     * 演示弱引用 — 下次 GC 即回收
     *
     * 弱引用比软引用更弱，只要 GC 发现就回收。
     * 适合做不影响对象生命周期的映射（如 WeakHashMap）。
     *
     * 典型场景：
     * 1. WeakHashMap — key 被回收后 entry 自动清理
     * 2. ThreadLocal — ThreadLocalMap 的 key 是弱引用
     * 3. ClassLoader — 避免类加载器泄漏
     */
    public Map<String, Object> demonstrateWeakReference() {
        Map<String, Object> result = new LinkedHashMap<>();

        ReferenceQueue<Object> refQueue = new ReferenceQueue<>();
        Object weakTarget = new Object();
        int hashCode = System.identityHashCode(weakTarget);

        WeakReference<Object> weakRef = new WeakReference<>(weakTarget, refQueue);

        result.put("代码", """
            Object target = new Object();
            WeakReference<Object> weakRef = new WeakReference<>(target, refQueue);
            """);
        result.put("GC 前引用存在", weakRef.get() != null);
        result.put("对象 hashCode", hashCode);

        // 释放强引用，触发 GC
        weakTarget = null;
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        result.put("GC 后引用存在", weakRef.get() != null);
        result.put("引用队列", refQueue.poll() != null ? "已入队（对象被回收）" : "未入队");
        result.put("用途", """
            1. WeakHashMap — key 弱引用，GC 后自动清理 entry
            2. ThreadLocal — 防止内存泄漏（key 弱引用）
            3. 缓存 — 不阻止对象被回收
            """);

        return result;
    }

    /**
     * 演示虚引用 — 跟踪对象回收
     *
     * 虚引用是最弱的引用，get() 始终返回 null。
     * 唯一目的：在对象被 GC 回收时收到通知（通过 ReferenceQueue）。
     *
     * 典型场景：
     * 1. DirectByteBuffer 的 Cleaner 回收机制
     * 2. 跟踪对象的回收时机
     * 3. 管理堆外内存
     */
    public Map<String, Object> demonstratePhantomReference() {
        Map<String, Object> result = new LinkedHashMap<>();

        ReferenceQueue<Object> refQueue = new ReferenceQueue<>();
        Object phantomTarget = new Object();
        int hashCode = System.identityHashCode(phantomTarget);

        PhantomReference<Object> phantomRef = new PhantomReference<>(phantomTarget, refQueue);

        result.put("代码", """
            Object target = new Object();
            PhantomReference<Object> phantomRef = new PhantomReference<>(target, refQueue);
            """);
        result.put("phantomRef.get()", phantomRef.get()); // 始终返回 null
        result.put("对象 hashCode", hashCode);

        // 释放强引用，触发 GC
        phantomTarget = null;
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        Reference<?> polled = refQueue.poll();
        result.put("引用队列", polled != null ? "已入队（对象被回收，可以清理资源）" : "未入队");
        result.put("用途", """
            1. DirectByteBuffer — 虚引用 + Cleaner 清理堆外内存
            2. 跟踪对象回收时机
            3. 管理非 GC 资源（文件句柄、网络连接）
            4. JDK 9+ 推荐替代 finalize()
            """);
        result.put("vs finalize", """
            finalize(): 不确定何时执行，可能抛异常，已被废弃
            PhantomReference: 确定性回收通知，不会阻止 GC，推荐使用
            """);

        return result;
    }

    /**
     * 演示 WeakHashMap — 弱引用的实际应用
     *
     * WeakHashMap 的 key 是弱引用：
     * - key 没有强引用时，GC 会回收 key
     * - key 被回收后，对应的 entry 会被自动清理
     * - 适合做缓存（不阻止 key 对象被回收）
     */
    public Map<String, Object> demonstrateWeakHashMap() {
        Map<String, Object> result = new LinkedHashMap<>();

        java.util.WeakHashMap<String, String> weakMap = new java.util.WeakHashMap<>();
        java.util.HashMap<String, String> normalMap = new java.util.HashMap<>();

        // 用临时字符串作为 key
        String key1 = new String("key1");
        String key2 = new String("key2");

        weakMap.put(key1, "value1");
        weakMap.put(key2, "value2");
        normalMap.put(key1, "value1");
        normalMap.put(key2, "value2");

        result.put("GC 前", Map.of(
            "weakMap.size()", weakMap.size(),
            "normalMap.size()", normalMap.size()
        ));

        // 释放强引用
        key1 = null;
        key2 = null;
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        result.put("GC 后", Map.of(
            "weakMap.size()", weakMap.size(),  // 可能减少
            "normalMap.size()", normalMap.size() // 不变（强引用）
        ));
        result.put("说明", """
            WeakHashMap 的 key 被 GC 后，entry 自动清理
            普通 HashMap 即使 key 不再使用，entry 仍然存在（内存泄漏风险）
            适用场景：缓存、监听器注册、元数据关联
            """);

        return result;
    }
}
