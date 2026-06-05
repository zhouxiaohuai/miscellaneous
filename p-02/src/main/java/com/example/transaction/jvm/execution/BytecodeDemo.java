package com.example.transaction.jvm.execution;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 字节码结构与反编译演示
 *
 * .class 文件结构：
 * ┌─────────────────────────────────────────┐
 * │              Class 文件                   │
 * ├─────────────────────────────────────────┤
 * │ 魔数 (4 bytes)     │ 0xCAFEBABE         │
 * │ 版本号 (4 bytes)   │ 次版本 + 主版本      │
 * │ 常量池 (变长)       │ 字面量 + 符号引用    │
 * │ 访问标志 (2 bytes)  │ public/abstract... │
 * │ 类索引 (2 bytes)    │ 指向父类            │
 * │ 父类索引 (2 bytes)  │                    │
 * │ 接口索引 (变长)      │ 实现的接口          │
 * │ 字段表 (变长)       │ 类的字段定义        │
 * │ 方法表 (变长)       │ 类的方法定义        │
 * │ 属性表 (变长)       │ 附加信息            │
 * └─────────────────────────────────────────┘
 *
 * 字节码指令分类：
 * - 加载/存储：iload, astore, aload
 * - 运算：iadd, imul, ddiv
 * - 类型转换：i2l, f2i, d2i
 * - 对象创建：new, newarray, anewarray
 * - 栈操作：pop, dup, swap
 * - 控制转移：ifeq, goto, tableswitch
 * - 方法调用：invokevirtual, invokeinterface, invokespecial, invokestatic, invokedynamic
 * - 异常：athrow
 * - 同步：monitorenter, monitorexit
 */
@Component
public class BytecodeDemo {

    /**
     * 演示常见字节码指令
     *
     * 用简单代码展示对应的字节码指令
     */
    public Map<String, Object> demonstrateBytecodeInstructions() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("1. 变量操作", """
            Java: int a = 10;
            字节码: bipush 10 → istore_1

            Java: int b = a + 20;
            字节码: iload_1 → bipush 20 → iadd → istore_2

            含义:
            - bipush: 将 byte 常量压入操作数栈
            - istore_N: 将 int 值存入局部变量表 slot N
            - iload_N: 从局部变量表 slot N 加载 int 值到操作数栈
            - iadd: 弹出两个 int 相加，结果压栈
            """);

        result.put("2. 方法调用", """
            Java: obj.method(args)

            5 种调用指令:
            ┌──────────────────┬─────────────────────────────────┐
            │ invokevirtual    │ 普通实例方法（虚方法分派）        │
            │ invokeinterface  │ 接口方法                         │
            │ invokespecial    │ 构造方法、private 方法、super 调用 │
            │ invokestatic     │ 静态方法                         │
            │ invokedynamic    │ Lambda、方法引用（运行时动态绑定） │
            └──────────────────┴─────────────────────────────────┘
            """);

        result.put("3. 控制流", """
            Java: if (a > 0) { ... } else { ... }
            字节码: iload_1 → ifle <else_branch> → ... → goto <end> → <else_branch>: ...

            Java: for (int i = 0; i < 10; i++) { ... }
            字节码: iconst_0 → istore_1 → <loop>: iload_1 → bipush 10 → if_icmpge <end> → ... → iinc 1,1 → goto <loop>

            含义:
            - ifle: 小于等于时跳转
            - if_icmpge: 比较两个 int，大于等于时跳转
            - goto: 无条件跳转
            - tableswitch/lookupswitch: switch 语句
            """);

        result.put("4. 对象操作", """
            Java: new Object()
            字节码: new → dup → invokespecial <init>

            Java: obj.field
            字节码: aload_N → getfield

            含义:
            - new: 创建对象（分配内存，压入栈）
            - dup: 复制栈顶值（构造方法需要 this 引用）
            - invokespecial <init>: 调用构造方法
            - getfield/putfield: 读写实例字段
            - getstatic/putstatic: 读写静态字段
            """);

        result.put("5. 异常处理", """
            Java: try { ... } catch (Exception e) { ... }
            字节码: 异常表（Exception Table）

            异常表结构:
            ┌─────────┬─────────┬──────────┬──────────┐
            │ 起始PC   │ 结束PC   │ 捕获PC    │ 捕获类型  │
            ├─────────┼─────────┼──────────┼──────────┤
            │ 0       │ 10      │ 15       │ Exception │
            └─────────┴─────────┴──────────┴──────────┘

            PC 0-10 之间的代码如果抛出 Exception，跳转到 PC 15
            """);

        result.put("6. 同步", """
            Java: synchronized (obj) { ... }
            字节码: aload_N → monitorenter → ... → monitorexit

            含义:
            - monitorenter: 获取对象的监视器锁
            - monitorexit: 释放对象的监视器锁
            - 编译器会生成异常表确保异常时也能释放锁

            注意: 方法级 synchronized 不用这两个指令
            而是通过方法访问标志 ACC_SYNCHRONIZED 实现
            """);

        return result;
    }

    /**
     * 演示方法分派
     *
     * 静态分派（编译期）：重载（Overload）
     *   - 编译器根据静态类型（声明类型）选择方法
     *   - 属于"编译期确定"
     *
     * 动态分派（运行期）：重写（Override）
     *   - 运行时根据实际类型选择方法
     *   - 通过 invokevirtual 的虚方法表（vtable）实现
     */
    public Map<String, Object> demonstrateDispatch() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("静态分派（重载）", """
            // 编译期根据静态类型选择方法
            class Overload {
                void print(Object obj) { System.out.println("Object"); }
                void print(String str) { System.out.println("String"); }
                void print(int i) { System.out.println("int"); }
            }

            // 编译器根据传入参数的声明类型选择
            // "hello" 的静态类型是 String → print(String)
            // (Object)"hello" 的静态类型是 Object → print(Object)
            """);

        result.put("动态分派（重写）", """
            // 运行期根据实际类型选择方法
            class Parent { void say() { System.out.println("Parent"); } }
            class Child extends Parent { void say() { System.out.println "Child"); } }

            Parent p = new Child();
            p.say(); // 输出 "Child"

            // invokevirtual 查找顺序:
            // 1. 实际类型（Child）的方法表
            // 2. 找到 say() → 执行 Child.say()
            // 3. 找不到 → 沿继承链向上查找
            """);

        result.put("虚方法表（vtable）", """
            ┌─────────────────────────────────┐
            │          Child vtable            │
            ├──────────┬──────────────────────┤
            │ hashCode │ Object.hashCode()     │
            │ equals   │ Object.equals()       │
            │ toString │ Object.toString()     │
            │ say      │ Child.say()  ← 重写了 │
            │ work     │ Parent.work() ← 继承  │
            └──────────┴──────────────────────┘

            每个类有自己的 vtable
            invokevirtual 通过 vtable 实现多态
            """);

        return result;
    }

    /**
     * 演示 Lambda 的字节码实现
     *
     * Lambda 通过 invokedynamic 指令实现：
     * 1. 首次调用时，invokedynamic 引导方法生成实现类
     * 2. 后续调用直接使用生成的实现类
     * 3. 实现类不编译在 .class 中，运行时动态生成
     */
    public Map<String, Object> demonstrateLambdaBytecode() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("Lambda 代码", """
            Runnable r = () -> System.out.println("hello");
            r.run();
            """);

        result.put("字节码", """
            // 编译后生成两个方法:
            // 1. 包含 Lambda 体的方法（合成方法）
            private static void lambda$main$0() {
                System.out.println("hello");
            }

            // 2. 主方法中用 invokedynamic 调用
            invokedynamic #0:run()Ljava/lang/Runnable;
            // 引导方法: LambdaMetafactory.metafactory()
            // 运行时生成实现了 Runnable 的类
            """);

        result.put("invokedynamic 流程", """
            1. 首次执行 invokedynamic
            2. 调用引导方法（LambdaMetafactory.metafactory）
            3. 生成一个实现 Runnable 的匿名类
            4. 该类的 run() 调用 lambda$main$0()
            5. 后续调用直接使用这个匿名类

            优势:
            - 不在编译期生成匿名类（减少 .class 数量）
            - 运行时可以优化（内联 Lambda 体）
            - 支持函数式编程
            """);

        result.put("对比匿名内部类", """
            ┌─────────────┬──────────────────┬──────────────────┐
            │  对比项       │  Lambda          │  匿名内部类       │
            ├─────────────┼──────────────────┼──────────────────┤
            │ 实现方式      │ invokedynamic    │ 编译期生成 .class │
            │ 类数量        │ 不额外生成类      │ 每个生成一个类     │
            │ this 指向     │ 外部类           │ 匿名类自身        │
            │ 序列化        │ 需要特殊处理      │ 默认支持          │
            │ 性能          │ 首次慢，后续快    │ 每次创建新实例     │
            └─────────────┴──────────────────┴──────────────────┘
            """);

        return result;
    }
}
