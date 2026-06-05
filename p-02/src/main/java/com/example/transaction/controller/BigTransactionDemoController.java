package com.example.transaction.controller;

import com.example.transaction.entity.User;
import com.example.transaction.service.BatchTransactionService;
import com.example.transaction.service.BigTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * ========================================
 * 大事务处理完整演示
 * ========================================
 *
 * 核心目标：演示如何处理大事务，控制事务时间，保证生产数据不会很慢
 *
 * 演示内容：
 * 1. 大事务问题演示（反模式）
 * 2. 分片提交策略 - 将大事务拆成小事务
 * 3. 超时控制策略 - 防止事务无限期持有
 * 4. 异步处理策略 - 非核心操作丢到后台
 * 5. 生产环境优化建议
 */
@Slf4j
@RestController
@RequestMapping("/api/big-tx-demo")
@RequiredArgsConstructor
public class BigTransactionDemoController {

    private final BigTransactionService bigTxService;
    private final BatchTransactionService batchService;

    // ========================================
    // 1. 大事务问题演示（反模式）
    // ========================================

    /**
     * 演示1：大事务的典型问题
     *
     * GET /api/big-tx-demo/problem-demo?userId=1
     *
     * 演示大事务的三大问题：
     * 1. 事务中包含远程调用 → 连接被白白占用
     * 2. N+1循环查询 → 事务时间线性增长
     * 3. 全方法事务 → 查询也占用事务时间
     */
    @GetMapping("/problem-demo")
    public Map<String, Object> problemDemo(@RequestParam Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("说明", "大事务的典型问题演示");

        // 问题1：事务中包含远程调用
        log.info("=== 演示问题1：事务中包含远程调用 ===");
        Map<String, Object> rpcResult = bigTxService.antiPattern_RemoteCallInTx(userId);
        result.put("问题1_事务中RPC", rpcResult);

        // 问题2：N+1循环查询
        log.info("=== 演示问题2：N+1循环查询 ===");
        List<Long> userIds = Arrays.asList(userId, userId + 1, userId + 2);
        Map<String, Object> n1Result = bigTxService.antiPattern_NPlus1InTx(userIds);
        result.put("问题2_N+1循环", n1Result);

        // 问题3：全方法事务
        log.info("=== 演示问题3：全方法事务 ===");
        Map<String, Object> fullTxResult = bigTxService.antiPattern_FullMethodTx(userId, BigDecimal.valueOf(100));
        result.put("问题3_全方法事务", fullTxResult);

        return result;
    }

    // ========================================
    // 2. 分片提交策略演示
    // ========================================

    /**
     * 演示2：分片提交 - 将大事务拆成小事务
     *
     * GET /api/big-tx-demo/chunked-commit?totalCount=1000&chunkSize=100
     *
     * 核心思想：
     * - 将10000条数据按每500条一批，每批独立事务
     * - 每个事务只持有连接很短时间 → 不阻塞其他请求
     * - 某批失败只回滚当前批次 → 不影响已提交的批次
     */
    @GetMapping("/chunked-commit")
    public Map<String, Object> chunkedCommitDemo(
            @RequestParam(defaultValue = "1000") int totalCount,
            @RequestParam(defaultValue = "100") int chunkSize) {

        log.info("=== 分片提交演示：{}条数据，每片{}条 ===", totalCount, chunkSize);

        // 生成测试数据
        List<User> users = batchService.generateTestUsers(totalCount);

        // 执行分片提交
        Map<String, Object> result = bigTxService.chunkedCommit(users, chunkSize);
        result.put("演示说明", "将" + totalCount + "条数据按每" + chunkSize + "条分片提交");
        result.put("优势", Arrays.asList(
                "每个事务只持有连接很短时间",
                "某批失败只回滚当前批次",
                "Undo Log及时回收，不膨胀",
                "不会阻塞其他请求"
        ));

        return result;
    }

    // ========================================
    // 3. 超时控制策略演示
    // ========================================

    /**
     * 演示3：超时控制 - 防止事务无限期持有
     *
     * GET /api/big-tx-demo/timeout-control?userId=1&workSeconds=5
     *
     * 三层超时防护：
     * 1. @Transactional(timeout = N) → Spring层面超时
     * 2. 数据库 innodb_lock_wait_timeout → 锁等待超时
     * 3. 连接池 maxLifetime → 连接最大生命周期
     */
    @GetMapping("/timeout-control")
    public Map<String, Object> timeoutControlDemo(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "5") int workSeconds) {

        log.info("=== 超时控制演示：模拟耗时{}秒 ===", workSeconds);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("演示说明", "事务超时控制演示");
        result.put("超时设置", "3秒");
        result.put("模拟耗时", workSeconds + "秒");

        try {
            Map<String, Object> txResult = bigTxService.timeoutControlDemo(userId, workSeconds);
            result.put("执行结果", txResult);
            result.put("结论", "事务未超时，正常完成");
        } catch (Exception e) {
            result.put("执行结果", "事务超时回滚");
            result.put("异常信息", e.getMessage());
            result.put("结论", "事务超时，自动回滚，防止长时间占用连接");
        }

        result.put("超时防护层次", Arrays.asList(
                "1. Spring @Transactional(timeout=3) → 3秒超时",
                "2. MySQL innodb_lock_wait_timeout → 锁等待超时(默认50秒)",
                "3. HikariCP maxLifetime → 连接最大生命周期(建议30分钟)"
        ));

        return result;
    }

    /**
     * 演示3.1：编程式动态超时
     *
     * GET /api/big-tx-demo/dynamic-timeout?timeoutSeconds=10
     *
     * 适合需要根据业务参数动态调整超时的场景
     */
    @GetMapping("/dynamic-timeout")
    public Map<String, Object> dynamicTimeoutDemo(
            @RequestParam(defaultValue = "10") int timeoutSeconds) {

        log.info("=== 编程式动态超时演示：{}秒 ===", timeoutSeconds);

        Map<String, Object> result = bigTxService.programmaticTimeoutDemo(timeoutSeconds);
        result.put("演示说明", "编程式事务动态设置超时");
        result.put("适用场景", Arrays.asList(
                "批量操作：根据数据量动态调整超时",
                "不同业务：核心业务超时短，非核心超时长",
                "运维需求：根据系统负载动态调整"
        ));

        return result;
    }

    // ========================================
    // 4. 异步处理策略演示
    // ========================================

    /**
     * 演示4：异步处理 - 非核心操作丢到后台
     *
     * GET /api/big-tx-demo/async-processing?userId=1
     *
     * 核心思想：
     * - 事务只管核心数据一致性
     * - 日志、通知等非核心操作异步执行
     * - 事务时间大幅缩短
     */
    @GetMapping("/async-processing")
    public Map<String, Object> asyncProcessingDemo(@RequestParam Long userId) {

        log.info("=== 异步处理演示 ===");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("演示说明", "非核心操作异步处理");

        // 反模式：同步全包
        log.info("演示反模式：同步全包");
        Map<String, Object> syncResult = bigTxService.antiPattern_SyncAllInTx(userId);
        result.put("反模式_同步全包", syncResult);

        // 正确：异步非核心
        log.info("演示正确：异步非核心");
        Map<String, Object> asyncResult = bigTxService.correct_AsyncNonCore(userId);
        result.put("正确_异步非核心", asyncResult);

        result.put("对比总结", Arrays.asList(
                "反模式：核心业务+日志+通知 全在一个事务 → 事务时间长",
                "正确：核心业务在事务，日志通知异步 → 事务时间短",
                "优势：接口响应快，用户体验好，连接占用少"
        ));

        return result;
    }

    // ========================================
    // 5. 生产环境优化建议
    // ========================================

    /**
     * 演示5：生产环境优化建议
     *
     * GET /api/big-tx-demo/production-optimization
     *
     * 生产环境大事务优化的完整建议
     */
    @GetMapping("/production-optimization")
    public Map<String, Object> productionOptimizationDemo() {

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("生产环境大事务优化建议", "以下是完整的优化策略");

        // 1. 代码层面优化
        result.put("1_代码层面优化", Arrays.asList(
                "【事务最小化】只在真正需要事务的方法上加@Transactional",
                "【远程调用外提】RPC/HTTP/文件操作必须在事务外完成",
                "【批量操作优化】使用批量查询+批量更新，减少SQL次数",
                "【非核心异步】日志、通知等非核心操作异步执行",
                "【延迟加锁】SELECT FOR UPDATE 尽量靠近更新操作"
        ));

        // 2. 数据库层面优化
        result.put("2_数据库层面优化", Arrays.asList(
                "【索引优化】确保查询条件有合适索引，避免全表扫描",
                "【锁粒度优化】使用行锁而非表锁，减少锁冲突",
                "【批量提交】大批量数据分片提交，每片100-500条",
                "【读写分离】查询走从库，写操作走主库"
        ));

        // 3. 连接池配置优化
        Map<String, Object> poolConfig = new LinkedHashMap<>();
        poolConfig.put("maximumPoolSize", "20-50 (根据并发量调整)");
        poolConfig.put("minimumIdle", "5-10");
        poolConfig.put("maxLifetime", "1800000 (30分钟)");
        poolConfig.put("idleTimeout", "600000 (10分钟)");
        poolConfig.put("connectionTimeout", "30000 (30秒)");
        poolConfig.put("leakDetectionThreshold", "30000 (30秒)");
        result.put("3_连接池配置", poolConfig);

        // 4. 监控告警
        result.put("4_监控告警", Arrays.asList(
                "【事务耗时监控】正常<200ms，关注200ms-1s，警告1s-5s，严重>5s",
                "【连接池监控】活跃连接数、空闲连接数、等待线程数",
                "【长事务告警】超过5秒的事务必须优化",
                "【慢SQL告警】超过1秒的SQL必须优化"
        ));

        // 5. 应急处理
        result.put("5_应急处理", Arrays.asList(
                "【长事务排查】SELECT * FROM information_schema.INNODB_TRX ORDER BY trx_started",
                "【锁等待排查】SELECT * FROM information_schema.INNODB_LOCK_WAITS",
                "【杀长事务】KILL <thread_id> (慎用，可能导致数据不一致)",
                "【连接池耗尽】临时增大连接池，排查长事务根因"
        ));

        return result;
    }

    // ========================================
    // 6. 综合对比演示
    // ========================================

    /**
     * 演示6：综合对比 - 同一场景的不同实现
     *
     * GET /api/big-tx-demo/comprehensive-comparison?userCount=1000
     *
     * 对比三种实现：
     * 1. 单大事务 - 一次性提交
     * 2. 分片提交 - 每N条提交一次
     * 3. 每条单独事务 - 精确控制
     */
    @GetMapping("/comprehensive-comparison")
    public Map<String, Object> comprehensiveComparisonDemo(
            @RequestParam(defaultValue = "1000") int userCount) {

        log.info("=== 综合对比演示：{}条数据 ===", userCount);

        Map<String, Object> result = bigTxService.comprehensiveComparison(userCount);
        result.put("演示说明", "同一业务场景(批量更新" + userCount + "个用户状态)的三种实现对比");
        result.put("选择建议", Arrays.asList(
                "数据量小(<1000)：单事务批量插入",
                "数据量中等(1000-10000)：分批提交(每100-500条一批)",
                "数据量大(>10000)：分批提交 + 异步处理",
                "要求严格原子性：单事务(但要注意连接超时)"
        ));

        return result;
    }

    // ========================================
    // 7. 大事务处理完整流程演示
    // ========================================

    /**
     * 演示7：大事务处理完整流程
     *
     * GET /api/big-tx-demo/complete-flow?userId=1&amount=1000
     *
     * 模拟一个完整的业务场景：
     * 1. 查询用户信息（事务外）
     * 2. 远程调用获取汇率（事务外）
     * 3. 业务校验（事务外）
     * 4. 核心业务：扣款+记录日志（事务内）
     * 5. 非核心：发送通知（异步）
     */
    @GetMapping("/complete-flow")
    public Map<String, Object> completeFlowDemo(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1000") BigDecimal amount) {

        log.info("=== 大事务处理完整流程演示 ===");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("演示说明", "大事务处理完整流程");
        result.put("业务场景", "用户扣款操作");

        long start = System.currentTimeMillis();

        // 步骤1：查询用户信息（事务外）
        log.info("步骤1：查询用户信息（事务外）");
        // 模拟查询
        simulateStep(100);
        result.put("步骤1_查询用户", "事务外执行，不占用连接");

        // 步骤2：远程调用获取汇率（事务外）
        log.info("步骤2：远程调用获取汇率（事务外）");
        simulateStep(500);
        result.put("步骤2_远程调用", "事务外执行，不占用连接");

        // 步骤3：业务校验（事务外）
        log.info("步骤3：业务校验（事务外）");
        simulateStep(50);
        result.put("步骤3_业务校验", "事务外执行，不占用连接");

        // 步骤4：核心业务（事务内）
        log.info("步骤4：核心业务（事务内）");
        long txStart = System.currentTimeMillis();
        Map<String, Object> txResult = bigTxService.correct_MinimalTxScope(userId, amount);
        long txCost = System.currentTimeMillis() - txStart;
        result.put("步骤4_核心业务", Map.of(
                "执行位置", "事务内",
                "耗时", txCost + "ms",
                "内容", "扣款+记录日志"
        ));

        // 步骤5：非核心操作（异步）
        log.info("步骤5：非核心操作（异步）");
        result.put("步骤5_异步通知", Arrays.asList(
                "短信通知 → 异步线程池执行",
                "邮件通知 → 异步线程池执行",
                "操作日志 → 异步线程池执行"
        ));

        long totalCost = System.currentTimeMillis() - start;
        result.put("总耗时", totalCost + "ms");
        result.put("事务耗时", txCost + "ms");
        result.put("优化效果", "事务仅占总耗时的" + (txCost * 100 / totalCost) + "%");

        return result;
    }

    // ========================================
    // 辅助方法
    // ========================================

    private void simulateStep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
