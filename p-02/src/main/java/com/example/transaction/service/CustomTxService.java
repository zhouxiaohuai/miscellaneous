package com.example.transaction.service;

import com.example.transaction.entity.Account;
import com.example.transaction.entity.TransactionLog;
import com.example.transaction.entity.User;
import com.example.transaction.framework.Tx;
import com.example.transaction.jpa.repository.AccountRepository;
import com.example.transaction.jpa.repository.TransactionLogRepository;
import com.example.transaction.jpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义事务框架演示服务
 *
 * 对比 @Transactional 注解和 Tx 框架的使用方式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomTxService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionLogRepository transactionLogRepository;

    // ========================================
    // 1. 自调用事务：@Transactional 失效 vs Tx 正常
    // ========================================

    /**
     * 【反模式】@Transactional 自调用失效
     *
     * 内部方法 innerSave() 上的 @Transactional 不会生效
     * 因为 Spring AOP 代理无法拦截自调用
     */
    @Transactional
    public Map<String, Object> selfInvoke_AnnotationFail() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("说明", "@Transactional 自调用失效演示");

        // 调用内部方法 - @Transactional 失效
        try {
            innerSaveWithAnnotation();
            result.put("结果", "内部方法执行成功（但这正是问题所在：如果内部方法需要独立事务，@Transactional 做不到）");
        } catch (Exception e) {
            result.put("异常", e.getMessage());
        }

        return result;
    }

    /**
     * 【正确】使用 Tx 框架解决自调用问题
     *
     * Tx.readOnly() / Tx.writable() 是静态方法调用
     * 不依赖 AOP 代理，自调用也能正常工作
     */
    public Map<String, Object> selfInvoke_TxWork() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("说明", "Tx 框架解决自调用问题");

        // 1. 外层使用只读事务查询
        List<User> users = Tx.readOnly(() -> userRepository.findAll());
        result.put("查询用户数", users.size());

        // 2. 内层使用读写事务更新（独立事务，不受外层影响）
        Tx.writable(() -> {
            User user = User.builder()
                    .username("self_invoke_tx_" + System.currentTimeMillis())
                    .email("self_invoke@test.com")
                    .status(1)
                    .build();
            userRepository.save(user);
            log.info("[Tx自调用] 用户保存成功: {}", user.getUsername());
        });

        result.put("结果", "Tx 框架自调用正常工作，内部事务独立生效");
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void innerSaveWithAnnotation() {
        User user = User.builder()
                .username("inner_save_" + System.currentTimeMillis())
                .email("inner@test.com")
                .status(1)
                .build();
        userRepository.save(user);
    }

    // ========================================
    // 2. 最小事务范围：只在写操作时开启事务
    // ========================================

    /**
     * 【反模式】整个方法都在事务里
     *
     * 查询、校验、远程调用全在事务中，连接占用时间长
     */
    @Transactional
    public Map<String, Object> minimalScope_Annotation(Long userId, BigDecimal amount) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 查询（在事务中，不需要！）
        User user = userRepository.findById(userId).orElseThrow();
        Account account = accountRepository.findByUserId(userId).orElseThrow();

        // 2. 校验（在事务中，不需要！）
        if (account.getBalance().compareTo(amount) < 0) {
            result.put("状态", "余额不足");
            return result;
        }

        // 3. 模拟远程调用（在事务中，连接被白白占用！）
        simulateRemoteCall(1000);

        // 4. 写操作（真正需要事务的部分）
        accountRepository.updateBalance(account.getId(), amount.negate());

        long cost = System.currentTimeMillis() - start;
        result.put("模式", "反模式: @Transactional 全方法事务");
        result.put("总耗时", cost + "ms");
        result.put("说明", "查询+校验+远程调用 全在事务中，连接占用 " + cost + "ms");

        return result;
    }

    /**
     * 【正确】使用 Tx 框架实现最小事务范围
     *
     * 查询和校验在事务外，只在写操作时开启事务
     * 连接占用时间大幅缩短
     */
    public Map<String, Object> minimalScope_Tx(Long userId, BigDecimal amount) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 【事务外】查询 - 不占用事务连接
        User user = userRepository.findById(userId).orElseThrow();
        Account account = accountRepository.findByUserId(userId).orElseThrow();

        // 2. 【事务外】校验 - 不占用事务连接
        if (account.getBalance().compareTo(amount) < 0) {
            result.put("状态", "余额不足");
            return result;
        }

        // 3. 【事务外】远程调用 - 不占用事务连接
        simulateRemoteCall(1000);

        // 4. 【事务内】只包含写操作
        long txStart = System.currentTimeMillis();
        Tx.writable(() -> {
            accountRepository.updateBalance(account.getId(), amount.negate());
            transactionLogRepository.save(TransactionLog.builder()
                    .operation("DEDUCT")
                    .status("SUCCESS")
                    .detail("userId=" + userId + ", amount=" + amount)
                    .createTime(LocalDateTime.now())
                    .build());
        });
        long txCost = System.currentTimeMillis() - txStart;

        long totalCost = System.currentTimeMillis() - start;
        result.put("模式", "正确: Tx 最小事务范围");
        result.put("总耗时", totalCost + "ms");
        result.put("事务耗时", txCost + "ms");
        result.put("说明", "查询+校验+远程调用在事务外，事务仅占 " + txCost + "ms");

        return result;
    }

    // ========================================
    // 3. 只读 vs 读写事务对比
    // ========================================

    /**
     * 只读事务演示
     *
     * 使用 Tx.readOnly() 开启只读事务
     * 数据库可做优化：不加写锁、不记录 undo log
     */
    public Map<String, Object> readOnlyDemo() {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        // 只读事务 - 查询所有用户
        List<User> users = Tx.readOnly(() -> userRepository.findAll());

        // 只读事务 - 有返回值查询
        long count = Tx.readOnly(() -> userRepository.findAll().stream().count());

        long cost = System.currentTimeMillis() - start;
        result.put("模式", "Tx 只读事务");
        result.put("查询用户数", users.size());
        result.put("耗时", cost + "ms");
        result.put("优势", "数据库可做优化：不加写锁、不记录 undo log");
        result.put("适用场景", "查询、报表、数据导出");

        return result;
    }

    /**
     * 读写事务演示
     *
     * 使用 Tx.writable() 开启读写事务
     */
    public Map<String, Object> writableDemo() {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        // 读写事务 - 创建用户
        User saved = Tx.writable(() -> {
            User user = User.builder()
                    .username("writable_demo_" + System.currentTimeMillis())
                    .email("writable@test.com")
                    .status(1)
                    .build();
            return userRepository.save(user);
        });

        long cost = System.currentTimeMillis() - start;
        result.put("模式", "Tx 读写事务");
        result.put("保存用户", saved.getUsername());
        result.put("耗时", cost + "ms");
        result.put("适用场景", "INSERT、UPDATE、DELETE");

        return result;
    }

    // ========================================
    // 4. 链式 Builder：动态超时 + 传播行为
    // ========================================

    /**
     * 链式 Builder 演示
     *
     * 使用 Tx.builder() 链式配置事务属性
     */
    public Map<String, Object> builderDemo(Long userId) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        // 链式配置：只读 + 5秒超时
        User user = Tx.builder()
                .readOnly()
                .timeout(5)
                .execute(() -> userRepository.findById(userId).orElseThrow());

        // 链式配置：读写 + 10秒超时 + REQUIRES_NEW 传播行为
        User saved = Tx.builder()
                .writable()
                .timeout(10)
                .propagation(Propagation.REQUIRES_NEW)
                .execute(() -> {
                    User newUser = User.builder()
                            .username("builder_demo_" + System.currentTimeMillis())
                            .email("builder@test.com")
                            .status(1)
                            .build();
                    return userRepository.save(newUser);
                });

        long cost = System.currentTimeMillis() - start;
        result.put("模式", "Tx Builder 链式配置");
        result.put("查询用户", user.getUsername());
        result.put("保存用户", saved.getUsername());
        result.put("耗时", cost + "ms");
        result.put("优势", "灵活配置：超时、传播行为、隔离级别");

        return result;
    }

    // ========================================
    // 5. 完整业务流程：方法内任意位置开启事务
    // ========================================

    /**
     * 完整业务流程演示
     *
     * 展示如何在方法的任意位置开启事务
     * 查询、校验、远程调用在事务外，只在写操作时开启事务
     */
    public Map<String, Object> completeFlowDemo(Long userId, BigDecimal amount) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("说明", "完整业务流程：方法内任意位置开启事务");

        // ===== 步骤1：事务外查询 =====
        log.info("[完整流程] 步骤1: 事务外查询");
        User user = Tx.readOnly(() -> userRepository.findById(userId).orElseThrow());
        Account account = Tx.readOnly(() -> accountRepository.findByUserId(userId).orElseThrow());
        result.put("步骤1", "事务外查询 - 用户: " + user.getUsername() + ", 余额: " + account.getBalance());

        // ===== 步骤2：事务外校验 =====
        log.info("[完整流程] 步骤2: 事务外校验");
        if (account.getBalance().compareTo(amount) < 0) {
            result.put("状态", "余额不足");
            return result;
        }
        result.put("步骤2", "事务外校验 - 余额充足");

        // ===== 步骤3：事务外远程调用 =====
        log.info("[完整流程] 步骤3: 事务外远程调用");
        simulateRemoteCall(500);
        result.put("步骤3", "事务外远程调用完成");

        // ===== 步骤4：事务内写操作 =====
        log.info("[完整流程] 步骤4: 事务内写操作");
        long txStart = System.currentTimeMillis();
        Tx.writable(() -> {
            // 扣款
            accountRepository.updateBalance(account.getId(), amount.negate());

            // 记录日志
            transactionLogRepository.save(TransactionLog.builder()
                    .operation("DEDUCT")
                    .status("SUCCESS")
                    .detail("userId=" + userId + ", amount=" + amount)
                    .createTime(LocalDateTime.now())
                    .build());

            log.info("[完整流程] 步骤4完成: 扣款 {} 元", amount);
        });
        long txCost = System.currentTimeMillis() - txStart;
        result.put("步骤4", "事务内写操作完成，事务耗时 " + txCost + "ms");

        // ===== 步骤5：事务外异步通知（模拟） =====
        log.info("[完整流程] 步骤5: 事务外异步通知");
        result.put("步骤5", "事务外异步通知（模拟）");

        long totalCost = System.currentTimeMillis() - start;
        result.put("总耗时", totalCost + "ms");
        result.put("事务耗时", txCost + "ms");
        result.put("优化效果", "事务仅占总耗时的 " + (txCost * 100 / totalCost) + "%");

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
