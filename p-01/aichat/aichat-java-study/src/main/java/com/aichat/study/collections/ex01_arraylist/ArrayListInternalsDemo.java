package com.aichat.study.collections.ex01_arraylist;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 目标：理解 ArrayList 的“数组 + 扩容”本质。
 *
 * 核心知识：
 * - ArrayList 内部就是一个 Object[]（elementData）
 * - new ArrayList<>() 时，数组是空数组（容量=0）
 * - 第一次 add 时，扩容到 10
 * - 后续满了再扩容：新容量 = 旧容量 + 旧容量 >> 1（≈1.5 倍）
 * - 扩容意味着：申请新数组 + 拷贝旧数据（O(n)，有成本）
 *
 * 你要能背出来的结论：
 * - get(i)     → O(1)（数组下标直接访问）
 * - add(e)     → 均摊 O(1)（偶尔扩容会有 O(n)）
 * - add(i, e)  → O(n)（要把 i 及后面的元素往后移）
 * - remove(i)  → O(n)（要把后面的元素往前移）
 */
public class ArrayListInternalsDemo {

    public static void main(String[] args) {
        // ====== 实验 1：观察容量变化 ======
        System.out.println("===== 实验 1：容量观察 =====");
        List<Integer> list = new ArrayList<>();
        printCapacity(list, "刚创建");

        // 逐个添加，观察扩容点
        int lastCap = 0;
        for (int i = 0; i < 35; i++) {
            list.add(i);
            int cap = getCapacity(list);
            if (cap != lastCap) {
                System.out.println("  add(" + i + ") → 容量扩容到: " + cap);
                lastCap = cap;
            }
        }
        System.out.println("最终 size=" + list.size() + ", capacity=" + lastCap);

        // ====== 实验 2：按索引插入 vs 尾部追加 =====
        System.out.println("\n===== 实验 2：add(i, e) vs add(e) =====");
        List<String> list2 = new ArrayList<>();
        list2.add("A");
        list2.add("B");
        list2.add("C");
        System.out.println("初始:   " + list2);

        list2.add(1, "X"); // 在索引 1 插入，B 和 C 都要往后挪
        System.out.println("add(1, X) 后: " + list2);

        list2.remove(2);   // 删除索引 2 的元素，后面的要往前挪
        System.out.println("remove(2) 后: " + list2);

        // ====== 实验 3：避免扩容——预估大小 =====
        System.out.println("\n===== 实验 3：预估容量避免反复扩容 =====");
        int n = 100_000;
        // 不预估：反复扩容
        long t1 = System.currentTimeMillis();
        List<Integer> noHint = new ArrayList<>();
        for (int i = 0; i < n; i++) noHint.add(i);
        long t2 = System.currentTimeMillis();

        // 预估容量：一次到位
        List<Integer> withHint = new ArrayList<>(n);
        for (int i = 0; i < n; i++) withHint.add(i);
        long t3 = System.currentTimeMillis();

        System.out.println("不预估容量耗时: " + (t2 - t1) + "ms");
        System.out.println("预估容量耗时:   " + (t3 - t2) + "ms");
        System.out.println("（差异主要来自反复扩容时的数组拷贝）");
    }

    // ---- 反射工具方法，勿关注实现细节 ----
    private static int getCapacity(List<?> list) {
        try {
            Field field = ArrayList.class.getDeclaredField("elementData");
            field.setAccessible(true);
            Object[] elementData = (Object[]) field.get(list);
            return elementData.length;
        } catch (Exception e) {
            return -1;
        }
    }

    private static void printCapacity(List<?> list, String tag) {
        System.out.println(tag + " → size=" + list.size() + ", capacity=" + getCapacity(list));
    }
}
