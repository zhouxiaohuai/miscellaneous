package com.example.transaction.jvm.execution;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JIT 编译与优化演示
 *
 * Java 代码执行方式：
 *
 *   .java 源码
 *      ↓ javac 编译
 *   .class 字节码（平台无关）
 *      ↓ 类加载器加载
 *   方法区（字节码 + 元数据）
 *      ↓
 *   ┌─────────────────────────────────────────────────┐
 *   │              执行引擎                             │
 *   │  ┌─────────────────────────────────────────────┐ │
 *   │  │ 解释执行（Interpreter）                       │ │
 *   │  │ 逐条翻译字节码→机器码，启动快，执行慢          │ │
 *   │  └─────────────────────────────────────────────┘ │
 *   │  ┌─────────────────────────────────────────────┐ │
 *   │  │ JIT 编译（Just-In-Time Compiler）             │ │
 *   │  │ 热点代码→机器码缓存，启动慢，执行快            │ │
 *   │  │                                              │ │
 *   │  │ C1 编译器（Client）：快速编译，简单优化        │ │
 *   │  │ C2 编译器（Server）：深度优化，编译较慢        │ │
 *   │  │ 分层编译：C1 + C2 配合（默认模式）            │ │
 *   │  └─────────────────────────────────────────────┘ │
 *   └─────────────────────────────────────────────────┘
 *
 * 热点代码探测：
 * - 方法调用计数器：方法被调用超过阈值（-XX:CompileThreshold=10000）→ JIT 编译
 * - 回边计数器：循环体执行超过阈值 → 触发 OSR（栈上替换）
 *
 * JIT 优化手段：
 * 1. 方法内联（Inlining）— 把被调用方法的代码展开到调用处
 * 2. 逃逸分析（Escape Analysis）— 分析对象是否逃出方法作用域
 * 3. 标量替换（Scalar Replacement）— 把对象拆成基本类型
 * 4. 锁消除（Lock Elimination）— 去掉不可能竞争的同步锁
 * 5. 循环展开（Loop Unrolling）— 减少循环跳转开销
 * 6. 常量折叠（Constant Folding）— 编译期计算常量表达式
 */
@Component
public class JitCompileDemo {

    /**
     * 演示解释执行 vs JIT 编译的性能差异
     *
     * 解释执行：逐条翻译字节码，启动快但执行慢
     * JIT 编译：热点代码编译为机器码，启动慢但执行快
     *
     * -XX:+PrintCompilation 可以观察 JIT 编译事件
     */
    public Map<String, Object> demonstrateInterpretVsJit(int iterations) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 预热（让 JIT 编译器有机会编译热点代码）
        long warmupSum = 0;
        for (int i = 0; i < 10_000; i++) {
            warmupSum += simpleCalculation(i);
        }

        // JIT 编译后执行
        long start = System.nanoTime();
        long sum1 = 0;
        for (int i = 0; i < iterations; i++) {
            sum1 += simpleCalculation(i);
        }
        long jitTime = System.nanoTime() - start;

        result.put("迭代次数", iterations);
        result.put("预热后执行耗时", jitTime / 1_000_000 + " ms");
        result.put("结果", sum1);
        result.put("说明", """
            首次执行：解释执行，较慢
            热点代码（方法调用超过 10000 次）：JIT 编译为机器码
            后续执行：直接执行机器码，极快
            -XX:CompileThreshold=10000（默认，Client 模式 1500）
            -XX:+PrintCompilation（打印 JIT 编译日志）
            """);

        return result;
    }

    /**
     * 演示方法内联（Inlining）
     *
     * 方法内联是最重要的 JIT 优化：
     * 把被调用方法的代码展开到调用处，消除方法调用开销。
     *
     * 内联条件：
     * - 方法体足够小（-XX:MaxInlineSize=35 字节码）
     * - 热点方法体可以更大（-XX:FreqInlineSize=325 字节码）
     * - 非虚方法（final/static/private）更容易内联
     *
     * 内联效果：
     * 1. 消除方法调用开销（压栈、跳转、返回）
     * 2. 为后续优化创造条件（常量折叠、死代码消除）
     */
    public Map<String, Object> demonstrateMethodInlining(int iterations) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 方式1：小方法（容易被内联）
        long start = System.nanoTime();
        long sum1 = 0;
        for (int i = 0; i < iterations; i++) {
            sum1 += inlineFriendly(i); // 小方法，JIT 会内联
        }
        long time1 = System.nanoTime() - start;

        // 方式2：大方法（不容易被内联）
        start = System.nanoTime();
        long sum2 = 0;
        for (int i = 0; i < iterations; i++) {
            sum2 += inlineUnfriendly(i); // 大方法，可能不内联
        }
        long time2 = System.nanoTime() - start;

        result.put("小方法（可内联）", time1 / 1_000_000 + " ms");
        result.put("大方法（难内联）", time2 / 1_000_000 + " ms");
        result.put("性能差异", String.format("%.1f%%", (time2 - time1) * 100.0 / time1));
        result.put("说明", """
            内联优化：
            1. 小方法（< 35 字节码）自动内联
            2. 热点方法阈值放宽（< 325 字节码）
            3. final/static/private 方法更容易内联（非虚方法）
            4. 虚方法需要类型推断确认后才能内联

            建议：不要为了避免内联而不写小方法，JIT 编译器很聪明
            -XX:+PrintInlining（打印内联决策）
            """);

        return result;
    }

    /**
     * 演示逃逸分析 + 标量替换
     *
     * 逃逸分析：对象是否逃出方法作用域
     * - 不逃逸 → 可以栈上分配（方法结束自动回收）
     * - 不逃逸 → 可以标量替换（拆成基本类型变量）
     * - 不逃逸 → 可以锁消除（不可能竞争）
     *
     * 标量替换示例：
     * Point p = new Point(x, y);
     * return p.x + p.y;
     * ↓ 优化为
     * int px = x; int py = y;
     * return px + py;
     * 完全消除对象分配！
     */
    public Map<String, Object> demonstrateEscapeAnalysis(int iterations) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 场景1：标量替换优化（对象不逃逸）
        long start = System.nanoTime();
        long sum1 = 0;
        for (int i = 0; i < iterations; i++) {
            sum1 += scalarReplacement(i, i + 1);
        }
        long time1 = System.nanoTime() - start;

        // 场景2：对象逃逸（无法标量替换）
        start = System.nanoTime();
        long sum2 = 0;
        Point holder = null;
        for (int i = 0; i < iterations; i++) {
            holder = new Point(i, i + 1);
            sum2 += holder.x + holder.y;
        }
        long time2 = System.nanoTime() - start;

        result.put("标量替换（不逃逸）", time1 / 1_000_000 + " ms");
        result.put("堆分配（逃逸）", time2 / 1_000_000 + " ms");
        result.put("sum1", sum1);
        result.put("sum2", sum2);
        result.put("说明", """
            逃逸分析三种优化：
            1. 标量替换：对象拆成基本类型，不创建对象
            2. 栈上分配：对象在栈上分配，方法结束自动回收
            3. 锁消除：去掉不可能竞争的 synchronized

            VM args:
            -XX:+DoEscapeAnalysis（默认开启）
            -XX:+EliminateAllocations（默认开启）
            -XX:+EliminateLocks（默认开启）
            -XX:+PrintEscapeAnalysis（打印逃逸分析结果）
            """);

        return result;
    }

    /**
     * 演示 C1 vs C2 编译器
     *
     * 分层编译（Tiered Compilation）：
     * Level 0: 解释执行
     * Level 1: C1 编译，不带 profiling
     * Level 2: C1 编译，带有限 profiling
     * Level 3: C1 编译，带完整 profiling
     * Level 4: C2 编译，深度优化
     *
     * 执行路径：解释 → C1（收集信息）→ C2（深度优化）
     */
    public Map<String, Object> demonstrateTieredCompilation() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("编译级别", """
            Level 0: 解释执行（收集基本 profiling）
            Level 1: C1 编译，简单优化（不收集 profiling）
            Level 2: C1 编译，有限 profiling（方法调用计数、分支概率）
            Level 3: C1 编译，完整 profiling
            Level 4: C2 编译，深度优化（基于 profiling 数据）
            """);

        result.put("C1 vs C2", """
            ┌─────────┬────────────┬────────────┐
            │  对比项   │  C1 (Client)│  C2 (Server)│
            ├─────────┼────────────┼────────────┤
            │ 编译速度  │ 快          │ 慢          │
            │ 优化程度  │ 低          │ 高          │
            │ 适用场景  │ 启动速度    │ 峰值性能    │
            │ 内联阈值  │ 小          │ 大          │
            │ 逃逸分析  │ 基础        │ 深度        │
            └─────────┴────────────┴────────────┘
            """);

        result.put("分层编译流程", """
            方法首次调用
            → Level 0（解释执行，收集调用计数）
            → 计数达到阈值
            → Level 3（C1 编译 + 完整 profiling）
            → profiling 数据充分
            → Level 4（C2 编译 + 深度优化）

            优势：兼顾启动速度和峰值性能
            VM args: -XX:+TieredCompilation（默认开启）
            """);

        result.put("相关 JVM 参数", """
            -XX:+TieredCompilation          开启分层编译（默认）
            -XX:TieredStopAtLevel=4         最高编译级别（默认 4）
            -XX:CompileThreshold=10000      JIT 编译阈值
            -XX:+PrintCompilation           打印编译事件
            -XX:+PrintInlining              打印内联决策
            -XX:ReservedCodeCacheSize=240m  代码缓存大小
            """);

        return result;
    }

    // === 辅助方法 ===

    private long simpleCalculation(int n) {
        return (long) n * n + n;
    }

    // 小方法，容易被内联
    private long inlineFriendly(int x) {
        return x * x + x;
    }

    // 大方法，不容易被内联
    private long inlineUnfriendly(int x) {
        long result = x;
        result += x * 2;
        result += x * 3;
        result += x * 4;
        result += x * 5;
        result += x * 6;
        result += x * 7;
        result += x * 8;
        result += x * 9;
        result += x * 10;
        result += Math.sqrt(result);
        result += Math.sin(result);
        result += Math.cos(result);
        return result;
    }

    // 不逃逸，可以标量替换
    private long scalarReplacement(int x, int y) {
        Point p = new Point(x, y); // JIT 会优化为：int px = x; int py = y;
        return p.x + p.y;
    }

    static class Point {
        int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }
}
