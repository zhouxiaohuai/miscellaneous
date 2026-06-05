package com.example.transaction.service;

import com.example.transaction.entity.User;
import com.example.transaction.jpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ========================================
 * 7种事务传播行为演示
 * ========================================
 *
 * 传播行为定义：当一个事务方法被另一个事务方法调用时，事务应该如何传播。
 *
 * | 传播行为         | 外部有事务 | 外部无事务 | 说明                          |
 * |-----------------|-----------|-----------|-------------------------------|
 * | REQUIRED        | 加入       | 新建      | 默认值，最常用                  |
 * | REQUIRES_NEW    | 挂起，新建 | 新建      | 始终新事务，独立提交/回滚        |
 * | SUPPORTS        | 加入       | 非事务    | 跟随外部                       |
 * | NOT_SUPPORTED   | 挂起，非事务| 非事务    | 始终非事务执行                  |
 * | MANDATORY       | 加入       | 抛异常    | 必须在事务中调用                |
 * | NEVER           | 抛异常     | 非事务    | 不能在事务中调用                |
 * | NESTED          | 嵌套事务   | 新建      | 通过 savepoint 实现部分回滚     |
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropagationService {

    private final UserRepository userRepository;
    private final InnerService innerService;

    // ========================================
    // 1. REQUIRED（默认）
    // ========================================

    /**
     * 外部方法 - REQUIRED（默认）
     */
    @Transactional
    public void requiredOuter(User user1, User user2) {
        log.info("=== REQUIRED 传播行为演示 ===");
        log.info("[外部] 事务开始，保存用户1: {}", user1.getUsername());
        userRepository.save(user1);

        try {
            // 调用内部方法（REQUIRED - 加入当前事务）
            innerService.requiredInner(user2);
        } catch (Exception e) {
            log.info("[外部] 捕获内部异常: {}", e.getMessage());
            log.info("[外部] 结论: 内部方法回滚 → 外部事务也一起回滚（同一事务）");
        }

        // 验证：查询用户1是否还在
        boolean user1Exists = userRepository.findByUsername(user1.getUsername()).isPresent();
        log.info("[外部] 用户1是否存在: {}（应该不存在，因为内部异常导致整体回滚）", user1Exists);
    }

    // ========================================
    // 2. REQUIRES_NEW
    // ========================================

    /**
     * 外部方法 - REQUIRES_NEW
     */
    @Transactional
    public void requiresNewOuter(User user1, User user2) {
        log.info("=== REQUIRES_NEW 传播行为演示 ===");
        log.info("[外部] 事务开始，保存用户1: {}", user1.getUsername());
        userRepository.save(user1);

        try {
            // 调用内部方法（REQUIRES_NEW - 新建独立事务）
            innerService.requiresNewInner(user2);
        } catch (Exception e) {
            log.info("[外部] 捕获内部异常: {}", e.getMessage());
            log.info("[外部] 结论: 内部事务独立回滚，外部事务不受影响");
        }

        // 验证：查询用户1是否还在
        boolean user1Exists = userRepository.findByUsername(user1.getUsername()).isPresent();
        log.info("[外部] 用户1是否存在: {}（应该存在，外部事务独立提交）", user1Exists);
    }

    // ========================================
    // 3. SUPPORTS
    // ========================================

    /**
     * 有事务时调用 SUPPORTS
     */
    @Transactional
    public void supportsWithTransaction(User user) {
        log.info("=== SUPPORTS 传播行为演示 ===");
        log.info("[有事务] 调用 SUPPORTS 方法");
        innerService.supportsInner(user);
        log.info("[有事务] 结论: SUPPORTS 方法加入了外部事务");
    }

    /**
     * 无事务时调用 SUPPORTS
     */
    public void supportsWithoutTransaction(User user) {
        log.info("=== SUPPORTS 传播行为演示 ===");
        log.info("[无事务] 调用 SUPPORTS 方法");
        innerService.supportsInner(user);
        log.info("[无事务] 结论: SUPPORTS 方法以非事务方式执行");
    }

    // ========================================
    // 4. NOT_SUPPORTED
    // ========================================

    /**
     * 有事务时调用 NOT_SUPPORTED
     */
    @Transactional
    public void notSupportedWithTransaction(User user) {
        log.info("=== NOT_SUPPORTED 传播行为演示 ===");
        log.info("[有事务] 外部事务开始");
        innerService.notSupportedInner(user);
        log.info("[有事务] 结论: 内部方法挂起外部事务，以非事务方式执行");
    }

    // ========================================
    // 5. MANDATORY
    // ========================================

    /**
     * 有事务时调用 MANDATORY
     */
    @Transactional
    public void mandatoryWithTransaction(User user) {
        log.info("=== MANDATORY 传播行为演示 ===");
        log.info("[有事务] 调用 MANDATORY 方法");
        innerService.mandatoryInner(user);
        log.info("[有事务] 结论: MANDATORY 方法正常加入事务");
    }

    /**
     * 无事务时调用 MANDATORY（会抛异常）
     */
    public void mandatoryWithoutTransaction(User user) {
        log.info("=== MANDATORY 传播行为演示 ===");
        log.info("[无事务] 调用 MANDATORY 方法");
        try {
            innerService.mandatoryInner(user);
        } catch (Exception e) {
            log.info("[无事务] 异常: {}", e.getMessage());
            log.info("[无事务] 结论: MANDATORY 要求必须在事务中调用，否则抛出 IllegalTransactionStateException");
        }
    }

    // ========================================
    // 6. NEVER
    // ========================================

    /**
     * 无事务时调用 NEVER（正常执行）
     */
    public void neverWithoutTransaction(User user) {
        log.info("=== NEVER 传播行为演示 ===");
        log.info("[无事务] 调用 NEVER 方法");
        innerService.neverInner(user);
        log.info("[无事务] 结论: NEVER 方法以非事务方式正常执行");
    }

    /**
     * 有事务时调用 NEVER（会抛异常）
     */
    @Transactional
    public void neverWithTransaction(User user) {
        log.info("=== NEVER 传播行为演示 ===");
        log.info("[有事务] 调用 NEVER 方法");
        try {
            innerService.neverInner(user);
        } catch (Exception e) {
            log.info("[有事务] 异常: {}", e.getMessage());
            log.info("[有事务] 结论: NEVER 不允许在事务中调用，否则抛出 IllegalTransactionStateException");
        }
    }

    // ========================================
    // 7. NESTED
    // ========================================

    /**
     * 外部方法 - NESTED
     */
    @Transactional
    public void nestedOuter(User user1, User user2) {
        log.info("=== NESTED 传播行为演示 ===");
        log.info("[外部] 事务开始，保存用户1: {}", user1.getUsername());
        userRepository.save(user1);

        try {
            // 调用内部方法（NESTED - 嵌套事务/Savepoint）
            innerService.nestedInner(user2);
        } catch (Exception e) {
            log.info("[外部] 捕获内部异常: {}", e.getMessage());
            log.info("[外部] 结论: 嵌套事务回滚到 Savepoint，外部事务可以继续");
        }

        // 验证：查询用户1是否还在
        boolean user1Exists = userRepository.findByUsername(user1.getUsername()).isPresent();
        log.info("[外部] 用户1是否存在: {}（应该存在，外部事务继续提交）", user1Exists);
    }

    /**
     * 内部服务类 - 用于演示各种传播行为
     */
    @Service
    public static class InnerService {

        private final UserRepository userRepository;

        public InnerService(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public void requiredInner(User user) {
            log.info("[内部-REQUIRED] 保存用户: {}", user.getUsername());
            userRepository.save(user);
            log.info("[内部-REQUIRED] 即将抛出异常...");
            throw new RuntimeException("REQUIRED 内部异常");
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void requiresNewInner(User user) {
            log.info("[内部-REQUIRES_NEW] 新建独立事务，保存用户: {}", user.getUsername());
            userRepository.save(user);
            log.info("[内部-REQUIRES_NEW] 即将抛出异常...");
            throw new RuntimeException("REQUIRES_NEW 内部异常");
        }

        @Transactional(propagation = Propagation.SUPPORTS)
        public void supportsInner(User user) {
            log.info("[内部-SUPPORTS] 保存用户: {}", user.getUsername());
            userRepository.save(user);
        }

        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        public void notSupportedInner(User user) {
            log.info("[内部-NOT_SUPPORTED] 以非事务方式保存用户: {}", user.getUsername());
            userRepository.save(user);
        }

        @Transactional(propagation = Propagation.MANDATORY)
        public void mandatoryInner(User user) {
            log.info("[内部-MANDATORY] 加入外部事务，保存用户: {}", user.getUsername());
            userRepository.save(user);
        }

        @Transactional(propagation = Propagation.NEVER)
        public void neverInner(User user) {
            log.info("[内部-NEVER] 以非事务方式保存用户: {}", user.getUsername());
            userRepository.save(user);
        }

        @Transactional(propagation = Propagation.NESTED)
        public void nestedInner(User user) {
            log.info("[内部-NESTED] 创建嵌套事务(Savepoint)，保存用户: {}", user.getUsername());
            userRepository.save(user);
            log.info("[内部-NESTED] 即将抛出异常...");
            throw new RuntimeException("NESTED 内部异常");
        }
    }
}
