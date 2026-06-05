package com.example.transaction.service;

import com.example.transaction.entity.User;
import com.example.transaction.jpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * ========================================
 * 12种事务失效场景 ★★★ 核心重点 ★★★
 * ========================================
 *
 * 这是面试和实际开发中最常遇到的问题。
 * 每个场景都给出了：错误写法 → 原因分析 → 正确写法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionFailService {

    private final UserRepository userRepository;
    private final ApplicationContext applicationContext;
    private final SelfInvokeService selfInvokeService;

    // ========================================
    // 场景1：方法非 public
    // ========================================

    /**
     * ❌ 错误：@Transactional 用在非 public 方法上
     *
     * 原因：Spring AOP 基于代理模式，默认只拦截 public 方法。
     *      CGLIB 代理通过继承实现，无法代理 private/protected/default 方法。
     *      AbstractFallbackTransactionAttributeSource.computeTransactionAttribute()
     *      会检查方法的可见性，非 public 方法直接返回 null。
     */
    @Transactional
    protected void fail01_NotPublic(User user) {
        userRepository.save(user);
        throw new RuntimeException("异常被抛出，但事务不会回滚！");
    }

    /**
     * 演示场景1
     */
    public String scenario01_NotPublic() {
        log.info("=== 场景1：方法非 public ===");
        User user = buildTestUser("fail01");

        try {
            fail01_NotPublic(user);
        } catch (Exception e) {
            log.info("捕获异常: {}", e.getMessage());
        }

        boolean exists = userRepository.findByUsername("fail01").isPresent();
        String result = exists ? "事务失效！数据已保存（没有回滚）" : "事务正常回滚";
        log.info("结果: {}", result);
        log.info("原因: protected 方法无法被 Spring AOP 代理拦截");
        log.info("解决: 将方法改为 public");
        return result;
    }

    // ========================================
    // 场景2：自调用（this 调用，未走代理）
    // ========================================

    /**
     * ❌ 错误：在同一类内部调用 @Transactional 方法
     *
     * 原因：Spring AOP 基于代理模式，自调用时调用的是目标对象(this)的方法，
     *      而不是代理对象的方法。代理拦截不会生效，事务也就不会开启。
     *
     * 调用链：controller → 代理对象.methodA() → this.methodB()
     *      methodB 的 @Transactional 不会生效，因为 this 是目标对象，不是代理对象
     */
    public void fail02_SelfInvoke_Outer(User user) {
        log.info("[自调用-外部] 开始");
        // ❌ 错误写法：直接调用本类的事务方法
        this.fail02_SelfInvoke_Inner(user);
    }

    @Transactional
    public void fail02_SelfInvoke_Inner(User user) {
        userRepository.save(user);
        throw new RuntimeException("异常被抛出，但事务不会回滚！");
    }

    /**
     * ✅ 正确写法：通过代理对象调用
     */
    public void success02_SelfInvoke(User user) {
        log.info("[自调用-正确] 通过代理对象调用");
        // 获取代理对象并调用
        TransactionFailService proxy = (TransactionFailService) AopProxyUtils.getSingletonTarget(applicationContext.getBean("transactionFailService"));
        // 或者注入自身代理
        selfInvokeService.innerTransactionalMethod(user);
    }

    /**
     * 演示场景2
     */
    public String scenario02_SelfInvoke() {
        log.info("=== 场景2：自调用（this调用未走代理） ===");
        User user = buildTestUser("fail02");

        try {
            fail02_SelfInvoke_Outer(user);
        } catch (Exception e) {
            log.info("捕获异常: {}", e.getMessage());
        }

        boolean exists = userRepository.findByUsername("fail02").isPresent();
        String result = exists ? "事务失效！数据已保存（没有回滚）" : "事务正常回滚";
        log.info("结果: {}", result);
        log.info("原因: this 调用未走 AOP 代理，@Transactional 不生效");
        log.info("解决1: 注入自身代理对象调用");
        log.info("解决2: 使用 AopContext.currentProxy() 获取代理对象");
        log.info("解决3: 将事务方法移到另一个 Service 类中");
        return result;
    }

    // ========================================
    // 场景3：异常被 catch 未抛出
    // ========================================

    /**
     * ❌ 错误：catch 了异常但没有重新抛出
     *
     * 原因：Spring 事务默认只在捕获到 RuntimeException 或 Error 时回滚。
     *      如果异常被 catch 了，Spring 看不到异常，会正常提交事务。
     */
    @Transactional
    public void fail03_ExceptionCaught(User user) {
        userRepository.save(user);
        try {
            int i = 1 / 0; // 抛出 ArithmeticException
        } catch (ArithmeticException e) {
            log.info("[场景3] 异常被 catch: {}", e.getMessage());
            // ❌ 没有重新抛出异常，Spring 不知道发生了异常
        }
    }

    /**
     * ✅ 正确写法：catch 后重新抛出
     */
    @Transactional
    public void success03_ExceptionCaught(User user) {
        userRepository.save(user);
        try {
            int i = 1 / 0;
        } catch (ArithmeticException e) {
            log.info("[场景3-正确] 异常被 catch，重新抛出");
            throw e; // ✅ 重新抛出，Spring 能捕获到并回滚
        }
    }

    /**
     * 演示场景3
     */
    public String scenario03_ExceptionCaught() {
        log.info("=== 场景3：异常被 catch 未抛出 ===");
        User user = buildTestUser("fail03");
        fail03_ExceptionCaught(user);

        boolean exists = userRepository.findByUsername("fail03").isPresent();
        String result = exists ? "事务失效！数据已保存（没有回滚）" : "事务正常回滚";
        log.info("结果: {}", result);
        log.info("原因: 异常被 catch 了，Spring 看不到异常，正常提交事务");
        log.info("解决: catch 后重新 throw，或使用 @Transactional(rollbackFor=Exception.class)");
        return result;
    }

    // ========================================
    // 场景4：抛出非 RuntimeException（checked 异常）
    // ========================================

    /**
     * ❌ 错误：抛出 checked 异常（如 IOException）
     *
     * 原因：@Transactional 默认只对 RuntimeException 和 Error 回滚。
     *      对于 checked 异常（如 IOException, SQLException），不会回滚。
     *      源码：RuleBasedTransactionAttribute 中默认的 rollbackRules 只包含 RuntimeException
     */
    @Transactional
    public void fail04_CheckedException(User user) throws IOException {
        userRepository.save(user);
        throw new IOException("抛出 checked 异常，但事务不会回滚！");
    }

    /**
     * ✅ 正确写法：指定 rollbackFor
     */
    @Transactional(rollbackFor = Exception.class)
    public void success04_CheckedException(User user) throws IOException {
        userRepository.save(user);
        throw new IOException("指定 rollbackFor 后，事务会回滚");
    }

    /**
     * 演示场景4
     */
    public String scenario04_CheckedException() {
        log.info("=== 场景4：抛出 checked 异常 ===");
        User user = buildTestUser("fail04");

        try {
            fail04_CheckedException(user);
        } catch (Exception e) {
            log.info("捕获异常: {}", e.getMessage());
        }

        boolean exists = userRepository.findByUsername("fail04").isPresent();
        String result = exists ? "事务失效！数据已保存（没有回滚）" : "事务正常回滚";
        log.info("结果: {}", result);
        log.info("原因: 默认只对 RuntimeException 和 Error 回滚，IOException 是 checked 异常");
        log.info("解决: @Transactional(rollbackFor = Exception.class)");
        return result;
    }

    // ========================================
    // 场景5：rollbackFor 配置不正确
    // ========================================

    /**
     * ❌ 错误：rollbackFor 指定了错误的异常类型
     *
     * 原因：rollbackFor 指定的异常类型与实际抛出的异常不匹配
     */
    @Transactional(rollbackFor = IOException.class)
    public void fail05_WrongRollbackFor(User user) {
        userRepository.save(user);
        throw new RuntimeException("RuntimeException 不在 rollbackFor 范围内");
    }

    /**
     * 演示场景5
     */
    public String scenario05_WrongRollbackFor() {
        log.info("=== 场景5：rollbackFor 配置不正确 ===");
        User user = buildTestUser("fail05");

        try {
            fail05_WrongRollbackFor(user);
        } catch (Exception e) {
            log.info("捕获异常: {}", e.getMessage());
        }

        boolean exists = userRepository.findByUsername("fail05").isPresent();
        String result = exists ? "事务失效！数据已保存（没有回滚）" : "事务正常回滚";
        log.info("结果: {}", result);
        log.info("原因: rollbackFor 指定 IOException.class，但实际抛出 RuntimeException");
        log.info("解决: @Transactional(rollbackFor = Exception.class) 覆盖所有异常");
        return result;
    }

    // ========================================
    // 场景6：数据库引擎不支持事务
    // ========================================

    /**
     * ❌ 错误：使用不支持事务的数据库引擎（如 MyISAM）
     *
     * 原因：MySQL 的 MyISAM 引擎不支持事务，即使 Spring 开启了事务，
     *      数据库层面也不会进行事务控制。
     *
     * 注意：本演示项目使用的是 InnoDB 引擎，此场景需要手动修改表引擎来测试。
     *      ALTER TABLE t_user ENGINE = MyISAM;
     */
    @Transactional
    public void fail06_MyISAM(User user) {
        log.info("[场景6] 此场景需要手动将表引擎改为 MyISAM 来测试");
        userRepository.save(user);
        throw new RuntimeException("如果表是 MyISAM，数据不会回滚");
    }

    /**
     * 演示场景6
     */
    public String scenario06_MyISAM() {
        log.info("=== 场景6：数据库引擎不支持事务 ===");
        log.info("当前表使用 InnoDB 引擎，此场景正常");
        log.info("如需测试：ALTER TABLE t_user ENGINE = MyISAM;");
        log.info("原因: MyISAM 不支持事务，InnoDB 支持");
        log.info("解决: 确保使用 InnoDB 引擎");
        return "此场景需要手动修改表引擎测试，当前使用 InnoDB 不受影响";
    }

    // ========================================
    // 场景7：未被 Spring 管理（手动 new）
    // ========================================

    /**
     * ❌ 错误：对象不是 Spring Bean，手动 new 的对象
     *
     * 原因：Spring 只能管理它创建的 Bean。手动 new 的对象不在 Spring 容器中，
     *      @Transactional 注解不会被处理。
     *
     * 注意：这是一个演示类，展示如果这样做会怎样
     */
    public static class NotSpringManaged {
        @Transactional
        public void saveUser(UserRepository userRepository, User user) {
            userRepository.save(user);
            throw new RuntimeException("事务不会生效");
        }
    }

    /**
     * 演示场景7
     */
    public String scenario07_NotSpringManaged() {
        log.info("=== 场景7：未被 Spring 管理 ===");
        User user = buildTestUser("fail07");

        NotSpringManaged notManaged = new NotSpringManaged();
        try {
            notManaged.saveUser(userRepository, user);
        } catch (Exception e) {
            log.info("捕获异常: {}", e.getMessage());
        }

        boolean exists = userRepository.findByUsername("fail07").isPresent();
        String result = exists ? "事务失效！数据已保存（没有回滚）" : "事务正常回滚";
        log.info("结果: {}", result);
        log.info("原因: 手动 new 的对象不在 Spring 容器中，@Transactional 不会被处理");
        log.info("解决: 使用 @Component 注解让 Spring 管理，通过 @Autowired 注入");
        return result;
    }

    // ========================================
    // 场景8：多线程调用
    // ========================================

    /**
     * ❌ 错误：在新线程中执行事务方法
     *
     * 原因：Spring 事务基于 ThreadLocal 存储事务信息（数据库连接）。
     *      新线程有自己独立的 ThreadLocal，无法获取主线程的事务上下文。
     *      所以新线程中的 @Transactional 方法会开启一个独立的新事务。
     *
     * ThreadLocal 原理：
     * TransactionSynchronizationManager 内部使用 ThreadLocal 绑定：
     * - 当前数据库连接
     * - 当前事务隔离级别
     * - 事务同步回调
     */
    @Transactional
    public void fail08_MultiThread(User user) {
        log.info("[场景8] 主线程事务开始");
        userRepository.save(user);
        log.info("[场景8] 主线程已保存用户");

        // ❌ 在新线程中执行事务方法
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            // 这里的 @Transactional 不会加入主线程的事务
            selfInvokeService.innerMethod(newThreadUser());
        });

        try {
            future.get();
        } catch (Exception e) {
            log.info("[场景8] 子线程异常: {}", e.getMessage());
        }

        throw new RuntimeException("主线程异常，但子线程的数据不会回滚！");
    }

    private User newThreadUser() {
        return buildTestUser("fail08_thread");
    }

    /**
     * 演示场景8
     */
    public String scenario08_MultiThread() {
        log.info("=== 场景8：多线程调用 ===");
        User user = buildTestUser("fail08");

        try {
            fail08_MultiThread(user);
        } catch (Exception e) {
            log.info("捕获异常: {}", e.getMessage());
        }

        boolean mainExists = userRepository.findByUsername("fail08").isPresent();
        boolean threadExists = userRepository.findByUsername("fail08_thread").isPresent();

        log.info("主线程数据是否存在: {}（应该不存在，主线程事务回滚）", mainExists);
        log.info("子线程数据是否存在: {}（应该存在，子线程独立事务已提交）", threadExists);

        String result = String.format("主线程数据: %s, 子线程数据: %s",
                mainExists ? "存在" : "不存在",
                threadExists ? "存在(事务失效)" : "不存在");
        log.info("结果: {}", result);
        log.info("原因: 事务信息存储在 ThreadLocal 中，新线程无法获取主线程的事务上下文");
        log.info("解决1: 避免在事务中开启新线程操作数据库");
        log.info("解决2: 使用编程式事务手动管理");
        return result;
    }

    // ========================================
    // 场景9：propagation 设置错误
    // ========================================

    /**
     * ❌ 错误：使用了 NOT_SUPPORTED 传播行为
     *
     * 原因：NOT_SUPPORTED 会挂起当前事务，以非事务方式执行。
     *      即使外层有事务，内层也不会在事务中执行。
     */
    @Transactional
    public void fail09_WrongPropagation(User user) {
        log.info("[场景9] 外层事务开始");
        userRepository.save(user);
        selfInvokeService.notSupportedMethod(buildTestUser("fail09_inner"));
        throw new RuntimeException("外层异常");
    }

    /**
     * 演示场景9
     */
    public String scenario09_WrongPropagation() {
        log.info("=== 场景9：propagation 设置错误 ===");
        log.info("NOT_SUPPORTED 会挂起事务，导致内层操作不受事务保护");
        log.info("解决: 根据业务需求选择正确的传播行为");
        return "propagation 设置错误会导致事务不按预期工作";
    }

    // ========================================
    // 场景10：final/static 方法无法代理
    // ========================================

    /**
     * ❌ 错误：final 方法
     *
     * 原因：CGLIB 代理通过继承目标类来创建代理类。
     *      final 方法不能被重写（override），所以代理类无法拦截 final 方法。
     *      @Transactional 在 final 方法上不会生效。
     *
     * JDK 动态代理（接口代理）不受此影响，但 Spring Boot 默认使用 CGLIB 代理。
     */

    // 注意：final 方法无法在同一个类中演示（编译器会报错）
    // 这里用注释说明：
    // @Transactional
    // public final void fail10_FinalMethod(User user) { ... }

    /**
     * 演示场景10
     */
    public String scenario10_FinalMethod() {
        log.info("=== 场景10：final/static 方法无法代理 ===");
        log.info("CGLIB 代理通过继承实现，final 方法不能被重写");
        log.info("static 方法属于类，不属于实例，AOP 无法拦截");
        log.info("解决: 避免在 final/static 方法上使用 @Transactional");
        return "final/static 方法上的 @Transactional 不会生效";
    }

    // ========================================
    // 场景11：类未被代理
    // ========================================

    /**
     * ❌ 错误：类没有被 Spring 代理
     *
     * 原因：某些情况下类不会被代理：
     * 1. 没有被 @Component 等注解标注
     * 2. 在 @Configuration 中通过 @Bean 创建的对象（需要特殊处理）
     * 3. 使用了 aspectj 代理模式但没有配置 AspectJ weaving
     */
    public String scenario11_NotProxied() {
        log.info("=== 场景11：类未被代理 ===");
        log.info("确保类被 @Component/@Service/@Repository 等注解标注");
        log.info("确保类在 Spring 的组件扫描路径下");
        log.info("Spring Boot 默认使用 CGLIB 代理（proxyTargetClass=true）");
        return "确保类在 Spring 管理下且被正确代理";
    }

    // ========================================
    // 场景12：BeanPostProcessor 顺序问题
    // ========================================

    /**
     * ❌ 错误：自定义 BeanPostProcessor 影响了事务代理的创建顺序
     *
     * 原因：Spring 通过 BeanPostProcessor 来创建 AOP 代理。
     *      如果自定义的 BeanPostProcessor 修改了 Bean 的顺序或类型，
     *      可能导致事务代理无法正确创建。
     *
     * 常见问题：
     * 1. @Async 和 @Transactional 的顺序问题
     * 2. 自定义 AOP 切面与事务切面的顺序冲突
     */
    public String scenario12_BeanPostProcessor() {
        log.info("=== 场景12：BeanPostProcessor 顺序问题 ===");
        log.info("常见问题: @Async + @Transactional 的顺序");
        log.info("如果 @Async 在 @Transactional 之前处理，");
        log.info("那么异步方法会在新线程中执行，事务上下文丢失");
        log.info("解决: 使用 @Order 控制切面顺序，或避免 @Async 和 @Transactional 组合");
        return "BeanPostProcessor 顺序问题需要关注切面执行顺序";
    }

    // ========================================
    // 辅助方法
    // ========================================

    private User buildTestUser(String username) {
        return User.builder()
                .username(username)
                .email(username + "@test.com")
                .status(1)
                .build();
    }
}
