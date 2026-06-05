package com.example.transaction.redis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 基础操作演示
 *
 * 演示 Redis 5 种数据结构的操作：
 * 1. String - 字符串
 * 2. List - 列表
 * 3. Hash - 哈希
 * 4. Set - 集合
 * 5. ZSet - 有序集合
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisBasicService {

    private final RedisTemplate<String, Object> redisTemplate;

    // ========================================
    // 1. String 字符串操作
    // ========================================

    /**
     * String 基础操作
     *
     * SET key value           - 设置值
     * GET key                 - 获取值
     * SETEX key seconds value - 设置值并指定过期时间
     * SETNX key value         - 不存在时才设置（分布式锁基础）
     * INCR key                - 原子递增
     * DECR key                - 原子递减
     */
    public Map<String, Object> stringDemo() {
        Map<String, Object> result = new LinkedHashMap<>();

        String prefix = "demo:string:";

        // 1. SET / GET
        redisTemplate.opsForValue().set(prefix + "name", "张三");
        String name = (String) redisTemplate.opsForValue().get(prefix + "name");
        result.put("SET/GET", "name = " + name);

        // 2. SETEX（带过期时间）
        redisTemplate.opsForValue().set(prefix + "token", "abc123", 60, TimeUnit.SECONDS);
        Long ttl = redisTemplate.getExpire(prefix + "token");
        result.put("SETEX", "token = abc123, TTL = " + ttl + "s");

        // 3. SETNX（不存在才设置）
        Boolean setnx1 = redisTemplate.opsForValue().setIfAbsent(prefix + "lock", "locked");
        Boolean setnx2 = redisTemplate.opsForValue().setIfAbsent(prefix + "lock", "locked");
        result.put("SETNX", "第1次: " + setnx1 + ", 第2次: " + setnx2 + " (第2次失败，因为key已存在)");

        // 4. INCR / DECR（原子操作）
        redisTemplate.opsForValue().set(prefix + "counter", 100);
        Long afterIncr = redisTemplate.opsForValue().increment(prefix + "counter");
        Long afterDecr = redisTemplate.opsForValue().decrement(prefix + "counter");
        Long afterIncrBy = redisTemplate.opsForValue().increment(prefix + "counter", 10);
        result.put("INCR/DECR", "初始100, INCR→" + afterIncr + ", DECR→" + afterDecr + ", INCRBY 10→" + afterIncrBy);

        // 5. 批量操作
        Map<String, Object> batchData = new HashMap<>();
        batchData.put(prefix + "batch1", "值1");
        batchData.put(prefix + "batch2", "值2");
        batchData.put(prefix + "batch3", "值3");
        redisTemplate.opsForValue().multiSet(batchData);

        // 批量获取
        List<String> keys = List.of(prefix + "batch1", prefix + "batch2", prefix + "batch3");
        List<Object> batchResult = redisTemplate.opsForValue().multiGet(keys);
        result.put("MSET/MGET", "批量设置成功，获取结果: " + batchResult);

        // 清理测试数据
        redisTemplate.delete(Set.of(
                prefix + "name", prefix + "token", prefix + "lock",
                prefix + "counter", prefix + "batch1", prefix + "batch2", prefix + "batch3"
        ));

        return result;
    }

    // ========================================
    // 2. List 列表操作
    // ========================================

    /**
     * List 基础操作
     *
     * LPUSH key value    - 左边插入
     * RPUSH key value    - 右边插入
     * LPOP key           - 左边弹出
     * RPOP key           - 右边弹出
     * LRANGE key start stop - 范围查询
     * LLEN key           - 列表长度
     */
    public Map<String, Object> listDemo() {
        Map<String, Object> result = new LinkedHashMap<>();

        String key = "demo:list:queue";

        // 清理旧数据
        redisTemplate.delete(key);

        // 1. RPUSH（右边插入，模拟队列）
        redisTemplate.opsForList().rightPush(key, "任务1");
        redisTemplate.opsForList().rightPush(key, "任务2");
        redisTemplate.opsForList().rightPush(key, "任务3");
        result.put("RPUSH", "插入 3 个任务");

        // 2. LRANGE（范围查询）
        List<Object> all = redisTemplate.opsForList().range(key, 0, -1);
        result.put("LRANGE 0 -1", all);

        // 3. LLEN（列表长度）
        Long size = redisTemplate.opsForList().size(key);
        result.put("LLEN", "列表长度: " + size);

        // 4. LPOP（左边弹出，FIFO）
        Object first = redisTemplate.opsForList().leftPop(key);
        result.put("LPOP", "弹出: " + first);

        // 5. LPUSH（左边插入，模拟栈）
        redisTemplate.opsForList().leftPush(key, "优先任务");
        all = redisTemplate.opsForList().range(key, 0, -1);
        result.put("LPUSH", "左边插入后: " + all);

        // 6. 阻塞弹出（BLPOP，带超时）
        // Object blocked = redisTemplate.opsForList().leftPop(key, 1, TimeUnit.SECONDS);
        result.put("BLPOP", "阻塞弹出（1秒超时），适合消息队列场景");

        // 清理
        redisTemplate.delete(key);

        return result;
    }

    // ========================================
    // 3. Hash 哈希操作
    // ========================================

    /**
     * Hash 基础操作
     *
     * HSET key field value   - 设置字段
     * HGET key field         - 获取字段
     * HGETALL key            - 获取所有字段
     * HDEL key field         - 删除字段
     * HINCRBY key field num  - 字段递增
     */
    public Map<String, Object> hashDemo() {
        Map<String, Object> result = new LinkedHashMap<>();

        String key = "demo:hash:user:1";

        // 清理旧数据
        redisTemplate.delete(key);

        // 1. HSET（设置字段）
        redisTemplate.opsForHash().put(key, "name", "张三");
        redisTemplate.opsForHash().put(key, "age", 25);
        redisTemplate.opsForHash().put(key, "email", "zhangsan@test.com");
        result.put("HSET", "设置 name/age/email");

        // 2. HGET（获取单个字段）
        String name = (String) redisTemplate.opsForHash().get(key, "name");
        result.put("HGET name", name);

        // 3. HGETALL（获取所有字段）
        Map<Object, Object> all = redisTemplate.opsForHash().entries(key);
        result.put("HGETALL", all);

        // 4. HINCRBY（字段递增）
        redisTemplate.opsForHash().increment(key, "age", 1);
        int age = (int) redisTemplate.opsForHash().get(key, "age");
        result.put("HINCRBY", "age 递增后: " + age);

        // 5. HDEL（删除字段）
        redisTemplate.opsForHash().delete(key, "email");
        all = redisTemplate.opsForHash().entries(key);
        result.put("HDEL email", "删除后: " + all);

        // 6. HMSET（批量设置）
        Map<String, Object> batch = Map.of(
                "city", "北京",
                "job", "工程师"
        );
        redisTemplate.opsForHash().putAll(key, batch);
        all = redisTemplate.opsForHash().entries(key);
        result.put("HMSET", "批量设置后: " + all);

        // 清理
        redisTemplate.delete(key);

        return result;
    }

    // ========================================
    // 4. Set 集合操作
    // ========================================

    /**
     * Set 基础操作
     *
     * SADD key member     - 添加成员
     * SMEMBERS key        - 获取所有成员
     * SISMEMBER key member - 判断成员是否存在
     * SINTER key1 key2    - 交集
     * SUNION key1 key2    - 并集
     * SDIFF key1 key2     - 差集
     */
    public Map<String, Object> setDemo() {
        Map<String, Object> result = new LinkedHashMap<>();

        String key1 = "demo:set:tags:java";
        String key2 = "demo:set:tags:python";

        // 清理旧数据
        redisTemplate.delete(Set.of(key1, key2));

        // 1. SADD（添加成员）
        redisTemplate.opsForSet().add(key1, "面向对象", "多线程", "JVM", "Spring");
        redisTemplate.opsForSet().add(key2, "面向对象", "数据科学", "机器学习", "多线程");
        result.put("SADD", "Java标签: 面向对象/多线程/JVM/Spring");

        // 2. SMEMBERS（获取所有成员）
        Set<Object> javaTags = redisTemplate.opsForSet().members(key1);
        result.put("SMEMBERS", javaTags);

        // 3. SISMEMBER（判断是否存在）
        Boolean hasJvm = redisTemplate.opsForSet().isMember(key1, "JVM");
        Boolean hasGo = redisTemplate.opsForSet().isMember(key1, "Go");
        result.put("SISMEMBER", "JVM: " + hasJvm + ", Go: " + hasGo);

        // 4. SINTER（交集 - 共同标签）
        Set<Object> common = redisTemplate.opsForSet().intersect(key1, key2);
        result.put("SINTER（交集）", common);

        // 5. SUNION（并集 - 所有标签）
        Set<Object> all = redisTemplate.opsForSet().union(key1, key2);
        result.put("SUNION（并集）", all);

        // 6. SDIFF（差集 - Java独有）
        Set<Object> javaOnly = redisTemplate.opsForSet().difference(key1, key2);
        result.put("SDIFF（差集）", "Java独有: " + javaOnly);

        // 清理
        redisTemplate.delete(Set.of(key1, key2));

        return result;
    }

    // ========================================
    // 5. ZSet 有序集合操作
    // ========================================

    /**
     * ZSet 基础操作
     *
     * ZADD key score member   - 添加成员（带分数）
     * ZRANGE key start stop   - 按分数升序查询
     * ZREVRANGE key start stop - 按分数降序查询
     * ZINCRBY key num member  - 分数递增
     * ZRANK key member        - 获取排名（升序）
     * ZREVRANK key member     - 获取排名（降序）
     */
    public Map<String, Object> zsetDemo() {
        Map<String, Object> result = new LinkedHashMap<>();

        String key = "demo:zset:leaderboard";

        // 清理旧数据
        redisTemplate.delete(key);

        // 1. ZADD（添加成员和分数）
        redisTemplate.opsForZSet().add(key, "张三", 95);
        redisTemplate.opsForZSet().add(key, "李四", 88);
        redisTemplate.opsForZSet().add(key, "王五", 92);
        redisTemplate.opsForZSet().add(key, "赵六", 78);
        redisTemplate.opsForZSet().add(key, "钱七", 85);
        result.put("ZADD", "添加 5 个用户的成绩");

        // 2. ZRANGE（升序查询）
        Set<Object> asc = redisTemplate.opsForZSet().range(key, 0, -1);
        result.put("ZRANGE（升序）", asc);

        // 3. ZREVRANGE（降序查询 - 排行榜）
        Set<Object> desc = redisTemplate.opsForZSet().reverseRange(key, 0, -1);
        result.put("ZREVRANGE（降序）", desc);

        // 4. ZINCRBY（分数递增）
        redisTemplate.opsForZSet().incrementScore(key, "赵六", 15);
        Double score = redisTemplate.opsForZSet().score(key, "赵六");
        result.put("ZINCRBY", "赵六 +15，新分数: " + score);

        // 5. ZREVRANK（降序排名）
        Long rank = redisTemplate.opsForZSet().reverseRank(key, "王五");
        result.put("ZREVRANK", "王五排名: 第" + (rank + 1) + "名");

        // 6. TOP N（前3名）
        Set<Object> top3 = redisTemplate.opsForZSet().reverseRange(key, 0, 2);
        result.put("TOP 3", top3);

        // 7. ZCARD（集合大小）
        Long size = redisTemplate.opsForZSet().zCard(key);
        result.put("ZCARD", "总人数: " + size);

        // 8. ZCOUNT（分数区间计数）
        Long count = redisTemplate.opsForZSet().count(key, 90, 100);
        result.put("ZCOUNT", "90-100分人数: " + count);

        // 清理
        redisTemplate.delete(key);

        return result;
    }

    // ========================================
    // 6. 通用操作
    // ========================================

    /**
     * 通用操作
     *
     * EXISTS key    - 判断 key 是否存在
     * DEL key       - 删除 key
     * EXPIRE key seconds - 设置过期时间
     * TTL key       - 查看剩余过期时间
     * TYPE key      - 查看 key 类型
     * KEYS pattern  - 查找 key（生产环境慎用）
     */
    public Map<String, Object> commonDemo() {
        Map<String, Object> result = new LinkedHashMap<>();

        String prefix = "demo:common:";

        // 设置测试数据
        redisTemplate.opsForValue().set(prefix + "str", "value", 120, TimeUnit.SECONDS);
        redisTemplate.opsForList().rightPush(prefix + "list", "item1");
        redisTemplate.opsForHash().put(prefix + "hash", "field", "value");
        redisTemplate.opsForSet().add(prefix + "set", "member1");
        redisTemplate.opsForZSet().add(prefix + "zset", "member1", 1.0);

        // 1. EXISTS
        Boolean exists = redisTemplate.hasKey(prefix + "str");
        result.put("EXISTS", prefix + "str: " + exists);

        // 2. TYPE
        String type = redisTemplate.type(prefix + "str").code();
        result.put("TYPE", prefix + "str: " + type);

        // 3. TTL
        Long ttl = redisTemplate.getExpire(prefix + "str");
        result.put("TTL", prefix + "str: " + ttl + "s");

        // 4. EXPIRE
        redisTemplate.expire(prefix + "list", 60, TimeUnit.SECONDS);
        ttl = redisTemplate.getExpire(prefix + "list");
        result.put("EXPIRE", prefix + "list: " + ttl + "s");

        // 5. DEL
        redisTemplate.delete(prefix + "str");
        exists = redisTemplate.hasKey(prefix + "str");
        result.put("DEL", "删除后 EXISTS: " + exists);

        // 清理
        redisTemplate.delete(Set.of(
                prefix + "list", prefix + "hash", prefix + "set", prefix + "zset"
        ));

        return result;
    }
}
