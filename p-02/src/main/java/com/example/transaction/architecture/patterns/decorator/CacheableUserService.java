package com.example.transaction.architecture.patterns.decorator;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;

/**
 * ============================================================
 * 装饰器模式 — 用户服务装饰器
 * ============================================================
 *
 * 【定义】
 * 动态地给一个对象添加一些额外的职责。就增加功能来说，
 * 装饰器模式相比生成子类更为灵活。
 *
 * 【适用场景】
 * - 增强功能但不修改原类（遵循开闭原则）
 * - 功能可叠加（缓存 + 日志 + 限流）
 * - AOP 的底层思想
 *
 * 【本示例场景】
 * UserService 是核心服务，需要增加：
 * 1. 缓存装饰器 — 查询结果缓存到 Map
 * 2. 日志装饰器 — 记录方法调用耗时
 * 3. 限流装饰器 — 控制调用频率
 *
 * 装饰器可以自由组合：
 * - 只加缓存：new CacheDecorator(realService)
 * - 缓存+日志：new LogDecorator(new CacheDecorator(realService))
 *
 * 【与代理模式的区别】
 * 装饰器：关注"增强功能"，可以多层嵌套
 * 代理模式：关注"控制访问"，通常只有一层
 *
 * 【类图】
 * ┌────────────────────────┐
 * │ <<interface>>           │
 * │ UserService             │
 * │ + findById(id)          │
 * │ + findByName(name)      │
 * └───────────┬─────────────┘
 *             │
 *    ┌────────┼────────┐
 *    ▼        ▼        ▼
 * RealUser  Cache     Log
 * Service   Decorator Decorator
 *           │         │
 *           └─────────┘  (可以嵌套)
 */

// ==================== 用户数据 ====================

record UserVO(Long id, String name, String email, String phone) {}

// ==================== 核心接口 ====================

/**
 * 用户服务接口 — 装饰器和被装饰者都实现此接口
 */
interface UserService {
    Optional<UserVO> findById(Long id);
    Optional<UserVO> findByName(String name);
    String getUserSummary(Long id);
}

// ==================== 真实实现 ====================

/**
 * 真实的用户服务 — 只包含核心业务逻辑
 */
@Slf4j
class RealUserService implements UserService {

    // 模拟数据库
    private static final Map<Long, UserVO> DB = Map.of(
            1L, new UserVO(1L, "张三", "zhangsan@example.com", "13800001111"),
            2L, new UserVO(2L, "李四", "lisi@example.com", "13800002222"),
            3L, new UserVO(3L, "王五", "wangwu@example.com", "13800003333")
    );

    @Override
    public Optional<UserVO> findById(Long id) {
        log.info("[RealService] 查询数据库: id={}", id);
        // 模拟数据库查询耗时
        simulateSlowQuery();
        return Optional.ofNullable(DB.get(id));
    }

    @Override
    public Optional<UserVO> findByName(String name) {
        log.info("[RealService] 查询数据库: name={}", name);
        simulateSlowQuery();
        return DB.values().stream().filter(u -> u.name().equals(name)).findFirst();
    }

    @Override
    public String getUserSummary(Long id) {
        return findById(id)
                .map(u -> String.format("用户: %s, 邮箱: %s", u.name(), u.email()))
                .orElse("用户不存在");
    }

    private void simulateSlowQuery() {
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
    }
}

// ==================== 装饰器 1: 缓存装饰器 ====================

/**
 * 缓存装饰器 — 给用户服务添加缓存能力
 *
 * 【实现要点】
 * 1. 持有被装饰对象的引用（delegate）
 * 2. 在调用被装饰对象之前/之后添加缓存逻辑
 * 3. 对外表现和被装饰对象一样（都实现 UserService）
 */
@Slf4j
class CacheDecorator implements UserService {

    private final UserService delegate;
    private final Map<Long, UserVO> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public CacheDecorator(UserService delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<UserVO> findById(Long id) {
        // 先查缓存
        if (cache.containsKey(id)) {
            log.info("[CacheDecorator] 缓存命中: id={}", id);
            return Optional.of(cache.get(id));
        }

        // 缓存未命中，调用真实服务
        log.info("[CacheDecorator] 缓存未命中: id={}, 查询真实服务", id);
        Optional<UserVO> result = delegate.findById(id);

        // 写入缓存
        result.ifPresent(user -> {
            cache.put(id, user);
            log.info("[CacheDecorator] 写入缓存: id={}", id);
        });

        return result;
    }

    @Override
    public Optional<UserVO> findByName(String name) {
        // 缓存装饰器不处理按名称查询，直接委托
        return delegate.findByName(name);
    }

    @Override
    public String getUserSummary(Long id) {
        return delegate.getUserSummary(id);
    }
}

// ==================== 装饰器 2: 日志装饰器 ====================

/**
 * 日志装饰器 — 记录方法调用耗时
 */
@Slf4j
class LogDecorator implements UserService {

    private final UserService delegate;

    public LogDecorator(UserService delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<UserVO> findById(Long id) {
        long start = System.currentTimeMillis();
        log.info("[LogDecorator] >>> findById({}) 开始", id);

        Optional<UserVO> result = delegate.findById(id);

        long cost = System.currentTimeMillis() - start;
        log.info("[LogDecorator] <<< findById({}) 完成, 耗时: {}ms, 结果: {}",
                id, cost, result.isPresent() ? "找到" : "未找到");
        return result;
    }

    @Override
    public Optional<UserVO> findByName(String name) {
        long start = System.currentTimeMillis();
        log.info("[LogDecorator] >>> findByName({}) 开始", name);

        Optional<UserVO> result = delegate.findByName(name);

        long cost = System.currentTimeMillis() - start;
        log.info("[LogDecorator] <<< findByName({}) 完成, 耗时: {}ms", name, cost);
        return result;
    }

    @Override
    public String getUserSummary(Long id) {
        long start = System.currentTimeMillis();
        String result = delegate.getUserSummary(id);
        log.info("[LogDecorator] getUserSummary({}) 耗时: {}ms", id, System.currentTimeMillis() - start);
        return result;
    }
}
