package com.aichat.study.concurrency.ex02_counter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 练习：用 AtomicInteger 保证自增的原子性（CAS）。
 *
 * 预期：actual == expected。
 */
public final class CounterAtomicDemo {

  private static final AtomicInteger counter = new AtomicInteger(0);

  public static void main(String[] args) throws InterruptedException {
    int threads = 8;
    int perThread = 500_000;
    int expected = threads * perThread;

    List<Thread> list = new ArrayList<>(threads);
    for (int t = 0; t < threads; t++) {
      Thread th = new Thread(() -> {
        for (int i = 0; i < perThread; i++) {
          counter.incrementAndGet();
        }
      }, "t-" + t);
      list.add(th);
    }

    long start = System.currentTimeMillis();
    for (Thread th : list) th.start();
    for (Thread th : list) th.join();
    long costMs = System.currentTimeMillis() - start;

    System.out.println("CounterAtomicDemo");
    System.out.println("expected=" + expected);
    System.out.println("actual  =" + counter.get());
    System.out.println("costMs  =" + costMs);
  }
}

