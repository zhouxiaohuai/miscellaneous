package com.example.transaction.jvm.memory;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 栈 vs 堆分配演示
 *
 * 核心区别：
 * ┌──────────────┬────────────────────┬────────────────────┐
 * │    对比项      │      虚拟机栈        │        堆           │
 * ├──────────────┼────────────────────┼────────────────────┤
 * │ 线程归属      │ 线程私有             │ 线程共享             │
 * │ 存储内容      │ 基本类型、引用地址    │ 对象实例、数组        │
 * │ 分配速度      │ 极快（指针移动）      │ 较慢（需要 GC 配合）  │
 * │ 生命周期      │ 方法结束即释放       │ GC 回收时释放        │
 * │ 大小限制      │ -Xss（默认 1MB）    │ -Xmx               │
 * │ 异常         │ StackOverflowError  │ OutOfMemoryError    │
 * └──────────────┴────────────────────┴────────────────────┘
 *
 * 栈帧结构：
 * ┌─────────────────────────┐  ← 栈顶
 * │     局部变量表            │  存放方法参数和局部变量
 * ├─────────────────────────┤
 * │     操作数栈              │  字节码指令的工作区
 * ├─────────────────────────┤
 * │     动态链接              │  指向运行时常量池的引用
 * ├─────────────────────────┤
 * │     方法返回地址           │  方法退出后继续执行的位置
 * └─────────────────────────┘  ← 栈底
 */
@Component
public class StackHeapDemo {

    /**
     * 演示基本类型在栈上分配
     *
     * 方法调用时：
     * 1. 创建新栈帧
     * 2. 参数 a, b 存入局部变量表（slot 0, 1）
     * 3. 计算结果存入操作数栈
     * 4. 返回后栈帧销毁，局部变量随之消失
     */
    public Map<String, Object> demonstrateStackAllocation() {
        Map<String, Object> result = new LinkedHashMap<>();

        int a = 10;       // 局部变量表 slot 0
        int b = 20;       // 局部变量表 slot 1
        int sum = a + b;  // 操作数栈：load a → load b → iadd → istore

        result.put("局部变量", Map.of(
            "a", a, "b", b, "sum", sum
        ));
        result.put("说明", "基本类型（int/long/double/float/boolean/byte/char/short）直接存在栈的局部变量表中");
        result.put("字节码", """
            iload_0    // 将 slot 0 (a=10) 压入操作数栈
            iload_1    // 将 slot 1 (b=20) 压入操作数栈
            iadd       // 弹出两个 int 相加，结果压栈
            istore_2   // 结果存入 slot 2 (sum)
            """);

        return result;
    }

    /**
     * 演示对象在堆上分配
     *
     * 对象分配过程：
     * 1. new 指令 → 检查类是否已加载
     * 2. 分配内存（指针碰撞 or 空闲列表）
     * 3. 初始化零值
     * 4. 设置对象头（Mark Word + 类型指针）
     * 5. 执行 <init> 构造方法
     * 6. 栈上引用指向堆中对象
     */
    public Map<String, Object> demonstrateHeapAllocation() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Person 对象分配在堆上
        Person person = new Person("张三", 25);

        // person 变量在栈的局部变量表中，存储的是堆中对象的引用地址
        result.put("栈上引用", "person → 0x" + Integer.toHexString(System.identityHashCode(person)));
        result.put("堆中对象", Map.of(
            "name", person.name,
            "age", person.age,
            "hashCode", System.identityHashCode(person)
        ));
        result.put("对象内存布局", """
            ┌─────────────────────────┐
            │     对象头（12-16 bytes）  │
            │  ├─ Mark Word (8 bytes)  │  hashCode + GC 年龄 + 锁标志
            │  └─ 类型指针 (4-8 bytes)  │  指向类元数据
            ├─────────────────────────┤
            │     实例数据              │
            │  ├─ name (引用, 4-8 bytes)│  → 堆中 String 对象
            │  └─ age (int, 4 bytes)   │
            ├─────────────────────────┤
            │     对齐填充              │  凑齐 8 字节倍数
            └─────────────────────────┘
            """);

        return result;
    }

    /**
     * 演示逃逸分析 — 栈上分配
     *
     * 逃逸分析（Escape Analysis）：
     * JIT 编译器分析对象是否逃出方法作用域
     * - 不逃逸 → 可以栈上分配（方法结束自动回收，无需 GC）
     * - 不逃逸 → 可以标量替换（把对象拆成基本类型）
     * - 逃逸 → 必须堆上分配
     *
     * 开启逃逸分析：-XX:+DoEscapeAnalysis（默认开启）
     * 开启标量替换：-XX:+EliminateAllocations（默认开启）
     */
    public Map<String, Object> demonstrateEscapeAnalysis() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 场景1：对象不逃逸 — 可能被栈上分配
        long start = System.nanoTime();
        long sum = 0;
        for (int i = 0; i < 1_000_000; i++) {
            sum += createPointAndCalculate(i, i + 1); // Point 对象不逃逸
        }
        long time1 = System.nanoTime() - start;

        // 场景2：对象逃逸 — 必须堆上分配
        start = System.nanoTime();
        Point escapedPoint = null;
        for (int i = 0; i < 1_000_000; i++) {
            escapedPoint = new Point(i, i + 1); // 赋给外部变量，逃逸了
        }
        long time2 = System.nanoTime() - start;

        result.put("不逃逸（栈上分配）", time1 / 1_000_000 + " ms");
        result.put("逃逸（堆上分配）", time2 / 1_000_000 + " ms");
        result.put("sum", sum);
        result.put("说明", """
            逃逸分析优化手段：
            1. 栈上分配：对象随栈帧销毁，无需 GC
            2. 标量替换：把对象拆成基本类型变量（int x, int y 代替 Point）
            3. 锁消除：不逃逸的对象不可能被多线程访问，去掉同步锁
            VM args: -XX:+DoEscapeAnalysis -XX:+EliminateAllocations
            """);

        return result;
    }

    /**
     * 演示递归导致 StackOverflowError
     *
     * 每次递归调用都会创建新栈帧，栈空间有限（-Xss 默认 1MB）
     */
    public Map<String, Object> demonstrateStackOverflow() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            infiniteRecursion(0);
        } catch (StackOverflowError e) {
            result.put("异常", "StackOverflowError");
            result.put("当前栈深度", currentDepth);
            result.put("说明", "递归过深导致栈空间耗尽，可通过 -Xss 调整栈大小");
            result.put("VM args", "-Xss2m（增大栈大小）或优化为迭代");
        }
        return result;
    }

    private int currentDepth = 0;

    private void infiniteRecursion(int depth) {
        currentDepth = depth;
        // 每个栈帧占用：局部变量 + 操作数栈 + 动态链接 + 返回地址
        // 大约几十到几百字节，取决于方法的局部变量数量
        long localVar1 = depth;
        long localVar2 = depth * 2;
        long localVar3 = depth * 3;
        infiniteRecursion(depth + 1);
    }

    private long createPointAndCalculate(int x, int y) {
        // Point 对象只在这个方法内使用，不逃逸
        // JIT 可能优化为栈上分配或标量替换
        Point p = new Point(x, y);
        return p.x + p.y;
    }

    static class Point {
        long x, y;
        Point(long x, long y) { this.x = x; this.y = y; }
    }

    static class Person {
        String name;
        int age;
        Person(String name, int age) { this.name = name; this.age = age; }
    }
}
