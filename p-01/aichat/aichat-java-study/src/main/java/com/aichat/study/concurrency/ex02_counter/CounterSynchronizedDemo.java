package com.aichat.study.concurrency.ex02_counter;

import java.util.ArrayList;
import java.util.List;

/**
 * 练习：用 synchronized 保证 i++ 的原子性（互斥进入临界区）。
 *
 * 预期：actual == expected。
 */
public final class CounterSynchronizedDemo {

  private static int counter = 0;
  private static final Object LOCK = new Object();

  private static void inc() {
    synchronized (LOCK) {
      counter++;
    }
  }

  public static void main(String[] args) throws InterruptedException {
    int threads = 8;
    int perThread = 500_000;
    int expected = threads * perThread;

    List<Thread> list = new ArrayList<>(threads);
    for (int t = 0; t < threads; t++) {
      Thread th = new Thread(() -> {
        for (int i = 0; i < perThread; i++) {
          inc();
        }
      }, "t-" + t);
      list.add(th);
    }

    long start = System.currentTimeMillis();
    for (Thread th : list) th.start();
    for (Thread th : list) th.join();
    long costMs = System.currentTimeMillis() - start;

    System.out.println("CounterSynchronizedDemo");
    System.out.println("expected=" + expected);
    System.out.println("actual  =" + counter);
    System.out.println("costMs  =" + costMs);
  }
}

