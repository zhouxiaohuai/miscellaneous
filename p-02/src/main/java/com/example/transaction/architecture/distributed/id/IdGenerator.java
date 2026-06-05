package com.example.transaction.architecture.distributed.id;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ============================================================
 * 分布式 ID 生成器
 * ============================================================
 *
 * 【为什么需要分布式 ID？】
 * 单体应用：数据库自增 ID 就够了
 * 分布式系统：多个数据库实例，自增 ID 会冲突
 *
 * 【常见方案对比】
 * ┌──────────────┬──────────┬──────────┬──────────┬──────────┐
 * │ 方案          │ 有序性   │ 性能     │ 依赖     │ 适用场景  │
 * ├──────────────┼──────────┼──────────┼──────────┼──────────┤
 * │ UUID         │ 无序     │ 高       │ 无       │ 通用     │
 * │ 数据库自增    │ 有序     │ 低       │ DB       │ 单库     │
 * │ Redis 自增   │ 有序     │ 中       │ Redis    │ 中小规模  │
 * │ Snowflake    │ 有序     │ 高       │ 无       │ 大规模   │
 * │ Leaf         │ 有序     │ 高       │ DB/Redis │ 美团方案  │
 * └──────────────┴──────────┴──────────┴──────────┴──────────┘
 *
 * 【本示例实现】
 * 1. UUID 生成器 — 最简单，无依赖
 * 2. 雪花算法（Snowflake） — 最常用，有序且高性能
 * 3. 自增 ID 生成器 — 模拟 Redis INCR
 */

// ==================== 接口定义 ====================

/**
 * ID 生成器接口
 */
public interface IdGenerator {
    long nextId();
    String nextStringId();
    String name();
}

// ==================== 实现 1: UUID 生成器 ====================

/**
 * UUID 生成器
 *
 * 优点：简单、无依赖、全球唯一
 * 缺点：无序（不适合做数据库主键）、太长（36字符）
 * 适用：临时 ID、非索引字段
 */
class UuidIdGenerator implements IdGenerator {

    @Override
    public long nextId() {
        // UUID 转 long 会丢失精度，这里用 hashcode 演示
        return Math.abs(java.util.UUID.randomUUID().hashCode());
    }

    @Override
    public String nextStringId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public String name() { return "UUID"; }
}

// ==================== 实现 2: 雪花算法 ====================

/**
 * ============================================================
 * Snowflake 雪花算法 — 分布式 ID 生成器
 * ============================================================
 *
 * 【ID 结构（64 bit）】
 * ┌───────────────────────────────────────────────────────────┐
 * │ 0 │ 41 位时间戳  │ 10 位机器ID │ 12 位序列号             │
 * │符号│ (毫秒级)     │ (1024台)    │ (4096/ms)               │
 * └───────────────────────────────────────────────────────────┘
 *
 * 【设计要点】
 * - 41 位时间戳：可用 69 年（从某个起始时间算起）
 * - 10 位机器 ID：支持 1024 台机器（5 位数据中心 + 5 位机器）
 * - 12 位序列号：同一毫秒可生成 4096 个 ID
 *
 * 【优点】
 * - 有序（按时间递增）
 * - 高性能（纯内存计算，不依赖外部服务）
 * - 去中心化（每台机器独立生成）
 *
 * 【缺点】
 * - 依赖系统时钟（时钟回拨会导致 ID 重复）
 * - 需要分配机器 ID（手动或通过 ZooKeeper/数据库分配）
 */
@Slf4j
class SnowflakeIdGenerator implements IdGenerator {

    // ==================== 位数配置 ====================
    private static final long EPOCH = 1704067200000L; // 起始时间: 2024-01-01 00:00:00
    private static final long WORKER_ID_BITS = 5L;    // 机器 ID 位数
    private static final long DATACENTER_ID_BITS = 5L; // 数据中心 ID 位数
    private static final long SEQUENCE_BITS = 12L;     // 序列号位数

    // ==================== 最大值 ====================
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);       // 31
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS); // 31
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);         // 4095

    // ==================== 位移 ====================
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;                    // 12
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS; // 17
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS; // 22

    // ==================== 实例变量 ====================
    private final long workerId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("Worker ID 超出范围: 0 ~ " + MAX_WORKER_ID);
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("Datacenter ID 超出范围: 0 ~ " + MAX_DATACENTER_ID);
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        log.info("[Snowflake] 初始化: workerId={}, datacenterId={}", workerId, datacenterId);
    }

    @Override
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        // 时钟回拨检查
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                // 时钟回拨在 5ms 内，等待
                try { Thread.sleep(offset << 1); } catch (InterruptedException ignored) {}
                timestamp = System.currentTimeMillis();
            } else {
                throw new RuntimeException("时钟回拨超过 5ms，拒绝生成 ID");
            }
        }

        if (timestamp == lastTimestamp) {
            // 同一毫秒内，序列号递增
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 序列号溢出，等待下一毫秒
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，序列号重置
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 组装 ID
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    @Override
    public String nextStringId() {
        return String.valueOf(nextId());
    }

    @Override
    public String name() { return "Snowflake"; }

    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}

// ==================== 实现 3: 自增 ID 生成器 ====================

/**
 * 自增 ID 生成器（模拟 Redis INCR）
 *
 * 优点：简单、有序
 * 缺点：依赖外部存储、单点瓶颈
 * 适用：中小规模系统
 */
@Slf4j
class IncrementIdGenerator implements IdGenerator {

    private final AtomicLong counter;

    public IncrementIdGenerator(long initialValue) {
        this.counter = new AtomicLong(initialValue);
    }

    @Override
    public long nextId() {
        long id = counter.incrementAndGet();
        log.debug("[IncrementId] 生成 ID: {}", id);
        return id;
    }

    @Override
    public String nextStringId() {
        return String.valueOf(nextId());
    }

    @Override
    public String name() { return "Increment"; }
}

// ==================== 工厂 ====================

/**
 * ID 生成器工厂
 */
@Slf4j
class IdGeneratorFactory {

    public static IdGenerator createSnowflake(long workerId, long datacenterId) {
        return new SnowflakeIdGenerator(workerId, datacenterId);
    }

    public static IdGenerator createUuid() {
        return new UuidIdGenerator();
    }

    public static IdGenerator createIncrement(long initialValue) {
        return new IncrementIdGenerator(initialValue);
    }
}
