package com.example.transaction.controller;

import com.example.transaction.service.CustomTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自定义事务框架演示控制器
 *
 * 提供 REST 接口方便浏览器/Postman 测试
 */
@Slf4j
@RestController
@RequestMapping("/api/custom-tx")
@RequiredArgsConstructor
public class CustomTxController {

    private final CustomTxService customTxService;

    // ========================================
    // 1. 自调用事务演示
    // ========================================

    /**
     * @Transactional 自调用失效
     * GET /api/custom-tx/self-invoke-annotation
     */
    @GetMapping("/self-invoke-annotation")
    public Map<String, Object> selfInvokeAnnotation() {
        return customTxService.selfInvoke_AnnotationFail();
    }

    /**
     * Tx 框架解决自调用问题
     * GET /api/custom-tx/self-invoke-tx
     */
    @GetMapping("/self-invoke-tx")
    public Map<String, Object> selfInvokeTx() {
        return customTxService.selfInvoke_TxWork();
    }

    // ========================================
    // 2. 最小事务范围演示
    // ========================================

    /**
     * @Transactional 全方法事务（反模式）
     * GET /api/custom-tx/minimal-annotation?userId=1&amount=100
     */
    @GetMapping("/minimal-annotation")
    public Map<String, Object> minimalAnnotation(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "100") BigDecimal amount) {
        return customTxService.minimalScope_Annotation(userId, amount);
    }

    /**
     * Tx 最小事务范围（正确）
     * GET /api/custom-tx/minimal-tx?userId=1&amount=100
     */
    @GetMapping("/minimal-tx")
    public Map<String, Object> minimalTx(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "100") BigDecimal amount) {
        return customTxService.minimalScope_Tx(userId, amount);
    }

    // ========================================
    // 3. 只读 vs 读写事务
    // ========================================

    /**
     * 只读事务演示
     * GET /api/custom-tx/readonly
     */
    @GetMapping("/readonly")
    public Map<String, Object> readonly() {
        return customTxService.readOnlyDemo();
    }

    /**
     * 读写事务演示
     * GET /api/custom-tx/writable
     */
    @GetMapping("/writable")
    public Map<String, Object> writable() {
        return customTxService.writableDemo();
    }

    // ========================================
    // 4. 链式 Builder 演示
    // ========================================

    /**
     * 链式 Builder 演示
     * GET /api/custom-tx/builder?userId=1
     */
    @GetMapping("/builder")
    public Map<String, Object> builder(@RequestParam Long userId) {
        return customTxService.builderDemo(userId);
    }

    // ========================================
    // 5. 完整业务流程演示
    // ========================================

    /**
     * 完整业务流程演示
     * GET /api/custom-tx/complete-flow?userId=1&amount=100
     */
    @GetMapping("/complete-flow")
    public Map<String, Object> completeFlow(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "100") BigDecimal amount) {
        return customTxService.completeFlowDemo(userId, amount);
    }

    // ========================================
    // 6. 框架总览
    // ========================================

    /**
     * 框架总览
     * GET /api/custom-tx/overview
     */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> overview = new LinkedHashMap<>();

        overview.put("框架说明", "自定义事务框架 - 在代码任意位置开启事务");

        overview.put("核心API", Map.of(
                "Tx.readOnly(Runnable)", "只读事务，无返回值",
                "Tx.readOnly(Callable<T>)", "只读事务，有返回值",
                "Tx.writable(Runnable)", "读写事务，无返回值",
                "Tx.writable(Callable<T>)", "读写事务，有返回值",
                "Tx.builder()", "链式构建器，支持自定义属性"
        ));

        overview.put("演示接口", Map.of(
                "自调用(@Transactional失效)", "GET /api/custom-tx/self-invoke-annotation",
                "自调用(Tx正常)", "GET /api/custom-tx/self-invoke-tx",
                "全方法事务(反模式)", "GET /api/custom-tx/minimal-annotation?userId=1&amount=100",
                "最小事务范围(正确)", "GET /api/custom-tx/minimal-tx?userId=1&amount=100",
                "只读事务", "GET /api/custom-tx/readonly",
                "读写事务", "GET /api/custom-tx/writable",
                "链式Builder", "GET /api/custom-tx/builder?userId=1",
                "完整业务流程", "GET /api/custom-tx/complete-flow?userId=1&amount=100"
        ));

        overview.put("解决的问题", Map.of(
                "自调用失效", "Tx是静态方法调用，不依赖AOP代理",
                "方法级边界", "Tx可在方法内任意位置开启/关闭",
                "必须public", "Tx不受方法访问修饰符限制"
        ));

        return overview;
    }
}
