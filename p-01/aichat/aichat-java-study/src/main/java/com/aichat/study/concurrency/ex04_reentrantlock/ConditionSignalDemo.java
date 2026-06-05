package com.aichat.study.concurrency.ex04_reentrantlock;

import java.time.Duration;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 练习：Condition.await()/signal()
 *
 * 观察点：
 * - await 会释放锁并进入条件等待队列
 * - signal 会唤醒一个等待者（被唤醒后需要重新竞争锁）
 */
public final class ConditionSignalDemo {

  private static final ReentrantLock LOCK = new ReentrantLock();
  private static final Condition READY = LOCK.newCondition();
  private static boolean ready = false;

  public static void main(String[] args) throws InterruptedException {
    System.out.println("Demo: Condition await/signal");

    Thread waiter = new Thread(() -> {
      LOCK.lock();
      try {
        while (!ready) {
          System.out.println("waiter: not ready, await...");
          try {
            READY.await();
          } catch (InterruptedException e) {
            System.out.println("waiter: interrupted");
            return;
          }
        }
        System.out.println("waiter: observed ready=true, continue");
      } finally {
        LOCK.unlock();
      }
    }, "waiter");

    Thread signaler = new Thread(() -> {
      try {
        Thread.sleep(Duration.ofSeconds(1).toMillis());
      } catch (InterruptedException ignored) {
        // ignore
      }

      LOCK.lock();
      try {
        ready = true;
        System.out.println("signaler: set ready=true, signal");
        READY.signal();
      } finally {
        LOCK.unlock();
      }
    }, "signaler");

    waiter.start();
    signaler.start();

    waiter.join();
    signaler.join();
    System.out.println("done");
  }
}

