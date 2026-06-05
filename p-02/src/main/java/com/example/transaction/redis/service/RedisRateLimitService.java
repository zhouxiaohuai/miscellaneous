package com.example.transaction.redis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 限流配置类（存储在 Redis 中）
 */
@lombok.Data
class RateLimitConfig {
    private int maxRequests;      // 最大请求数
    private int windowSeconds;    // 窗口时间（秒）
    private String description;   // 配置描述

    public RateLimitConfig() {}

    public RateLimitConfig(int maxRequests, int windowSeconds, String description) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.description = description;
    }
}

/**
 * Redis 限流策略演示
 *
 * 常见限流算法：
 * 1. 固定窗口计数器
 * 2. 滑动窗口计数器
 * 3. 漏桶算法
 * 4. 令牌桶算法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisRateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RATE_LIMIT_PREFIX = "rate:";
    private static final String CONFIG_PREFIX = "rate:config:";

    // 默认配置（Redis 中没有配置时使用）
    private static final Map<String, RateLimitConfig> DEFAULT_CONFIGS = Map.of(
        "global", new RateLimitConfig(10000, 1, "全局限流：每秒10000次"),
        "ip", new RateLimitConfig(100, 60, "IP限流：每分钟100次"),
        "user", new RateLimitConfig(1000, 3600, "用户限流：每小时1000次")
    );

    // ========================================
    // 动态配置管理
    // ========================================

    /**
     * 获取限流配置（优先从 Redis 读取，没有则用默认值）
     */
    public RateLimitConfig getConfig(String type) {
        String key = CONFIG_PREFIX + type;
        RateLimitConfig config = (RateLimitConfig) redisTemplate.opsForValue().get(key);

        if (config == null) {
            // 使用默认配置
            config = DEFAULT_CONFIGS.getOrDefault(type, new RateLimitConfig(100, 60, "默认配置"));
        }
        return config;
    }

    /**
     * 更新限流配置
     */
    public Map<String, Object> updateConfig(String type, int maxRequests, int windowSeconds, String description) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = CONFIG_PREFIX + type;

        RateLimitConfig config = new RateLimitConfig(maxRequests, windowSeconds, description);
        redisTemplate.opsForValue().set(key, config);

        result.put("状态", "配置已更新");
        result.put("类型", type);
        result.put("新配置", config);
        return result;
    }

    /**
     * 获取所有限流配置
     */
    public Map<String, Object> getAllConfigs() {
        Map<String, Object> result = new LinkedHashMap<>();

        for (String type : DEFAULT_CONFIGS.keySet()) {
            result.put(type, getConfig(type));
        }

        return result;
    }

    /**
     * 删除配置（恢复默认）
     */
    public Map<String, Object> resetConfig(String type) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = CONFIG_PREFIX + type;

        redisTemplate.delete(key);

        result.put("状态", "已重置为默认");
        result.put("类型", type);
        result.put("默认配置", DEFAULT_CONFIGS.get(type));
        return result;
    }

    // ========================================
    // 1. 固定窗口计数器
    // ========================================

    /**
     * 固定窗口计数器
     *
     * 原理：在固定时间窗口内计数，超过阈值则拒绝
     *
     * 优点：简单易实现
     * 缺点：窗口边界可能出现突发流量（两个窗口交界处）
     *
     * 例如：限制每分钟最多 100 次请求
     * 00:00-00:59 → 计数
     * 01:00-01:59 → 重新计数
     */
    public Map<String, Object> fixedWindowLimiter(String userId, int maxRequests, int windowSeconds) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = RATE_LIMIT_PREFIX + "fixed:" + userId;

        // 获取当前计数
        Integer count = (Integer) redisTemplate.opsForValue().get(key);

        if (count == null) {
            // 首次请求，设置计数和过期时间
            redisTemplate.opsForValue().set(key, 1, windowSeconds, TimeUnit.SECONDS);
            result.put("状态", "允许");
            result.put("当前次数", 1);
            result.put("限制", maxRequests + "次/" + windowSeconds + "秒");
        } else if (count < maxRequests) {
            // 未超过限制，计数+1
            redisTemplate.opsForValue().increment(key);
            result.put("状态", "允许");
            result.put("当前次数", count + 1);
            result.put("限制", maxRequests + "次/" + windowSeconds + "秒");
        } else {
            // 超过限制
            Long ttl = redisTemplate.getExpire(key);
            result.put("状态", "拒绝");
            result.put("原因", "超过限制");
            result.put("重试时间", ttl + "秒后");
        }

        return result;
    }

    // ========================================
    // 2. 滑动窗口计数器
    // ========================================

    /**
     * 滑动窗口计数器
     *
     * 原理：使用 Redis Sorted Set，以时间戳为分数
     *
     * 优点：解决了固定窗口的边界问题
     * 缺点：内存消耗较大
     */
    public Map<String, Object> slidingWindowLimiter(String userId, int maxRequests, int windowSeconds) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = RATE_LIMIT_PREFIX + "sliding:" + userId;

        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000L);

        // 移除窗口外的记录
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // 获取当前窗口内的请求数
        Long count = redisTemplate.opsForZSet().zCard(key);

        if (count != null && count >= maxRequests) {
            // 超过限制
            result.put("状态", "拒绝");
            result.put("原因", "超过限制");
            result.put("当前次数", count);
            result.put("限制", maxRequests + "次/" + windowSeconds + "秒");
        } else {
            // 允许请求，添加记录
            redisTemplate.opsForZSet().add(key, String.valueOf(now), now);
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);

            result.put("状态", "允许");
            result.put("当前次数", count != null ? count + 1 : 1);
            result.put("限制", maxRequests + "次/" + windowSeconds + "秒");
        }

        return result;
    }

    // ========================================
    // 3. 漏桶算法
    // ========================================

    /**
     * 漏桶算法
     *
     * 原理：请求进入桶中，以固定速率流出处理
     *
     * 优点：平滑请求速率
     * 缺点：无法应对突发流量
     *
     * 实现：
     * - 桶容量：最大请求数
     * - 流出速率：每秒处理请求数
     */
    public Map<String, Object> leakyBucketLimiter(String userId, int bucketCapacity, int leakRate) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = RATE_LIMIT_PREFIX + "leaky:" + userId;
        String lastLeakKey = key + ":last";

        long now = System.currentTimeMillis();

        // 获取上次漏水时间
        Long lastLeakTime = (Long) redisTemplate.opsForValue().get(lastLeakKey);
        Integer currentLevel = (Integer) redisTemplate.opsForValue().get(key);

        if (lastLeakTime == null) {
            // 首次请求
            redisTemplate.opsForValue().set(key, 1);
            redisTemplate.opsForValue().set(lastLeakKey, now);
            result.put("状态", "允许");
            result.put("桶中请求数", 1);
        } else {
            // 计算漏水量
            long elapsed = now - lastLeakTime;
            int leaked = (int) (elapsed / 1000 * leakRate);

            if (currentLevel == null) {
                currentLevel = 0;
            }

            // 更新桶中水量
            int newLevel = Math.max(0, currentLevel - leaked);

            if (newLevel < bucketCapacity) {
                // 桶未满，允许请求
                redisTemplate.opsForValue().set(key, newLevel + 1);
                redisTemplate.opsForValue().set(lastLeakKey, now);

                result.put("状态", "允许");
                result.put("桶中请求数", newLevel + 1);
                result.put("桶容量", bucketCapacity);
            } else {
                // 桶已满，拒绝请求
                result.put("状态", "拒绝");
                result.put("原因", "桶已满");
                result.put("桶中请求数", currentLevel);
                result.put("桶容量", bucketCapacity);
            }
        }

        return result;
    }

    // ========================================
    // 4. 令牌桶算法
    // ========================================

    /**
     * 令牌桶算法
     *
     * 原理：以固定速率向桶中添加令牌，请求需要消耗令牌
     *
     * 优点：允许一定的突发流量（桶中有积累的令牌）
     * 缺点：实现相对复杂
     *
     * 实现：
     * - 桶容量：最大令牌数
     * - 添加速率：每秒添加令牌数
     * - 每个请求消耗 1 个令牌
     */
    public Map<String, Object> tokenBucketLimiter(String userId, int bucketCapacity, int refillRate) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = RATE_LIMIT_PREFIX + "token:" + userId;
        String lastRefillKey = key + ":last";

        long now = System.currentTimeMillis();

        // 获取上次填充时间
        Long lastRefillTime = (Long) redisTemplate.opsForValue().get(lastRefillKey);
        Integer currentTokens = (Integer) redisTemplate.opsForValue().get(key);

        if (lastRefillTime == null) {
            // 首次请求，初始化桶
            redisTemplate.opsForValue().set(key, bucketCapacity - 1);
            redisTemplate.opsForValue().set(lastRefillKey, now);
            result.put("状态", "允许");
            result.put("剩余令牌", bucketCapacity - 1);
        } else {
            // 计算应添加的令牌数
            long elapsed = now - lastRefillTime;
            int refill = (int) (elapsed / 1000 * refillRate);

            if (currentTokens == null) {
                currentTokens = 0;
            }

            // 更新令牌数（不超过桶容量）
            int newTokens = Math.min(bucketCapacity, currentTokens + refill);

            if (newTokens > 0) {
                // 有令牌，允许请求
                redisTemplate.opsForValue().set(key, newTokens - 1);
                redisTemplate.opsForValue().set(lastRefillKey, now);

                result.put("状态", "允许");
                result.put("剩余令牌", newTokens - 1);
                result.put("桶容量", bucketCapacity);
            } else {
                // 无令牌，拒绝请求
                result.put("状态", "拒绝");
                result.put("原因", "令牌不足");
                result.put("剩余令牌", 0);
            }
        }

        return result;
    }

    // ========================================
    // 5. 全局限流
    // ========================================

    /**
     * 全局限流（保护系统整体）- 使用动态配置
     *
     * 场景：防止系统被打垮，不管是来自哪个 IP 或用户
     * 例如：突然涌入大量不同 IP 的请求（DDoS、爬虫池）
     */
    public Map<String, Object> globalRateLimiter() {
        // 从 Redis 获取动态配置
        RateLimitConfig config = getConfig("global");
        return globalRateLimiter(config.getMaxRequests(), config.getWindowSeconds());
    }

    /**
     * 全局限流（指定参数）
     */
    public Map<String, Object> globalRateLimiter(int maxRequests, int windowSeconds) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = RATE_LIMIT_PREFIX + "global";

        // 使用滑动窗口
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000L);

        // 移除窗口外的记录
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // 获取当前窗口内的总请求数
        Long count = redisTemplate.opsForZSet().zCard(key);

        if (count != null && count >= maxRequests) {
            result.put("状态", "拒绝");
            result.put("原因", "系统繁忙，请稍后再试");
            result.put("当前总请求", count);
            result.put("限制", maxRequests + "次/" + windowSeconds + "秒");
        } else {
            // 使用请求ID作为member，避免重复
            String requestId = now + ":" + Thread.currentThread().getId();
            redisTemplate.opsForZSet().add(key, requestId, now);
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);

            result.put("状态", "允许");
            result.put("当前总请求", count != null ? count + 1 : 1);
            result.put("限制", maxRequests + "次/" + windowSeconds + "秒");
        }

        return result;
    }

    // ========================================
    // 6. 多层限流（推荐生产使用）
    // ========================================

    /**
     * 多层限流策略 - 使用动态配置
     *
     * 执行顺序：全局 → IP → 用户
     * 任何一层触发限制，直接拒绝
     *
     * @param ip       客户端 IP
     * @param userId   用户 ID（可为 null，表示未登录）
     */
    public Map<String, Object> multiLayerRateLimiter(String ip, String userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 第一层：全局限流（保护系统）- 使用动态配置
        Map<String, Object> globalResult = globalRateLimiter();
        if ("拒绝".equals(globalResult.get("状态"))) {
            result.put("状态", "拒绝");
            result.put("层级", "全局限流");
            result.put("原因", "系统繁忙，请稍后再试");
            return result;
        }

        // 第二层：IP 限流（防止单IP攻击）- 使用动态配置
        Map<String, Object> ipResult = ipRateLimiter(ip);
        if ("拒绝".equals(ipResult.get("状态"))) {
            result.put("状态", "拒绝");
            result.put("层级", "IP限流");
            result.put("原因", "请求过于频繁");
            return result;
        }

        // 第三层：用户限流（防止单用户滥用，仅对登录用户）
        if (userId != null && !userId.isEmpty()) {
            Map<String, Object> userResult = fixedWindowLimiter(userId, 1000, 3600);
            if ("拒绝".equals(userResult.get("状态"))) {
                result.put("状态", "拒绝");
                result.put("层级", "用户限流");
                result.put("原因", "请求次数已达上限");
                return result;
            }
        }

        // 全部通过
        result.put("状态", "允许");
        result.put("层级", "全部通过");
        return result;
    }

    // ========================================
    // 7. IP 限流
    // ========================================

    /**
     * 基于 IP 的限流 - 使用动态配置
     */
    public Map<String, Object> ipRateLimiter(String ip) {
        RateLimitConfig config = getConfig("ip");
        return ipRateLimiter(ip, config.getMaxRequests(), config.getWindowSeconds());
    }

    /**
     * 基于 IP 的限流（指定参数）
     */
    public Map<String, Object> ipRateLimiter(String ip, int maxRequests, int windowSeconds) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = RATE_LIMIT_PREFIX + "ip:" + ip;

        // 使用滑动窗口
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000L);

        // 移除窗口外的记录
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // 获取当前窗口内的请求数
        Long count = redisTemplate.opsForZSet().zCard(key);

        if (count != null && count >= maxRequests) {
            result.put("状态", "拒绝");
            result.put("IP", ip);
            result.put("原因", "请求过于频繁");
            result.put("限制", maxRequests + "次/" + windowSeconds + "秒");
        } else {
            redisTemplate.opsForZSet().add(key, String.valueOf(now), now);
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);

            result.put("状态", "允许");
            result.put("IP", ip);
            result.put("当前次数", count != null ? count + 1 : 1);
        }

        return result;
    }

    // ========================================
    // 8. Lua 脚本原子限流（集群环境推荐）
    // ========================================

    /**
     * Lua 脚本：滑动窗口限流（原子操作）
     *
     * 为什么用 Lua？
     * - 保证 check + increment 的原子性
     * - 避免多个实例同时操作导致计数不准
     * - 减少网络往返（一次请求完成所有操作）
     *
     * 脚本逻辑：
     * 1. 移除窗口外的请求记录
     * 2. 获取当前窗口内的请求数
     * 3. 判断是否超过限制
     * 4. 未超过则添加当前请求记录
     */
    private static final String SLIDING_WINDOW_LUA = """
        -- KEYS[1]: 限流 key
        -- ARGV[1]: 窗口开始时间
        -- ARGV[2]: 当前时间戳
        -- ARGV[3]: 最大请求数
        -- ARGV[4]: 窗口大小（秒）

        -- 1. 移除窗口外的记录
        redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1])

        -- 2. 获取当前窗口内的请求数
        local current = redis.call('ZCARD', KEYS[1])

        -- 3. 判断是否超过限制
        if current < tonumber(ARGV[3]) then
            -- 未超过，添加当前请求
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[2] .. ':' .. math.random(100000))
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return {1, current + 1}  -- {允许, 当前次数}
        else
            return {0, current}  -- {拒绝, 当前次数}
        end
        """;

    /**
     * Lua 脚本：固定窗口计数器（原子操作）
     *
     * 脚本逻辑：
     * 1. 获取当前计数
     * 2. 判断是否超过限制
     * 3. 未超过则计数+1
     */
    private static final String FIXED_WINDOW_LUA = """
        -- KEYS[1]: 限流 key
        -- ARGV[1]: 最大请求数
        -- ARGV[2]: 窗口大小（秒）

        -- 1. 获取当前计数
        local current = redis.call('GET', KEYS[1])

        if current == false then
            -- 首次请求
            redis.call('SETEX', KEYS[1], ARGV[2], 1)
            return {1, 1}  -- {允许, 当前次数}
        end

        -- 2. 判断是否超过限制
        if tonumber(current) < tonumber(ARGV[1]) then
            -- 未超过，计数+1
            redis.call('INCR', KEYS[1])
            return {1, tonumber(current) + 1}
        else
            -- 超过限制
            local ttl = redis.call('TTL', KEYS[1])
            return {0, tonumber(current), ttl}  -- {拒绝, 当前次数, 剩余秒数}
        end
        """;

    /**
     * 集群限流 - 滑动窗口（Lua 脚本版本）
     *
     * 适用于多实例共享限流，保证原子性
     */
    public Map<String, Object> clusterSlidingWindowLimiter(String key, int maxRequests, int windowSeconds) {
        Map<String, Object> result = new LinkedHashMap<>();
        String redisKey = RATE_LIMIT_PREFIX + "cluster:sliding:" + key;

        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000L);

        // 执行 Lua 脚本
        DefaultRedisScript<List> script = new DefaultRedisScript<>(SLIDING_WINDOW_LUA, List.class);
        List<Long> scriptResult = redisTemplate.execute(
            script,
            Collections.singletonList(redisKey),
            windowStart, now, maxRequests, windowSeconds
        );

        if (scriptResult != null && scriptResult.get(0) == 1) {
            result.put("状态", "允许");
            result.put("当前次数", scriptResult.get(1));
            result.put("限制", maxRequests + "次/" + windowSeconds + "秒");
            result.put("模式", "集群滑动窗口");
        } else {
            result.put("状态", "拒绝");
            result.put("原因", "超过限制");
            result.put("当前次数", scriptResult != null ? scriptResult.get(1) : "未知");
            result.put("限制", maxRequests + "次/" + windowSeconds + "秒");
        }

        return result;
    }

    /**
     * 集群限流 - 固定窗口（Lua 脚本版本）
     */
    public Map<String, Object> clusterFixedWindowLimiter(String key, int maxRequests, int windowSeconds) {
        Map<String, Object> result = new LinkedHashMap<>();
        String redisKey = RATE_LIMIT_PREFIX + "cluster:fixed:" + key;

        // 执行 Lua 脚本
        DefaultRedisScript<List> script = new DefaultRedisScript<>(FIXED_WINDOW_LUA, List.class);
        List<Long> scriptResult = redisTemplate.execute(
            script,
            Collections.singletonList(redisKey),
            maxRequests, windowSeconds
        );

        if (scriptResult != null && scriptResult.get(0) == 1) {
            result.put("状态", "允许");
            result.put("当前次数", scriptResult.get(1));
            result.put("限制", maxRequests + "次/" + windowSeconds + "秒");
            result.put("模式", "集群固定窗口");
        } else {
            result.put("状态", "拒绝");
            result.put("原因", "超过限制");
            result.put("当前次数", scriptResult.get(1));
            if (scriptResult.size() > 2) {
                result.put("重试时间", scriptResult.get(2) + "秒后");
            }
        }

        return result;
    }

    /**
     * 集群多层限流（Lua 脚本版本）
     *
     * 所有限流都使用 Lua 脚本保证原子性
     */
    public Map<String, Object> clusterMultiLayerRateLimiter(String ip, String userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 第一层：全局限流
        Map<String, Object> globalResult = clusterSlidingWindowLimiter("global", 10000, 1);
        if ("拒绝".equals(globalResult.get("状态"))) {
            result.put("状态", "拒绝");
            result.put("层级", "全局限流");
            result.put("原因", "系统繁忙，请稍后再试");
            return result;
        }

        // 第二层：IP 限流
        Map<String, Object> ipResult = clusterSlidingWindowLimiter("ip:" + ip, 100, 60);
        if ("拒绝".equals(ipResult.get("状态"))) {
            result.put("状态", "拒绝");
            result.put("层级", "IP限流");
            result.put("原因", "请求过于频繁");
            return result;
        }

        // 第三层：用户限流（仅对登录用户）
        if (userId != null && !userId.isEmpty()) {
            Map<String, Object> userResult = clusterFixedWindowLimiter("user:" + userId, 1000, 3600);
            if ("拒绝".equals(userResult.get("状态"))) {
                result.put("状态", "拒绝");
                result.put("层级", "用户限流");
                result.put("原因", "请求次数已达上限");
                return result;
            }
        }

        // 全部通过
        result.put("状态", "允许");
        result.put("层级", "全部通过");
        return result;
    }
}
