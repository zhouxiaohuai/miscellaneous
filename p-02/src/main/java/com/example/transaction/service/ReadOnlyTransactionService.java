package com.example.transaction.service;

import com.example.transaction.entity.User;
import com.example.transaction.jpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ========================================
 * 只读事务演示
 * ========================================
 *
 * @Transactional(readOnly = true) 的作用：
 *
 * 1. 【优化提示】告诉底层数据库这是只读操作，数据库可以进行优化
 *    - MySQL: 设置 session 为只读模式（START TRANSACTION READ ONLY）
 *    - PostgreSQL: 设置为只读事务
 *
 * 2. 【Hibernate 一级缓存优化】
 *    - 只读事务中，Hibernate 不会进行脏检查（dirty checking）
 *    - 减少内存开销和 CPU 消耗
 *
 * 3. 【防止误写】
 *    - 在只读事务中执行写操作，可能抛出异常或不生效
 *
 * 4. 【路由到从库】
 *    - 如果配置了读写分离，只读事务可以自动路由到从库
 *
 * 使用场景：
 * - 查询接口
 * - 报表生成
 * - 数据导出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadOnlyTransactionService {

    private final UserRepository userRepository;

    /**
     * 只读事务 - 查询
     */
    @Transactional(readOnly = true)
    public List<User> findAllReadOnly() {
        log.info("[只读事务] 查询所有用户");
        log.info("[只读事务] Hibernate 不会进行脏检查，性能更优");
        return userRepository.findAll();
    }

    /**
     * 普通事务 - 查询（对比）
     */
    @Transactional
    public List<User> findAllNormal() {
        log.info("[普通事务] 查询所有用户");
        log.info("[普通事务] Hibernate 会进行脏检查，开销更大");
        return userRepository.findAll();
    }

    /**
     * 只读事务中的写操作（可能会出问题）
     */
    @Transactional(readOnly = true)
    public User readOnlyWithWrite(User user) {
        log.info("[只读事务-写操作] 尝试在只读事务中写入数据...");
        try {
            User saved = userRepository.save(user);
            log.info("[只读事务-写操作] 写入成功（某些数据库可能允许）");
            return saved;
        } catch (Exception e) {
            log.info("[只读事务-写操作] 写入失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 只读事务的典型使用场景：分页查询
     */
    @Transactional(readOnly = true)
    public List<User> findUsersForReport() {
        log.info("[报表查询] 使用只读事务进行报表查询");
        log.info("[报表查询] 1. 不进行脏检查 → 减少内存开销");
        log.info("[报表查询] 2. 可路由到从库 → 减少主库压力");
        log.info("[报表查询] 3. 数据库层面优化 → 可能使用 MVCC 快照读");
        return userRepository.findAll();
    }
}
