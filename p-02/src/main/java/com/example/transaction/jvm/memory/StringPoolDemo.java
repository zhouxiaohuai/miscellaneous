package com.example.transaction.jvm.memory;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 字符串常量池演示
 *
 * 字符串常量池（String Pool）：
 * - JDK 7+ 从方法区移到堆中
 * - 本质是一个 HashTable（StringTable），默认大小 1009（-XX:StringTableSize）
 * - 目的：减少重复字符串对象，节省内存
 *
 * 内存布局：
 * ┌────────────────────────────────────────────────────────┐
 * │                        堆（Heap）                       │
 * │  ┌──────────────┐  ┌─────────────────────────────────┐ │
 * │  │ String Pool   │  │ 普通对象区                        │ │
 * │  │ (StringTable) │  │                                 │ │
 * │  │               │  │  new String("abc") → 0x1234     │ │
 * │  │ "abc" → 0x5678│  │                                 │ │
 * │  │ "xyz" → 0x9abc│  │                                 │ │
 * │  └──────────────┘  └─────────────────────────────────┘ │
 * └────────────────────────────────────────────────────────┘
 *
 * 六种经典场景：
 *
 * 场景1: String s1 = "abc";
 *   → 字面量，直接入池，s1 指向池中对象
 *
 * 场景2: String s2 = new String("abc");
 *   → 堆中创建新对象，池中已有 "abc" 不重复创建
 *   → s2 指向堆中新对象（不是池中的）
 *   → 总共 1 个池对象 + 1 个堆对象
 *
 * 场景3: String s3 = s1.intern();
 *   → 返回池中已有的 "abc" 引用
 *   → s3 == s1 为 true
 *
 * 场景4: String s4 = new String("ab") + new String("cd");
 *   → 编译期无法确定，运行时拼接（StringBuilder）
 *   → 池中无 "abcd"
 *
 * 场景5: String s5 = s4.intern();
 *   → JDK 7+：在池中记录 s4 的引用（不再创建新对象）
 *   → s5 == s4 为 true（JDK 7+）
 *
 * 场景6: String s6 = "ab" + "cd";
 *   → 编译期常量折叠，等价于 "abcd"
 *   → 直接入池
 */
@Component
public class StringPoolDemo {

    /**
     * 演示 6 种字符串常量池场景
     */
    public Map<String, Object> demonstrateAllCases() {
        Map<String, Object> result = new LinkedHashMap<>();

        // === 场景1: 字面量 ===
        String s1 = "abc";
        String s1b = "abc";
        Map<String, Object> case1 = new LinkedHashMap<>();
        case1.put("代码", "String s1 = \"abc\"; String s1b = \"abc\";");
        case1.put("s1 == s1b", s1 == s1b);
        case1.put("s1.hashCode()", s1.hashCode());
        case1.put("说明", "字面量直接入池，相同字面量返回同一引用");
        result.put("场景1: 字面量", case1);

        // === 场景2: new String ===
        String s2 = new String("abc");
        Map<String, Object> case2 = new LinkedHashMap<>();
        case2.put("代码", "String s2 = new String(\"abc\");");
        case2.put("s1 == s2", s1 == s2);
        case2.put("s1.equals(s2)", s1.equals(s2));
        case2.put("说明", "new 创建堆中新对象，== 比较引用地址不同，equals 比较值相同");
        result.put("场景2: new String", case2);

        // === 场景3: intern 返回池中引用 ===
        String s3 = s2.intern();
        Map<String, Object> case3 = new LinkedHashMap<>();
        case3.put("代码", "String s3 = s2.intern();");
        case3.put("s1 == s3", s1 == s3);
        case3.put("说明", "intern() 返回池中已有对象的引用");
        result.put("场景3: intern()", case3);

        // === 场景4: 运行时拼接 ===
        String s4 = new String("ab") + new String("cd");
        Map<String, Object> case4 = new LinkedHashMap<>();
        case4.put("代码", "String s4 = new String(\"ab\") + new String(\"cd\");");
        case4.put("s4", s4);
        case4.put("s4 == \"abcd\"", s4 == "abcd");
        case4.put("说明", "运行时拼接用 StringBuilder，结果在堆中，不在池中");
        result.put("场景4: 运行时拼接", case4);

        // === 场景5: intern 运行时字符串 ===
        String s5 = s4.intern();
        String s5b = "abcd";
        Map<String, Object> case5 = new LinkedHashMap<>();
        case5.put("代码", "String s5 = s4.intern(); String s5b = \"abcd\";");
        case5.put("s5 == s4", s5 == s4);
        case5.put("s5 == s5b", s5 == s5b);
        case5.put("说明", "JDK 7+ intern() 在池中记录引用（不复制），s5 == s4 == s5b");
        result.put("场景5: intern 运行时字符串", case5);

        // === 场景6: 编译期常量折叠 ===
        String s6 = "ab" + "cd";
        Map<String, Object> case6 = new LinkedHashMap<>();
        case6.put("代码", "String s6 = \"ab\" + \"cd\";");
        case6.put("s6 == \"abcd\"", s6 == "abcd");
        case6.put("s6 == s5", s6 == s5);
        case6.put("说明", "编译期常量折叠，直接变成 \"abcd\"，从池中获取");
        result.put("场景6: 常量折叠", case6);

        return result;
    }

    /**
     * 演示 intern() 的性能影响
     *
     * intern() 优点：减少重复对象，节省内存
     * intern() 缺点：
     * 1. 需要计算 hashCode，有 CPU 开销
     * 2. StringTable 过大影响性能（-XX:StringTableSize 调整）
     * 3. JDK 7+ 池在堆中，大量 intern 会占用堆内存
     */
    public Map<String, Object> demonstrateInternPerformance(int count) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 不使用 intern
        long start = System.nanoTime();
        String[] arr1 = new String[count];
        for (int i = 0; i < count; i++) {
            arr1[i] = new String("hello_" + (i % 1000));
        }
        long time1 = System.nanoTime() - start;

        // 使用 intern
        start = System.nanoTime();
        String[] arr2 = new String[count];
        for (int i = 0; i < count; i++) {
            arr2[i] = new String("hello_" + (i % 1000)).intern();
        }
        long time2 = System.nanoTime() - start;

        // 统计去重效果
        int uniqueWithoutIntern = 0;
        int uniqueWithIntern = 0;
        java.util.Set<String> set1 = new java.util.HashSet<>();
        java.util.Set<String> set2 = new java.util.HashSet<>();
        for (int i = 0; i < count; i++) {
            if (set1.add(arr1[i])) uniqueWithoutIntern++;
            if (set2.add(String.valueOf(System.identityHashCode(arr2[i])))) uniqueWithIntern++;
        }

        result.put("创建数量", count);
        result.put("不用 intern 耗时", time1 / 1_000_000 + " ms");
        result.put("用 intern 耗时", time2 / 1_000_000 + " ms");
        result.put("不用 intern 对象数", uniqueWithoutIntern);
        result.put("用 intern 对象数", uniqueWithIntern);
        result.put("建议", "大量重复字符串场景（如城市名、状态码）适合 intern；随机字符串不适合");

        return result;
    }

    /**
     * 字符串驻留的 JDK 版本差异
     */
    public Map<String, Object> showJdkVersionDifference() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("JDK 6", Map.of(
            "常量池位置", "方法区（PermGen）",
            "intern 行为", "复制字符串到 PermGen 的常量池",
            "问题", "PermGen 空间有限，大量 intern 导致 OOM: PermGen space"
        ));
        result.put("JDK 7+", Map.of(
            "常量池位置", "堆（Heap）",
            "intern 行为", "在池中记录堆中对象的引用（不复制）",
            "优势", "不受 PermGen 限制，但会占用堆空间"
        ));
        result.put("JDK 8+", Map.of(
            "PermGen", "已移除，改为 Metaspace（本地内存）",
            "常量池", "仍在堆中",
            "StringTableSize", "-XX:StringTableSize=60013（默认 60013，建议调大）"
        ));

        return result;
    }
}
