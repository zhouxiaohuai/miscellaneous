package com.aichat.study.concurrency.ex07_completablefuture;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 练习：对比 thenApply vs thenCompose
 *
 * - thenApply：把结果映射成另一个值（同步转换）；如果返回的是 CompletableFuture，会变成“嵌套”。
 * - thenCompose：专门用来“摊平”异步链（避免 CompletableFuture<CompletableFuture<T>>）。
 */
public final class CfComposeDemo {

  public static void main(String[] args) {
    System.out.println("Demo: thenApply vs thenCompose");

    CompletableFuture<Integer> base =
        CompletableFuture.supplyAsync(
            () -> {
              sleep(200);
              return 21;
            });

    CompletableFuture<CompletableFuture<Integer>> nested =
        base.thenApply(
            x ->
                CompletableFuture.supplyAsync(
                    () -> {
                      sleep(200);
                      return x * 2;
                    }));

    CompletableFuture<Integer> flattened =
        base.thenCompose(
            x ->
                CompletableFuture.supplyAsync(
                    () -> {
                      sleep(200);
                      return x * 2;
                    }));

    System.out.println("nested join  =" + nested.join().join());
    System.out.println("flatten join=" + flattened.join());
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(Duration.ofMillis(ms).toMillis());
    } catch (InterruptedException ignored) {
      // ignore
    }
  }
}

