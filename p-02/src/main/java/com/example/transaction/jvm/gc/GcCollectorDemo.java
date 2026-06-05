package com.example.transaction.jvm.gc;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GC 收集器对比演示
 *
 * 七种经典收集器：
 *
 * ┌──────────────────────────────────────────────────────────────┐
 * │                        堆内存                                 │
 * │  ┌─────────────────────────┬──────────────────────────────┐  │
 * │  │       新生代              │          老年代               │  │
 * │  │  Serial                  │  Serial Old (MSC)            │  │
 * │  │  ParNew                  │  CMS                         │  │
 * │  │  Parallel Scavenge       │  Parallel Old                │  │
 * │  │                          │                              │  │
 * │  │      G1 ─────────────────┼───────────────────────       │  │
 * │  │      ZGC ────────────────┼───────────────────────       │  │
 * │  │      Shenandoah ─────────┼───────────────────────       │  │
 * │  └─────────────────────────┴──────────────────────────────┘  │
 * └──────────────────────────────────────────────────────────────┘
 *
 * 收集器搭配关系：
 * Serial     → Serial Old
 * ParNew     → Serial Old / CMS
 * Parallel   → Parallel Old
 * G1         （整堆）
 * ZGC        （整堆）
 * Shenandoah （整堆）
 */
@Component
public class GcCollectorDemo {

    /**
     * 七种收集器详细对比
     */
    public Map<String, Object> compareCollectors() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("1. Serial 收集器", """
            类型：单线程，Stop-The-World
            算法：新生代-复制，老年代-标记-整理
            参数：-XX:+UseSerialGC
            场景：Client 模式、嵌入式、小堆（< 100MB）
            优点：简单高效（单线程无交互开销）
            缺点：STW 时间长
            """);

        result.put("2. ParNew 收集器", """
            类型：多线程版 Serial
            算法：新生代-复制
            参数：-XX:+UseParNewGC
            场景：配合 CMS 使用（唯一能与 CMS 配合的新生代收集器）
            优点：多线程并行，缩短 STW
            缺点：单线程环境不如 Serial
            """);

        result.put("3. Parallel Scavenge 收集器", """
            类型：多线程，吞吐量优先
            算法：新生代-复制
            参数：-XX:+UseParallelGC
            场景：后台计算、批处理
            优点：高吞吐量（用户代码时间 / 总时间）
            缺点：STW 时间不可控
            关键参数：
              -XX:MaxGCPauseMillis=200  最大停顿时间
              -XX:GCTimeRatio=99         吞吐量目标（99%）
              -XX:+UseAdaptiveSizePolicy 自适应策略
            """);

        result.put("4. Serial Old 收集器", """
            类型：单线程，老年代
            算法：标记-整理
            参数：-XX:+UseSerialGC
            场景：CMS 的后备方案（Concurrent Mode Failure 时）
            """);

        result.put("5. Parallel Old 收集器", """
            类型：多线程，老年代
            算法：标记-整理
            参数：-XX:+UseParallelOldGC
            场景：配合 Parallel Scavenge 使用
            JDK 8 默认组合：Parallel Scavenge + Parallel Old
            """);

        result.put("6. CMS 收集器", """
            类型：并发，低延迟
            算法：标记-清除
            参数：-XX:+UseConcMarkSweepGC
            目标：最短停顿时间

            四个阶段：
            1. 初始标记（STW）— 标记 GC Roots 直接关联的对象（快）
            2. 并发标记 — 从 GC Roots 遍历整个引用链（与用户线程并行）
            3. 重新标记（STW）— 修正并发标记期间变动的对象
            4. 并发清除 — 清除垃圾对象（与用户线程并行）

            优点：停顿时间短（只有初始标记和重新标记 STW）
            缺点：
              - 内存碎片（标记-清除算法）
              - CPU 敏感（并发阶段占用 CPU）
              - 浮动垃圾（并发清除期间新产生的垃圾）
              - Concurrent Mode Failure（CMS 来不及回收，退化为 Serial Old）
            """);

        result.put("7. G1 收集器", """
            类型：整堆，可预测停顿
            算法：Region 化分代
            参数：-XX:+UseG1GC（JDK 9+ 默认）
            目标：在 M 毫秒内回收 N% 的垃圾

            核心设计：
            - 把堆划分为大小相等的 Region（1MB~32MB）
            - 每个 Region 可以是 Eden/Survivor/Old/Humongous
            - 优先回收价值最大的 Region（Garbage First 名称由来）

            四个阶段：
            1. 初始标记（STW）— 标记 GC Roots
            2. 并发标记 — 遍历引用链
            3. 最终标记（STW）— 处理并发标记遗留
            4. 筛选回收（STW）— 根据价值排序，回收 Region

            关键参数：
              -XX:MaxGCPauseMillis=200   目标停顿时间
              -XX:G1HeapRegionSize=N     Region 大小
              -XX:InitiatingHeapOccupancyPercent=45  触发并发标记的堆占用比
            """);

        result.put("8. ZGC 收集器", """
            类型：整堆，超低延迟
            算法：着色指针 + 读屏障
            参数：-XX:+UseZGC（JDK 15+ 生产就绪）
            目标：停顿时间 < 10ms（不随堆大小增长）

            核心技术：
            - 着色指针（Colored Pointer）：在指针中存储 GC 状态
            - 读屏障（Load Barrier）：访问对象时检查指针状态
            - 并发整理：对象移动与用户线程并行

            特点：
            - 停顿时间极短（< 10ms）
            - 支持 TB 级堆
            - 不分代（JDK 21 开始实验性分代）
            - 吞吐量略低于 G1（约 5-10%）
            """);

        result.put("9. Shenandoah 收集器", """
            类型：整堆，超低延迟
            算法：着色指针 + 读写屏障
            参数：-XX:+UseShenandoahGC
            目标：停顿时间与堆大小无关

            与 ZGC 的区别：
            - 使用读写屏障（ZGC 只用读屏障）
            - 支持并发整理（与 ZGC 类似）
            - Oracle JDK 不支持（OpenJDK 支持）
            """);

        return result;
    }

    /**
     * 收集器选择建议
     */
    public Map<String, Object> recommendCollector() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("JDK 8 默认", "Parallel Scavenge + Parallel Old（吞吐量优先）");
        result.put("JDK 9+ 默认", "G1（平衡吞吐量和延迟）");

        result.put("选择指南", """
            ┌────────────────┬─────────────────────────────────┐
            │ 场景            │ 推荐收集器                       │
            ├────────────────┼─────────────────────────────────┤
            │ 小堆 < 100MB   │ Serial（简单高效）                │
            │ 后台计算        │ Parallel（吞吐量优先）            │
            │ Web 服务       │ G1（平衡）或 ZGC（低延迟）        │
            │ 金融/实时       │ ZGC（停顿 < 10ms）               │
            │ 大堆 > 16GB    │ ZGC 或 Shenandoah               │
            │ JDK 8          │ G1（-XX:+UseG1GC）              │
            │ JDK 11+        │ ZGC（-XX:+UseZGC）              │
            │ JDK 17+        │ ZGC（生产就绪）                   │
            └────────────────┴─────────────────────────────────┘
            """);

        result.put("当前 JVM 收集器", getCurrentCollector());

        return result;
    }

    /**
     * 演示 G1 的 Region 机制
     */
    public Map<String, Object> demonstrateG1Regions() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("G1 内存布局", """
            ┌─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┐
            │  E  │  E  │  S  │  O  │  O  │  H  │  H  │  E  │
            ├─────┼─────┼─────┼─────┼─────┼─────┼─────┼─────┤
            │  O  │  O  │  E  │  S  │  O  │  E  │  O  │  O  │
            ├─────┼─────┼─────┼─────┼─────┼─────┼─────┼─────┤
            │  E  │  O  │  O  │  E  │  O  │  O  │  S  │  E  │
            └─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┘

            E = Eden    S = Survivor    O = Old    H = Humongous
            每个 Region 大小相同（1MB~32MB），角色可变
            """);

        result.put("Humongous 对象", """
            大小超过 Region 50% 的对象 → Humongous Region
            - 分配在连续的 Humongous Region 中
            - 不参与 Young GC（只在 Mixed GC 中回收）
            - 容易导致碎片化和提前触发 GC
            建议：避免使用大对象（大数组、大集合）
            """);

        result.put("G1 回收模式", """
            Young GC：只回收 Eden 和 Survivor Region
            Mixed GC：回收 Eden + Survivor + 部分 Old Region
            Full GC：Serial Old（兜底方案，应尽量避免）

            Mixed GC 触发条件：
            Old Region 占比超过 -XX:InitiatingHeapOccupancyPercent=45%
            """);

        return result;
    }

    private String getCurrentCollector() {
        for (var gc : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            String name = gc.getName();
            if (name.contains("G1")) return "G1";
            if (name.contains("ZGC")) return "ZGC";
            if (name.contains("Shenandoah")) return "Shenandoah";
            if (name.contains("MarkSweep") || name.contains("CMS")) return "CMS";
            if (name.contains("Scavenge")) return "Parallel Scavenge";
            if (name.contains("Copy")) return "Serial";
        }
        return "Unknown";
    }
}
