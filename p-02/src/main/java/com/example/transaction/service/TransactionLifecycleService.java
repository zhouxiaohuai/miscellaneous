package com.example.transaction.service;

import com.example.transaction.entity.User;
import com.example.transaction.jpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * ========================================
 * 事务生命周期全过程演示
 * ========================================
 *
 * Spring 事务的完整生命周期：
 *
 * 1. 【解析阶段】Spring 容器启动时，扫描 @Transactional 注解
 *    → 通过 BeanPostProcessor 创建 AOP 代理对象
 *
 * 2. 【拦截阶段】方法调用时，AOP 代理拦截调用
 *    → TransactionInterceptor.invoke() 被调用
 *
 * 3. 【获取事务】TransactionManager.getTransaction()
 *    → 根据传播行为决定：新建事务 or 加入已有事务
 *    → 通过 DataSource 获取数据库连接
 *    → 设置 autoCommit = false
 *    → 将连接绑定到 ThreadLocal（TransactionSynchronizationManager）
 *
 * 4. 【执行业务】执行被 @Transactional 标注的方法
 *    → 此时的数据库操作都在同一个事务中
 *
 * 5. 【提交/回滚】
 *    → 正常完成：TransactionManager.commit() → connection.commit()
 *    → 抛出异常：TransactionManager.rollback() → connection.rollback()
 *
 * 6. 【清理阶段】
 *    → 解绑 ThreadLocal 中的连接
 *    → 归还连接到连接池
 *    → 清理事务同步回调
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionLifecycleService {

    private final UserRepository userRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * 演示1：声明式事务（@Transactional）的完整生命周期
     *
     * 调用此方法时，Spring 内部的执行流程：
     * TransactionInterceptor.invoke()
     *   → createTransactionIfNecessary()
     *     → doGetTransaction()  // 获取事务对象
     *     → startTransaction()  // 开启事务
 *       → DataSourceUtils.getConnection()  // 从数据源获取连接
 *       → connection.setAutoCommit(false)  // 关闭自动提交
 *       → TransactionSynchronizationManager.bindResource()  // 绑定到ThreadLocal
     *   → invokeWithinTransaction()  // 执行业务方法
     *   → commitTransactionAfterReturning()  // 提交事务
     *     → connection.commit()
     *     → cleanupAfterCompletion()  // 清理资源
     */
    @Transactional
    public User declarativeTransactionDemo(User user) {
        log.info("========== 声明式事务开始 ==========");
        log.info("Step 1: 事务已由 Spring 自动开启（@Transactional）");
        log.info("Step 2: 当前线程: {}", Thread.currentThread().getName());

        // 执行数据库操作
        User saved = userRepository.save(user);
        log.info("Step 3: SQL 已执行，数据尚未持久化（在事务中）");

        // 可以在此查询验证数据在事务内可见
        User found = userRepository.findByUsername(user.getUsername()).orElse(null);
        log.info("Step 4: 事务内查询结果: {}", found != null ? "存在" : "不存在");

        log.info("Step 5: 方法正常返回，Spring 将自动提交事务");
        log.info("========== 声明式事务结束 ==========");
        return saved;
    }

    /**
     * 演示2：声明式事务回滚
     */
    @Transactional
    public User declarativeTransactionRollbackDemo(User user) {
        log.info("========== 声明式事务(回滚)开始 ==========");

        userRepository.save(user);
        log.info("Step 1: 用户已保存（在事务中）");

        log.info("Step 2: 即将抛出异常...");
        throw new RuntimeException("模拟业务异常，触发事务回滚");
        // Spring 捕获到 RuntimeException 后，执行 rollback()
        // 数据库执行 ROLLBACK，数据恢复到事务开始前的状态
    }

    /**
     * 演示3：编程式事务 - 完全手动控制事务生命周期
     *
     * 与声明式事务对比：
     * - 声明式：Spring 自动管理 开启→执行→提交/回滚
     * - 编程式：开发者手动控制每一步
     */
    public User programmaticTransactionDemo(User user) {
        log.info("========== 编程式事务开始 ==========");

        // Step 1: 定义事务属性
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("programmatic-tx-demo");
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        def.setTimeout(30);

        // Step 2: 获取事务状态对象
        TransactionStatus status = transactionManager.getTransaction(def);
        log.info("Step 1: 事务已手动开启");

        try {
            // Step 3: 执行业务逻辑
            User saved = userRepository.save(user);
            log.info("Step 2: SQL 已执行");

            // Step 4: 手动提交事务
            transactionManager.commit(status);
            log.info("Step 3: 事务已手动提交");
            log.info("========== 编程式事务结束(提交) ==========");
            return saved;
        } catch (Exception e) {
            // Step 5: 手动回滚事务
            transactionManager.rollback(status);
            log.info("Step 3: 事务已手动回滚，原因: {}", e.getMessage());
            log.info("========== 编程式事务结束(回滚) ==========");
            throw e;
        }
    }

    /**
     * 演示4：编程式事务 - 回滚场景
     */
    public User programmaticTransactionRollbackDemo(User user) {
        log.info("========== 编程式事务(回滚)开始 ==========");

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("programmatic-tx-rollback-demo");
        TransactionStatus status = transactionManager.getTransaction(def);

        try {
            userRepository.save(user);
            log.info("Step 1: 用户已保存（在事务中）");

            log.info("Step 2: 即将抛出异常...");
            throw new RuntimeException("模拟异常");

        } catch (Exception e) {
            transactionManager.rollback(status);
            log.info("Step 3: 事务已回滚，数据恢复到事务前状态");
            log.info("========== 编程式事务(回滚)结束 ==========");
            throw e;
        }
    }
}
