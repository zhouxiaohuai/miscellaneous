package com.example.transaction.service;

import com.example.transaction.entity.User;
import com.example.transaction.jpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/**
 * ========================================
 * 超时事务演示
 * ========================================
 *
 * @Transactional(timeout = N) 的作用：
 *
 * 1. 设置事务超时时间（秒）
 * 2. 超时后事务自动回滚
 * 3. 抛出 TransactionTimedOutException
 *
 * 使用场景：
 * - 防止长时间运行的事务占用数据库连接
 * - 防止死锁
 * - 保护数据库资源
 *
 * 注意事项：
 * 1. 超时从事务开始时计算（不是从方法开始时）
 * 2. 包含获取连接、执行SQL、等待锁等所有时间
 * 3. 设置为 -1 表示使用数据库默认超时
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeoutTransactionService {

    private final UserRepository userRepository;

    /**
     * 正常事务（不超时）
     */
    @Transactional(timeout = 30)
    public User normalTransaction(User user) {
        log.info("[超时事务] 正常操作，超时设置30秒");
        return userRepository.save(user);
    }

    /**
     * 超时事务 - 模拟超时
     *
     * 注意：实际测试时，timeout 设置为很短的值来触发超时
     */
    @Transactional(timeout = 1) // 1秒超时
    public User timeoutTransaction(User user) {
        log.info("[超时事务] 开始，超时设置1秒");

        User saved = userRepository.save(user);
        log.info("[超时事务] 数据已保存");

        // 模拟长时间操作
        try {
            log.info("[超时事务] 模拟长时间操作（sleep 3秒）...");
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[超时事务] 操作完成（但事务可能已超时回滚）");
        return saved;
    }

    /**
     * 超时事务 + 隔离级别
     *
     * 组合使用 timeout 和 isolation
     */
    @Transactional(timeout = 5, isolation = Isolation.READ_COMMITTED)
    public User timeoutWithIsolation(User user) {
        log.info("[超时事务+隔离级别] timeout=5s, isolation=READ_COMMITTED");
        return userRepository.save(user);
    }
}
