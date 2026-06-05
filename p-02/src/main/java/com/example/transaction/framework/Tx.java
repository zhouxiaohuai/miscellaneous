package com.example.transaction.framework;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.concurrent.Callable;

/**
 * 自定义事务框架 - 门面类
 *
 * 提供简洁的静态方法，在代码任意位置开启事务
 * 解决 @Transactional 的三大局限：
 * 1. 自调用失效 → Tx 是静态方法调用，不依赖 AOP 代理
 * 2. 方法级边界 → Tx 可在方法内任意位置开启/关闭
 * 3. 必须 public → Tx 不受方法访问修饰符限制
 *
 * 使用示例：
 * <pre>
 * // 只读事务
 * Tx.readOnly(() -> {
 *     List<User> users = userRepository.findAll();
 * });
 *
 * // 读写事务
 * Tx.writable(() -> {
 *     user.setStatus(2);
 *     userRepository.save(user);
 * });
 *
 * // 有返回值
 * User user = Tx.readOnly(() -> userRepository.findById(1L).orElse(null));
 *
 * // 链式配置
 * Tx.builder().writable().timeout(10).execute(() -> {
 *     // 业务逻辑
 * });
 * </pre>
 */
@Slf4j
public class Tx {

    private Tx() {
        // 工具类，禁止实例化
    }

    // ========================================
    // 1. 只读事务
    // ========================================

    /**
     * 只读事务 - 无返回值
     *
     * 适用场景：查询、报表、数据导出
     * 优势：数据库可做优化（不加写锁、不记录 undo log）
     *
     * @param action 业务逻辑
     */
    public static void readOnly(Runnable action) {
        executeInternal(true, 0, null, null, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 只读事务 - 有返回值
     *
     * @param action 业务逻辑
     * @param <T>    返回值类型
     * @return 业务返回值
     */
    public static <T> T readOnly(Callable<T> action) {
        return executeInternal(true, 0, null, null, action);
    }

    // ========================================
    // 2. 读写事务
    // ========================================

    /**
     * 读写事务 - 无返回值
     *
     * 适用场景：INSERT、UPDATE、DELETE
     *
     * @param action 业务逻辑
     */
    public static void writable(Runnable action) {
        executeInternal(false, 0, null, null, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 读写事务 - 有返回值
     *
     * @param action 业务逻辑
     * @param <T>    返回值类型
     * @return 业务返回值
     */
    public static <T> T writable(Callable<T> action) {
        return executeInternal(false, 0, null, null, action);
    }

    // ========================================
    // 3. 链式 Builder
    // ========================================

    /**
     * 创建链式构建器
     *
     * 使用示例：
     * <pre>
     * Tx.builder()
     *     .writable()
     *     .timeout(10)
     *     .propagation(Propagation.REQUIRES_NEW)
     *     .executeWithoutResult(() -> {
     *         // 业务逻辑
     *     });
     * </pre>
     *
     * @return TxBuilder 实例
     */
    public static TxBuilder builder() {
        return new TxBuilder();
    }

    // ========================================
    // 内部执行方法
    // ========================================

    /**
     * 事务执行的核心方法
     *
     * @param readOnly    是否只读
     * @param timeout     超时秒数（0 表示使用默认值）
     * @param propagation 传播行为（null 表示使用默认 REQUIRED）
     * @param action      业务逻辑
     * @param <T>         返回值类型
     * @return 业务返回值
     */
    static <T> T executeInternal(boolean readOnly, int timeout,
                                  org.springframework.transaction.annotation.Propagation propagation,
                                  org.springframework.transaction.annotation.Isolation isolation,
                                  Callable<T> action) {
        // 构建事务定义
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setReadOnly(readOnly);

        if (timeout > 0) {
            def.setTimeout(timeout);
        }

        if (propagation != null) {
            def.setPropagationBehavior(propagation.value());
        }

        if (isolation != null) {
            def.setIsolationLevel(isolation.value());
        }

        // 获取事务管理器
        org.springframework.transaction.PlatformTransactionManager txManager =
                TxContext.getTransactionManager();

        // 开启事务
        TransactionStatus status = txManager.getTransaction(def);
        String txType = readOnly ? "只读" : "读写";

        log.debug("[Tx] {}事务开启, timeout={}, propagation={}",
                txType,
                timeout > 0 ? timeout + "s" : "默认",
                propagation != null ? propagation : "REQUIRED");

        try {
            // 执行业务逻辑
            T result = action.call();

            // 提交事务
            txManager.commit(status);
            log.debug("[Tx] {}事务提交", txType);

            return result;
        } catch (Exception e) {
            // 回滚事务
            txManager.rollback(status);
            log.debug("[Tx] {}事务回滚, 原因: {}", txType, e.getMessage());

            // 包装异常抛出
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new TxException("事务执行失败", e);
        }
    }
}
