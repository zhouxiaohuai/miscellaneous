package com.example.transaction.redis.service;

import com.example.transaction.entity.User;
import com.example.transaction.jpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 布隆过滤器实现
 *
 * 原理：
 * - 一个 bit 数组 + 多个哈希函数
 * - 添加元素：计算多个哈希值，将对应位置设为 1
 * - 查询元素：检查所有对应位置是否都为 1
 *   - 有 0 → 一定不存在（100% 准确）
 *   - 全 1 → 可能存在（有误判率）
 */
class BloomFilter {
    protected final BitSet bitSet;        // bit 数组
    protected final int size;             // 数组大小
    protected final int hashCount;        // 哈希函数数量

    /**
     * @param size       bit 数组大小
     * @param hashCount  哈希函数数量
     */
    public BloomFilter(int size, int hashCount) {
        this.size = size;
        this.hashCount = hashCount;
        this.bitSet = new BitSet(size);
    }

    /**
     * 添加元素
     * 计算 hashCount 个哈希值，将对应位置设为 1
     */
    public void add(String element) {
        for (int i = 0; i < hashCount; i++) {
            int hash = hash(element, i);
            bitSet.set(hash);
        }
    }

    /**
     * 查询元素
     * 检查所有对应位置是否都为 1
     *
     * @return true=可能存在, false=一定不存在
     */
    public boolean mightContain(String element) {
        for (int i = 0; i < hashCount; i++) {
            int hash = hash(element, i);
            if (!bitSet.get(hash)) {
                return false;  // 有一个位置是 0，一定不存在
            }
        }
        return true;  // 全是 1，可能存在
    }

    /**
     * 哈希函数
     * 使用双重哈希：hash(element, i) = h1(element) + i * h2(element)
     *
     * 这种方式可以用两个基础哈希函数模拟多个哈希函数
     */
    protected int hash(String element, int i) {
        int hash1 = element.hashCode();
        int hash2 = hash1 >>> 16;  // 右移 16 位作为第二个哈希

        // 组合哈希，取绝对值后对 size 取模
        int combinedHash = hash1 + (i * hash2);
        return Math.abs(combinedHash) % size;
    }

    /**
     * 获取当前 bit 数组中 1 的数量
     */
    public int cardinality() {
        return bitSet.cardinality();
    }
}

/**
 * 计数布隆过滤器（支持删除）
 *
 * 原理：用计数器代替 bit
 * - 标准布隆过滤器：每个位置存 0 或 1
 * - 计数布隆过滤器：每个位置存一个计数器
 *
 * 添加：计数器 +1
 * 删除：计数器 -1
 * 查询：检查计数器是否 > 0
 *
 * 缺点：内存占用增加（每个位置从 1 bit 变成 4 字节）
 */
class CountingBloomFilter {
    private final int[] counters;         // 计数器数组
    private final int size;
    private final int hashCount;

    public CountingBloomFilter(int size, int hashCount) {
        this.size = size;
        this.hashCount = hashCount;
        this.counters = new int[size];
    }

    /**
     * 添加元素：计数器 +1
     */
    public void add(String element) {
        for (int i = 0; i < hashCount; i++) {
            int hash = hash(element, i);
            counters[hash]++;
        }
    }

    /**
     * 删除元素：计数器 -1
     *
     * 注意：只有确定元素存在时才能删除
     * 否则可能导致其他元素的计数器被误减
     */
    public boolean remove(String element) {
        // 先检查是否存在
        if (!mightContain(element)) {
            return false;
        }

        // 存在则计数器 -1
        for (int i = 0; i < hashCount; i++) {
            int hash = hash(element, i);
            counters[hash]--;
        }
        return true;
    }

    /**
     * 查询元素：检查所有计数器是否 > 0
     */
    public boolean mightContain(String element) {
        for (int i = 0; i < hashCount; i++) {
            int hash = hash(element, i);
            if (counters[hash] == 0) {
                return false;
            }
        }
        return true;
    }

    private int hash(String element, int i) {
        int hash1 = element.hashCode();
        int hash2 = hash1 >>> 16;
        int combinedHash = hash1 + (i * hash2);
        return Math.abs(combinedHash) % size;
    }
}

/**
 * 可扩展布隆过滤器（动态增长）
 *
 * 原理：多个子过滤器组成链表
 * - 当前过滤器满了 → 创建新的子过滤器
 * - 新元素添加到最新的子过滤器
 * - 查询时检查所有子过滤器
 *
 * 优点：不需要预估数据量
 * 缺点：查询时需要检查多个过滤器
 */
class ScalableBloomFilter {
    private final List<BloomFilter> filters = new ArrayList<>();
    private final int initialSize;
    private final int hashCount;
    private final double growthFactor;  // 增长因子
    private final double errorRate;     // 目标误判率

    /**
     * @param initialSize   初始大小
     * @param hashCount     哈希函数数量
     * @param growthFactor  增长因子（如 2.0 表示每次翻倍）
     * @param errorRate     目标误判率
     */
    public ScalableBloomFilter(int initialSize, int hashCount, double growthFactor, double errorRate) {
        this.initialSize = initialSize;
        this.hashCount = hashCount;
        this.growthFactor = growthFactor;
        this.errorRate = errorRate;

        // 创建第一个子过滤器
        filters.add(new BloomFilter(initialSize, hashCount));
    }

    /**
     * 添加元素
     *
     * 如果当前过滤器接近满（> 50%），创建新的子过滤器
     */
    public void add(String element) {
        BloomFilter current = filters.get(filters.size() - 1);

        // 检查是否需要扩展（当填充率 > 50% 时）
        if ((double) current.cardinality() / current.size > 0.5) {
            // 创建新的子过滤器，大小按增长因子扩大
            int newSize = (int) (current.size * growthFactor);
            current = new BloomFilter(newSize, hashCount);
            filters.add(current);
        }

        current.add(element);
    }

    /**
     * 查询元素：检查所有子过滤器
     *
     * 只要有一个子过滤器说「可能存在」，就返回 true
     */
    public boolean mightContain(String element) {
        for (BloomFilter filter : filters) {
            if (filter.mightContain(element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取子过滤器数量
     */
    public int getFilterCount() {
        return filters.size();
    }

    /**
     * 获取总元素数量（近似）
     */
    public int getTotalElements() {
        return filters.stream().mapToInt(BloomFilter::cardinality).sum();
    }
}

/**
 * Redis 缓存策略演示
 *
 * 核心策略：
 * 1. Cache Aside（旁路缓存）- 最常用
 * 2. Read Through（读穿透）
 * 3. Write Through（写穿透）
 * 4. Write Behind（异步写入）
 *
 * 常见问题：
 * 1. 缓存穿透 - 查询不存在的数据
 * 2. 缓存击穿 - 热点 key 过期
 * 3. 缓存雪崩 - 大量 key 同时过期
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;

    private static final String CACHE_PREFIX = "cache:user:";
    private static final long CACHE_TTL = 300; // 5分钟

    // ========================================
    // 1. Cache Aside（旁路缓存）
    // ========================================

    /**
     * 旁路缓存 - 读操作
     *
     * 流程：
     * 1. 先查缓存
     * 2. 缓存命中 → 直接返回
     * 3. 缓存未命中 → 查数据库 → 写入缓存 → 返回
     */
    public Map<String, Object> cacheAsideRead(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = CACHE_PREFIX + userId;

        long start = System.currentTimeMillis();
        // "sys:user"+key

        // 1. 先查缓存
        Object cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            // 缓存命中
            long cost = System.currentTimeMillis() - start;
            result.put("来源", "缓存");
            result.put("数据", cached);
            result.put("耗时", cost + "ms");
            log.info("[旁路缓存] 缓存命中: userId={}", userId);
            return result;
        }

        // 2. 缓存未命中，查数据库
        log.info("[旁路缓存] 缓存未命中，查询数据库: userId={}", userId);
        User user = userRepository.findById(userId).orElse(null);

        if (user != null) {
            // 3. 写入缓存
            redisTemplate.opsForValue().set(key, user, CACHE_TTL, TimeUnit.SECONDS);
            result.put("来源", "数据库");
            result.put("数据", user);
            result.put("操作", "已写入缓存，TTL=" + CACHE_TTL + "s");
        } else {
            result.put("来源", "数据库");
            result.put("数据", "用户不存在");
        }

        long cost = System.currentTimeMillis() - start;
        result.put("耗时", cost + "ms");

        return result;
    }

    /**
     * 旁路缓存 - 写操作
     *
     * 流程：
     * 1. 更新数据库
     * 2. 删除缓存（而不是更新缓存）
     *
     * 为什么删除而不是更新？
     * - 避免并发写导致缓存与数据库不一致
     * - 删除后下次读会自动从数据库加载最新数据
     */
    public Map<String, Object> cacheAsideWrite(Long userId, String newEmail) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = CACHE_PREFIX + userId;

        // 1. 更新数据库
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            result.put("状态", "用户不存在");
            return result;
        }

        user.setEmail(newEmail);
        userRepository.save(user);

        // 2. 删除缓存（而不是更新）
        redisTemplate.delete(key);

        result.put("状态", "更新成功");
        result.put("操作", "已更新数据库 + 删除缓存");
        result.put("说明", "下次读取时会从数据库加载最新数据并写入缓存");

        return result;
    }

    // ========================================
    // 2. 缓存穿透防护
    // ========================================

    // 布隆过滤器实例（实际项目中应该用 Redis 的 BF.ADD/BF.EXISTS 命令）
    // 这里用 Java 代码演示原理
    private static final BloomFilter userBloomFilter = new BloomFilter(1000000, 3);
    private static boolean bloomFilterInitialized = false;

    /**
     * 初始化布隆过滤器（把所有存在的用户 ID 加入）
     *
     * 实际项目中：
     * 1. 可以在应用启动时加载
     * 2. 或者用 Redis 的 RedisBloom 模块：BF.ADD / BF.EXISTS
     */
    private void initBloomFilter() {
        if (!bloomFilterInitialized) {
            // 从数据库加载所有用户 ID 到布隆过滤器
            List<User> allUsers = userRepository.findAll();
            for (User user : allUsers) {
                userBloomFilter.add(String.valueOf(user.getId()));
            }
            bloomFilterInitialized = true;
            log.info("[布隆过滤器] 初始化完成，加载 {} 个用户", allUsers.size());
        }
    }

    // ========================================
    // 动态更新布隆过滤器方案
    // ========================================

    /**
     * 方案一：定期重建布隆过滤器
     *
     * 优点：简单可靠，支持新增和删除
     * 缺点：重建期间有短暂的不一致
     *
     * 适合：数据变化不频繁（如每天新增几百条）
     */
    private volatile BloomFilter dynamicBloomFilter = new BloomFilter(1000000, 7);
    private volatile long lastRebuildTime = 0;

    /**
     * 重建布隆过滤器（可定时调用）
     *
     * 使用 volatile + 引用替换保证线程安全
     */
    public Map<String, Object> rebuildBloomFilter() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 创建新的布隆过滤器
        BloomFilter newFilter = new BloomFilter(1000000, 7);

        // 2. 从数据库加载最新数据
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            newFilter.add(String.valueOf(user.getId()));
        }

        // 3. 原子替换（volatile 保证可见性）
        this.dynamicBloomFilter = newFilter;
        this.lastRebuildTime = System.currentTimeMillis();

        result.put("状态", "重建完成");
        result.put("元素数量", allUsers.size());
        result.put("重建时间", new java.util.Date());

        log.info("[布隆过滤器] 重建完成，加载 {} 个用户", allUsers.size());
        return result;
    }

    /**
     * 使用动态布隆过滤器查询
     */
    public Map<String, Object> queryWithDynamicBloomFilter(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 布隆过滤器判断
        boolean mightExist = dynamicBloomFilter.mightContain(String.valueOf(userId));

        if (!mightExist) {
            result.put("来源", "布隆过滤器");
            result.put("数据", "用户不存在");
            result.put("过滤器最后重建时间", new java.util.Date(lastRebuildTime));
            return result;
        }

        // 2. 正常查询流程...
        String key = CACHE_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            result.put("来源", "缓存");
            result.put("数据", cached);
            return result;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            redisTemplate.opsForValue().set(key, user, CACHE_TTL, TimeUnit.SECONDS);
            result.put("来源", "数据库");
            result.put("数据", user);
        } else {
            redisTemplate.opsForValue().set(key, "NULL", 60, TimeUnit.SECONDS);
            result.put("来源", "数据库");
            result.put("数据", "用户不存在");
        }

        return result;
    }

    /**
     * 方案二：计数布隆过滤器（支持删除）
     *
     * 适合：需要频繁删除元素的场景
     */
    private final CountingBloomFilter countingBloomFilter = new CountingBloomFilter(1000000, 7);

    /**
     * 向计数布隆过滤器添加元素
     */
    public Map<String, Object> addToCountingBloomFilter(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        countingBloomFilter.add(String.valueOf(userId));

        result.put("状态", "已添加");
        result.put("userId", userId);
        result.put("说明", "计数布隆过滤器支持删除");
        return result;
    }

    /**
     * 从计数布隆过滤器删除元素
     */
    public Map<String, Object> removeFromCountingBloomFilter(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        boolean removed = countingBloomFilter.remove(String.valueOf(userId));

        if (removed) {
            result.put("状态", "已删除");
            result.put("userId", userId);
        } else {
            result.put("状态", "删除失败");
            result.put("原因", "元素不存在");
        }
        return result;
    }

    /**
     * 方案三：可扩展布隆过滤器（动态增长）
     *
     * 适合：数据量持续增长，无法预估总量的场景
     */
    private final ScalableBloomFilter scalableBloomFilter =
        new ScalableBloomFilter(10000, 7, 2.0, 0.01);

    /**
     * 向可扩展布隆过滤器添加元素
     */
    public Map<String, Object> addToScalableBloomFilter(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        scalableBloomFilter.add(String.valueOf(userId));

        result.put("状态", "已添加");
        result.put("userId", userId);
        result.put("子过滤器数量", scalableBloomFilter.getFilterCount());
        result.put("总元素数量", scalableBloomFilter.getTotalElements());
        return result;
    }

    /**
     * 使用布隆过滤器防止缓存穿透
     *
     * 流程：
     * 1. 先用布隆过滤器判断 key 是否存在
     * 2. 不存在 → 直接返回（不查数据库）
     * 3. 可能存在 → 正常查询流程
     */
    public Map<String, Object> cachePenetrationWithBloomFilter(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 确保布隆过滤器已初始化
        initBloomFilter();

        // 1. 布隆过滤器判断
        boolean mightExist = userBloomFilter.mightContain(String.valueOf(userId));

        if (!mightExist) {
            // 布隆过滤器说「不存在」→ 100% 不存在，直接返回
            result.put("来源", "布隆过滤器");
            result.put("数据", "用户不存在");
            result.put("说明", "布隆过滤器拦截，未查询数据库");
            result.put("userId", userId);
            return result;
        }

        // 2. 布隆过滤器说「可能存在」→ 正常查询流程
        String key = CACHE_PREFIX + userId;

        // 先查缓存
        Object cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            if ("NULL".equals(cached.toString())) {
                result.put("来源", "缓存（空值）");
                result.put("数据", "用户不存在");
            } else {
                result.put("来源", "缓存");
                result.put("数据", cached);
            }
            return result;
        }

        // 查数据库
        User user = userRepository.findById(userId).orElse(null);

        if (user != null) {
            redisTemplate.opsForValue().set(key, user, CACHE_TTL, TimeUnit.SECONDS);
            result.put("来源", "数据库");
            result.put("数据", user);
        } else {
            // 缓存空值（较短 TTL）
            redisTemplate.opsForValue().set(key, "NULL", 60, TimeUnit.SECONDS);
            result.put("来源", "数据库");
            result.put("数据", "用户不存在");
            result.put("说明", "布隆过滤器有误判，实际不存在，已缓存空值");
        }

        return result;
    }

    /**
     * 缓存穿透 - 查询不存在的数据
     *
     * 问题：查询不存在的数据，缓存永远未命中，每次都查数据库
     *
     * 解决方案：
     * 1. 缓存空值（设置较短TTL）
     * 2. 布隆过滤器（预判断key是否存在）
     */
    public Map<String, Object> cachePenetrationProtection(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = CACHE_PREFIX + userId;

        // 1. 先查缓存
        Object cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            // 检查是否是空值标记
            if ("NULL".equals(cached.toString())) {
                result.put("来源", "缓存（空值标记）");
                result.put("数据", "用户不存在");
                result.put("说明", "缓存了空值，防止穿透");
                return result;
            }

            result.put("来源", "缓存");
            result.put("数据", cached);
            return result;
        }

        // 2. 查数据库
        User user = userRepository.findById(userId).orElse(null);

        if (user != null) {
            // 3a. 用户存在，写入缓存
            redisTemplate.opsForValue().set(key, user, CACHE_TTL, TimeUnit.SECONDS);
            result.put("来源", "数据库");
            result.put("数据", user);
        } else {
            // 3b. 用户不存在，缓存空值（较短TTL）
            redisTemplate.opsForValue().set(key, "NULL", 60, TimeUnit.SECONDS);
            result.put("来源", "数据库");
            result.put("数据", "用户不存在");
            result.put("操作", "缓存空值，TTL=60s，防止穿透");
        }

        return result;
    }

    // ========================================
    // 3. 缓存击穿防护
    // ========================================

    /**
     * 缓存击穿 - 热点 key 过期
     *
     * 问题：热点 key 过期瞬间，大量请求直接打到数据库
     *
     * 解决方案：
     * 1. 互斥锁（只允许一个请求重建缓存）
     * 2. 逻辑过期（不设置TTL，由业务判断是否过期）
     */
    public Map<String, Object> cacheBreakdownProtection(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = CACHE_PREFIX + userId;
        String lockKey = "lock:" + key;

        // 1. 先查缓存
        Object cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            result.put("来源", "缓存");
            result.put("数据", cached);
            return result;
        }

        // 2. 缓存未命中，尝试获取锁
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(locked)) {
            // 3a. 获取到锁，重建缓存
            log.info("[缓存击穿] 获取到锁，重建缓存: userId={}", userId);
            User user = userRepository.findById(userId).orElse(null);

            if (user != null) {
                redisTemplate.opsForValue().set(key, user, CACHE_TTL, TimeUnit.SECONDS);
                result.put("来源", "数据库（重建缓存）");
                result.put("数据", user);
            }

            // 释放锁
            redisTemplate.delete(lockKey);
        } else {
            // 3b. 未获取到锁，等待后重试
            log.info("[缓存击穿] 未获取到锁，等待重试: userId={}", userId);
            try {
                Thread.sleep(100);
                // 重试读取缓存
                cached = redisTemplate.opsForValue().get(key);
                result.put("来源", "缓存（重试获取）");
                result.put("数据", cached);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return result;
    }

    // ========================================
    // 4. 缓存雪崩防护
    // ========================================

    /**
     * 缓存雪崩 - 大量 key 同时过期
     *
     * 问题：大量 key 在同一时间过期，请求全部打到数据库
     *
     * 解决方案：
     * 1. 随机过期时间（避免同时过期）
     * 2. 多级缓存（本地缓存 + Redis）
     * 3. 限流降级
     */
    public Map<String, Object> cacheAvalancheProtection() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 演示：随机过期时间
        long baseTTL = 300; // 基础TTL 5分钟
        long randomRange = 60; // 随机范围 1分钟

        for (int i = 1; i <= 5; i++) {
            String key = CACHE_PREFIX + "avalanche:" + i;
            // 随机过期时间：300-360秒
            long ttl = baseTTL + (long) (Math.random() * randomRange);
            redisTemplate.opsForValue().set(key, "value" + i, ttl, TimeUnit.SECONDS);

            Long actualTTL = redisTemplate.getExpire(key);
            result.put("Key " + i, "TTL = " + actualTTL + "s");
        }

        result.put("说明", "通过随机过期时间，避免大量key同时过期");
        result.put("策略", "基础TTL(" + baseTTL + "s) + 随机(0-" + randomRange + "s)");

        // 清理
        for (int i = 1; i <= 5; i++) {
            redisTemplate.delete(CACHE_PREFIX + "avalanche:" + i);
        }

        return result;
    }

    // ========================================
    // 5. 缓存清理
    // ========================================

    /**
     * 清理指定用户的缓存
     */
    public void evictCache(Long userId) {
        String key = CACHE_PREFIX + userId;
        redisTemplate.delete(key);
        log.info("[缓存] 清理缓存: {}", key);
    }

    /**
     * 清理所有用户缓存
     */
    public void evictAllCache() {
        Set<String> keys = redisTemplate.keys(CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("[缓存] 清理所有用户缓存，共 {} 个", keys.size());
        }
    }
}
