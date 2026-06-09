package com.example.transaction.seckill.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * P1-1：防重复提交
 *
 * 方案：Redis 分布式锁（SETNX）
 *
 * 原理：
 *   用户点击"秒杀"时，先尝试获取锁
 *   - 获取成功 → 进入秒杀流程
 *   - 获取失败 → 说明有请求正在处理，拒绝重复提交
 *
 * 为什么不用数据库唯一索引？
 *   - 唯一索引能防止重复订单，但不能防止重复请求
 *   - 重复请求还是会到达数据库，浪费资源
 *   - 分布式锁在 Redis 层就拦截了重复请求
 *
 * 锁的 key：seckill:lock:{userId}:{productId}
 * 锁的过期时间：5 秒（防止死锁）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillDuplicateService {

    private final StringRedisTemplate redisTemplate;

    /** 锁 key：seckill:lock:{userId}:{productId} */
    private static final String LOCK_KEY = "seckill:lock:%d:%d";

    /** 锁过期时间：5 秒 */
    private static final long LOCK_EXPIRE_SECONDS = 5;

    /**
     * 尝试获取锁
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @return true=获取成功，false=重复请求
     */
    public boolean tryLock(Long userId, Long productId) {
        String key = String.format(LOCK_KEY, userId, productId);

        // SET key value NX EX seconds
        // NX = 不存在才设置（获取锁）
        // EX = 设置过期时间（防止死锁）
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);

        boolean locked = Boolean.TRUE.equals(success);

        if (!locked) {
            log.warn("[防重复] 用户 {} 商品 {} 请求正在处理中，拒绝重复提交", userId, productId);
        }

        return locked;
    }

    /**
     * 释放锁
     *
     * @param userId    用户ID
     * @param productId 商品ID
     */
    public void unlock(Long userId, Long productId) {
        String key = String.format(LOCK_KEY, userId, productId);
        redisTemplate.delete(key);
    }

    /**
     * 检查是否持有锁
     */
    public boolean isLocked(Long userId, Long productId) {
        String key = String.format(LOCK_KEY, userId, productId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
