package com.example.transaction.seckill.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * P0-2：用户级限流
 *
 * 方案：Redis 滑动窗口限流（ZSet 实现）
 *
 * 原理：
 *   用 Redis ZSet 存储每次请求的时间戳（score = 时间戳）
 *   每次请求时，删除窗口外的旧记录，统计窗口内的请求数
 *   超过限制就拒绝
 *
 * 为什么用 ZSet 而不是 INCR + EXPIRE？
 *   INCR + EXPIRE = 固定窗口，有边界问题：
 *     窗口末尾5次 + 窗口开头5次 = 1秒内10次，绕过限制
 *   ZSet = 滑动窗口，统计"过去N秒"的精确请求数，无边界问题
 *
 * 为什么需要用户级限流？
 *   - 防止恶意用户无限刷接口
 *   - 保护后端服务不被打垮
 *   - 保证公平性，让更多用户有机会抢到
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillRateLimitService {

    private final StringRedisTemplate redisTemplate;

    /** 限流 key：seckill:rate:{userId}:{productId}（ZSet 结构） */
    private static final String RATE_LIMIT_KEY = "seckill:rate:%d:%d";

    /** 限流参数：每 60 秒最多 5 次 */
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MILLIS = 60 * 1000;

    /**
     * Lua 脚本：滑动窗口限流（ZSet 实现）
     *
     * 原理：
     *   1. 删除窗口外的旧记录（score < 当前时间 - 窗口大小）
     *   2. 统计窗口内的请求数
     *   3. 如果未超过限制，添加当前请求记录
     *
     * KEYS[1] = 限流 key（ZSet）
     * ARGV[1] = 窗口开始时间戳（当前时间 - 窗口大小）
     * ARGV[2] = 当前时间戳
     * ARGV[3] = 最大次数
     *
     * 返回值：
     *   1 = 允许通过
     *   0 = 超过限制
     */
    private static final String RATE_LIMIT_LUA = """
            -- 1. 删除窗口外的旧记录
            redis.call('zremrangebyscore', KEYS[1], '-inf', ARGV[1])

            -- 2. 统计窗口内的请求数
            local count = redis.call('zcard', KEYS[1])

            -- 3. 判断是否超过限制
            if count >= tonumber(ARGV[3]) then
                return 0
            end

            -- 4. 未超过限制，添加当前请求（score = 时间戳，value = 时间戳+随机数）
            local member = ARGV[2] .. ':' .. math.random(1000000)
            redis.call('zadd', KEYS[1], ARGV[2], member)

            -- 5. 设置 key 过期时间（避免内存泄漏）
            redis.call('expire', KEYS[1], math.ceil(tonumber(ARGV[3]) / 1000) + 1)

            return 1
            """;

    /**
     * 检查是否允许请求（滑动窗口）
     *
     * @param userId    用户ID
     * @param productId 商品ID
     * @return true=允许，false=拒绝
     */
    public boolean isAllowed(Long userId, Long productId) {
        String key = String.format(RATE_LIMIT_KEY, userId, productId);
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MILLIS;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(RATE_LIMIT_LUA, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key),
                String.valueOf(windowStart), String.valueOf(now), String.valueOf(MAX_ATTEMPTS));

        boolean allowed = result != null && result == 1;

        if (!allowed) {
            log.warn("[限流] 用户 {} 请求商品 {} 过于频繁，已拒绝", userId, productId);
        }

        return allowed;
    }

    /**
     * 获取用户当前的请求次数（窗口内）
     */
    public Long getCurrentCount(Long userId, Long productId) {
        String key = String.format(RATE_LIMIT_KEY, userId, productId);
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MILLIS;

        // 删除过期记录后统计
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count != null ? count : 0L;
    }

    /**
     * 重置用户的限流计数（测试用）
     */
    public void reset(Long userId, Long productId) {
        String key = String.format(RATE_LIMIT_KEY, userId, productId);
        redisTemplate.delete(key);
    }
}
