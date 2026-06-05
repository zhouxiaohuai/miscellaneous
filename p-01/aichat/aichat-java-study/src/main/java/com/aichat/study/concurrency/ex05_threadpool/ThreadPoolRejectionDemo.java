package com.aichat.study.concurrency.ex05_threadpool;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 练习：强制触发拒绝策略。
 *
 * 对比：
 * - AbortPolicy：直接抛 RejectedExecutionException
 * - CallerRunsPolicy：由提交任务的线程（这里是 main）自己执行任务，形成“反压”
 */
public final class ThreadPoolRejectionDemo {

  public static void main(String[] args) throws InterruptedException {
    System.out.println("Demo: ThreadPool rejection policies");

    runAbortPolicy();
    System.out.println("---");
    runCallerRunsPolicy();
  }

  private static void runAbortPolicy() throws InterruptedException {
    System.out.println("AbortPolicy:");

    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy());

    submitBurst(pool, 6);
    shutdownAndWait(pool);
  }

  private static void runCallerRunsPolicy() throws InterruptedException {
    System.out.println("CallerRunsPolicy:");

    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.CallerRunsPolicy());

    submitBurst(pool, 6);
    shutdownAndWait(pool);
  }

  private static void submitBurst(ThreadPoolExecutor pool, int tasks) {
    for (int i = 0; i < tasks; i++) {
      int id = i;
      try {
        pool.execute(() -> work(id));
        System.out.println("submitted " + id);
      } catch (RejectedExecutionException e) {
        System.out.println("rejected " + id + " -> " + e.getClass().getSimpleName());
      }
    }
  }

  private static void work(int id) {
    String tn = Thread.currentThread().getName();
    System.out.println("run " + id + " on " + tn);
    try {
      Thread.sleep(Duration.ofMillis(500).toMillis());
    } catch (InterruptedException ignored) {
      // ignore
    }
  }

  private static void shutdownAndWait(ThreadPoolExecutor pool) throws InterruptedException {
    pool.shutdown();
    pool.awaitTermination(10, TimeUnit.SECONDS);
  }
}

