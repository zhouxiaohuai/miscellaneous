package com.aichat.study.concurrency.ex02_counter;

import java.util.ArrayList;
import java.util.List;

/**
 * 练习：复现 i++ 的竞态条件（丢失更新）。
 *
 * 预期：最终 counter 通常小于 expected。
 */
public final class CounterRaceDemo {

  private static int counter = 0;

  public static void main(String[] args) throws InterruptedException {
    int threads = 8;
    int perThread = 500_000;
    int expected = threads * perThread;

    List<Thread> list = new ArrayList<>(threads);
    for (int t = 0; t < threads; t++) {
      Thread th = new Thread(() -> {
        for (int i = 0; i < perThread; i++) {
          counter++; // 非原子：read -> add -> write
        }
      }, "t-" + t);
      list.add(th);
    }

    long start = System.currentTimeMillis();
    for (Thread th : list) th.start();
    for (Thread th : list) th.join(); // 等待所有线程完成
    // join 的含义
    // 1. 等待线程完成
    // 2. 阻塞当前线程，直到线程完成
    // 3. 返回线程的执行结果
    // 4. 如果线程抛出异常，join 会抛出异常
    // 5. 如果线程正常完成，join 会返回线程的执行结果

    long costMs = System.currentTimeMillis() - start;

    System.out.println("CounterRaceDemo");
    System.out.println("expected=" + expected);
    System.out.println("actual  =" + counter);
    System.out.println("lost    =" + (expected - counter));
    System.out.println("costMs  =" + costMs);
  }
}

