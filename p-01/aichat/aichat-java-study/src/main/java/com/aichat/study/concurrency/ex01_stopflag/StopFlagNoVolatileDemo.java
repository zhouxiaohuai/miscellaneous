package com.aichat.study.concurrency.ex01_stopflag;

import java.time.Duration;

/**
 * 练习：可见性问题（不使用 volatile）。
 *
 * 现象：在某些机器/某些运行时机下，worker 线程可能迟迟不退出（看不到 stop=true）。
 * 注意：该现象不保证 100% 复现；你可以多跑几次，或加大运行时间观察。
 */
public final class StopFlagNoVolatileDemo {

  private static boolean stop = false;

  public static void main(String[] args) throws InterruptedException {
    System.out.println("Demo: stop-flag WITHOUT volatile");

    Thread worker = new Thread(() -> {
      long spins = 0;
      while (!stop) {
        spins++;
        // 故意不做任何同步操作；空转读 stop
      }
      System.out.println("worker exit, spins=" + spins);
    }, "worker");

    worker.start();

    Thread.sleep(Duration.ofSeconds(1).toMillis());
    stop = true;
    System.out.println("main set stop=true");

    worker.join(Duration.ofSeconds(2).toMillis());
    System.out.println("worker alive? " + worker.isAlive());

    if (worker.isAlive()) {
      // Windows 控制台默认编码可能导致中文显示为乱码；用英文提示更稳。
      System.out.println("Possible visibility issue: worker did not observe stop=true in time.");
      System.out.println("Try rerunning a few times, then compare StopFlagVolatileDemo.");
    }
  }
}

