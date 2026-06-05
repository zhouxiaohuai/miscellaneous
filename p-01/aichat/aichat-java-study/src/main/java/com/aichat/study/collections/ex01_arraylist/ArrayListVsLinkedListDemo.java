package com.aichat.study.collections.ex01_arraylist;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 目标：亲手感知 ArrayList 和 LinkedList 在不同场景下的性能差异。
 *
 * 核心对比表（背下来）：
 * ┌──────────────────┬──────────────┬──────────────┐
 * │ 操作             │ ArrayList    │ LinkedList   │
 * ├──────────────────┼──────────────┼──────────────┤
 * │ get(i) 随机访问   │ O(1)  ★快   │ O(n)         │
 * │ add(e) 尾部追加   │ 均摊 O(1)   │ O(1)         │
 * │ add(i,e) 中间插入 │ O(n)        │ O(n)*        │
 * │ remove(i) 删除    │ O(n)        │ O(n)*        │
 * │ 内存占用          │ 纯数据数组   │ 数据+前后指针 │
 * │ 缓存友好          │ ★好（连续）  │ 差（分散）    │
 * └──────────────────┴──────────────┴──────────────┘
 * *LinkedList add/remove 理论 O(1)，但需要先 O(n) 找到位置
 *
 * 选型口诀：
 * - 改查多 → ArrayList
 * - 头尾增删多 → LinkedList（或直接用 ArrayDeque）
 * - 不知道选啥 → 默认 ArrayList
 */
public class ArrayListVsLinkedListDemo {

    private static final int N = 100_000;

    public static void main(String[] args) {
        List<Integer> arrayList = new ArrayList<>();
        List<Integer> linkedList = new LinkedList<>();

        // ====== 测试 1：尾部追加 ======
        System.out.println("===== 测试 1：尾部追加 " + N + " 个元素 =====");
        testTailAdd(arrayList, "ArrayList  ");
        testTailAdd(linkedList, "LinkedList ");

        // ====== 测试 2：随机访问（遍历） ======
        System.out.println("\n===== 测试 2：随机访问遍历 =====");
        testRandomAccess(new ArrayList<>(arrayList), "ArrayList  ");
        testRandomAccess(new LinkedList<>(linkedList), "LinkedList ");

        // ====== 测试 3：头部插入 ======
        System.out.println("\n===== 测试 3：头部插入 10000 个元素 =====");
        testHeadInsert("ArrayList  ");
        testHeadInsert("LinkedList ");

        // ====== 测试 4：中间插入 ======
        System.out.println("\n===== 测试 4：中间位置插入 10000 个元素 =====");
        testMiddleInsert("ArrayList  ");
        testMiddleInsert("LinkedList ");
    }

    private static void testTailAdd(List<Integer> list, String label) {
        list.clear();
        long t1 = System.nanoTime();
        for (int i = 0; i < N; i++) { list.add(i); }
        long t2 = System.nanoTime();
        System.out.println("  " + label + ": " + (t2 - t1) / 1_000_000.0 + " ms");
        list.clear();
    }

    private static void testRandomAccess(List<Integer> list, String label) {
        long sum = 0;
        long t1 = System.nanoTime();
        for (int i = 0; i < list.size(); i++) { sum += list.get(i); }
        long t2 = System.nanoTime();
        System.out.println("  " + label + ": " + (t2 - t1) / 1_000_000.0 + " ms (sum=" + sum + ")");
    }

    private static void testHeadInsert(String label) {
        long t1 = System.nanoTime();
        List<Integer> list = label.startsWith("A") ? new ArrayList<>() : new LinkedList<>();
        for (int i = 0; i < 10_000; i++) { list.add(0, i); }
        long t2 = System.nanoTime();
        System.out.println("  " + label + ": " + (t2 - t1) / 1_000_000.0 + " ms");
    }

    private static void testMiddleInsert(String label) {
        long t1 = System.nanoTime();
        List<Integer> list = label.startsWith("A") ? new ArrayList<>() : new LinkedList<>();
        // 先填 10000 个元素
        for (int i = 0; i < 10_000; i++) { list.add(0); }
        // 在中间不停插入
        for (int i = 0; i < 10_000; i++) { list.add(list.size() / 2, i); }
        long t2 = System.nanoTime();
        System.out.println("  " + label + ": " + (t2 - t1) / 1_000_000.0 + " ms");
    }
}
