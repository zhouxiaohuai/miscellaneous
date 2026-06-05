package com.example.transaction.controller;

import com.example.transaction.entity.User;
import com.example.transaction.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * 事务演示 REST Controller
 *
 * 所有知识点通过 HTTP 接口暴露，方便浏览器/Postman 测试
 */
@Slf4j
@RestController
@RequestMapping("/api/tx")
@RequiredArgsConstructor
public class TransactionDemoController {

    private final TransactionLifecycleService lifecycleService;
    private final PropagationService propagationService;
    private final IsolationService isolationService;
    private final TransactionFailService failService;
    private final ProgrammaticTransactionService programmaticService;
    private final NestedTransactionService nestedService;
    private final ReadOnlyTransactionService readOnlyService;
    private final TimeoutTransactionService timeoutService;
    private final BatchTransactionService batchService;
    private final BigTransactionService bigTxService;

    // ========================================
    // 1. 事务生命周期
    // ========================================

    /**
     * 声明式事务 - 正常提交
     * GET /api/tx/lifecycle/declarative
     */
    @GetMapping("/lifecycle/declarative")
    public Map<String, Object> lifecycleDeclarative() {
        User user = buildUser("lifecycle_" + System.currentTimeMillis());
        User saved = lifecycleService.declarativeTransactionDemo(user);
        return result("声明式事务(正常提交)", saved);
    }

    /**
     * 声明式事务 - 回滚
     * GET /api/tx/lifecycle/declarative-rollback
     */
    @GetMapping("/lifecycle/declarative-rollback")
    public Map<String, Object> lifecycleDeclarativeRollback() {
        User user = buildUser("lifecycle_rollback_" + System.currentTimeMillis());
        try {
            lifecycleService.declarativeTransactionRollbackDemo(user);
            return result("声明式事务(回滚)", "未触发回滚");
        } catch (Exception e) {
            return result("声明式事务(回滚)", "已回滚: " + e.getMessage());
        }
    }

    /**
     * 编程式事务 - 正常提交
     * GET /api/tx/lifecycle/programmatic
     */
    @GetMapping("/lifecycle/programmatic")
    public Map<String, Object> lifecycleProgrammatic() {
        User user = buildUser("programmatic_" + System.currentTimeMillis());
        User saved = lifecycleService.programmaticTransactionDemo(user);
        return result("编程式事务(正常提交)", saved);
    }

    /**
     * 编程式事务 - 回滚
     * GET /api/tx/lifecycle/programmatic-rollback
     */
    @GetMapping("/lifecycle/programmatic-rollback")
    public Map<String, Object> lifecycleProgrammaticRollback() {
        User user = buildUser("programmatic_rollback_" + System.currentTimeMillis());
        try {
            lifecycleService.programmaticTransactionRollbackDemo(user);
            return result("编程式事务(回滚)", "未触发回滚");
        } catch (Exception e) {
            return result("编程式事务(回滚)", "已回滚: " + e.getMessage());
        }
    }

    // ========================================
    // 2. 传播行为
    // ========================================

    /**
     * REQUIRED 传播行为
     * GET /api/tx/propagation/required
     */
    @GetMapping("/propagation/required")
    public Map<String, Object> propagationRequired() {
        User u1 = buildUser("req_outer_" + System.currentTimeMillis());
        User u2 = buildUser("req_inner_" + System.currentTimeMillis());
        propagationService.requiredOuter(u1, u2);
        return result("REQUIRED 传播行为", "内部异常 → 内外都回滚（同一事务）");
    }

    /**
     * REQUIRES_NEW 传播行为
     * GET /api/tx/propagation/requires-new
     */
    @GetMapping("/propagation/requires-new")
    public Map<String, Object> propagationRequiresNew() {
        User u1 = buildUser("new_outer_" + System.currentTimeMillis());
        User u2 = buildUser("new_inner_" + System.currentTimeMillis());
        propagationService.requiresNewOuter(u1, u2);
        return result("REQUIRES_NEW 传播行为", "内部异常 → 内部回滚，外部不受影响");
    }

    /**
     * SUPPORTS 传播行为
     * GET /api/tx/propagation/supports
     */
    @GetMapping("/propagation/supports")
    public Map<String, Object> propagationSupports() {
        User u1 = buildUser("sup_with_" + System.currentTimeMillis());
        User u2 = buildUser("sup_without_" + System.currentTimeMillis());
        propagationService.supportsWithTransaction(u1);
        propagationService.supportsWithoutTransaction(u2);
        return result("SUPPORTS 传播行为", "有事务则加入，无事务则非事务执行");
    }

    /**
     * NOT_SUPPORTED 传播行为
     * GET /api/tx/propagation/not-supported
     */
    @GetMapping("/propagation/not-supported")
    public Map<String, Object> propagationNotSupported() {
        User u = buildUser("notsup_" + System.currentTimeMillis());
        propagationService.notSupportedWithTransaction(u);
        return result("NOT_SUPPORTED 传播行为", "挂起外部事务，以非事务方式执行");
    }

    /**
     * MANDATORY 传播行为
     * GET /api/tx/propagation/mandatory
     */
    @GetMapping("/propagation/mandatory")
    public Map<String, Object> propagationMandatory() {
        User u1 = buildUser("mand_with_" + System.currentTimeMillis());
        User u2 = buildUser("mand_without_" + System.currentTimeMillis());
        propagationService.mandatoryWithTransaction(u1);
        propagationService.mandatoryWithoutTransaction(u2);
        return result("MANDATORY 传播行为", "必须在事务中调用，否则抛异常");
    }

    /**
     * NEVER 传播行为
     * GET /api/tx/propagation/never
     */
    @GetMapping("/propagation/never")
    public Map<String, Object> propagationNever() {
        User u1 = buildUser("never_without_" + System.currentTimeMillis());
        User u2 = buildUser("never_with_" + System.currentTimeMillis());
        propagationService.neverWithoutTransaction(u1);
        propagationService.neverWithTransaction(u2);
        return result("NEVER 传播行为", "不能在事务中调用，否则抛异常");
    }

    /**
     * NESTED 传播行为
     * GET /api/tx/propagation/nested
     */
    @GetMapping("/propagation/nested")
    public Map<String, Object> propagationNested() {
        User u1 = buildUser("nested_outer_" + System.currentTimeMillis());
        User u2 = buildUser("nested_inner_" + System.currentTimeMillis());
        propagationService.nestedOuter(u1, u2);
        return result("NESTED 传播行为", "内部异常 → 内部回滚到Savepoint，外部继续提交");
    }

    // ========================================
    // 3. 隔离级别
    // ========================================

    /**
     * 初始化隔离级别测试数据
     * GET /api/tx/isolation/init
     */
    @GetMapping("/isolation/init")
    public Map<String, Object> isolationInit() {
        isolationService.initTestData();
        return result("隔离级别测试", "测试数据已初始化: test_key = 100");
    }

    /**
     * 获取当前测试数据值
     * GET /api/tx/isolation/current
     */
    @GetMapping("/isolation/current")
    public Map<String, Object> isolationCurrent() {
        int value = isolationService.getCurrentValue();
        return result("当前值", value);
    }

    /**
     * 重置测试数据
     * GET /api/tx/isolation/reset
     */
    @GetMapping("/isolation/reset")
    public Map<String, Object> isolationReset() {
        isolationService.resetTestData();
        return result("隔离级别测试", "测试数据已重置: test_key = 100");
    }

    // ========================================
    // 4. 事务失效场景 ★
    // ========================================

    /**
     * 场景1：方法非 public
     * GET /api/tx/fail/01
     */
    @GetMapping("/fail/01")
    public Map<String, Object> fail01() {
        String result = failService.scenario01_NotPublic();
        return result("场景1: 方法非public", result);
    }

    /**
     * 场景2：自调用
     * GET /api/tx/fail/02
     */
    @GetMapping("/fail/02")
    public Map<String, Object> fail02() {
        String result = failService.scenario02_SelfInvoke();
        return result("场景2: 自调用", result);
    }

    /**
     * 场景3：异常被catch未抛出
     * GET /api/tx/fail/03
     */
    @GetMapping("/fail/03")
    public Map<String, Object> fail03() {
        String result = failService.scenario03_ExceptionCaught();
        return result("场景3: 异常被catch", result);
    }

    /**
     * 场景4：checked异常
     * GET /api/tx/fail/04
     */
    @GetMapping("/fail/04")
    public Map<String, Object> fail04() {
        String result = failService.scenario04_CheckedException();
        return result("场景4: checked异常", result);
    }

    /**
     * 场景5：rollbackFor配置错误
     * GET /api/tx/fail/05
     */
    @GetMapping("/fail/05")
    public Map<String, Object> fail05() {
        String result = failService.scenario05_WrongRollbackFor();
        return result("场景5: rollbackFor错误", result);
    }

    /**
     * 场景6：MyISAM引擎
     * GET /api/tx/fail/06
     */
    @GetMapping("/fail/06")
    public Map<String, Object> fail06() {
        String result = failService.scenario06_MyISAM();
        return result("场景6: MyISAM引擎", result);
    }

    /**
     * 场景7：未被Spring管理
     * GET /api/tx/fail/07
     */
    @GetMapping("/fail/07")
    public Map<String, Object> fail07() {
        String result = failService.scenario07_NotSpringManaged();
        return result("场景7: 未被Spring管理", result);
    }

    /**
     * 场景8：多线程调用
     * GET /api/tx/fail/08
     */
    @GetMapping("/fail/08")
    public Map<String, Object> fail08() {
        String result = failService.scenario08_MultiThread();
        return result("场景8: 多线程调用", result);
    }

    /**
     * 场景9：propagation设置错误
     * GET /api/tx/fail/09
     */
    @GetMapping("/fail/09")
    public Map<String, Object> fail09() {
        String result = failService.scenario09_WrongPropagation();
        return result("场景9: propagation错误", result);
    }

    /**
     * 场景10：final/static方法
     * GET /api/tx/fail/10
     */
    @GetMapping("/fail/10")
    public Map<String, Object> fail10() {
        String result = failService.scenario10_FinalMethod();
        return result("场景10: final/static方法", result);
    }

    /**
     * 场景11：类未被代理
     * GET /api/tx/fail/11
     */
    @GetMapping("/fail/11")
    public Map<String, Object> fail11() {
        String result = failService.scenario11_NotProxied();
        return result("场景11: 类未被代理", result);
    }

    /**
     * 场景12：BeanPostProcessor顺序
     * GET /api/tx/fail/12
     */
    @GetMapping("/fail/12")
    public Map<String, Object> fail12() {
        String result = failService.scenario12_BeanPostProcessor();
        return result("场景12: BeanPostProcessor顺序", result);
    }

    // ========================================
    // 5. 编程式事务
    // ========================================

    /**
     * TransactionTemplate - 有返回值
     * GET /api/tx/programmatic/template-return
     */
    @GetMapping("/programmatic/template-return")
    public Map<String, Object> programmaticTemplateReturn() {
        User user = buildUser("template_" + System.currentTimeMillis());
        User saved = programmaticService.templateWithReturn(user);
        return result("TransactionTemplate(有返回值)", saved);
    }

    /**
     * TransactionTemplate - 手动回滚
     * GET /api/tx/programmatic/template-rollback
     */
    @GetMapping("/programmatic/template-rollback")
    public Map<String, Object> programmaticTemplateRollback() {
        User user = buildUser("template_rollback_" + System.currentTimeMillis());
        programmaticService.templateManualRollback(user);
        return result("TransactionTemplate(手动回滚)", "已执行，数据应被回滚");
    }

    /**
     * PlatformTransactionManager - 完整流程
     * GET /api/tx/programmatic/manager
     */
    @GetMapping("/programmatic/manager")
    public Map<String, Object> programmaticManager() {
        User user = buildUser("manager_" + System.currentTimeMillis());
        User saved = programmaticService.managerComplete(user);
        return result("PlatformTransactionManager", saved);
    }

    /**
     * PlatformTransactionManager - 嵌套事务
     * GET /api/tx/programmatic/manager-nested
     */
    @GetMapping("/programmatic/manager-nested")
    public Map<String, Object> programmaticManagerNested() {
        User u1 = buildUser("mgr_outer_" + System.currentTimeMillis());
        User u2 = buildUser("mgr_inner_" + System.currentTimeMillis());
        programmaticService.managerNestedTransaction(u1, u2);
        return result("PlatformTransactionManager(嵌套)", "外层提交，内层回滚到Savepoint");
    }

    // ========================================
    // 6. 嵌套事务
    // ========================================

    /**
     * NESTED - 内层异常不影响外层
     * GET /api/tx/nested/demo1
     */
    @GetMapping("/nested/demo1")
    public Map<String, Object> nestedDemo1() {
        User u1 = buildUser("nested_outer_" + System.currentTimeMillis());
        User u2 = buildUser("nested_inner_" + System.currentTimeMillis());
        nestedService.nestedDemo1(u1, u2);
        return result("嵌套事务演示1", "内层回滚到Savepoint，外层继续提交");
    }

    /**
     * 手动Savepoint
     * GET /api/tx/nested/manual-savepoint
     */
    @GetMapping("/nested/manual-savepoint")
    public Map<String, Object> nestedManualSavepoint() {
        User u1 = buildUser("sp_outer_" + System.currentTimeMillis());
        User u2 = buildUser("sp_inner_" + System.currentTimeMillis());
        nestedService.manualSavepointDemo(u1, u2);
        return result("手动Savepoint", "用户1保存成功，用户2被回滚到Savepoint");
    }

    // ========================================
    // 7. 只读事务
    // ========================================

    /**
     * 只读事务查询
     * GET /api/tx/readonly
     */
    @GetMapping("/readonly")
    public Map<String, Object> readOnly() {
        List<User> users = readOnlyService.findAllReadOnly();
        return result("只读事务", "查询到 " + users.size() + " 条记录");
    }

    // ========================================
    // 8. 超时事务
    // ========================================

    /**
     * 正常事务
     * GET /api/tx/timeout/normal
     */
    @GetMapping("/timeout/normal")
    public Map<String, Object> timeoutNormal() {
        User user = buildUser("timeout_normal_" + System.currentTimeMillis());
        User saved = timeoutService.normalTransaction(user);
        return result("正常事务(30s超时)", saved);
    }

    /**
     * 超时事务
     * GET /api/tx/timeout/timeout
     */
    @GetMapping("/timeout/timeout")
    public Map<String, Object> timeoutTimeout() {
        User user = buildUser("timeout_" + System.currentTimeMillis());
        try {
            User saved = timeoutService.timeoutTransaction(user);
            return result("超时事务(1s超时)", "未超时: " + saved.getUsername());
        } catch (Exception e) {
            return result("超时事务(1s超时)", "已超时回滚: " + e.getMessage());
        }
    }

    // ========================================
    // 9. 批量操作
    // ========================================

    /**
     * 单事务批量插入
     * GET /api/tx/batch/single?count=100
     */
    @GetMapping("/batch/single")
    public Map<String, Object> batchSingle(@RequestParam(defaultValue = "100") int count) {
        List<User> users = batchService.generateTestUsers(count);
        int result = batchService.batchInsertSingleTransaction(users);
        return result("单事务批量插入(" + count + "条)", "成功插入 " + result + " 条");
    }

    /**
     * 分批提交
     * GET /api/tx/batch/batch-commit?count=100&batchSize=20
     */
    @GetMapping("/batch/batch-commit")
    public Map<String, Object> batchCommit(
            @RequestParam(defaultValue = "100") int count,
            @RequestParam(defaultValue = "20") int batchSize) {
        List<User> users = batchService.generateTestUsers(count);
        int result = batchService.batchInsertWithBatchCommit(users, batchSize);
        return result("分批提交(" + count + "条, 每批" + batchSize + "条)", "成功插入 " + result + " 条");
    }

    // ========================================
    // 10. 大事务处理 ★★★
    // ========================================

    /**
     * 反模式：事务中包含远程调用
     * GET /api/tx/big/anti-rpc?userId=1
     */
    @GetMapping("/big/anti-rpc")
    public Map<String, Object> bigAntiRpc(@RequestParam Long userId) {
        return bigTxService.antiPattern_RemoteCallInTx(userId);
    }

    /**
     * 正确：远程调用外提
     * GET /api/tx/big/correct-rpc?userId=1
     */
    @GetMapping("/big/correct-rpc")
    public Map<String, Object> bigCorrectRpc(@RequestParam Long userId) {
        return bigTxService.correct_RemoteCallOutsideTx(userId);
    }

    /**
     * 反模式：N+1循环查询
     * GET /api/tx/big/anti-n1?userIds=1,2,3
     */
    @GetMapping("/big/anti-n1")
    public Map<String, Object> bigAntiN1(@RequestParam List<Long> userIds) {
        return bigTxService.antiPattern_NPlus1InTx(userIds);
    }

    /**
     * 正确：批量查询+更新
     * GET /api/tx/big/correct-batch?userIds=1,2,3
     */
    @GetMapping("/big/correct-batch")
    public Map<String, Object> bigCorrectBatch(@RequestParam List<Long> userIds) {
        return bigTxService.correct_BatchUpdateInTx(userIds);
    }

    /**
     * 分片提交
     * GET /api/tx/big/chunked?count=200&chunkSize=50
     */
    @GetMapping("/big/chunked")
    public Map<String, Object> bigChunked(
            @RequestParam(defaultValue = "200") int count,
            @RequestParam(defaultValue = "50") int chunkSize) {
        List<User> users = batchService.generateTestUsers(count);
        return bigTxService.chunkedCommit(users, chunkSize);
    }

    /**
     * 超时控制演示
     * GET /api/tx/big/timeout?userId=1&workSeconds=5
     */
    @GetMapping("/big/timeout")
    public Map<String, Object> bigTimeout(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "5") int workSeconds) {
        try {
            return bigTxService.timeoutControlDemo(userId, workSeconds);
        } catch (Exception e) {
            return result("超时事务(3s超时)", "事务超时回滚: " + e.getMessage());
        }
    }

    /**
     * 编程式动态超时
     * GET /api/tx/big/programmatic-timeout?timeoutSeconds=10
     */
    @GetMapping("/big/programmatic-timeout")
    public Map<String, Object> bigProgrammaticTimeout(
            @RequestParam(defaultValue = "10") int timeoutSeconds) {
        return bigTxService.programmaticTimeoutDemo(timeoutSeconds);
    }

    /**
     * 反模式：全方法事务
     * GET /api/tx/big/anti-full-tx?userId=1&amount=100
     */
    @GetMapping("/big/anti-full-tx")
    public Map<String, Object> bigAntiFullTx(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "100") BigDecimal amount) {
        return bigTxService.antiPattern_FullMethodTx(userId, amount);
    }

    /**
     * 正确：最小事务范围
     * GET /api/tx/big/correct-minimal?userId=1&amount=100
     */
    @GetMapping("/big/correct-minimal")
    public Map<String, Object> bigCorrectMinimal(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "100") BigDecimal amount) {
        return bigTxService.correct_MinimalTxScope(userId, amount);
    }

    /**
     * 反模式：事务一开始就加锁
     * GET /api/tx/big/anti-early-lock?accountId=1&amount=50
     */
    @GetMapping("/big/anti-early-lock")
    public Map<String, Object> bigAntiEarlyLock(
            @RequestParam Long accountId,
            @RequestParam(defaultValue = "50") BigDecimal amount) {
        return bigTxService.antiPattern_EarlyLock(accountId, amount);
    }

    /**
     * 正确：延迟加锁
     * GET /api/tx/big/correct-deferred-lock?accountId=1&amount=50
     */
    @GetMapping("/big/correct-deferred-lock")
    public Map<String, Object> bigCorrectDeferredLock(
            @RequestParam Long accountId,
            @RequestParam(defaultValue = "50") BigDecimal amount) {
        return bigTxService.correct_DeferredLock(accountId, amount);
    }

    /**
     * 反模式：所有操作同步在事务里
     * GET /api/tx/big/anti-sync-all?userId=1
     */
    @GetMapping("/big/anti-sync-all")
    public Map<String, Object> bigAntiSyncAll(@RequestParam Long userId) {
        return bigTxService.antiPattern_SyncAllInTx(userId);
    }

    /**
     * 正确：非核心操作异步
     * GET /api/tx/big/correct-async?userId=1
     */
    @GetMapping("/big/correct-async")
    public Map<String, Object> bigCorrectAsync(@RequestParam Long userId) {
        return bigTxService.correct_AsyncNonCore(userId);
    }

    /**
     * 事务监控与排查建议
     * GET /api/tx/big/monitor
     */
    @GetMapping("/big/monitor")
    public Map<String, Object> bigMonitor() {
        return bigTxService.monitorTransactionStats();
    }

    /**
     * 综合对比：同一场景不同实现
     * GET /api/tx/big/compare?count=100
     */
    @GetMapping("/big/compare")
    public Map<String, Object> bigCompare(@RequestParam(defaultValue = "100") int count) {
        return bigTxService.comprehensiveComparison(count);
    }

    // ========================================
    // 11. 事务知识总览
    // ========================================

    /**
     * 事务知识总览
     * GET /api/tx/overview
     */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> overview = new LinkedHashMap<>();

        overview.put("1_事务生命周期", Map.of(
                "声明式事务", "GET /api/tx/lifecycle/declarative",
                "声明式事务回滚", "GET /api/tx/lifecycle/declarative-rollback",
                "编程式事务", "GET /api/tx/lifecycle/programmatic",
                "编程式事务回滚", "GET /api/tx/lifecycle/programmatic-rollback"
        ));

        overview.put("2_传播行为(7种)", Map.of(
                "REQUIRED(默认)", "GET /api/tx/propagation/required",
                "REQUIRES_NEW", "GET /api/tx/propagation/requires-new",
                "SUPPORTS", "GET /api/tx/propagation/supports",
                "NOT_SUPPORTED", "GET /api/tx/propagation/not-supported",
                "MANDATORY", "GET /api/tx/propagation/mandatory",
                "NEVER", "GET /api/tx/propagation/never",
                "NESTED", "GET /api/tx/propagation/nested"
        ));

        overview.put("3_隔离级别(4种)", Map.of(
                "初始化数据", "GET /api/tx/isolation/init",
                "查看当前值", "GET /api/tx/isolation/current",
                "重置数据", "GET /api/tx/isolation/reset"
        ));

        overview.put("4_事务失效场景(12种)", Map.ofEntries(
                Map.entry("场景1: 方法非public", "GET /api/tx/fail/01"),
                Map.entry("场景2: 自调用", "GET /api/tx/fail/02"),
                Map.entry("场景3: 异常被catch", "GET /api/tx/fail/03"),
                Map.entry("场景4: checked异常", "GET /api/tx/fail/04"),
                Map.entry("场景5: rollbackFor错误", "GET /api/tx/fail/05"),
                Map.entry("场景6: MyISAM引擎", "GET /api/tx/fail/06"),
                Map.entry("场景7: 未被Spring管理", "GET /api/tx/fail/07"),
                Map.entry("场景8: 多线程调用", "GET /api/tx/fail/08"),
                Map.entry("场景9: propagation错误", "GET /api/tx/fail/09"),
                Map.entry("场景10: final/static方法", "GET /api/tx/fail/10"),
                Map.entry("场景11: 类未被代理", "GET /api/tx/fail/11"),
                Map.entry("场景12: BeanPostProcessor顺序", "GET /api/tx/fail/12")
        ));

        overview.put("5_编程式事务", Map.of(
                "Template有返回值", "GET /api/tx/programmatic/template-return",
                "Template手动回滚", "GET /api/tx/programmatic/template-rollback",
                "Manager完整流程", "GET /api/tx/programmatic/manager",
                "Manager嵌套事务", "GET /api/tx/programmatic/manager-nested"
        ));

        overview.put("6_嵌套事务", Map.of(
                "内层异常不影响外层", "GET /api/tx/nested/demo1",
                "手动Savepoint", "GET /api/tx/nested/manual-savepoint"
        ));

        overview.put("7_只读事务", Map.of(
                "只读查询", "GET /api/tx/readonly"
        ));

        overview.put("8_超时事务", Map.of(
                "正常事务", "GET /api/tx/timeout/normal",
                "超时事务", "GET /api/tx/timeout/timeout"
        ));

        overview.put("9_批量操作", Map.of(
                "单事务批量", "GET /api/tx/batch/single?count=100",
                "分批提交", "GET /api/tx/batch/batch-commit?count=100&batchSize=20"
        ));

        Map<String, String> bigTxOverview = new LinkedHashMap<>();
        bigTxOverview.put("反模式:事务中RPC", "GET /api/tx/big/anti-rpc?userId=1");
        bigTxOverview.put("正确:RPC外提", "GET /api/tx/big/correct-rpc?userId=1");
        bigTxOverview.put("反模式:N+1循环", "GET /api/tx/big/anti-n1?userIds=1,2,3");
        bigTxOverview.put("正确:批量查询", "GET /api/tx/big/correct-batch?userIds=1,2,3");
        bigTxOverview.put("分片提交", "GET /api/tx/big/chunked?count=200&chunkSize=50");
        bigTxOverview.put("超时控制", "GET /api/tx/big/timeout?userId=1&workSeconds=5");
        bigTxOverview.put("编程式动态超时", "GET /api/tx/big/programmatic-timeout?timeoutSeconds=10");
        bigTxOverview.put("反模式:全方法事务", "GET /api/tx/big/anti-full-tx?userId=1&amount=100");
        bigTxOverview.put("正确:最小事务范围", "GET /api/tx/big/correct-minimal?userId=1&amount=100");
        bigTxOverview.put("反模式:早加锁", "GET /api/tx/big/anti-early-lock?accountId=1&amount=50");
        bigTxOverview.put("正确:延迟加锁", "GET /api/tx/big/correct-deferred-lock?accountId=1&amount=50");
        bigTxOverview.put("反模式:同步全包", "GET /api/tx/big/anti-sync-all?userId=1");
        bigTxOverview.put("正确:异步非核心", "GET /api/tx/big/correct-async?userId=1");
        bigTxOverview.put("监控与排查", "GET /api/tx/big/monitor");
        bigTxOverview.put("综合对比", "GET /api/tx/big/compare?count=100");
        overview.put("10_大事务处理★★★", bigTxOverview);

        return overview;
    }

    // ========================================
    // 辅助方法
    // ========================================

    private User buildUser(String username) {
        return User.builder()
                .username(username)
                .email(username + "@test.com")
                .status(1)
                .build();
    }

    private Map<String, Object> result(String title, Object data) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", title);
        map.put("data", data);
        map.put("timestamp", System.currentTimeMillis());
        return map;
    }
}
