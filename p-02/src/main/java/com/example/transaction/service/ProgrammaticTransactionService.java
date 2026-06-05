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
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ========================================
 * 编程式事务演示
 * ========================================
 *
 * 编程式事务 vs 声明式事务：
 *
 * | 对比项     | 声明式事务              | 编程式事务                    |
 * |-----------|----------------------|------------------------------|
 * | 使用方式   | @Transactional 注解    | TransactionTemplate 或 API   |
 * | 代码侵入性 | 无侵入                 | 侵入业务代码                  |
 * | 灵活性     | 低（方法级别）          | 高（代码块级别）               |
 * | 粒度       | 整个方法               | 方法内的任意代码块             |
 * | 适用场景   | 绝大多数场景            | 需要精细控制事务边界时          |
 *
 * 编程式事务的两种方式：
 * 1. TransactionTemplate（推荐，更简洁）
 * 2. PlatformTransactionManager（更底层，更灵活）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgrammaticTransactionService {

    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;
    private final PlatformTransactionManager transactionManager;

    // ========================================
    // 方式1：TransactionTemplate（推荐）
    // ========================================

    /**
     * TransactionTemplate - 有返回值
     */
    public User templateWithReturn(User user) {
        log.info("[TransactionTemplate-有返回值] 开始");
        return transactionTemplate.execute(status -> {
            log.info("[TransactionTemplate] 事务已开启");
            User saved = userRepository.save(user);
            log.info("[TransactionTemplate] 用户已保存");
            return saved;
        });
    }

    /**
     * TransactionTemplate - 无返回值
     */
    public void templateWithoutReturn(User user) {
        log.info("[TransactionTemplate-无返回值] 开始");
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                log.info("[TransactionTemplate] 事务已开启");
                userRepository.save(user);
                log.info("[TransactionTemplate] 用户已保存");
            }
        });
    }

    /**
     * TransactionTemplate - 手动回滚
     */
    public void templateManualRollback(User user) {
        log.info("[TransactionTemplate-手动回滚] 开始");
        transactionTemplate.execute(status -> {
            userRepository.save(user);
            log.info("[TransactionTemplate] 用户已保存，准备手动回滚");

            // 手动标记回滚
            status.setRollbackOnly();
            log.info("[TransactionTemplate] 已标记为回滚");

            return null;
        });
    }

    /**
     * TransactionTemplate - 异常回滚
     */
    public void templateExceptionRollback(User user) {
        log.info("[TransactionTemplate-异常回滚] 开始");
        transactionTemplate.execute(status -> {
            userRepository.save(user);
            log.info("[TransactionTemplate] 用户已保存，即将抛出异常");
            throw new RuntimeException("模拟异常");
        });
    }

    // ========================================
    // 方式2：PlatformTransactionManager（底层API）
    // ========================================

    /**
     * PlatformTransactionManager - 完整流程
     */
    public User managerComplete(User user) {
        log.info("[PlatformTransactionManager] 开始");

        // 1. 定义事务属性
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("programmatic-tx");
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        def.setTimeout(30);

        // 2. 获取事务
        TransactionStatus status = transactionManager.getTransaction(def);
        log.info("[PlatformTransactionManager] 事务已开启");

        try {
            // 3. 执行业务
            User saved = userRepository.save(user);
            log.info("[PlatformTransactionManager] 用户已保存");

            // 4. 提交事务
            transactionManager.commit(status);
            log.info("[PlatformTransactionManager] 事务已提交");
            return saved;
        } catch (Exception e) {
            // 5. 回滚事务
            transactionManager.rollback(status);
            log.info("[PlatformTransactionManager] 事务已回滚: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * PlatformTransactionManager - 嵌套事务
     */
    public void managerNestedTransaction(User user1, User user2) {
        log.info("[PlatformTransactionManager-嵌套] 开始");

        // 外层事务
        DefaultTransactionDefinition outerDef = new DefaultTransactionDefinition();
        outerDef.setName("outer-tx");
        TransactionStatus outerStatus = transactionManager.getTransaction(outerDef);

        try {
            userRepository.save(user1);
            log.info("[外层事务] 用户1已保存");

            // 内层事务（NESTED）
            DefaultTransactionDefinition innerDef = new DefaultTransactionDefinition();
            innerDef.setName("inner-tx");
            innerDef.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
            TransactionStatus innerStatus = transactionManager.getTransaction(innerDef);

            try {
                userRepository.save(user2);
                log.info("[内层事务] 用户2已保存");
                throw new RuntimeException("内层异常");
            } catch (Exception e) {
                // 回滚到 Savepoint
                transactionManager.rollback(innerStatus);
                log.info("[内层事务] 已回滚到 Savepoint");
            }

            // 外层事务继续提交
            transactionManager.commit(outerStatus);
            log.info("[外层事务] 已提交（用户1保存成功，用户2被回滚）");

        } catch (Exception e) {
            transactionManager.rollback(outerStatus);
            log.info("[外层事务] 已回滚");
        }
    }

    /**
     * 对比：声明式事务
     */
    @Transactional
    public User declarativeTransaction(User user) {
        log.info("[声明式事务] 开始");
        User saved = userRepository.save(user);
        log.info("[声明式事务] 用户已保存，Spring 自动管理事务");
        return saved;
    }
}
