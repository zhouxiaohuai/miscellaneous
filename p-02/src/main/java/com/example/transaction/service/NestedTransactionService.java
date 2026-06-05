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
 * 嵌套事务与 Savepoint 演示
 * ========================================
 *
 * 嵌套事务（NESTED）的核心概念：
 *
 * 1. 嵌套事务通过 Savepoint 实现
 * 2. 内层事务回滚只会回滚到 Savepoint，不影响外层事务
 * 3. 外层事务提交时，内层事务也会被提交
 * 4. 外层事务回滚时，内层事务也会被回滚
 *
 * 与 REQUIRES_NEW 的区别：
 * - NESTED：内层回滚不影响外层，但内层提交依赖外层
 * - REQUIRES_NEW：完全独立，内层提交/回滚都不影响外层
 *
 * 注意：并非所有数据库驱动都支持 Savepoint！
 * MySQL InnoDB 支持。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NestedTransactionService {

    private final UserRepository userRepository;
    private final PlatformTransactionManager transactionManager;
    private final PropagationService.InnerService innerService;

    /**
     * 演示1：NESTED 传播行为 - 内层异常不影响外层
     */
    @Transactional
    public void nestedDemo1(User outerUser, User innerUser) {
        log.info("=== 嵌套事务演示1: 内层异常不影响外层 ===");
        log.info("[外层] 保存用户: {}", outerUser.getUsername());
        userRepository.save(outerUser);

        try {
            innerService.nestedInner(innerUser);
        } catch (Exception e) {
            log.info("[外层] 内层异常已捕获: {}", e.getMessage());
            log.info("[外层] 内层事务已回滚到 Savepoint，外层事务继续");
        }

        log.info("[外层] 外层事务正常提交");
    }

    /**
     * 演示2：REQUIRES_NEW vs NESTED 对比
     */
    @Transactional
    public void requiresNewVsNested(User user1, User user2, User user3) {
        log.info("=== REQUIRES_NEW vs NESTED 对比 ===");

        // 保存用户1（外层事务）
        userRepository.save(user1);
        log.info("[外层] 用户1已保存");

        // 演示 REQUIRES_NEW：完全独立的事务
        try {
            innerService.requiresNewInner(user2);
        } catch (Exception e) {
            log.info("[外层] REQUIRES_NEW 异常: {}（内层已独立回滚）", e.getMessage());
        }

        // 演示 NESTED：嵌套事务（Savepoint）
        try {
            innerService.nestedInner(user3);
        } catch (Exception e) {
            log.info("[外层] NESTED 异常: {}（内层已回滚到 Savepoint）", e.getMessage());
        }

        log.info("[外层] 外层事务正常提交（用户1保存成功）");
    }

    /**
     * 演示3：手动使用 Savepoint（编程式）
     *
     * 这是 NESTED 传播行为的底层实现原理
     */
    public void manualSavepointDemo(User user1, User user2) {
        log.info("=== 手动 Savepoint 演示 ===");

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("manual-savepoint-tx");
        TransactionStatus status = transactionManager.getTransaction(def);

        try {
            // 保存用户1
            userRepository.save(user1);
            log.info("[Step 1] 用户1已保存");

            // 创建 Savepoint
            Object savepoint = status.createSavepoint();
            log.info("[Step 2] Savepoint 已创建");

            try {
                // 保存用户2
                userRepository.save(user2);
                log.info("[Step 3] 用户2已保存");

                // 模拟异常
                throw new RuntimeException("模拟异常");

            } catch (Exception e) {
                // 回滚到 Savepoint（只有用户2被回滚）
                status.rollbackToSavepoint(savepoint);
                log.info("[Step 4] 已回滚到 Savepoint，用户2被撤销");
            }

            // 继续提交（用户1被保存）
            transactionManager.commit(status);
            log.info("[Step 5] 事务已提交，用户1保存成功，用户2被回滚");

        } catch (Exception e) {
            transactionManager.rollback(status);
            log.info("外层异常，整体回滚: {}", e.getMessage());
        }
    }
}
