package com.example.transaction.architecture.distributed.lock;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * ============================================================
 * 分布式锁 — Redisson 实现
 * ============================================================
 *
 * 【为什么需要分布式锁？】
 * 单机：synchronized / ReentrantLock 就够了
 * 集群：多个 JVM 实例，本地锁失效，需要分布式锁
 *
 * 【分布式锁方案对比】
 * ┌──────────────┬──────────┬──────────┬──────────┬──────────┐
 * │ 方案          │ 可靠性   │ 性能     │ 可重入   │ 自动续期  │
 * ├──────────────┼──────────┼──────────┼──────────┼──────────┤
 * │ SETNX        │ 中       │ 高       │ 否       │ 否       │
 * │ Redisson     │ 高       │ 高       │ 是       │ 是       │
 * │ ZooKeeper    │ 高       │ 中       │ 是       │ 是       │
 * │ Etcd         │ 高       │ 中       │ 是       │ 是       │
 * └──────────────┴──────────┴──────────┴──────────┴──────────┘
 *
 * 【Redisson 分布式锁原理】
 * 1. 加锁：Lua 脚本执行 SET key value NX EX
 * 2. 看门狗（Watchdog）：后台线程每 10s 续期一次（默认锁 30s）
 * 3. 解锁：Lua 脚本校验 value 一致后删除（防止误解锁）
 * 4. 可重入：用 Hash 结构记录重入次数
 *
 * 【本示例场景】
 * 秒杀场景：多个节点同时扣减库存，需要用分布式锁保证原子性。
 */
@Slf4j
@Component
public class DistributedLockDemo {

    private final RedissonClient redissonClient;

    public DistributedLockDemo(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 示例 1: 基本用法 — lock/unlock
     */
    public void basicLockExample() {
        String lockKey = "demo:lock:basic";
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试加锁（阻塞等待）
            lock.lock();
            log.info("[分布式锁] 获取锁成功: key={}", lockKey);

            // 模拟业务处理
            Thread.sleep(2000);
            log.info("[分布式锁] 业务处理完成");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // 必须在 finally 中释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("[分布式锁] 释放锁: key={}", lockKey);
            }
        }
    }

    /**
     * 示例 2: 尝试加锁 — tryLock（非阻塞）
     *
     * @param stockKey 库存 key
     * @param quantity 扣减数量
     * @return 是否扣减成功
     */
    public boolean tryDeductStock(String stockKey, int quantity) {
        String lockKey = "lock:stock:" + stockKey;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试加锁，最多等待 3 秒，锁自动过期 10 秒
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);

            if (!acquired) {
                log.warn("[分布式锁] 获取锁超时: key={}", lockKey);
                return false;
            }

            log.info("[分布式锁] 获取锁成功: key={}", lockKey);

            // 模拟扣减库存（实际从 Redis/DB 读取并扣减）
            log.info("[分布式锁] 扣减库存: stockKey={}, quantity={}", stockKey, quantity);
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("[分布式锁] 释放锁: key={}", lockKey);
            }
        }
    }

    /**
     * 示例 3: 看门狗续期 — 长时间任务
     *
     * 【看门狗机制】
     * 默认锁过期时间 30 秒
     * 如果业务执行超过 30 秒，看门狗会自动续期
     * 只有调用 unlock() 或进程宕机才会释放锁
     */
    public void watchdogExample() {
        String lockKey = "demo:lock:watchdog";
        RLock lock = redissonClient.getLock(lockKey);

        try {
            lock.lock(); // 不指定过期时间，看门狗自动续期
            log.info("[分布式锁-看门狗] 获取锁成功，开始长时间任务");

            // 模拟长时间任务（60 秒）
            // 看门狗会每 10 秒续期一次，不用担心锁过期
            Thread.sleep(60000);
            log.info("[分布式锁-看门狗] 长时间任务完成");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("[分布式锁-看门狗] 释放锁");
            }
        }
    }

    /**
     * 示例 4: 公平锁 — 按请求顺序获取锁
     *
     * 【公平 vs 非公平】
     * 非公平锁：所有线程竞争，可能有的线程一直抢不到（饥饿）
     * 公平锁：按请求顺序排队，先到先得
     */
    public void fairLockExample() {
        String lockKey = "demo:lock:fair";
        RLock fairLock = redissonClient.getFairLock(lockKey);

        try {
            fairLock.lock();
            log.info("[分布式锁-公平锁] 获取锁成功");

            // 业务处理
            Thread.sleep(1000);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (fairLock.isHeldByCurrentThread()) {
                fairLock.unlock();
            }
        }
    }

    /**
     * 示例 5: 读写锁 — 读多写少场景
     *
     * 【读写锁特性】
     * - 读锁共享：多个线程可以同时持有读锁
     * - 写锁独占：写锁和任何锁互斥
     * - 适用：读多写少的缓存更新场景
     */
    public void readWriteLockExample() {
        String lockKey = "demo:lock:rw";
        var rwLock = redissonClient.getReadWriteLock(lockKey);

        // 读锁
        RLock readLock = rwLock.readLock();
        try {
            readLock.lock();
            log.info("[读写锁] 获取读锁成功，可并发读");
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        } finally {
            if (readLock.isHeldByCurrentThread()) {
                readLock.unlock();
            }
        }

        // 写锁
        RLock writeLock = rwLock.writeLock();
        try {
            writeLock.lock();
            log.info("[读写锁] 获取写锁成功，独占写");
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        } finally {
            if (writeLock.isHeldByCurrentThread()) {
                writeLock.unlock();
            }
        }
    }
}
