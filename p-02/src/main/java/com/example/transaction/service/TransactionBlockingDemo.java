package com.example.transaction.service;

import com.example.transaction.entity.User;
import com.example.transaction.jpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * 事务阻塞行为演示
 *
 * 核心问题：事务阻塞时，同一线程中事务外面的代码会执行吗？
 * 答案：不会。因为是同一个线程，线程被阻塞就意味着什么都不能做。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionBlockingDemo {

    private final UserRepository userRepository;

    // ============================================================
    // 场景 1：事务内阻塞 — 整个线程卡住
    // ============================================================

    /**
     * 事务内的代码执行到一半阻塞了（比如等数据库锁）
     *
     * 时间线：
     *   T0: ① 开启事务
     *   T1: ② 查询用户 → 成功
     *   T2: ③ SELECT FOR UPDATE → 阻塞（等锁）  ← 线程卡在这里
     *       ............（线程什么都做不了）............
     *   T5: ④ 锁释放，继续执行更新
     *   T5: ⑤ 提交事务
     */
    @Transactional
    public String blockingInsideTx(Long userId) {
        log.info("[事务内阻塞] ① 开启事务，查询用户");
        User user = userRepository.findById(userId).orElseThrow();

        log.info("[事务内阻塞] ② 准备加锁（SELECT FOR UPDATE），可能阻塞...");
        // 如果另一个事务持有这行的锁，这里会阻塞
        // 阻塞期间，下面的代码不会执行
        userRepository.findById(userId).orElseThrow(); // 模拟加锁查询

        log.info("[事务内阻塞] ③ 更新用户");
        user.setUsername("updated");
        userRepository.save(user);

        log.info("[事务内阻塞] ④ 提交事务");
        return "done";
    }

    // ============================================================
    // 场景 2：事务外代码 — 同步调用，必须等事务返回
    // ============================================================

    /**
     * 调用方和事务方法在同一个线程里
     *
     * 时间线：
     *   T0: [调用方] ① 准备调用事务方法
     *   T0: [调用方] 进入事务方法 ↓
     *   T0: [事务方法] 开始执行
     *   T1: [事务方法] 模拟阻塞 3 秒...
     *       ............（调用方线程也被卡住，因为是同一个线程）............
     *   T4: [事务方法] 阻塞结束，提交
     *   T4: [调用方] ② 事务方法返回了 ← 此时才能继续
     *   T4: [调用方] ③ 继续执行后续逻辑
     *
     * 关键：② 和 ③ 不会提前执行，必须等事务方法返回
     */
    public String demonstrateBlockingOutside() {
        log.info("[调用方] ① 准备调用事务方法");

        // 这个调用会阻塞当前线程，直到事务方法返回
        String result = doSomethingInTx();

        // 这行代码在事务方法返回之前不会执行！
        log.info("[调用方] ② 事务方法返回了，result={}", result);
        log.info("[调用方] ③ 继续执行后续逻辑");

        return "调用方完成: " + result;
    }

    @Transactional
    public String doSomethingInTx() {
        log.info("[事务方法] 开始执行");
        try {
            log.info("[事务方法] 模拟阻塞 3 秒（比如等数据库锁）");
            TimeUnit.SECONDS.sleep(3); // 模拟阻塞
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[事务方法] 阻塞结束，准备提交");
        return "事务完成";
    }

    // ============================================================
    // 场景 3：同一事务内，阻塞点之后的代码
    // ============================================================

    /**
     * 同一事务方法内，阻塞点之后的代码会不会执行？
     *
     * 答案：阻塞结束后会执行。
     *
     * 时间线：
     *   T0: ① 开启事务，第一次查询 → 成功
     *   T1: ② Thread.sleep(2000) → 阻塞 2 秒     ← 线程卡住
     *       ............（什么都做不了）............
     *   T3: ③ 阻塞结束，第二次查询 → 执行        ← 阻塞结束后才执行
     *   T3: ④ 更新 → 执行
     *   T3: ⑤ 提交事务
     *
     * 关键：③④⑤ 不会在 T1~T3 期间执行，必须等阻塞结束
     */
    @Transactional
    public String blockingThenContinue(Long userId) {
        log.info("[事务内] ① 开启事务，第一次查询");
        User user = userRepository.findById(userId).orElseThrow();
        log.info("[事务内] 查询成功: {}", user.getUsername());

        log.info("[事务内] ② 开始阻塞 2 秒...");
        try {
            TimeUnit.SECONDS.sleep(2); // 模拟阻塞（比如等外部服务响应）
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 阻塞结束后，继续执行
        log.info("[事务内] ③ 阻塞结束，第二次查询");
        User user2 = userRepository.findById(userId).orElseThrow();

        log.info("[事务内] ④ 更新用户");
        user2.setUsername("阻塞后更新");
        userRepository.save(user2);

        log.info("[事务内] ⑤ 提交事务");
        return "完成: " + user2.getUsername();
    }

    // ============================================================
    // 场景 4：最直观的例子 — 事务内外代码交替
    // ============================================================

    /**
     * 最直观的例子：事务内外代码在同一方法中
     *
     * 时间线：
     *   T0: ① 事务外代码A → 执行
     *   T0: ② 进入事务方法
     *   T0:   ③ 事务内代码 → 执行
     *   T1:   ④ 阻塞（等锁/sleep）          ← 线程卡住
     *       ............（线程什么都做不了）............
     *   T4:   ⑤ 阻塞结束，继续执行
     *   T4:   ⑥ 提交事务，返回
     *   T4: ⑦ 事务外代码B → 此时才能执行     ← 不会提前执行！
     *
     * 结论：⑦ 不会在 T1~T4 期间执行
     */
    public String intuitiveExample(Long userId) {
        // ① 事务外代码 A
        log.info("[直观例子] ① 事务外代码A → 先执行");

        // ② 进入事务方法（同步调用，同一个线程）
        String result = blockingThenContinue(userId);

        // ⑦ 事务外代码 B — 必须等事务方法返回才能执行！
        log.info("[直观例子] ⑦ 事务外代码B → 事务返回后才执行，result={}", result);

        return result;
    }

    // ============================================================
    // 场景 5：数据库锁阻塞 — 最常见的实际场景
    // ============================================================

    /**
     * 最常见的阻塞场景：事务 A 持有行锁，事务 B 等锁
     *
     * 线程1（事务A）：                     线程2（事务B）：
     *   BEGIN;                              BEGIN;
     *   UPDATE account SET balance=100      -- 阻塞在这里，等线程1释放锁
     *   WHERE id=1;                         UPDATE account SET balance=200
     *   -- 持有锁                            WHERE id=1;
     *   -- 做其他事情...                     -- 线程2卡住了，什么都做不了
     *   COMMIT; 释放锁                      -- 锁释放，线程2继续执行
     *                                       COMMIT;
     *
     * 关键：线程2在等锁期间，线程2上的所有代码都不会执行
     *      不管是事务内的还是事务外的，全部卡住
     */
    @Transactional
    public String lockContentionDemo(Long accountId) {
        log.info("[锁竞争] ① 开启事务，查询账户");

        // 模拟：另一个事务持有这行的锁
        // 这里会阻塞，直到另一个事务提交或回滚
        log.info("[锁竞争] ② 尝试更新（可能阻塞）");

        // 如果阻塞：
        // - 事务内的后续代码不会执行
        // - 调用方的后续代码也不会执行
        // - 整个线程卡住

        User user = userRepository.findById(accountId).orElseThrow();
        user.setUsername("锁竞争更新");
        userRepository.save(user);

        log.info("[锁竞争] ③ 更新成功，提交事务");
        return "锁竞争完成";
    }

    // ============================================================
    // 总结
    // ============================================================

    /*
     * ╔══════════════════════════════════════════════════════════════╗
     * ║                    事务阻塞行为总结                          ║
     * ╠══════════════════════════════════════════════════════════════╣
     * ║                                                            ║
     * ║  核心原则：同一时刻，一个线程只能做一件事                      ║
     * ║                                                            ║
     * ║  场景 1：事务内阻塞（等锁/sleep/IO）                         ║
     * ║    → 阻塞点之后的事务内代码不会执行（直到阻塞结束）             ║
     * ║    → 事务外的调用方代码也不会执行（直到事务方法返回）            ║
     * ║                                                            ║
     * ║  场景 2：事务外代码在事务方法之后                              ║
     * ║    → 必须等事务方法返回后才能执行                              ║
     * ║    → 不会在事务阻塞期间"偷偷"执行                             ║
     * ║                                                            ║
     * ║  场景 3：数据库锁阻塞（最常见）                               ║
     * ║    → 事务 A 持有行锁，事务 B 等锁                            ║
     * ║    → 事务 B 的线程完全卡住，什么代码都不执行                   ║
     * ║                                                            ║
     * ║  为什么？                                                   ║
     * ║    → Java 是单线程同步执行模型                               ║
     * ║    → 方法调用 = 压栈，返回 = 弹栈                           ║
     * ║    → 阻塞 = 线程暂停，不等于"跳过"                           ║
     * ║                                                            ║
     * ║  例外情况（异步）：                                          ║
     * ║    → @Async：事务在另一个线程执行，调用方线程不阻塞             ║
     * ║    → 但事务所在的线程仍然会被阻塞                             ║
     * ║                                                            ║
     * ╚══════════════════════════════════════════════════════════════╝
     */
}
