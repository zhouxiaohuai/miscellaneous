package com.example.transaction.jvm.gc;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 内存泄漏场景演示
 *
 * 内存泄漏 vs 内存溢出：
 * - 内存泄漏：对象不再使用但无法被 GC 回收（引用未释放）
 * - 内存溢出：内存不足，无法分配新对象（OOM）
 * - 内存泄漏积累 → 最终导致内存溢出
 *
 * 常见内存泄漏场景：
 * 1. 静态集合持有对象引用
 * 2. 未关闭的资源（连接、流）
 * 3. ThreadLocal 泄漏
 * 4. 内部类持有外部类引用
 * 5. 不正确的 equals/hashCode
 * 6. 缓存无过期策略
 * 7. 监听器未注销
 */
@Component
public class MemoryLeakDemo {

    // === 场景1：静态集合 ===
    private static final List<Object> staticList = new ArrayList<>();

    /**
     * 场景1：静态集合导致内存泄漏
     *
     * 静态变量的生命周期 = 类的生命周期 = JVM 生命周期
     * 不断往静态集合添加对象，永远不会被 GC 回收
     */
    public Map<String, Object> demonstrateStaticCollectionLeak(int count) {
        Map<String, Object> result = new LinkedHashMap<>();

        long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        for (int i = 0; i < count; i++) {
            staticList.add(new byte[1024]); // 每次添加 1KB
        }

        long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        result.put("添加数量", count + " 个 1KB 对象");
        result.put("列表大小", staticList.size());
        result.put("内存增长", (after - before) / 1024 + " KB");
        result.put("问题", "静态变量引用的对象永远不会被 GC 回收");
        result.put("解决", """
            1. 使用完及时 clear()
            2. 使用 WeakHashMap 代替
            3. 设置最大容量限制
            4. 定期清理过期数据
            """);

        // 清理
        staticList.clear();
        return result;
    }

    // === 场景2：未关闭的资源 ===

    /**
     * 场景2：未关闭资源导致泄漏
     *
     * 数据库连接、IO 流、网络连接等资源如果不关闭：
     * - 底层操作系统资源不会释放
     * - 连接池耗尽
     * - 文件描述符泄漏
     */
    public Map<String, Object> demonstrateResourceLeak() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("错误示例", """
            Connection conn = dataSource.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            // 如果中间抛异常，conn/ps/rs 不会关闭！

            // 正确做法：try-with-resources
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                // 使用资源
            } // 自动关闭，即使抛异常
            """);

        result.put("常见泄漏资源", """
            1. Connection / PreparedStatement / ResultSet
            2. InputStream / OutputStream
            3. Socket / ServerSocket
            4. BufferedReader / BufferedWriter
            5. ExecutorService（未 shutdown）
            6. Timer（未 cancel）
            """);

        result.put("排查工具", """
            1. jstat -gc <pid> 1000  — 观察 GC 频率和内存变化
            2. jmap -histo <pid>     — 查看对象数量排名
            3. jmap -dump:format=b,file=heap.hprof <pid>  — 堆转储
            4. MAT (Memory Analyzer Tool) — 分析堆转储
            5. Arthas: dashboard / heapdump
            """);

        return result;
    }

    /**
     * 场景3：ThreadLocal 泄漏
     *
     * ThreadLocal 的 key 是弱引用，但 value 是强引用：
     * - key 被 GC 回收 → value 无法访问但仍被 Entry 强引用
     * - 线程池中的线程长期存活 → value 永远不回收
     *
     * 解决：使用完调用 ThreadLocal.remove()
     */
    public Map<String, Object> demonstrateThreadLocalLeak() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("ThreadLocal 内存结构", """
            Thread → ThreadLocalMap → Entry[] → Entry(key, value)
                                              │
                                    key: WeakReference<ThreadLocal>
                                    value: Object（强引用）

            问题：
            1. ThreadLocal 变量被回收（key 的弱引用断开）
            2. 但 value 仍然被 Entry 强引用
            3. 线程池中线程长期存活 → value 永远不回收
            """);

        result.put("错误示例", """
            private static ThreadLocal<byte[]> tl = new ThreadLocal<>();

            public void process() {
                tl.set(new byte[1024 * 1024]); // 1MB
                try {
                    // 业务逻辑
                } finally {
                    // 忘记 remove()！
                    // 线程池中，这个 1MB 永远不会被回收
                }
            }
            """);

        result.put("正确示例", """
            private static ThreadLocal<byte[]> tl = new ThreadLocal<>();

            public void process() {
                tl.set(new byte[1024 * 1024]);
                try {
                    // 业务逻辑
                } finally {
                    tl.remove(); // 必须 remove！
                }
            }
            """);

        result.put("ThreadLocal 最佳实践", """
            1. 用完必须 remove()（放在 finally 块中）
            2. 尽量用 private static 修饰（避免被子类覆盖）
            3. 线程池场景尤其注意（线程复用，value 不会自动清理）
            4. InheritableThreadLocal 同样有此问题
            """);

        return result;
    }

    /**
     * 场景4：内部类持有外部类引用
     *
     * 非静态内部类隐式持有外部类的 this 引用
     * 如果内部类的生命周期比外部类长 → 外部类无法被 GC
     */
    public Map<String, Object> demonstrateInnerClassLeak() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("问题代码", """
            public class MainActivity extends Activity {
                // 非静态内部类（Handler）持有外部类引用
                private Handler handler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        // 使用 MainActivity 的成员变量
                    }
                };

                public void startTask() {
                    handler.sendMessageDelayed(msg, 60_000);
                    // Activity 退出后，Handler 仍然持有 Activity 引用
                    // 60 秒内 Activity 无法被 GC → 内存泄漏
                }
            }
            """);

        result.put("解决方案", """
            方案1：静态内部类 + 弱引用
            private static class MyHandler extends Handler {
                private WeakReference<MainActivity> ref;
                MyHandler(MainActivity activity) {
                    ref = new WeakReference<>(activity);
                }
                @Override
                public void handleMessage(Message msg) {
                    MainActivity activity = ref.get();
                    if (activity != null) { /* 安全使用 */ }
                }
            }

            方案2：Activity 退出时 removeCallbacksAndMessages
            @Override
            protected void onDestroy() {
                super.onDestroy();
                handler.removeCallbacksAndMessages(null);
            }
            """);

        return result;
    }

    /**
     * 场景5：缓存无过期策略
     *
     * HashMap 做缓存，不设过期 → 对象永远不回收
     * 应该用 Caffeine、Guava Cache、Redis 等有淘汰策略的缓存
     */
    public Map<String, Object> demonstrateCacheLeak() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("问题代码", """
            // 永不过期的缓存 → 内存泄漏
            private Map<String, Object> cache = new HashMap<>();
            public Object get(String key) {
                if (!cache.containsKey(key)) {
                    Object value = loadFromDB(key);
                    cache.put(key, value); // 只进不出
                }
                return cache.get(key);
            }
            """);

        result.put("解决方案", """
            1. WeakHashMap — key 弱引用，GC 自动清理
            2. Guava Cache — expireAfterAccess/expireAfterWrite
            3. Caffeine — 高性能缓存，支持过期/大小限制
            4. Redis — 外部缓存，支持 TTL
            5. LinkedHashMap（LRU）— accessOrder=true + removeEldestEntry
            """);

        result.put("LRU 缓存示例", """
            Map<String, Object> lruCache = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    return size() > 100; // 最多 100 个元素
                }
            };
            """);

        return result;
    }

    /**
     * 演示如何排查内存泄漏
     */
    public Map<String, Object> showLeakDiagnosis() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("Step 1: 确认泄漏", """
            jstat -gcutil <pid> 1000
            观察：
            - Old 区使用率持续增长
            - Full GC 频率增加但回收效果差
            - Full GC 后 Old 区使用率不下降
            """);

        result.put("Step 2: 获取堆转储", """
            方式1: jmap -dump:format=b,file=heap.hprof <pid>
            方式2: -XX:+HeapDumpOnOutOfMemoryError（OOM 时自动 dump）
            方式3: Arthas: heapdump /tmp/heap.hprof
            方式4: jcmd <pid> GC.heap_dump /tmp/heap.hprof
            """);

        result.put("Step 3: 分析堆转储", """
            工具：Eclipse MAT（Memory Analyzer Tool）
            1. 打开 heap.hprof
            2. Leak Suspects Report — 自动分析可疑泄漏点
            3. Dominator Tree — 查看占用内存最大的对象
            4. Histogram — 按类统计对象数量和大小
            5. GC Roots — 查看对象为什么不能被回收
            """);

        result.put("Step 4: 代码修复", """
            1. 找到持有引用的 GC Root
            2. 分析为什么引用没有释放
            3. 修改代码（remove/close/置 null）
            4. 验证修复（压测 + 观察 GC）
            """);

        return result;
    }
}
