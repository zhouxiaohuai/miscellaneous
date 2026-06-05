package com.aichat.study.concurrency.ex01_stopflag;

import java.time.Duration;

/**
 * 练习：可见性问题（使用 volatile 修复）。
 *
 * 预期：main 线程写入 stop=true 后，worker 线程能及时观察到并退出。
 */
public final class StopFlagVolatileDemo {

  private static volatile boolean stop = false;

  public static void main(String[] args) throws InterruptedException {
    System.out.println("Demo: stop-flag WITH volatile");

    Thread worker = new Thread(() -> {
      long spins = 0;
      while (!stop) {
        spins++;
      }
      System.out.println("worker exit, spins=" + spins);
    }, "worker");

    worker.start();

    Thread.sleep(Duration.ofSeconds(1).toMillis());
    stop = true;
    System.out.println("main set stop=true");

    worker.join(Duration.ofSeconds(2).toMillis());
    System.out.println("worker alive? " + worker.isAlive());
  }
}

