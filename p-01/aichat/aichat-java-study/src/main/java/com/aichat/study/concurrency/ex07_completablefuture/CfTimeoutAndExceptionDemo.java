package com.aichat.study.concurrency.ex07_completablefuture;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 练习：超时与异常兜底
 *
 * - orTimeout：超时完成为异常
 * - completeOnTimeout：超时给默认值
 * - exceptionally：异常兜底
 */
public final class CfTimeoutAndExceptionDemo {

  public static void main(String[] args) {
    System.out.println("Demo: timeout + exception fallback");

    CompletableFuture<String> slow =
        CompletableFuture.supplyAsync(
                () -> {
                  sleep(800);
                  return "slow-ok";
                })
            .orTimeout(300, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> "fallback(" + ex.getClass().getSimpleName() + ")");

    CompletableFuture<String> slowDefault =
        CompletableFuture.supplyAsync(
                () -> {
                  sleep(800);
                  return "slow-ok";
                })
            .completeOnTimeout("default-on-timeout", 300, TimeUnit.MILLISECONDS);

    CompletableFuture<String> boom =
        CompletableFuture.<String>supplyAsync(
                () -> {
                  throw new RuntimeException("boom");
                })
            .handle(
                (v, ex) -> {
                  if (ex != null) return "handled(" + ex.getClass().getSimpleName() + ")";
                  return v;
                });

    System.out.println("orTimeout + exceptionally -> " + slow.join());
    System.out.println("completeOnTimeout         -> " + slowDefault.join());
    System.out.println("handle(boom)              -> " + boom.join());
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(Duration.ofMillis(ms).toMillis());
    } catch (InterruptedException ignored) {
      // ignore
    }
  }
}

