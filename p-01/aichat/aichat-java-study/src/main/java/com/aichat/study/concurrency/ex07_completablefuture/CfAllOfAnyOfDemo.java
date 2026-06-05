package com.aichat.study.concurrency.ex07_completablefuture;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 练习：allOf / anyOf
 *
 * - allOf：等待全部完成后再汇总
 * - anyOf：任意一个完成就继续（竞速取最快）
 */
public final class CfAllOfAnyOfDemo {

  public static void main(String[] args) {
    System.out.println("Demo: allOf / anyOf");

    List<CompletableFuture<String>> futures =
        List.of(task("A"), task("B"), task("C"));

    CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    CompletableFuture<List<String>> allResults =
        all.thenApply(v -> futures.stream().map(CompletableFuture::join).toList());

    CompletableFuture<Object> any = CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]));

    System.out.println("anyOf winner=" + any.join());
    System.out.println("allOf results=" + allResults.join());
  }

  private static CompletableFuture<String> task(String name) {
    return CompletableFuture.supplyAsync(
        () -> {
          int ms = ThreadLocalRandom.current().nextInt(200, 800);
          sleep(ms);
          return name + "(" + ms + "ms)";
        });
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(Duration.ofMillis(ms).toMillis());
    } catch (InterruptedException ignored) {
      // ignore
    }
  }
}

