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

import java.util.ArrayList;
import java.util.List;

/**
 * ========================================
 * 批量操作事务优化演示
 * ========================================
 *
 * 大批量数据操作的事务策略：
 *
 * 1. 【单事务批量插入】- 一次性提交
 *    优点：速度快（一次提交）
 *    缺点：事务长时间持有连接，失败全部回滚
 *
 * 2. 【分批提交】- 每N条提交一次
 *    优点：减少连接持有时间，失败只回滚当前批次
 *    缺点：不是原子操作，可能部分成功部分失败
 *
 * 3. 【每条单独事务】- 每条记录一个事务
 *    优点：精确控制，失败不影响其他记录
 *    缺点：性能最差（频繁开启/提交事务）
 *
 * 选择建议：
 * - 数据量小（<1000）：单事务批量插入
 * - 数据量中等（1000-10000）：分批提交（每100-500条一批）
 * - 数据量大（>10000）：分批提交 + 异步处理
 * - 要求严格原子性：单事务（但要注意连接超时）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchTransactionService {

    private final UserRepository userRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * 策略1：单事务批量插入
     * 所有记录在同一个事务中，一次提交
     */
    @Transactional
    public int batchInsertSingleTransaction(List<User> users) {
        log.info("[单事务批量插入] 开始，共 {} 条记录", users.size());
        long start = System.currentTimeMillis();

        for (int i = 0; i < users.size(); i++) {
            userRepository.save(users.get(i));
            if ((i + 1) % 100 == 0) {
                log.info("[单事务批量插入] 已处理 {} 条", i + 1);
            }
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[单事务批量插入] 完成，共 {} 条，耗时 {}ms", users.size(), cost);
        return users.size();
    }

    /**
     * 策略2：分批提交
     * 每 batchSize 条记录提交一次事务
     */
    public int batchInsertWithBatchCommit(List<User> users, int batchSize) {
        log.info("[分批提交] 开始，共 {} 条记录，每批 {} 条", users.size(), batchSize);
        long start = System.currentTimeMillis();
        int totalProcessed = 0;

        for (int i = 0; i < users.size(); i += batchSize) {
            int end = Math.min(i + batchSize, users.size());
            List<User> batch = users.subList(i, end);

            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setName("batch-tx-" + (i / batchSize));
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            TransactionStatus status = transactionManager.getTransaction(def);

            try {
                for (User user : batch) {
                    userRepository.save(user);
                }
                transactionManager.commit(status);
                totalProcessed += batch.size();
                log.info("[分批提交] 第 {} 批完成，已处理 {} 条",
                        (i / batchSize + 1), totalProcessed);
            } catch (Exception e) {
                transactionManager.rollback(status);
                log.error("[分批提交] 第 {} 批失败，已回滚: {}",
                        (i / batchSize + 1), e.getMessage());
                throw e;
            }
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[分批提交] 完成，共 {} 条，耗时 {}ms", totalProcessed, cost);
        return totalProcessed;
    }

    /**
     * 策略3：每条单独事务
     * 每条记录独立事务，精确控制
     */
    public int batchInsertPerRecord(List<User> users) {
        log.info("[每条单独事务] 开始，共 {} 条记录", users.size());
        long start = System.currentTimeMillis();
        int successCount = 0;

        for (User user : users) {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setName("single-tx-" + user.getUsername());
            TransactionStatus status = transactionManager.getTransaction(def);

            try {
                userRepository.save(user);
                transactionManager.commit(status);
                successCount++;
            } catch (Exception e) {
                transactionManager.rollback(status);
                log.warn("[每条单独事务] 用户 {} 保存失败: {}",
                        user.getUsername(), e.getMessage());
            }
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[每条单独事务] 完成，成功 {} / {} 条，耗时 {}ms",
                successCount, users.size(), cost);
        return successCount;
    }

    /**
     * 生成测试数据
     */
    public List<User> generateTestUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(User.builder()
                    .username("batch_user_" + System.currentTimeMillis() + "_" + i)
                    .email("batch" + i + "@test.com")
                    .status(1)
                    .build());
        }
        return users;
    }
}
