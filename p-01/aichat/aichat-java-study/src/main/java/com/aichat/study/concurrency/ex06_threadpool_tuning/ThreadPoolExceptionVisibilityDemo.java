package com.aichat.study.concurrency.ex06_threadpool_tuning;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 练习：线程池中任务异常的“可见性”。
 *
 * 观察点：
 * - execute(Runnable) 提交的任务抛异常：异常发生在工作线程，通常不会传回提交线程。
 * - submit(...) 会返回 Future；只有在 get() 时异常才会以 ExecutionException 的形式回到调用方。
 */
public final class ThreadPoolExceptionVisibilityDemo {

  public static void main(String[] args) throws Exception {
    System.out.println("Demo: exception visibility in ThreadPoolExecutor");

    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(4),
            namedFactory("ex-"),
            new ThreadPoolExecutor.AbortPolicy());

    System.out.println("1) execute(Runnable) throws:");
    pool.execute(() -> {
      throw new RuntimeException("boom-from-execute");
    });

    Thread.sleep(200);

    System.out.println("2) submit(Callable) throws, observe via Future.get():");
    Future<Integer> f =
        pool.submit(
            () -> {
              throw new IllegalStateException("boom-from-submit");
            });

    try {
      f.get();
    } catch (ExecutionException e) {
      System.out.println("caught from Future.get(): " + e.getCause().getClass().getSimpleName()
          + " -> " + e.getCause().getMessage());
    }

    pool.shutdown();
    pool.awaitTermination(5, TimeUnit.SECONDS);
    System.out.println("done");
  }

  private static ThreadFactory namedFactory(String prefix) {
    AtomicInteger seq = new AtomicInteger(1);
    return r -> {
      Thread t = new Thread(r);
      t.setName(prefix + seq.getAndIncrement());
      t.setUncaughtExceptionHandler(
          (th, ex) -> System.out.println("uncaught in " + th.getName() + ": " + ex.getMessage()));
      return t;
    };
  }
}

