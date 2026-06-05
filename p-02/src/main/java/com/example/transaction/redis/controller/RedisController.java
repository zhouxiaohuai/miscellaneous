package com.example.transaction.redis.controller;

import com.example.transaction.redis.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Redis 演示控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/redis")
@RequiredArgsConstructor
public class RedisController {

    private final RedisBasicService basicService;
    private final RedisCacheService cacheService;
    private final RedisLockService lockService;
    private final RedisRateLimitService rateLimitService;

    // ========================================
    // 1. 基础操作
    // ========================================

    /**
     * String 操作演示
     * GET /api/redis/basic/string
     */
    @GetMapping("/basic/string")
    public Map<String, Object> stringDemo() {
        return basicService.stringDemo();
    }

    /**
     * List 操作演示
     * GET /api/redis/basic/list
     */
    @GetMapping("/basic/list")
    public Map<String, Object> listDemo() {
        return basicService.listDemo();
    }

    /**
     * Hash 操作演示
     * GET /api/redis/basic/hash
     */
    @GetMapping("/basic/hash")
    public Map<String, Object> hashDemo() {
        return basicService.hashDemo();
    }

    /**
     * Set 操作演示
     * GET /api/redis/basic/set
     */
    @GetMapping("/basic/set")
    public Map<String, Object> setDemo() {
        return basicService.setDemo();
    }

    /**
     * ZSet 操作演示
     * GET /api/redis/basic/zset
     */
    @GetMapping("/basic/zset")
    public Map<String, Object> zsetDemo() {
        return basicService.zsetDemo();
    }

    /**
     * 通用操作演示
     * GET /api/redis/basic/common
     */
    @GetMapping("/basic/common")
    public Map<String, Object> commonDemo() {
        return basicService.commonDemo();
    }

    // ========================================
    // 2. 缓存策略
    // ========================================

    /**
     * 旁路缓存 - 读操作
     * GET /api/redis/cache/read?userId=1
     */
    @GetMapping("/cache/read")
    public Map<String, Object> cacheRead(@RequestParam Long userId) {
        return cacheService.cacheAsideRead(userId);
    }

    /**
     * 旁路缓存 - 写操作
     * GET /api/redis/cache/write?userId=1&email=new@test.com
     */
    @GetMapping("/cache/write")
    public Map<String, Object> cacheWrite(
            @RequestParam Long userId,
            @RequestParam String email) {
        return cacheService.cacheAsideWrite(userId, email);
    }

    /**
     * 缓存穿透防护（缓存空值方案）
     * GET /api/redis/cache/penetration?userId=99999
     */
    @GetMapping("/cache/penetration")
    public Map<String, Object> cachePenetration(@RequestParam Long userId) {
        return cacheService.cachePenetrationProtection(userId);
    }

    /**
     * 缓存穿透防护（布隆过滤器方案）
     * GET /api/redis/cache/bloom?userId=99999
     */
    @GetMapping("/cache/bloom")
    public Map<String, Object> cacheBloomFilter(@RequestParam Long userId) {
        return cacheService.cachePenetrationWithBloomFilter(userId);
    }

    /**
     * 重建布隆过滤器（定期任务调用）
     * POST /api/redis/cache/bloom/rebuild
     */
    @PostMapping("/cache/bloom/rebuild")
    public Map<String, Object> rebuildBloomFilter() {
        return cacheService.rebuildBloomFilter();
    }

    /**
     * 计数布隆过滤器 - 添加元素
     * POST /api/redis/cache/bloom/counting/add?userId=1001
     */
    @PostMapping("/cache/bloom/counting/add")
    public Map<String, Object> addToCountingBloomFilter(@RequestParam Long userId) {
        return cacheService.addToCountingBloomFilter(userId);
    }

    /**
     * 计数布隆过滤器 - 删除元素
     * DELETE /api/redis/cache/bloom/counting/remove?userId=1001
     */
    @DeleteMapping("/cache/bloom/counting/remove")
    public Map<String, Object> removeFromCountingBloomFilter(@RequestParam Long userId) {
        return cacheService.removeFromCountingBloomFilter(userId);
    }

    /**
     * 可扩展布隆过滤器 - 添加元素
     * POST /api/redis/cache/bloom/scalable/add?userId=1001
     */
    @PostMapping("/cache/bloom/scalable/add")
    public Map<String, Object> addToScalableBloomFilter(@RequestParam Long userId) {
        return cacheService.addToScalableBloomFilter(userId);
    }

    /**
     * 缓存击穿防护
     * GET /api/redis/cache/breakdown?userId=1
     */
    @GetMapping("/cache/breakdown")
    public Map<String, Object> cacheBreakdown(@RequestParam Long userId) {
        return cacheService.cacheBreakdownProtection(userId);
    }

    /**
     * 缓存雪崩防护
     * GET /api/redis/cache/avalanche
     */
    @GetMapping("/cache/avalanche")
    public Map<String, Object> cacheAvalanche() {
        return cacheService.cacheAvalancheProtection();
    }

    // ========================================
    // 3. 分布式锁
    // ========================================

    /**
     * 简单锁演示
     * GET /api/redis/lock/simple?key=test1
     */
    @GetMapping("/lock/simple")
    public Map<String, Object> lockSimple(@RequestParam(defaultValue = "test1") String key) {
        return lockService.simpleLockDemo(key);
    }

    /**
     * 带过期时间的锁
     * GET /api/redis/lock/ttl?key=test2
     */
    @GetMapping("/lock/ttl")
    public Map<String, Object> lockTTL(@RequestParam(defaultValue = "test2") String key) {
        return lockService.lockWithTTLDemo(key);
    }

    /**
     * 带唯一标识的锁
     * GET /api/redis/lock/uuid?key=test3
     */
    @GetMapping("/lock/uuid")
    public Map<String, Object> lockUUID(@RequestParam(defaultValue = "test3") String key) {
        return lockService.lockWithUniqueIDDemo(key);
    }

    /**
     * 防止重复下单
     * GET /api/redis/lock/order?userId=1
     */
    @GetMapping("/lock/order")
    public Map<String, Object> lockOrder(@RequestParam String userId) {
        return lockService.preventDuplicateOrder(userId);
    }

    /**
     * 秒杀库存扣减（原版 - 存在问题，仅供对比）
     * GET /api/redis/lock/seckill?productId=1001
     *
     * 问题：不记录用户、锁释放非原子、无防重复
     */
    @GetMapping("/lock/seckill")
    public Map<String, Object> lockSeckill(@RequestParam String productId) {
        return lockService.seckillStockDeduction(productId);
    }

    /**
     * 秒杀库存扣减（改进版 - Lua 原子操作 + 用户记录）
     * GET /api/redis/lock/seckill/v2?productId=1001&userId=user001
     *
     * 改进点：
     * 1. Lua 脚本原子操作，无需分布式锁
     * 2. Set 记录已秒杀用户，防重复下单
     * 3. 扣库存 + 记人一步完成
     */
    @GetMapping("/lock/seckill/v2")
    public Map<String, Object> lockSeckillV2(@RequestParam String productId,
                                              @RequestParam String userId) {
        return lockService.seckillWithUserRecord(productId, userId);
    }

    // ========================================
    // 4. 限流策略
    // ========================================

    /**
     * 固定窗口限流
     * GET /api/redis/ratelimit/fixed?userId=1&maxRequests=5&windowSeconds=60
     */
    @GetMapping("/ratelimit/fixed")
    public Map<String, Object> rateLimitFixed(
            @RequestParam(defaultValue = "user1") String userId,
            @RequestParam(defaultValue = "5") int maxRequests,
            @RequestParam(defaultValue = "60") int windowSeconds) {
        return rateLimitService.fixedWindowLimiter(userId, maxRequests, windowSeconds);
    }

    /**
     * 滑动窗口限流
     * GET /api/redis/ratelimit/sliding?userId=1&maxRequests=10&windowSeconds=60
     */
    @GetMapping("/ratelimit/sliding")
    public Map<String, Object> rateLimitSliding(
            @RequestParam(defaultValue = "user1") String userId,
            @RequestParam(defaultValue = "10") int maxRequests,
            @RequestParam(defaultValue = "60") int windowSeconds) {
        return rateLimitService.slidingWindowLimiter(userId, maxRequests, windowSeconds);
    }

    /**
     * 令牌桶限流
     * GET /api/redis/ratelimit/token?userId=1&bucketCapacity=10&refillRate=2
     */
    @GetMapping("/ratelimit/token")
    public Map<String, Object> rateLimitToken(
            @RequestParam(defaultValue = "user1") String userId,
            @RequestParam(defaultValue = "10") int bucketCapacity,
            @RequestParam(defaultValue = "2") int refillRate) {
        return rateLimitService.tokenBucketLimiter(userId, bucketCapacity, refillRate);
    }

    /**
     * IP 限流
     * GET /api/redis/ratelimit/ip?ip=192.168.1.1&maxRequests=100&windowSeconds=60
     */
    @GetMapping("/ratelimit/ip")
    public Map<String, Object> rateLimitIP(
            @RequestParam(defaultValue = "192.168.1.1") String ip,
            @RequestParam(defaultValue = "100") int maxRequests,
            @RequestParam(defaultValue = "60") int windowSeconds) {
        return rateLimitService.ipRateLimiter(ip, maxRequests, windowSeconds);
    }

    /**
     * 全局限流
     * GET /api/redis/ratelimit/global?maxRequests=1000&windowSeconds=60
     */
    @GetMapping("/ratelimit/global")
    public Map<String, Object> rateLimitGlobal(
            @RequestParam(defaultValue = "1000") int maxRequests,
            @RequestParam(defaultValue = "60") int windowSeconds) {
        return rateLimitService.globalRateLimiter(maxRequests, windowSeconds);
    }

    /**
     * 多层限流（推荐生产使用）
     * GET /api/redis/ratelimit/multi?ip=192.168.1.1&userId=user1
     */
    @GetMapping("/ratelimit/multi")
    public Map<String, Object> rateLimitMulti(
            @RequestParam(defaultValue = "192.168.1.1") String ip,
            @RequestParam(required = false) String userId) {
        return rateLimitService.multiLayerRateLimiter(ip, userId);
    }

    // ========================================
    // 6. 限流配置管理（动态调整）
    // ========================================

    /**
     * 获取所有限流配置
     * GET /api/redis/ratelimit/config
     */
    @GetMapping("/ratelimit/config")
    public Map<String, Object> getRateLimitConfigs() {
        return rateLimitService.getAllConfigs();
    }

    /**
     * 更新限流配置
     * POST /api/redis/ratelimit/config/global?maxRequests=20000&windowSeconds=1&description=高峰期调大
     */
    @PostMapping("/ratelimit/config/{type}")
    public Map<String, Object> updateRateLimitConfig(
            @PathVariable String type,
            @RequestParam int maxRequests,
            @RequestParam int windowSeconds,
            @RequestParam(defaultValue = "") String description) {
        return rateLimitService.updateConfig(type, maxRequests, windowSeconds, description);
    }

    /**
     * 重置限流配置（恢复默认）
     * DELETE /api/redis/ratelimit/config/global
     */
    @DeleteMapping("/ratelimit/config/{type}")
    public Map<String, Object> resetRateLimitConfig(@PathVariable String type) {
        return rateLimitService.resetConfig(type);
    }

    // ========================================
    // 7. 集群限流（Lua 脚本原子操作）
    // ========================================

    /**
     * 集群滑动窗口限流（Lua 脚本版本）
     * GET /api/redis/ratelimit/cluster/sliding?key=global&maxRequests=10000&windowSeconds=1
     */
    @GetMapping("/ratelimit/cluster/sliding")
    public Map<String, Object> clusterSlidingWindow(
            @RequestParam(defaultValue = "global") String key,
            @RequestParam(defaultValue = "10000") int maxRequests,
            @RequestParam(defaultValue = "1") int windowSeconds) {
        return rateLimitService.clusterSlidingWindowLimiter(key, maxRequests, windowSeconds);
    }

    /**
     * 集群固定窗口限流（Lua 脚本版本）
     * GET /api/redis/ratelimit/cluster/fixed?key=user1&maxRequests=1000&windowSeconds=3600
     */
    @GetMapping("/ratelimit/cluster/fixed")
    public Map<String, Object> clusterFixedWindow(
            @RequestParam String key,
            @RequestParam(defaultValue = "1000") int maxRequests,
            @RequestParam(defaultValue = "3600") int windowSeconds) {
        return rateLimitService.clusterFixedWindowLimiter(key, maxRequests, windowSeconds);
    }

    /**
     * 集群多层限流（推荐生产环境使用）
     * GET /api/redis/ratelimit/cluster/multi?ip=192.168.1.1&userId=user1
     */
    @GetMapping("/ratelimit/cluster/multi")
    public Map<String, Object> clusterMultiLayer(
            @RequestParam(defaultValue = "192.168.1.1") String ip,
            @RequestParam(required = false) String userId) {
        return rateLimitService.clusterMultiLayerRateLimiter(ip, userId);
    }

    // ========================================
    // 5. 总览
    // ========================================

    /**
     * Redis 知识总览
     * GET /api/redis/overview
     */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return Map.of(
                "说明", "Redis 学习演示",
                "基础操作", Map.of(
                        "String", "GET /api/redis/basic/string",
                        "List", "GET /api/redis/basic/list",
                        "Hash", "GET /api/redis/basic/hash",
                        "Set", "GET /api/redis/basic/set",
                        "ZSet", "GET /api/redis/basic/zset",
                        "通用操作", "GET /api/redis/basic/common"
                ),
                "缓存策略", Map.of(
                        "旁路缓存读", "GET /api/redis/cache/read?userId=1",
                        "旁路缓存写", "GET /api/redis/cache/write?userId=1&email=new@test.com",
                        "缓存穿透", "GET /api/redis/cache/penetration?userId=99999",
                        "缓存击穿", "GET /api/redis/cache/breakdown?userId=1",
                        "缓存雪崩", "GET /api/redis/cache/avalanche"
                ),
                "分布式锁", Map.of(
                        "简单锁", "GET /api/redis/lock/simple?key=test1",
                        "带过期锁", "GET /api/redis/lock/ttl?key=test2",
                        "唯一标识锁", "GET /api/redis/lock/uuid?key=test3",
                        "防重复下单", "GET /api/redis/lock/order?userId=1",
                        "秒杀扣减(原版)", "GET /api/redis/lock/seckill?productId=1001",
                        "秒杀扣减(改进版)", "GET /api/redis/lock/seckill/v2?productId=1001&userId=user001"
                ),
                "限流策略", Map.of(
                        "固定窗口", "GET /api/redis/ratelimit/fixed?userId=1",
                        "滑动窗口", "GET /api/redis/ratelimit/sliding?userId=1",
                        "令牌桶", "GET /api/redis/ratelimit/token?userId=1",
                        "IP限流", "GET /api/redis/ratelimit/ip?ip=192.168.1.1",
                        "全局限流", "GET /api/redis/ratelimit/global?maxRequests=1000",
                        "多层限流", "GET /api/redis/ratelimit/multi?ip=192.168.1.1&userId=user1"
                ),
                "限流配置管理", Map.of(
                        "查看配置", "GET /api/redis/ratelimit/config",
                        "更新配置", "POST /api/redis/ratelimit/config/{type}?maxRequests=xxx&windowSeconds=xxx",
                        "重置配置", "DELETE /api/redis/ratelimit/config/{type}"
                ),
                "集群限流（Lua脚本）", Map.of(
                        "集群滑动窗口", "GET /api/redis/ratelimit/cluster/sliding?key=global&maxRequests=10000&windowSeconds=1",
                        "集群固定窗口", "GET /api/redis/ratelimit/cluster/fixed?key=user1&maxRequests=1000&windowSeconds=3600",
                        "集群多层限流", "GET /api/redis/ratelimit/cluster/multi?ip=192.168.1.1&userId=user1"
                )
        );
    }
}
