package com.aichat.study.concurrency.ex03_happensbefore;

import java.time.Duration;

/**
 * 练习：不用 volatile，通过 synchronized 建立 happens-before（可见性）。
 *
 * 关键点：读 stop 与写 stop 必须使用“同一把锁”保护。
 */
public final class StopFlagSynchronizedDemo {

  private static boolean stop = false;

  private static synchronized boolean isStop() {
    return stop;
  }

  private static synchronized void setStop(boolean v) {
    stop = v;
  }

  public static void main(String[] args) throws InterruptedException {
    System.out.println("Demo: stop-flag WITH synchronized (no volatile)");

    Thread worker = new Thread(() -> {
      long spins = 0;
      while (!isStop()) {
        spins++;
      }
      System.out.println("worker exit, spins=" + spins);
    }, "worker");

    worker.start();

    Thread.sleep(Duration.ofSeconds(1).toMillis());
    setStop(true);
    System.out.println("main set stop=true");

    worker.join(Duration.ofSeconds(2).toMillis());
    System.out.println("worker alive? " + worker.isAlive());
  }
}

