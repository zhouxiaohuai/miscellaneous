package com.aichat.study.concurrency.ex06_threadpool_tuning;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 练习：CallerRunsPolicy 的“背压”效果。
 *
 * 观察点：当线程池忙且队列满时，部分任务会在 main 线程执行，导致提交动作变慢（自动降速）。
 */
public final class CallerRunsBackpressureDemo {

  public static void main(String[] args) throws InterruptedException {
    System.out.println("Demo: CallerRunsPolicy backpressure");

    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            new ThreadPoolExecutor.CallerRunsPolicy());

    int tasks = 8;
    long submitStart = System.currentTimeMillis();
    for (int i = 0; i < tasks; i++) {
      int id = i;
      pool.execute(() -> work(id));
      System.out.println("submitted " + id);
    }
    long submitCost = System.currentTimeMillis() - submitStart;

    pool.shutdown();
    pool.awaitTermination(10, TimeUnit.SECONDS);

    System.out.println("submitCostMs=" + submitCost);
    System.out.println("done");
  }

  private static void work(int id) {
    String tn = Thread.currentThread().getName();
    System.out.println("run " + id + " on " + tn);
    try {
      Thread.sleep(Duration.ofMillis(300).toMillis());
    } catch (InterruptedException ignored) {
      // ignore
    }
  }
}

