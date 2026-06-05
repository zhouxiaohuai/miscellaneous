package com.example.transaction.redis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁演示
 *
 * 实现方式：
 * 1. 基于 SETNX 的简单锁
 * 2. 带过期时间的锁（防止死锁）
 * 3. 带唯一标识的锁（防止误删）
 * 4. Lua 脚本保证原子性释放
 *
 * 生产环境建议使用 Redisson，这里演示底层原理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLockService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LOCK_PREFIX = "lock:";

    // ========================================
    // 1. 简单锁（SETNX）
    // ========================================

    /**
     * 简单分布式锁
     *
     * 问题：如果持有锁的进程崩溃，锁不会释放 → 死锁
     */
    public Map<String, Object> simpleLockDemo(String lockKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = LOCK_PREFIX + lockKey;

        // 加锁
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, "locked");

        if (Boolean.TRUE.equals(locked)) {
            result.put("状态", "获取锁成功");
            result.put("说明", "执行业务逻辑...");

            // 模拟业务处理
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 释放锁
            redisTemplate.delete(key);
            result.put("释放", "锁已释放");
        } else {
            result.put("状态", "获取锁失败");
            result.put("说明", "其他进程持有锁");
        }

        return result;
    }

    // ========================================
    // 2. 带过期时间的锁
    // ========================================

    /**
     * 带过期时间的分布式锁
     *
     * 解决死锁问题：即使进程崩溃，锁也会自动过期释放
     */
    public Map<String, Object> lockWithTTLDemo(String lockKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = LOCK_PREFIX + lockKey;

        // 加锁（带过期时间）
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, "locked", 30, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(locked)) {
            result.put("状态", "获取锁成功");
            result.put("过期时间", "30秒");

            // 模拟业务处理
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 释放锁
            redisTemplate.delete(key);
            result.put("释放", "锁已释放");
        } else {
            result.put("状态", "获取锁失败");
        }

        return result;
    }

    // ========================================
    // 3. 带唯一标识的锁
    // ========================================

    /**
     * 带唯一标识的分布式锁
     *
     * 问题场景：
     * 1. 线程A获取锁，执行时间超过过期时间，锁自动释放
     * 2. 线程B获取到锁
     * 3. 线程A执行完毕，释放锁 → 误删了线程B的锁！
     *
     * 解决：使用唯一标识，释放时验证标识
     */
    public Map<String, Object> lockWithUniqueIDDemo(String lockKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = LOCK_PREFIX + lockKey;
        String requestId = UUID.randomUUID().toString(); // 唯一标识

        // 加锁（带唯一标识和过期时间）
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, requestId, 30, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(locked)) {
            result.put("状态", "获取锁成功");
            result.put("requestId", requestId);

            // 模拟业务处理
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 释放锁（验证标识）
            String currentValue = (String) redisTemplate.opsForValue().get(key);
            if (requestId.equals(currentValue)) {
                redisTemplate.delete(key);
                result.put("释放", "锁已释放（标识匹配）");
            } else {
                result.put("释放", "锁已被其他线程持有，不释放");
            }
        } else {
            result.put("状态", "获取锁失败");
        }

        return result;
    }

    // ========================================
    // 4. 重入锁（可重入）
    // ========================================

    /**
     * 可重入锁
     *
     * 同一线程可以多次获取同一把锁
     * 使用 ThreadLocal 记录重入次数
     */
    private final ThreadLocal<Map<String, Integer>> lockCount = ThreadLocal.withInitial(HashMap::new);

    public Map<String, Object> reentrantLockDemo(String lockKey) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = LOCK_PREFIX + lockKey;
        String requestId = UUID.randomUUID().toString();

        // 第一次加锁
        boolean firstLock = tryLock(key, requestId, 30);
        result.put("第1次加锁", firstLock ? "成功" : "失败");

        if (firstLock) {
            // 第二次加锁（可重入）
            boolean secondLock = tryLock(key, requestId, 30);
            result.put("第2次加锁", secondLock ? "成功（可重入）" : "失败");

            // 获取重入次数
            Integer count = lockCount.get().get(key);
            result.put("重入次数", count);

            // 释放锁（需要释放两次）
            unlock(key, requestId);
            result.put("第1次释放", "重入次数: " + lockCount.get().getOrDefault(key, 0));

            unlock(key, requestId);
            result.put("第2次释放", "锁已完全释放");
        }

        return result;
    }

    private boolean tryLock(String key, String requestId, long expireSeconds) {
        Map<String, Integer> countMap = lockCount.get();
        Integer count = countMap.getOrDefault(key, 0);

        if (count > 0) {
            // 已持有锁，重入
            countMap.put(key, count + 1);
            return true;
        }

        // 尝试获取锁
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, requestId, expireSeconds, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(locked)) {
            countMap.put(key, 1);
            return true;
        }

        return false;
    }

    private void unlock(String key, String requestId) {
        Map<String, Integer> countMap = lockCount.get();
        Integer count = countMap.getOrDefault(key, 0);

        if (count <= 0) {
            return;
        }

        if (count > 1) {
            // 还有重入，只减少计数
            countMap.put(key, count - 1);
        } else {
            // 最后一次释放，删除锁
            String currentValue = (String) redisTemplate.opsForValue().get(key);
            if (requestId.equals(currentValue)) {
                redisTemplate.delete(key);
            }
            countMap.remove(key);
        }
    }

    // ========================================
    // 5. 锁的使用场景演示
    // ========================================

    /**
     * 场景：防止重复下单
     */
    public Map<String, Object> preventDuplicateOrder(String userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String lockKey = "order:" + userId;
        String requestId = UUID.randomUUID().toString();

        // 尝试获取锁
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                LOCK_PREFIX + lockKey, requestId, 10, TimeUnit.SECONDS
        );

        if (Boolean.TRUE.equals(locked)) {
            try {
                // 模拟下单逻辑
                log.info("[防重复下单] 用户 {} 开始下单", userId);
                Thread.sleep(2000);

                result.put("状态", "下单成功");
                result.put("userId", userId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                // 释放锁
                String currentValue = (String) redisTemplate.opsForValue().get(LOCK_PREFIX + lockKey);
                if (requestId.equals(currentValue)) {
                    redisTemplate.delete(LOCK_PREFIX + lockKey);
                }
            }
        } else {
            result.put("状态", "请勿重复提交");
            result.put("说明", "用户 " + userId + " 有正在进行的订单");
        }

        return result;
    }

    /**
     * 场景：秒杀库存扣减
     */
    public Map<String, Object> seckillStockDeduction(String productId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String lockKey = "seckill:" + productId;
        String stockKey = "stock:" + productId;
        String requestId = UUID.randomUUID().toString();

        // 初始化库存（演示用）
        redisTemplate.opsForValue().setIfAbsent(stockKey, 100);

        // 尝试获取锁
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                LOCK_PREFIX + lockKey, requestId, 10, TimeUnit.SECONDS
        );

        if (Boolean.TRUE.equals(locked)) {
            try {
                // 检查库存
                Integer stock = (Integer) redisTemplate.opsForValue().get(stockKey);

                if (stock != null && stock > 0) {
                    // 扣减库存
                    redisTemplate.opsForValue().decrement(stockKey);
                    result.put("状态", "秒杀成功");
                    result.put("剩余库存", stock - 1);
                } else {
                    result.put("状态", "库存不足");
                }
            } finally {
                // 释放锁
                String currentValue = (String) redisTemplate.opsForValue().get(LOCK_PREFIX + lockKey);
                if (requestId.equals(currentValue)) {
                    redisTemplate.delete(LOCK_PREFIX + lockKey);
                }
            }
        } else {
            result.put("状态", "系统繁忙，请重试");
        }

        return result;
    }

    // ========================================
    // 6. 秒杀改进版（Lua 脚本原子操作 + 用户记录）
    // ========================================

    /**
     * 秒杀 Lua 脚本
     *
     * KEYS[1] = stockKey   库存
     * KEYS[2] = buyerKey   已购买用户集合 (Set)
     * ARGV[1] = userId
     *
     * 返回:  1=成功  0=库存不足  -1=已抢过
     */
    private static final String SECKILL_LUA =
            "if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then " +
            "  return -1 " +
            "end " +
            "local stock = tonumber(redis.call('get', KEYS[1])) " +
            "if stock and stock > 0 then " +
            "  redis.call('decr', KEYS[1]) " +
            "  redis.call('sadd', KEYS[2], ARGV[1]) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end";

    /**
     * 场景：秒杀库存扣减（改进版）
     *
     * 对比原版 seckillStockDeduction 的改进：
     * 1. 原子操作：Lua 脚本保证「判重 + 扣库存 + 记人」一步完成，无需分布式锁
     * 2. 防重复秒杀：用 Set 记录已秒杀用户，同一用户不会重复扣减
     * 3. 无锁设计：避免了原版 GET+DELETE 非原子释放锁的隐患
     *
     * @param productId 商品ID
     * @param userId    用户ID
     * @return 秒杀结果
     */
    public Map<String, Object> seckillWithUserRecord(String productId, String userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String stockKey = "stock:" + productId;
        String buyerKey = "seckill:buyers:" + productId;

        // 初始化库存（演示用）
        redisTemplate.opsForValue().setIfAbsent(stockKey, 100);

        // Lua 脚本原子执行：防重复 + 扣库存 + 记人
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(SECKILL_LUA, Long.class);
        Long res = redisTemplate.execute(script, List.of(stockKey, buyerKey), userId);

        if (res == null) {
            result.put("状态", "系统异常");
        } else if (res == 1) {
            Integer stock = (Integer) redisTemplate.opsForValue().get(stockKey);
            result.put("状态", "秒杀成功");
            result.put("userId", userId);
            result.put("剩余库存", stock);
            // TODO: 生产环境此处发 MQ 消息，异步创建订单
            log.info("[秒杀成功] 用户 {} 抢到商品 {}", userId, productId);
        } else if (res == -1) {
            result.put("状态", "您已抢过，请勿重复下单");
            result.put("userId", userId);
        } else {
            result.put("状态", "库存不足，秒杀结束");
        }

        return result;
    }
}
