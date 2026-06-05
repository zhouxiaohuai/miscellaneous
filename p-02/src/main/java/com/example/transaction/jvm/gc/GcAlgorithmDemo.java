package com.example.transaction.jvm.gc;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GC 算法原理演示
 *
 * 垃圾回收要解决三个问题：
 * 1. 哪些对象是垃圾？（可达性分析）
 * 2. 怎么回收？（回收算法）
 * 3. 什么时候回收？（触发条件）
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │                  可达性分析（GC Roots）                    │
 * │                                                          │
 * │  GC Roots:                                               │
 * │  - 虚拟机栈中引用的对象（局部变量）                         │
 * │  - 方法区中 static 变量引用的对象                          │
 * │  - 方法区中常量引用的对象                                  │
 * │  - 本地方法栈中 JNI 引用的对象                             │
 * │  - synchronized 锁持有的对象                              │
 * │                                                          │
 * │  从 GC Roots 出发，沿引用链不可达的对象 → 垃圾              │
 * └─────────────────────────────────────────────────────────┘
 *
 * 四种回收算法：
 *
 * 1. 标记-清除（Mark-Sweep）
 *    - 标记存活对象，清除未标记对象
 *    - 缺点：内存碎片
 *
 * 2. 标记-复制（Mark-Copy）
 *    - 把存活对象复制到另一块空间，清空原空间
 *    - 缺点：空间利用率 50%
 *
 * 3. 标记-整理（Mark-Compact）
 *    - 标记存活对象，向一端移动，清除边界外内存
 *    - 缺点：移动对象开销大
 *
 * 4. 分代收集（Generational）
 *    - 新生代用复制算法（对象存活率低）
 *    - 老年代用标记-整理（对象存活率高）
 */
@Component
public class GcAlgorithmDemo {

    /**
     * 演示四种 GC 算法的特点和适用场景
     */
    public Map<String, Object> demonstrateAlgorithms() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("1. 标记-清除（Mark-Sweep）", """
            步骤：
            1. 从 GC Roots 遍历，标记所有存活对象
            2. 遍历堆，清除未标记对象

            优点：实现简单，速度快
            缺点：
              - 内存碎片（清除后空间不连续）
              - 大对象可能找不到连续空间 → 提前触发 GC

            适用：老年代（CMS 收集器的并发清除阶段）

            内存变化：
            [■ ■ □ ■ □ □ ■ □ ■ ■]  （■ 存活 □ 垃圾）
            [■ ■ · ■ · · ■ · ■ ■]  （· 清除后空闲，但碎片化）
            """);

        result.put("2. 标记-复制（Mark-Copy）", """
            步骤：
            1. 把内存分为两块（From 和 To）
            2. 只使用 From 区
            3. GC 时把存活对象复制到 To 区
            4. 清空 From 区
            5. From 和 To 角色互换

            优点：无内存碎片，分配效率高（指针碰撞）
            缺点：空间利用率 50%

            优化（Appel 引用）：
            Eden : Survivor0 : Survivor1 = 8 : 1 : 1
            每次只浪费 10% 空间

            适用：新生代（对象存活率低，复制成本小）

            内存变化：
            From: [■ □ ■ □ □ ■ □ ■]  To: [· · · · · · · ·]
            From: [· · · · · · · ·]  To: [■ ■ ■ ■ · · · ·]  （只复制存活对象）
            """);

        result.put("3. 标记-整理（Mark-Compact）", """
            步骤：
            1. 从 GC Roots 遍历，标记所有存活对象
            2. 把存活对象向一端移动
            3. 清除边界外的内存

            优点：无内存碎片，空间利用率 100%
            缺点：移动对象开销大（需要更新引用）

            适用：老年代（对象存活率高，复制成本大）

            内存变化：
            [■ □ ■ □ □ ■ □ ■ ■ ■]
            [■ ■ ■ ■ ■ ■ ■ · · ·]  （存活对象紧凑排列）
            """);

        result.put("4. 分代收集（Generational）", """
            核心思想：不同生命周期的对象用不同算法

            新生代（Young Generation）：
            - 对象存活率低（大部分朝生夕灭）
            - 使用标记-复制算法
            - 分为 Eden + S0 + S1（8:1:1）
            - Minor GC 频繁但速度快

            老年代（Old Generation）：
            - 对象存活率高（长期存活）
            - 使用标记-清除或标记-整理
            - Major GC / Full GC 频率低但速度慢

            分代依据（弱分代假说）：
            - 绝大多数对象朝生夕灭
            - 熬过多次 GC 的对象更难消亡
            """);

        return result;
    }

    /**
     * 演示对象分配与晋升流程
     *
     * 对象分配流程：
     * 1. 新对象 → Eden 区
     * 2. Eden 满 → Minor GC → 存活对象 → Survivor 区
     * 3. 每次 Minor GC 存活 → 年龄 +1
     * 4. 年龄达到阈值（默认 15）→ 晋升到 Old 区
     * 5. 大对象直接分配到 Old 区（-XX:PretenureSizeThreshold）
     * 6. 动态年龄判断：Survivor 中同龄对象超过一半 → 该年龄及以上直接晋升
     */
    public Map<String, Object> demonstrateObjectAllocation() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("对象分配流程", """
            ┌─────────────────────────────────────────────────┐
            │ 新对象                                           │
            │   │                                              │
            │   ├─ 大对象? ──是──→ 直接进入 Old 区              │
            │   │                                              │
            │   └─ 否 → Eden 区                                │
            │          │                                       │
            │       Eden 满?                                    │
            │          │                                       │
          Minor GC       │                                       │
            │          ▼                                       │
            │   存活对象 → Survivor（From）                     │
            │          │                                       │
            │       年龄 +1                                     │
            │          │                                       │
            │       年龄 >= 15?                                 │
            │        /    \\                                    │
            │      是      否                                   │
            │      ↓       ↓                                   │
            │    Old    Survivor（To）                          │
            │                    │                             │
            │              From ↔ To 互换                      │
            └─────────────────────────────────────────────────┘
            """);

        result.put("关键参数", """
            -XX:MaxTenuringThreshold=15    晋升年龄阈值（默认 15）
            -XX:PretenureSizeThreshold     大对象直接进 Old 的阈值
            -XX:TargetSurvivorRatio=50     Survivor 使用率阈值（动态年龄）
            -XX:+UseAdaptiveSizePolicy     自适应大小策略（默认开启）
            """);

        result.put("动态年龄判断", """
            规则：Survivor 区中某年龄所有对象大小之和 > Survivor 空间 / 2
            则 >= 该年龄的对象直接进入 Old 区

            示例：
            年龄1的对象 2MB，年龄2的对象 3MB，Survivor 10MB
            2 + 3 = 5MB >= 10MB / 2 = 5MB
            → 年龄 >= 2 的对象直接进入 Old 区

            意义：避免对象长期滞留 Survivor，提高 Survivor 利用率
            """);

        return result;
    }

    /**
     * 演示 GC 触发条件
     */
    public Map<String, Object> demonstrateGcTriggers() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("Minor GC 触发条件", """
            Eden 区空间不足时触发
            - 新对象分配失败
            - TLAB 分配失败

            特点：
            - 频率高（对象分配频繁）
            - 速度快（新生代存活对象少）
            - 通常会 Stop-The-World（暂停用户线程）
            """);

        result.put("Major GC 触发条件", """
            Old 区空间不足时触发
            - 对象从 Survivor 晋升到 Old 失败
            - 大对象直接分配到 Old 失败

            特点：
            - 频率低（老年代增长慢）
            - 速度慢（老年代空间大，存活对象多）
            - 通常伴随 Full GC
            """);

        result.put("Full GC 触发条件", """
            1. Old 区空间不足
            2. Metaspace 空间不足（加载大量类）
            3. System.gc() 建议触发（-XX:+ExplicitGCInvokesConcurrent 可避免）
            4. CMS GC 出现 Concurrent Mode Failure
            5. 空间担保失败（HandlePromotionFailure）

            特点：
            - 回收整个堆 + Metaspace
            - 速度最慢
            - Stop-The-World 时间最长
            - 应尽量避免或减少 Full GC
            """);

        return result;
    }

    /**
     * 当前 GC 信息
     */
    public Map<String, Object> showCurrentGcInfo() {
        Map<String, Object> result = new LinkedHashMap<>();

        java.lang.management.GarbageCollectorMXBean youngGc = null;
        java.lang.management.GarbageCollectorMXBean oldGc = null;

        for (var gc : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            String name = gc.getName();
            if (name.contains("Young") || name.contains("ParNew") || name.contains("Copy") || name.contains("G1 Young")) {
                youngGc = gc;
            } else {
                oldGc = gc;
            }
        }

        if (youngGc != null) {
            Map<String, Object> young = new LinkedHashMap<>();
            young.put("名称", youngGc.getName());
            young.put("收集次数", youngGc.getCollectionCount());
            young.put("收集耗时", youngGc.getCollectionTime() + " ms");
            young.put("内存池", String.join(", ", youngGc.getMemoryPoolNames()));
            result.put("新生代 GC", young);
        }

        if (oldGc != null) {
            Map<String, Object> old = new LinkedHashMap<>();
            old.put("名称", oldGc.getName());
            old.put("收集次数", oldGc.getCollectionCount());
            old.put("收集耗时", oldGc.getCollectionTime() + " ms");
            old.put("内存池", String.join(", ", oldGc.getMemoryPoolNames()));
            result.put("老年代 GC", old);
        }

        return result;
    }
}
