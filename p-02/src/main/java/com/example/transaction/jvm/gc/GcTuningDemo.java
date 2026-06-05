package com.example.transaction.jvm.gc;

import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GC 调优参数与工具演示
 *
 * GC 调优目标：
 * 1. 低延迟：STW 停顿时间短（Web 服务）
 * 2. 高吞吐量：用户代码执行时间占比高（批处理）
 * 3. 低内存：用最少的内存完成任务（嵌入式）
 *
 * 调优原则：
 * 1. 大多数场景不需要调优（JDK 默认值已很好）
 * 2. 先确定目标（延迟 or 吞吐量）
 * 3. 选择合适的收集器
 * 4. 调整堆大小和分代比例
 * 5. 监控 GC 日志，找到瓶颈
 */
@Component
public class GcTuningDemo {

    /**
     * 常用 GC 参数速查
     */
    public Map<String, Object> showGcParameters() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("堆大小参数", """
            -Xms<size>              初始堆大小（建议与 -Xmx 相同，避免动态扩缩）
            -Xmx<size>              最大堆大小
            -Xmn<size>              新生代大小
            -Xss<size>              线程栈大小（默认 1MB）
            -XX:NewRatio=2          Old : Young = 2 : 1
            -XX:SurvivorRatio=8     Eden : S0 : S1 = 8 : 1 : 1
            -XX:MaxMetaspaceSize    Metaspace 最大值（默认无限制）
            """);

        result.put("GC 收集器参数", """
            -XX:+UseSerialGC                Serial + Serial Old
            -XX:+UseParallelGC              Parallel Scavenge + Parallel Old（JDK 8 默认）
            -XX:+UseConcMarkSweepGC         ParNew + CMS
            -XX:+UseG1GC                    G1（JDK 9+ 默认）
            -XX:+UseZGC                     ZGC（JDK 15+）
            -XX:+UseShenandoahGC            Shenandoah
            """);

        result.put("GC 调优参数", """
            -XX:MaxGCPauseMillis=200    目标最大停顿时间（G1/ZGC）
            -XX:GCTimeRatio=99          吞吐量目标（99% 用户代码）
            -XX:MaxTenuringThreshold=15 晋升年龄阈值
            -XX:PretenureSizeThreshold  大对象直接进 Old 的阈值
            -XX:InitiatingHeapOccupancyPercent=45  G1 触发并发标记的堆占用比
            -XX:G1HeapRegionSize        G1 Region 大小
            """);

        result.put("GC 日志参数", """
            JDK 8:
            -XX:+PrintGCDetails             打印 GC 详情
            -XX:+PrintGCDateStamps          打印时间戳
            -Xloggc:gc.log                  输出到文件
            -XX:+PrintGCTimeStamps          打印 GC 时间
            -XX:+PrintHeapAtGC              GC 前后打印堆信息

            JDK 9+（统一日志框架）:
            -Xlog:gc*:file=gc.log:time,uptime,level,tags
            -Xlog:gc*:stdout:time,level,tags   输出到控制台
            -Xlog:gc*                            所有 GC 日志
            """);

        result.put("诊断参数", """
            -XX:+HeapDumpOnOutOfMemoryError     OOM 时自动 dump
            -XX:HeapDumpPath=./                 dump 文件路径
            -XX:+PrintFlagsFinal                打印所有 JVM 参数
            -XX:+UnlockDiagnosticVMOptions      解锁诊断选项
            -XX:+PrintCompilation               打印 JIT 编译
            -XX:+PrintInlining                  打印内联决策
            """);

        return result;
    }

    /**
     * GC 日志分析示例
     */
    public Map<String, Object> showGcLogAnalysis() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("G1 Young GC 日志示例", """
            [GC pause (G1 Evacuation Pause) (young), 0.0034567 secs]
               [Parallel Time: 3.2 ms, GC Workers: 8]
                  [GC Worker Start (ms):  1234.1  1234.1  1234.2 ...]
                  [Ext Root Scanning (ms): 0.5  0.4  0.5 ...]
                  [Update RS (ms):         0.1  0.1  0.1 ...]
                  [Scan RS (ms):           0.2  0.2  0.2 ...]
                  [Code Root Scanning (ms):0.0  0.0  0.0 ...]
                  [Object Copy (ms):       2.3  2.3  2.3 ...]
               [Eden: 100.0M(100.0M)->0.0B(100.0M)
                Survivors: 10.0M->10.0M
                Heap: 150.0M(256.0M)->60.0M(256.0M)]

            关键指标：
            - pause time: 0.0034567 secs（STW 停顿时间）
            - Eden: 100M → 0M（回收了 100M）
            - Heap: 150M → 60M（堆使用减少 90M）
            """);

        result.put("Full GC 日志示例", """
            [Full GC (Allocation Failure)
              [Tenured: 200.0M->180.0M(200.0M), 0.150 secs]
              [Metaspace: 30.0M->30.0M(100.0M)]
              Heap: 250.0M(256.0M)->180.0M(256.0M)]

            关键指标：
            - Allocation Failure: Old 区空间不足
            - Tenured: 200M → 180M（只回收了 20M，大部分是长期存活对象）
            - 耗时 0.150 秒（比 Young GC 慢很多）
            """);

        result.put("日志分析要点", """
            1. GC 频率：过于频繁说明堆太小或对象分配太快
            2. GC 耗时：STW 时间过长影响响应时间
            3. 回收效果：Full GC 后堆使用率不下降 → 可能有内存泄漏
            4. 晋升速率：对象过早进入 Old → 调大新生代
            5. 分配失败：频繁 Allocation Failure → 调大堆或优化代码
            """);

        return result;
    }

    /**
     * GC 调优工具介绍
     */
    public Map<String, Object> showGcTools() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("jstat — GC 统计", """
            jstat -gc <pid> 1000          每秒打印 GC 统计
            jstat -gcutil <pid> 1000      各区使用率百分比
            jstat -gccapacity <pid> 1000  各区容量

            输出字段：
            S0/S1: Survivor 使用率
            E: Eden 使用率
            O: Old 使用率
            M: Metaspace 使用率
            YGC: Young GC 次数
            YGCT: Young GC 总耗时
            FGC: Full GC 次数
            FGCT: Full GC 总耗时
            GCT: GC 总耗时
            """);

        result.put("jmap — 堆信息", """
            jmap -heap <pid>              堆配置和使用情况
            jmap -histo <pid>             对象数量排名（前 N）
            jmap -histo:live <pid>        活对象排名（触发 Full GC）
            jmap -dump:format=b,file=heap.hprof <pid>  堆转储
            """);

        result.put("jstack — 线程信息", """
            jstack <pid>                  线程栈信息
            jstack -l <pid>               包含锁信息
            用途：排查死锁、线程阻塞
            """);

        result.put("jcmd — 综合诊断", """
            jcmd <pid> VM.version         JVM 版本
            jcmd <pid> VM.flags           JVM 参数
            jcmd <pid> GC.heap_info      堆信息
            jcmd <pid> GC.heap_dump heap.hprof  堆转储
            jcmd <pid> VM.metaspace       Metaspace 信息
            jcmd <pid> Thread.print       线程栈
            """);

        result.put("Arthas — 在线诊断", """
            java -jar arthas-boot.jar     启动 Arthas
            dashboard                     实时面板（CPU/内存/GC/线程）
            thread -n 3                   CPU 最高的 3 个线程
            heapdump /tmp/heap.hprof      堆转储
            jvm                           JVM 信息
            memory                        内存使用
            gc                            GC 统计
            trace <class> <method>        方法调用链路耗时
            watch <class> <method>        观察方法参数/返回值/异常
            """);

        result.put("可视化工具", """
            1. Eclipse MAT — 堆转储分析（泄漏检测）
            2. VisualVM — 实时监控（CPU/内存/线程/GC）
            3. JConsole — JMX 监控
            4. GCViewer — GC 日志可视化分析
            5. GCEasy — 在线 GC 日志分析（gceasy.io）
            6. Prometheus + Grafana — 生产环境监控
            """);

        return result;
    }

    /**
     * GC 调优实战案例
     */
    public Map<String, Object> showTuningCases() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("案例1: Full GC 频繁", """
            现象：Full GC 每 10 分钟一次，每次停顿 500ms
            排查：
            1. jstat -gcutil 观察 Old 区持续增长
            2. jmap -histo 发现某类对象数量异常
            3. MAT 分析发现缓存未设置过期

            解决：
            1. 缓存加过期策略（Caffeine expireAfterWrite）
            2. 调大堆 -Xmx2g → -Xmx4g（治标）
            3. 修复泄漏代码（治本）
            """);

        result.put("案例2: Young GC 停顿长", """
            现象：Young GC 停顿 200ms，影响接口响应
            排查：
            1. GC 日志发现 Object Copy 耗时长
            2. Survivor 空间不足，大量对象直接晋升 Old

            解决：
            1. 调大新生代 -XX:NewRatio=1（Old:Young = 1:1）
            2. 调大 Survivor -XX:SurvivorRatio=6
            3. 换用 G1（-XX:MaxGCPauseMillis=50）
            """);

        result.put("案例3: Metaspace OOM", """
            现象：java.lang.OutOfMemoryError: Metaspace
            排查：
            1. jstat -gcutil 观察 M 区持续增长
            2. 发现大量 CGLIB 代理类未回收

            解决：
            1. 设置 -XX:MaxMetaspaceSize=512m
            2. 检查动态代理是否合理
            3. 避免重复创建 ClassLoader
            """);

        result.put("调优流程", """
            1. 确定目标（延迟 or 吞吐量）
            2. 选择收集器（G1/ZGC/Parallel）
            3. 设置堆大小（-Xms = -Xmx）
            4. 开启 GC 日志
            5. 压测 + 监控
            6. 分析 GC 日志找到瓶颈
            7. 调整参数
            8. 重复 5-7 直到达标

            经验值：
            - 堆大小：活跃数据的 1.5~2 倍
            - Young GC 频率：每秒 < 1 次
            - Full GC 频率：每天 < 1 次
            - GC 停顿：Web 服务 < 100ms
            """);

        return result;
    }

    /**
     * 当前 JVM 运行时信息
     */
    public Map<String, Object> showCurrentJvmInfo() {
        Map<String, Object> result = new LinkedHashMap<>();

        Runtime rt = Runtime.getRuntime();
        result.put("堆内存", Map.of(
            "最大", formatBytes(rt.maxMemory()),
            "总", formatBytes(rt.totalMemory()),
            "空闲", formatBytes(rt.freeMemory()),
            "已用", formatBytes(rt.totalMemory() - rt.freeMemory())
        ));

        result.put("GC 收集器", getGcInfo());

        result.put("JVM 参数", ManagementFactory.getRuntimeMXBean().getInputArguments());

        return result;
    }

    private Map<String, Object> getGcInfo() {
        Map<String, Object> gcInfo = new LinkedHashMap<>();
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcInfo.put(gc.getName(), Map.of(
                "次数", gc.getCollectionCount(),
                "耗时", gc.getCollectionTime() + " ms",
                "内存池", String.join(", ", gc.getMemoryPoolNames())
            ));
        }
        return gcInfo;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
