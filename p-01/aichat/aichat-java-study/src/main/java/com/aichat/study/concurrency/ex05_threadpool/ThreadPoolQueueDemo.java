package com.aichat.study.concurrency.ex05_threadpool;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 练习：观察线程池的“排队堆积”。
 *
 * 看点：activeCount、queue.size、completedTaskCount 的变化。
 */
public final class ThreadPoolQueueDemo {

  public static void main(String[] args) throws InterruptedException {
    System.out.println("Demo: ThreadPool queue backlog");

    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            2,
            2,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(5),
            namedFactory("demo-"),
            new ThreadPoolExecutor.AbortPolicy());

    int tasks = 6;
    for (int i = 0; i < tasks; i++) {
      int id = i;
      pool.execute(() -> {
        try {
          Thread.sleep(Duration.ofMillis(800).toMillis());
        } catch (InterruptedException ignored) {
          // ignore
        }
        if (id == tasks - 1) {
          System.out.println("last task done on " + Thread.currentThread().getName());
        }
      });
      printStats(pool, "submitted " + id);
    }

    // 周期性观察一会儿
    for (int t = 0; t < 6; t++) {
      Thread.sleep(300);
      printStats(pool, "tick " + t);
    }

    pool.shutdown();
    pool.awaitTermination(10, TimeUnit.SECONDS);
    printStats(pool, "terminated");
  }

  private static void printStats(ThreadPoolExecutor pool, String tag) {
    System.out.printf(
        "[%s] poolSize=%d active=%d queue=%d completed=%d%n",
        tag,
        pool.getPoolSize(),
        pool.getActiveCount(),
        pool.getQueue().size(),
        pool.getCompletedTaskCount());
  }

  private static ThreadFactory namedFactory(String prefix) {
    AtomicInteger seq = new AtomicInteger(1);
    return r -> {
      Thread t = new Thread(r);
      t.setName(prefix + seq.getAndIncrement());
      return t;
    };
  }
}

