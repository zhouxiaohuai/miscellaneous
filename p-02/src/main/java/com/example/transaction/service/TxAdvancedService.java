package com.example.transaction.service;

import com.example.transaction.entity.TransactionLog;
import com.example.transaction.entity.User;
import com.example.transaction.framework.Tx;
import com.example.transaction.jpa.repository.TransactionLogRepository;
import com.example.transaction.jpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tx 框架高级用法：大事务 + 嵌套事务联动
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TxAdvancedService {

    private final UserRepository userRepository;
    private final TransactionLogRepository transactionLogRepository;

    // ========================================
    // 1. 大事务 + Tx 联动
    // ========================================

    /**
     * 场景：批量导入 10000 条用户数据
     *
     * 问题：单事务 10000 条 → 事务时间长，连接占用久
     *
     * 解决：分片提交，每片独立事务
     */
    public Map<String, Object> bigTransaction_ChunkedCommit(int totalCount, int chunkSize) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        // 生成测试数据
        List<User> allUsers = new ArrayList<>();
        for (int i = 0; i < totalCount; i++) {
            allUsers.add(User.builder()
                    .username("chunk_" + System.currentTimeMillis() + "_" + i)
                    .email("chunk" + i + "@test.com")
                    .status(1)
                    .build());
        }

        int totalSuccess = 0;
        int totalFail = 0;
        List<Map<String, Object>> chunkResults = new ArrayList<>();

        // 分片提交：每 chunkSize 条一个独立事务
        for (int i = 0; i < allUsers.size(); i += chunkSize) {
            int chunkIndex = i / chunkSize + 1;
            List<User> chunk = allUsers.subList(i, Math.min(i + chunkSize, allUsers.size()));

            long chunkStart = System.currentTimeMillis();

            try {
                // ★ 每片用 Tx.writable() 包裹，独立事务
                List<User> saved = Tx.builder()
                        .writable()
                        .timeout(30)  // 每片独立超时
                        .execute(() -> userRepository.saveAll(chunk));

                long chunkCost = System.currentTimeMillis() - chunkStart;
                totalSuccess += saved.size();
                chunkResults.add(Map.of(
                        "chunk", chunkIndex,
                        "count", saved.size(),
                        "ms", chunkCost,
                        "status", "SUCCESS"
                ));
                log.info("[分片提交] 第 {} 片完成，{} 条，耗时 {}ms", chunkIndex, saved.size(), chunkCost);
            } catch (Exception e) {
                totalFail += chunk.size();
                chunkResults.add(Map.of(
                        "chunk", chunkIndex,
                        "count", chunk.size(),
                        "status", "FAIL",
                        "error", e.getMessage()
                ));
                log.error("[分片提交] 第 {} 片失败: {}", chunkIndex, e.getMessage());
            }
        }

        long cost = System.currentTimeMillis() - start;
        result.put("模式", "大事务 + Tx 分片提交");
        result.put("总数", totalCount);
        result.put("每片大小", chunkSize);
        result.put("成功", totalSuccess);
        result.put("失败", totalFail);
        result.put("总耗时", cost + "ms");
        result.put("分片详情", chunkResults);

        return result;
    }

    /**
     * 场景：转账操作，最小事务范围
     *
     * 问题：查询、校验、远程调用全在事务中 → 连接占用久
     *
     * 解决：只在写操作时用 Tx.writable() 开启事务
     */
    public Map<String, Object> bigTransaction_MinimalScope(Long userId) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        // ===== 事务外：查询 =====
        log.info("[最小事务范围] 步骤1: 事务外查询");
        User user = Tx.readOnly(() -> userRepository.findById(userId).orElseThrow());
        result.put("步骤1", "事务外查询 - 用户: " + user.getUsername());

        // ===== 事务外：校验 =====
        log.info("[最小事务范围] 步骤2: 事务外校验");
        if (user.getStatus() == 3) {
            result.put("状态", "用户已禁用");
            return result;
        }
        result.put("步骤2", "事务外校验 - 用户状态正常");

        // ===== 事务外：模拟远程调用 =====
        log.info("[最小事务范围] 步骤3: 事务外远程调用");
        simulateRemoteCall(500);
        result.put("步骤3", "事务外远程调用完成");

        // ===== 事务内：只包含写操作 =====
        log.info("[最小事务范围] 步骤4: 事务内写操作");
        long txStart = System.currentTimeMillis();

        Tx.writable(() -> {
            // 更新用户状态
            user.setStatus(2);
            userRepository.save(user);

            // 记录日志
            transactionLogRepository.save(TransactionLog.builder()
                    .operation("UPDATE_STATUS")
                    .status("SUCCESS")
                    .detail("userId=" + userId)
                    .createTime(LocalDateTime.now())
                    .build());
        });

        long txCost = System.currentTimeMillis() - txStart;
        result.put("步骤4", "事务内写操作完成，事务耗时 " + txCost + "ms");

        long totalCost = System.currentTimeMillis() - start;
        result.put("总耗时", totalCost + "ms");
        result.put("事务耗时", txCost + "ms");
        result.put("优化效果", "事务仅占总耗时的 " + (txCost * 100 / totalCost) + "%");

        return result;
    }

    // ========================================
    // 2. 嵌套事务 + Tx 联动
    // ========================================

    /**
     * 场景：批量处理用户，每个用户独立事务
     *
     * 如果某个用户处理失败，不影响其他用户
     *
     * 使用 REQUIRES_NEW：每个用户独立新事务
     */
    public Map<String, Object> nestedTransaction_RequiresNew(List<Long> userIds) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        int success = 0;
        int fail = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (Long userId : userIds) {
            try {
                // ★ 每个用户用 REQUIRES_NEW 独立事务
                User updated = Tx.builder()
                        .writable()
                        .propagation(Propagation.REQUIRES_NEW)  // 独立新事务
                        .timeout(10)
                        .execute(() -> {
                            User user = userRepository.findById(userId).orElseThrow();
                            user.setStatus(2);
                            return userRepository.save(user);
                        });

                success++;
                details.add(Map.of("userId", userId, "status", "SUCCESS", "username", updated.getUsername()));
                log.info("[REQUIRES_NEW] 用户 {} 处理成功", userId);
            } catch (Exception e) {
                fail++;
                details.add(Map.of("userId", userId, "status", "FAIL", "error", e.getMessage()));
                log.error("[REQUIRES_NEW] 用户 {} 处理失败: {}", userId, e.getMessage());
            }
        }

        long cost = System.currentTimeMillis() - start;
        result.put("模式", "嵌套事务 + Tx REQUIRES_NEW");
        result.put("成功", success);
        result.put("失败", fail);
        result.put("耗时", cost + "ms");
        result.put("说明", "每个用户独立事务，失败不影响其他用户");
        result.put("详情", details);

        return result;
    }

    /**
     * 场景：主流程 + 子流程，子流程失败可回滚到 Savepoint
     *
     * 使用 NESTED：内层回滚不影响外层
     */
    public Map<String, Object> nestedTransaction_Nested(Long userId) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        // 外层事务：创建用户
        User outerUser = Tx.builder()
                .writable()
                .timeout(30)
                .execute(() -> {
                    User user = User.builder()
                            .username("outer_" + System.currentTimeMillis())
                            .email("outer@test.com")
                            .status(1)
                            .build();
                    return userRepository.save(user);
                });
        result.put("外层事务", "创建用户: " + outerUser.getUsername());

        // 内层事务：尝试更新（可能失败）
        try {
            Tx.builder()
                    .writable()
                    .propagation(Propagation.NESTED)  // ★ 嵌套事务（Savepoint）
                    .timeout(10)
                    .executeWithoutResult(() -> {
                        // 模拟内层操作
                        User innerUser = User.builder()
                                .username("inner_" + System.currentTimeMillis())
                                .email("inner@test.com")
                                .status(1)
                                .build();
                        userRepository.save(innerUser);

                        // 模拟内层异常
                        throw new RuntimeException("内层业务异常");
                    });
        } catch (Exception e) {
            result.put("内层事务", "回滚到 Savepoint，原因: " + e.getMessage());
            log.info("[NESTED] 内层回滚，外层继续");
        }

        // 外层继续：记录日志
        Tx.writable(() -> {
            transactionLogRepository.save(TransactionLog.builder()
                    .operation("NESTED_DEMO")
                    .status("SUCCESS")
                    .detail("outerUser=" + outerUser.getUsername())
                    .createTime(LocalDateTime.now())
                    .build());
        });
        result.put("外层继续", "记录日志成功，外层事务正常提交");

        long cost = System.currentTimeMillis() - start;
        result.put("耗时", cost + "ms");
        result.put("说明", "NESTED：内层回滚到 Savepoint，外层不受影响");

        return result;
    }

    /**
     * 场景：主流程 + 多个子流程，每个子流程独立
     *
     * 综合演示：REQUIRES_NEW + NESTED 组合使用
     */
    public Map<String, Object> nestedTransaction_Combined(Long userId) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        // ===== 外层：主流程 =====
        log.info("[组合嵌套] 外层主流程开始");

        // 子流程1：REQUIRES_NEW - 独立事务（日志记录）
        log.info("[组合嵌套] 子流程1: REQUIRES_NEW 日志记录");
        Tx.builder()
                .writable()
                .propagation(Propagation.REQUIRES_NEW)  // 独立事务
                .timeout(10)
                .executeWithoutResult(() -> {
                    transactionLogRepository.save(TransactionLog.builder()
                            .operation("COMBINED_DEMO")
                            .status("START")
                            .detail("userId=" + userId)
                            .createTime(LocalDateTime.now())
                            .build());
                });
        result.put("子流程1", "REQUIRES_NEW - 日志记录成功（独立事务）");

        // 子流程2：NESTED - 嵌套事务（可回滚到 Savepoint）
        log.info("[组合嵌套] 子流程2: NESTED 业务处理");
        try {
            Tx.builder()
                    .writable()
                    .propagation(Propagation.NESTED)  // 嵌套事务
                    .timeout(10)
                    .executeWithoutResult(() -> {
                        User user = userRepository.findById(userId).orElseThrow();
                        user.setStatus(3);  // 禁用用户
                        userRepository.save(user);

                        // 模拟业务异常
                        if (user.getId() > 0) {
                            throw new RuntimeException("模拟业务异常，回滚到 Savepoint");
                        }
                    });
        } catch (Exception e) {
            result.put("子流程2", "NESTED - 回滚到 Savepoint，原因: " + e.getMessage());
            log.info("[组合嵌套] 子流程2 回滚，外层继续");
        }

        // 子流程3：REQUIRES_NEW - 独立事务（结束日志）
        log.info("[组合嵌套] 子流程3: REQUIRES_NEW 结束日志");
        Tx.builder()
                .writable()
                .propagation(Propagation.REQUIRES_NEW)  // 独立事务
                .timeout(10)
                .executeWithoutResult(() -> {
                    transactionLogRepository.save(TransactionLog.builder()
                            .operation("COMBINED_DEMO")
                            .status("END")
                            .detail("userId=" + userId)
                            .createTime(LocalDateTime.now())
                            .build());
                });
        result.put("子流程3", "REQUIRES_NEW - 结束日志记录成功（独立事务）");

        // ===== 外层：提交 =====
        log.info("[组合嵌套] 外层主流程提交");
        result.put("外层", "主流程正常提交");

        long cost = System.currentTimeMillis() - start;
        result.put("耗时", cost + "ms");
        result.put("说明", "REQUIRES_NEW 用于日志（独立），NESTED 用于业务（可回滚）");

        return result;
    }

    // ========================================
    // 辅助方法
    // ========================================

    private void simulateRemoteCall(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
