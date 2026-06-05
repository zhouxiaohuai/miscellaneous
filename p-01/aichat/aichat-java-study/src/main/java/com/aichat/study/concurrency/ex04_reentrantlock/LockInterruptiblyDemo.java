package com.aichat.study.concurrency.ex04_reentrantlock;

import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 练习：ReentrantLock.lockInterruptibly()
 *
 * 观察点：等待获取锁的线程可以被 interrupt() 立即打断（而不是傻等）。
 */
public final class LockInterruptiblyDemo {

  private static final ReentrantLock LOCK = new ReentrantLock();

  public static void main(String[] args) throws InterruptedException {
    System.out.println("Demo: lockInterruptibly()");

    Thread holder = new Thread(() -> {
      LOCK.lock();
      try {
        System.out.println("holder acquired lock, sleeping...");
        try {
          Thread.sleep(Duration.ofSeconds(5).toMillis());
        } catch (InterruptedException ignored) {
          // ignore
        }
        System.out.println("holder done");
      } finally {
        LOCK.unlock();
      }
    }, "holder");

    Thread waiter = new Thread(() -> {
      try {
        System.out.println("waiter trying lockInterruptibly...");
        LOCK.lockInterruptibly();
        try {
          System.out.println("waiter acquired lock (unexpected if interrupted early)");
        } finally {
          LOCK.unlock();
        }
      } catch (InterruptedException e) {
        System.out.println("waiter interrupted while waiting for lock");
      }
    }, "waiter");

    holder.start();
    Thread.sleep(Duration.ofMillis(200).toMillis());
    waiter.start();

    Thread.sleep(Duration.ofSeconds(1).toMillis());
    System.out.println("main interrupt waiter");
    waiter.interrupt();

    holder.join();
    waiter.join();
    System.out.println("done");
  }
}

