package com.example.transaction.framework;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;

import java.util.concurrent.Callable;

/**
 * 事务链式构建器
 *
 * 提供灵活的链式 API，支持自定义事务属性
 *
 * 使用示例：
 * <pre>
 * // 自定义超时 + 只读
 * User user = Tx.builder()
 *     .readOnly()
 *     .timeout(5)
 *     .execute(() -> userRepository.findById(1L).orElse(null));
 *
 * // 自定义传播行为 + 读写
 * Tx.builder()
 *     .writable()
 *     .propagation(Propagation.REQUIRES_NEW)
 *     .timeout(10)
 *     .executeWithoutResult(() -> {
 *         accountRepository.updateBalance(id, amount.negate());
 *     });
 *
 * // 动态超时：根据数据量调整
 * int timeout = dataSize > 10000 ? 60 : 10;
 * Tx.builder()
 *     .writable()
 *     .timeout(timeout)
 *     .executeWithoutResult(() -> {
 *         batchRepository.saveAll(data);
 *     });
 * </pre>
 */
public class TxBuilder {

    private boolean readOnly = false;
    private int timeout = 0;
    private Propagation propagation = null;
    private Isolation isolation = null;

    TxBuilder() {
        // 通过 Tx.builder() 创建
    }

    // ========================================
    // 链式配置方法
    // ========================================

    /**
     * 设置为只读事务
     *
     * 适用场景：查询、报表、数据导出
     */
    public TxBuilder readOnly() {
        this.readOnly = true;
        return this;
    }

    /**
     * 设置为读写事务
     *
     * 适用场景：INSERT、UPDATE、DELETE
     */
    public TxBuilder writable() {
        this.readOnly = false;
        return this;
    }

    /**
     * 设置超时时间（秒）
     *
     * @param seconds 超时秒数，0 表示使用默认值
     */
    public TxBuilder timeout(int seconds) {
        this.timeout = seconds;
        return this;
    }

    /**
     * 设置传播行为
     *
     * 常用值：
     * - REQUIRED（默认）：有事务就加入，没有就新建
     * - REQUIRES_NEW：总是新建事务，挂起当前事务
     * - NESTED：嵌套事务（Savepoint）
     * - NOT_SUPPORTED：以非事务方式执行，挂起当前事务
     *
     * @param propagation 传播行为
     */
    public TxBuilder propagation(Propagation propagation) {
        this.propagation = propagation;
        return this;
    }

    /**
     * 设置隔离级别
     *
     * 常用值：
     * - READ_COMMITTED：读已提交（推荐）
     * - REPEATABLE_READ：可重复读（MySQL 默认）
     * - SERIALIZABLE：串行化（最严格，性能最差）
     *
     * @param isolation 隔离级别
     */
    public TxBuilder isolation(Isolation isolation) {
        this.isolation = isolation;
        return this;
    }

    // ========================================
    // 执行方法
    // ========================================

    /**
     * 执行事务（有返回值）
     *
     * @param action 业务逻辑
     * @param <T>    返回值类型
     * @return 业务返回值
     */
    public <T> T execute(Callable<T> action) {
        return Tx.executeInternal(readOnly, timeout, propagation, isolation, action);
    }

    /**
     * 执行事务（无返回值）
     *
     * @param action 业务逻辑
     */
    public void executeWithoutResult(Runnable action) {
        Tx.executeInternal(readOnly, timeout, propagation, isolation, () -> {
            action.run();
            return null;
        });
    }

    @Override
    public String toString() {
        return "TxBuilder{" +
                "readOnly=" + readOnly +
                ", timeout=" + (timeout > 0 ? timeout + "s" : "默认") +
                ", propagation=" + (propagation != null ? propagation : "REQUIRED") +
                ", isolation=" + (isolation != null ? isolation : "默认") +
                '}';
    }
}
