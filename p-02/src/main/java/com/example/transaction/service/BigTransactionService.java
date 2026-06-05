package com.example.transaction.service;

import com.example.transaction.entity.Account;
import com.example.transaction.entity.TransactionLog;
import com.example.transaction.entity.User;
import com.example.transaction.jpa.repository.AccountRepository;
import com.example.transaction.jpa.repository.TransactionLogRepository;
import com.example.transaction.jpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ========================================
 * 大事务处理实战演示
 * ========================================
 *
 * 生产环境大事务的核心危害：
 *
 * 1. 【连接池耗尽】一个事务独占一个数据库连接，大事务长时间持有 → 其他请求拿不到连接
 * 2. 【锁竞争】事务内 UPDATE/SELECT FOR UPDATE 长时间持有行锁 → 其他事务等待超时
 * 3. 【Undo Log 膨胀】InnoDB 的 MVCC 依赖 undo log，长事务导致旧版本无法回收 → 磁盘膨胀
 * 4. 【主从延迟】大事务 binlog 传输慢 → 从库延迟增大
 * 5. 【级联回滚】事务越大，回滚代价越高，回滚时间可能远超执行时间
 *
 * ====================
 * 核心解决策略
 * ====================
 *
 * | 策略               | 适用场景                         | 核心思路                          |
 * |-------------------|---------------------------------|-----------------------------------|
 * | 非DB操作外提        | 事务内有RPC/文件/计算               | 先准备数据，再开事务只做DB操作        |
 * | 分片提交            | 批量操作（>1000条）                | 每N条一个事务，避免单事务过长          |
 * | 超时控制            | 所有写事务                        | @Transactional(timeout) 兜底       |
 * | 异步处理            | 非实时要求的批量操作                | 提交任务后立即返回，后台慢慢处理       |
 * | 只读分离            | 查询/报表                        | readOnly=true + 路由从库            |
 * | 缩小锁范围          | 需要加锁的更新操作                  | 延迟 SELECT FOR UPDATE，缩小锁粒度  |
 * | 编程式精确控制       | 事务边界需要灵活控制                | TransactionTemplate 代码块级别控制   |
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BigTransactionService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionLogRepository transactionLogRepository;
    private final PlatformTransactionManager transactionManager;
    private final TransactionTemplate transactionTemplate;

    // 异步线程池
    private final ExecutorService asyncExecutor = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    // ========================================
    // 1. 反模式：典型的大事务问题演示
    // ========================================

    /**
     * 【反模式】事务中包含远程调用
     *
     * 错误示范：在事务里调 RPC / 发邮件 / 读写文件
     * 后果：这些操作可能耗时数秒，整个事务期间一直持有连接和锁
     */
    @Transactional
    public Map<String, Object> antiPattern_RemoteCallInTx(Long userId) {
        long start = System.currentTimeMillis();
        log.info("[反模式-远程调用] 事务开始，持有数据库连接...");

        // 1. 查询用户
        User user = userRepository.findById(userId).orElseThrow();
        log.info("[反模式-远程调用] 查询用户完成，耗时 {}ms", System.currentTimeMillis() - start);

        // 2. 模拟远程调用（在事务中！连接一直被占用！）
        long rpcStart = System.currentTimeMillis();
        simulateRemoteCall(3000);
        log.info("[反模式-远程调用] RPC调用完成，耗时 {}ms ← 这3秒连接被白白占用！",
                System.currentTimeMillis() - rpcStart);

        // 3. 更新用户
        user.setStatus(2);
        userRepository.save(user);

        // 4. 写日志
        saveLog("ANTI_PATTERN_RPC", "SUCCESS", "事务中包含RPC调用");

        long cost = System.currentTimeMillis() - start;
        log.info("[反模式-远程调用] 事务提交，总耗时 {}ms（其中3秒是RPC白耗的）", cost);
        return Map.of("pattern", "反模式: 事务中包含远程调用", "totalMs", cost, "rpcMs", 3000);
    }

    /**
     * 【正确】先做远程调用，再开事务
     *
     * 核心原则：事务里只放 DB 操作，所有非 DB 操作在事务外完成
     */
    public Map<String, Object> correct_RemoteCallOutsideTx(Long userId) {
        long start = System.currentTimeMillis();

        // 1. 【事务外】先做远程调用
        long rpcStart = System.currentTimeMillis();
        String rpcResult = simulateRemoteCallWithResult(3000);
        log.info("[正确-远程调用外提] RPC调用完成(事务外)，耗时 {}ms", System.currentTimeMillis() - rpcStart);

        // 2. 【事务内】只做 DB 操作
        long txStart = System.currentTimeMillis();
        doInTransaction(userId, rpcResult);
        long txCost = System.currentTimeMillis() - txStart;

        long cost = System.currentTimeMillis() - start;
        log.info("[正确-远程调用外提] 完成，总耗时 {}ms，事务仅占 {}ms", cost, txCost);
        return Map.of("pattern", "正确: 远程调用外提", "totalMs", cost, "txMs", txCost, "rpcMs", 3000);
    }

    @Transactional
    public void doInTransaction(Long userId, String rpcResult) {
        User user = userRepository.findById(userId).orElseThrow();
        user.setStatus(2);
        userRepository.save(user);
        saveLog("CORRECT_RPC_OUTSIDE", "SUCCESS", "RPC结果: " + rpcResult);
    }

    // ========================================
    // 2. 反模式：事务中包含大量循环查询（N+1）
    // ========================================

    /**
     * 【反模式】循环中逐条查询 + 更新
     * 每条记录一次 SELECT + UPDATE，N条记录就是 2N 次 SQL，事务时间线性增长
     */
    @Transactional(timeout = 60)
    public Map<String, Object> antiPattern_NPlus1InTx(List<Long> userIds) {
        long start = System.currentTimeMillis();
        log.info("[反模式-N+1] 开始处理 {} 个用户", userIds.size());

        int updated = 0;
        for (Long userId : userIds) {
            // 逐条查询 → 逐条更新 → 事务时间 = N * (查询+更新)时间
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                user.setStatus(2);
                userRepository.save(user);
                updated++;
            }
        }

        long cost = System.currentTimeMillis() - start;
        log.info("[反模式-N+1] 完成，更新 {} 条，耗时 {}ms", updated, cost);
        return Map.of("pattern", "反模式: N+1循环查询", "updated", updated, "totalMs", cost);
    }

    /**
     * 【正确】批量查询 + 批量更新，减少SQL次数
     */
    @Transactional(timeout = 30)
    public Map<String, Object> correct_BatchUpdateInTx(List<Long> userIds) {
        long start = System.currentTimeMillis();
        log.info("[正确-批量更新] 开始处理 {} 个用户", userIds.size());

        // 1. 一次性查出所有数据
        List<User> users = userRepository.findAllById(userIds);
        log.info("[正确-批量更新] 批量查询完成，查到 {} 条", users.size());

        // 2. 内存中修改
        users.forEach(u -> u.setStatus(2));

        // 3. 批量保存
        userRepository.saveAll(users);

        long cost = System.currentTimeMillis() - start;
        log.info("[正确-批量更新] 完成，更新 {} 条，耗时 {}ms", users.size(), cost);
        return Map.of("pattern", "正确: 批量查询+更新", "updated", users.size(), "totalMs", cost);
    }

    // ========================================
    // 3. 分片提交：将大事务拆成小事务
    // ========================================

    /**
     * 分片提交 - 核心模式
     *
     * 将 10000 条数据按每 500 条一批，每批独立事务
     * 优势：
     * - 每个事务只持有连接很短时间 → 不阻塞其他请求
     * - 某批失败只回滚当前批次 → 不影响已提交的批次
     * - Undo Log 及时回收 → 不膨胀
     *
     * 劣势：
     * - 不是原子操作，可能部分成功部分失败
     * - 需要业务层处理"部分成功"的情况
     */
    public Map<String, Object> chunkedCommit(List<User> users, int chunkSize) {
        long start = System.currentTimeMillis();
        log.info("[分片提交] 开始，共 {} 条，每片 {} 条", users.size(), chunkSize);

        int totalSuccess = 0;
        int totalFail = 0;
        List<Map<String, Object>> chunkResults = new ArrayList<>();

        for (int i = 0; i < users.size(); i += chunkSize) {
            int chunkIndex = i / chunkSize + 1;
            List<User> chunk = users.subList(i, Math.min(i + chunkSize, users.size()));

            // 每个分片独立事务
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setName("chunk-tx-" + chunkIndex);
            def.setTimeout(30); // 每个分片独立超时
            TransactionStatus status = transactionManager.getTransaction(def);

            long chunkStart = System.currentTimeMillis();
            try {
                userRepository.saveAll(chunk);
                transactionManager.commit(status);
                long chunkCost = System.currentTimeMillis() - chunkStart;
                totalSuccess += chunk.size();
                chunkResults.add(Map.of(
                        "chunk", chunkIndex,
                        "count", chunk.size(),
                        "ms", chunkCost,
                        "status", "SUCCESS"
                ));
                log.info("[分片提交] 第 {} 片完成，{} 条，耗时 {}ms", chunkIndex, chunk.size(), chunkCost);
            } catch (Exception e) {
                transactionManager.rollback(status);
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
        log.info("[分片提交] 全部完成，成功 {} 条，失败 {} 条，总耗时 {}ms",
                totalSuccess, totalFail, cost);

        return Map.of(
                "pattern", "分片提交",
                "totalUsers", users.size(),
                "chunkSize", chunkSize,
                "totalSuccess", totalSuccess,
                "totalFail", totalFail,
                "totalMs", cost,
                "chunks", chunkResults
        );
    }

    // ========================================
    // 4. 超时控制：防止事务无限期持有
    // ========================================

    /**
     * 超时控制策略
     *
     * 三层超时防护：
     * 1. @Transactional(timeout = N)  → Spring 层面超时
     * 2. 数据库 innodb_lock_wait_timeout → 锁等待超时（默认50s）
     * 3. 连接池 maxLifetime → 连接最大生命周期
     */
    @Transactional(timeout = 3) // 3秒超时
    public Map<String, Object> timeoutControlDemo(Long userId, int simulateWorkSeconds) {
        long start = System.currentTimeMillis();
        log.info("[超时控制] 事务开始，超时设置 3 秒");

        try {
            User user = userRepository.findById(userId).orElseThrow();

            // 模拟业务耗时
            if (simulateWorkSeconds > 0) {
                log.info("[超时控制] 模拟耗时操作 {} 秒...", simulateWorkSeconds);
                Thread.sleep(simulateWorkSeconds * 1000L);
            }

            user.setStatus(3);
            userRepository.save(user);

            long cost = System.currentTimeMillis() - start;
            return Map.of("status", "SUCCESS", "txMs", cost);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long cost = System.currentTimeMillis() - start;
            return Map.of("status", "INTERRUPTED", "txMs", cost);
        }
    }

    /**
     * 编程式事务 - 精确控制超时
     * 适合需要根据业务参数动态调整超时的场景
     */
    public Map<String, Object> programmaticTimeoutDemo(int timeoutSeconds) {
        long start = System.currentTimeMillis();
        log.info("[编程式超时] 动态超时 {} 秒", timeoutSeconds);

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setTimeout(timeoutSeconds);
        def.setName("dynamic-timeout-tx");

        TransactionStatus status = transactionManager.getTransaction(def);
        try {
            // 业务操作
            long txAge = System.currentTimeMillis() - start;
            transactionManager.commit(status);
            return Map.of("status", "SUCCESS", "timeout", timeoutSeconds, "txMs", txAge);
        } catch (Exception e) {
            transactionManager.rollback(status);
            return Map.of("status", "FAIL", "timeout", timeoutSeconds, "error", e.getMessage());
        }
    }

    // ========================================
    // 5. 缩小事务范围：延迟到真正需要时才开事务
    // ========================================

    /**
     * 【反模式】整个方法都包裹在事务里
     * 即使只是查询，也开着事务占用连接
     */
    @Transactional
    public Map<String, Object> antiPattern_FullMethodTx(Long userId, BigDecimal amount) {
        long start = System.currentTimeMillis();

        // 1. 查询用户（不需要事务！）
        User user = userRepository.findById(userId).orElseThrow();

        // 2. 查询账户（不需要事务！）
        Account account = accountRepository.findByUserId(userId).orElseThrow();

        // 3. 余额校验（不需要事务！）
        if (account.getBalance().compareTo(amount) < 0) {
            return Map.of("status", "FAIL", "reason", "余额不足");
        }

        // 4. 只有这一步真正需要事务
        accountRepository.updateBalance(account.getId(), amount.negate());

        long cost = System.currentTimeMillis() - start;
        log.info("[反模式-全方法事务] 耗时 {}ms（查询和校验也占了事务时间）", cost);
        return Map.of("pattern", "反模式: 全方法事务", "txMs", cost);
    }

    /**
     * 【正确】只在写操作时开事务，查询和校验在事务外
     */
    public Map<String, Object> correct_MinimalTxScope(Long userId, BigDecimal amount) {
        long start = System.currentTimeMillis();

        // 1. 【事务外】查询 - 不占用事务连接
        User user = userRepository.findById(userId).orElseThrow();
        Account account = accountRepository.findByUserId(userId).orElseThrow();

        // 2. 【事务外】业务校验 - 不占用事务连接
        if (account.getBalance().compareTo(amount) < 0) {
            return Map.of("status", "FAIL", "reason", "余额不足");
        }

        // 3. 【事务内】只包含必须的写操作
        long txStart = System.currentTimeMillis();
        transactionTemplate.executeWithoutResult(status -> {
            accountRepository.updateBalance(account.getId(), amount.negate());
            saveLog("DEDUCT_BALANCE", "SUCCESS",
                    "userId=" + userId + ", amount=" + amount.negate());
        });
        long txCost = System.currentTimeMillis() - txStart;

        long cost = System.currentTimeMillis() - start;
        log.info("[正确-最小事务范围] 总耗时 {}ms，事务仅占 {}ms", cost, txCost);
        return Map.of("pattern", "正确: 最小事务范围", "totalMs", cost, "txMs", txCost);
    }

    // ========================================
    // 6. 锁范围优化：延迟加锁
    // ========================================

    /**
     * 【反模式】事务一开始就加锁
     * SELECT ... FOR UPDATE 太早 → 锁持有时间 = 整个事务时间
     */
    @Transactional(timeout = 10)
    public Map<String, Object> antiPattern_EarlyLock(Long accountId, BigDecimal amount) {
        long start = System.currentTimeMillis();

        // 1. 一上来就加锁 ← 锁持有时间最大化
        Account account = accountRepository.findById(accountId).orElseThrow();
        log.info("[反模式-早加锁] 获取锁，已持有 {}ms", System.currentTimeMillis() - start);

        // 2. 业务校验（锁一直被持有，其他事务在等待！）
        simulateBusinessLogic(2000);

        // 3. 更新操作
        accountRepository.updateBalance(accountId, amount.negate());

        long cost = System.currentTimeMillis() - start;
        log.info("[反模式-早加锁] 事务结束，锁持有 {}ms", cost);
        return Map.of("pattern", "反模式: 事务开始就加锁", "lockHoldMs", cost);
    }

    /**
     * 【正确】延迟加锁：先做不依赖锁的操作，最后才加锁
     * 缩短锁持有时间 → 减少其他事务等待
     */
    @Transactional(timeout = 10)
    public Map<String, Object> correct_DeferredLock(Long accountId, BigDecimal amount) {
        long start = System.currentTimeMillis();

        // 1. 【事务内但不加锁】可以先做其他不冲突的DB操作
        saveLog("DEFERRED_LOCK", "PROCESSING", "accountId=" + accountId);
        log.info("[正确-延迟加锁] 先做不需要锁的操作，耗时 {}ms",
                System.currentTimeMillis() - start);

        // 2. 【事务内但不加锁】业务校验前的准备工作
        simulateBusinessLogic(2000);

        // 3. 【最后才加锁】只在实际修改前才获取锁
        long lockStart = System.currentTimeMillis();
        Account account = accountRepository.findById(accountId).orElseThrow();
        long lockHoldMs = System.currentTimeMillis() - lockStart;

        // 4. 立即更新并提交 → 锁持有时间最短
        accountRepository.updateBalance(accountId, amount.negate());

        long cost = System.currentTimeMillis() - start;
        log.info("[正确-延迟加锁] 事务结束，总耗时 {}ms，锁仅持有 {}ms", cost, lockHoldMs);
        return Map.of("pattern", "正确: 延迟加锁", "totalMs", cost, "lockHoldMs", lockHoldMs);
    }

    // ========================================
    // 7. 异步处理：不重要的操作丢到后台
    // ========================================

    /**
     * 同步处理所有操作 → 大事务
     * 主业务 + 记日志 + 发通知 全在一个事务里
     */
    @Transactional
    public Map<String, Object> antiPattern_SyncAllInTx(Long userId) {
        long start = System.currentTimeMillis();

        // 1. 核心业务：更新用户
        User user = userRepository.findById(userId).orElseThrow();
        user.setStatus(2);
        userRepository.save(user);
        log.info("[反模式-同步全包] 核心业务完成，耗时 {}ms", System.currentTimeMillis() - start);

        // 2. 非核心：写操作日志（在事务内！不必要！）
        saveLog("USER_UPDATE", "SUCCESS", "userId=" + userId);

        // 3. 非核心：模拟发通知（在事务内！浪费！）
        simulateNotification(1000);

        long cost = System.currentTimeMillis() - start;
        log.info("[反模式-同步全包] 事务结束，总耗时 {}ms（通知白占1秒）", cost);
        return Map.of("pattern", "反模式: 所有操作同步在事务里", "txMs", cost);
    }

    /**
     * 【正确】核心操作在事务里，非核心操作异步执行
     * 事务只管核心数据一致性，日志和通知交给异步
     */
    public Map<String, Object> correct_AsyncNonCore(Long userId) {
        long start = System.currentTimeMillis();

        // 1. 【事务内】只做核心业务
        long txStart = System.currentTimeMillis();
        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findById(userId).orElseThrow();
            user.setStatus(2);
            userRepository.save(user);
        });
        long txCost = System.currentTimeMillis() - txStart;
        log.info("[正确-异步非核心] 核心事务完成，耗时 {}ms", txCost);

        // 2. 【事务外-异步】日志和通知丢到后台
        asyncExecutor.submit(() -> {
            try {
                saveLogInNewTx("USER_UPDATE_ASYNC", "SUCCESS", "userId=" + userId);
                simulateNotification(1000);
                log.info("[异步] 日志+通知完成");
            } catch (Exception e) {
                log.error("[异步] 日志/通知失败: {}", e.getMessage());
            }
        });

        long cost = System.currentTimeMillis() - start;
        log.info("[正确-异步非核心] 接口返回，总耗时 {}ms，事务仅占 {}ms", cost, txCost);
        return Map.of("pattern", "正确: 非核心操作异步", "totalMs", cost, "txMs", txCost);
    }

    // ========================================
    // 8. 大事务监控：实时统计事务耗时
    // ========================================

    /**
     * 事务耗时监控器
     *
     * 生产环境建议：
     * 1. 使用 Micrometer + Prometheus 监控事务耗时
     * 2. 设置告警：事务 > 1s 为 WARNING，> 5s 为 CRITICAL
     * 3. 开启 HikariCP 的 leakDetectionThreshold 检测连接泄露
     * 4. MySQL: SHOW ENGINE INNODB STATUS 查看当前事务
     * 5. MySQL: information_schema.INNODB_TRX 查看长事务
     */
    public Map<String, Object> monitorTransactionStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("连接池配置", Map.of(
                "maximumPoolSize", 20,
                "minimumIdle", 5,
                "建议leakDetectionThreshold", "30000(30s)",
                "说明", "超过此时间的连接借用将被记录为潜在泄露"
        ));

        stats.put("MySQL长事务排查SQL", List.of(
                "-- 1. 查看当前运行的所有事务",
                "SELECT * FROM information_schema.INNODB_TRX ORDER BY trx_started ASC;",
                "",
                "-- 2. 查看锁等待",
                "SELECT * FROM information_schema.INNODB_LOCK_WAITS;",
                "",
                "-- 3. 查看锁信息",
                "SELECT * FROM information_schema.INNODB_LOCKS;",
                "",
                "-- 4. 查看事务持有锁的时长",
                "SELECT trx_id, trx_state, trx_started, " +
                        "TIMESTAMPDIFF(SECOND, trx_started, NOW()) AS duration_seconds " +
                        "FROM information_schema.INNODB_TRX;",
                "",
                "-- 5. 杀掉长事务（慎用）",
                "KILL <thread_id>;"
        ));

        stats.put("事务耗时建议阈值", Map.of(
                "正常事务", "< 200ms",
                "需要关注", "200ms - 1s",
                "WARNING", "1s - 5s",
                "CRITICAL", "> 5s",
                "说明", "超过5秒的事务必须优化"
        ));

        stats.put("HikariCP配置建议", Map.of(
                "maxLifetime", "1800000(30min)",
                "idleTimeout", "600000(10min)",
                "connectionTimeout", "30000(30s)",
                "leakDetectionThreshold", "30000(30s)",
                "说明", "leakDetectionThreshold 用于检测连接是否被长时间持有不释放"
        ));

        return stats;
    }

    // ========================================
    // 9. 综合对比：同一场景的不同实现
    // ========================================

    /**
     * 综合演示：用户批量状态更新
     * 对比三种实现的事务持有时间
     */
    public Map<String, Object> comprehensiveComparison(int userCount) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("说明", "同一业务场景(批量更新" + userCount + "个用户状态)的三种实现对比");

        // 准备测试用户
        List<User> testUsers = new ArrayList<>();
        for (int i = 0; i < userCount; i++) {
            testUsers.add(User.builder()
                    .username("cmp_" + System.currentTimeMillis() + "_" + i)
                    .email("cmp" + i + "@test.com")
                    .status(1)
                    .build());
        }

        // 方案1：单大事务
        long start1 = System.currentTimeMillis();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                for (User user : testUsers) {
                    userRepository.save(user);
                }
            });
        } catch (Exception e) {
            log.error("方案1失败: {}", e.getMessage());
        }
        long cost1 = System.currentTimeMillis() - start1;

        // 方案2：分片提交
        int chunkSize = Math.max(10, userCount / 5);
        long start2 = System.currentTimeMillis();
        AtomicInteger success2 = new AtomicInteger(0);
        for (int i = 0; i < testUsers.size(); i += chunkSize) {
            List<User> chunk = testUsers.subList(i, Math.min(i + chunkSize, testUsers.size()));
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    userRepository.saveAll(chunk);
                });
                success2.addAndGet(chunk.size());
            } catch (Exception e) {
                log.error("方案2分片失败: {}", e.getMessage());
            }
        }
        long cost2 = System.currentTimeMillis() - start2;

        result.put("方案1_单大事务", Map.of(
                "txMs", cost1,
                "事务数", 1,
                "风险", "连接占用时间长，回滚代价高"
        ));

        result.put("方案2_分片提交", Map.of(
                "txMs", cost2,
                "事务数", (testUsers.size() + chunkSize - 1) / chunkSize,
                "chunkSize", chunkSize,
                "success", success2.get(),
                "优势", "连接占用时间短，回滚代价低"
        ));

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

    private String simulateRemoteCallWithResult(int millis) {
        simulateRemoteCall(millis);
        return "rpc_result_" + System.currentTimeMillis();
    }

    private void simulateBusinessLogic(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void simulateNotification(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void saveLog(String operation, String status, String detail) {
        transactionLogRepository.save(TransactionLog.builder()
                .operation(operation)
                .status(status)
                .detail(detail)
                .createTime(LocalDateTime.now())
                .build());
    }

    /**
     * 在新事务中保存日志（用于异步场景，调用时没有事务上下文）
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void saveLogInNewTx(String operation, String status, String detail) {
        transactionLogRepository.save(TransactionLog.builder()
                .operation(operation)
                .status(status)
                .detail(detail)
                .createTime(LocalDateTime.now())
                .build());
    }
}
