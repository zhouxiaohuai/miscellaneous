package com.example.transaction.jvm.memory;

import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JVM 内存区域结构演示
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │                      JVM 内存                            │
 * ├──────────┬──────────┬──────────┬──────────┬─────────────┤
 * │ 程序计数器 │ 虚拟机栈  │ 本地方法栈 │    堆    │  方法区      │
 * │ (PC)     │ (Stack)  │ (Native) │ (Heap)   │ (Metaspace) │
 * ├──────────┴──────────┴──────────┼──────────┼─────────────┤
 * │         线程私有                 │  线程共享  │  线程共享     │
 * └────────────────────────────────┴──────────┴─────────────┘
 *
 * 各区域职责：
 *
 * 1. 程序计数器（PC Register）
 *    - 线程私有，记录当前执行的字节码行号
 *    - 唯一不会 OOM 的区域
 *
 * 2. 虚拟机栈（VM Stack）
 *    - 线程私有，每个方法调用创建一个栈帧
 *    - 栈帧包含：局部变量表、操作数栈、动态链接、方法返回地址
 *    - 可能 StackOverflowError（递归过深）或 OOM
 *
 * 3. 本地方法栈（Native Method Stack）
 *    - 为 native 方法服务（如 System.arraycopy、Unsafe 操作）
 *
 * 4. 堆（Heap）
 *    - 线程共享，对象实例和数组分配在此
 *    - 分代：Young（Eden + S0 + S1）→ Old
 *    - GC 主要工作区域
 *
 * 5. 方法区（Method Area / Metaspace）
 *    - 存储类信息、常量、静态变量、JIT 编译后的代码
 *    - JDK 8 后由 Metaspace 实现（本地内存，不受 -Xmx 限制）
 *    - 可能 OOM: Metaspace（加载大量类）
 */
@Component
public class MemoryStructureDemo {

    /**
     * 展示 JVM 内存各区域的当前使用情况
     */
    public Map<String, Object> showMemoryStructure() {
        Map<String, Object> result = new LinkedHashMap<>();

        MemoryMXBean mxb = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = mxb.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = mxb.getNonHeapMemoryUsage();

        // 堆内存
        Map<String, Object> heap = new LinkedHashMap<>();
        heap.put("初始大小", formatBytes(heapUsage.getInit()));
        heap.put("已使用", formatBytes(heapUsage.getUsed()));
        heap.put("已提交", formatBytes(heapUsage.getCommitted()));
        heap.put("最大值", formatBytes(heapUsage.getMax()));
        result.put("堆（Heap）", heap);

        // 非堆内存（方法区/Metaspace）
        Map<String, Object> nonHeap = new LinkedHashMap<>();
        nonHeap.put("初始大小", formatBytes(nonHeapUsage.getInit()));
        nonHeap.put("已使用", formatBytes(nonHeapUsage.getUsed()));
        nonHeap.put("已提交", formatBytes(nonHeapUsage.getCommitted()));
        nonHeap.put("最大值", nonHeapUsage.getMax() == -1 ? "无限制" : formatBytes(nonHeapUsage.getMax()));
        result.put("方法区（Metaspace）", nonHeap);

        // JVM 参数
        Map<String, Object> jvmArgs = new LinkedHashMap<>();
        Runtime rt = Runtime.getRuntime();
        jvmArgs.put("最大内存", formatBytes(rt.maxMemory()));
        jvmArgs.put("总内存", formatBytes(rt.totalMemory()));
        jvmArgs.put("空闲内存", formatBytes(rt.freeMemory()));
        jvmArgs.put("可用处理器", rt.availableProcessors());
        result.put("运行时", jvmArgs);

        // 内存区域说明
        result.put("说明", Map.of(
            "程序计数器", "线程私有，记录字节码行号，唯一不会 OOM 的区域",
            "虚拟机栈", "线程私有，每个方法一个栈帧（局部变量表+操作数栈+动态链接+返回地址）",
            "本地方法栈", "线程私有，为 native 方法服务",
            "堆", "线程共享，对象实例分配在此，GC 主要工作区域",
            "方法区", "线程共享，存储类信息/常量/静态变量，JDK8+ 用 Metaspace"
        ));

        return result;
    }

    /**
     * 演示堆内存的分代结构
     *
     * 堆内存布局：
     * ┌──────────────────────────────────────────────────┐
     * │                      堆（Heap）                    │
     * ├──────────────────────────────┬───────────────────┤
     * │        新生代（Young）        │    老年代（Old）    │
     * ├──────┬──────┬──────┤        │                   │
     * │ Eden │  S0  │  S1  │        │                   │
     * │ 80%  │ 10%  │ 10%  │        │                   │
     * └──────┴──────┴──────┴────────┴───────────────────┘
     *
     * 对象分配流程：
     * 1. 新对象 → Eden 区
     * 2. Eden 满 → Minor GC → 存活对象 → Survivor 区
     * 3. 年龄达到阈值（默认 15）→ 晋升到 Old 区
     * 4. Old 区满 → Major GC / Full GC
     */
    public Map<String, Object> showHeapGenerations() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 通过 MemoryMXBean 获取各内存池信息
        java.lang.management.MemoryPoolMXBean youngPool = null;
        java.lang.management.MemoryPoolMXBean oldPool = null;

        for (var pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getName().contains("Eden") || pool.getName().contains("Young")) {
                youngPool = pool;
            }
            if (pool.getName().contains("Old") || pool.getName().contains("Tenured")) {
                oldPool = pool;
            }
        }

        if (youngPool != null) {
            MemoryUsage usage = youngPool.getUsage();
            Map<String, Object> young = new LinkedHashMap<>();
            young.put("名称", youngPool.getName());
            young.put("已使用", formatBytes(usage.getUsed()));
            young.put("已提交", formatBytes(usage.getCommitted()));
            young.put("最大值", formatBytes(usage.getMax()));
            result.put("新生代（Young）", young);
        }

        if (oldPool != null) {
            MemoryUsage usage = oldPool.getUsage();
            Map<String, Object> old = new LinkedHashMap<>();
            old.put("名称", oldPool.getName());
            old.put("已使用", formatBytes(usage.getUsed()));
            old.put("已提交", formatBytes(usage.getCommitted()));
            old.put("最大值", formatBytes(usage.getMax()));
            result.put("老年代（Old）", old);
        }

        result.put("分代说明", Map.of(
            "Eden", "新对象首先分配在此（80%）",
            "Survivor", "Minor GC 后存活对象存放（S0/S1 各 10%，交替使用）",
            "Old", "长期存活对象（年龄>阈值）或大对象直接分配",
            "晋升阈值", "-XX:MaxTenuringThreshold=15（默认）"
        ));

        return result;
    }

    /**
     * 演示 OOM 场景 — 堆溢出
     * VM args: -Xmx128m -Xms128m
     *
     * 触发方式：不断创建大对象，不释放引用
     */
    public String demonstrateHeapOOM(int objectCount) {
        try {
            java.util.List<byte[]> list = new java.util.ArrayList<>();
            for (int i = 0; i < objectCount; i++) {
                list.add(new byte[1024 * 1024]); // 每次分配 1MB
            }
            return "分配了 " + objectCount + "MB 内存，未触发 OOM（堆足够大）";
        } catch (OutOfMemoryError e) {
            return "触发 OOM: " + e.getMessage() + "，已分配的对象会被 GC 回收";
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "N/A";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
