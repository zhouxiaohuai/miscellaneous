package com.example.transaction.architecture.controller;

import com.example.transaction.architecture.ddd.DddDemo;
import com.example.transaction.architecture.distributed.id.IdGeneratorDemo;
import com.example.transaction.architecture.patterns.chain.OrderFilterChain;
import com.example.transaction.architecture.patterns.chain.OrderSubmitRequest;
import com.example.transaction.architecture.patterns.factory.CreateOrderRequest;
import com.example.transaction.architecture.patterns.factory.Order;
import com.example.transaction.architecture.patterns.factory.OrderFactory;
import com.example.transaction.architecture.patterns.observer.OrderEventPublisher;
import com.example.transaction.architecture.patterns.strategy.PayContext;
import com.example.transaction.architecture.patterns.strategy.PayResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 架构设计模块 — 演示接口
 */
@Slf4j
@RestController
@RequestMapping("/api/architecture")
@RequiredArgsConstructor
public class ArchitectureController {

    private final PayContext payContext;
    private final OrderFactory orderFactory;
    private final OrderEventPublisher eventPublisher;
    private final OrderFilterChain filterChain;
    private final DddDemo dddDemo;
    private final IdGeneratorDemo idGeneratorDemo;

    // ==================== 策略模式 ====================

    /**
     * 策略模式 — 支付演示
     * GET /api/architecture/strategy/pay?channel=ALIPAY&amount=100&orderId=ORD-001
     */
    @GetMapping("/strategy/pay")
    public PayResult strategyPay(
            @RequestParam String channel,
            @RequestParam BigDecimal amount,
            @RequestParam String orderId) {
        log.info("===== 策略模式演示 =====");
        return payContext.executePay(channel, amount, orderId);
    }

    /**
     * 策略模式 — 查看支持的支付渠道
     */
    @GetMapping("/strategy/channels")
    public Map<String, Object> strategyChannels() {
        return Map.of(
                "channels", payContext.getSupportedChannels(),
                "count", payContext.getSupportedChannels().size()
        );
    }

    // ==================== 工厂模式 ====================

    /**
     * 工厂模式 — 创建订单
     * POST /api/architecture/factory/order
     * Body: {"type":"NORMAL","userId":"U001","productId":"P001","quantity":2,"unitPrice":99.9}
     */
    @PostMapping("/factory/order")
    public Order factoryCreateOrder(@RequestBody CreateOrderRequest request) {
        log.info("===== 工厂模式演示 =====");
        return orderFactory.createOrder(request);
    }

    // ==================== 观察者模式 ====================

    /**
     * 观察者模式 — 触发订单创建事件
     * GET /api/architecture/observer/order-created?orderId=ORD-001&userId=U001&productId=P001&quantity=2&amount=199.8
     */
    @GetMapping("/observer/order-created")
    public Map<String, Object> observerOrderCreated(
            @RequestParam String orderId,
            @RequestParam String userId,
            @RequestParam String productId,
            @RequestParam int quantity,
            @RequestParam BigDecimal amount) {
        log.info("===== 观察者模式演示 =====");
        eventPublisher.publishOrderCreated(orderId, userId, productId, quantity, amount);
        return Map.of("success", true, "message", "事件已发布，观察者将异步处理");
    }

    /**
     * 观察者模式 — 触发订单支付事件
     */
    @GetMapping("/observer/order-paid")
    public Map<String, Object> observerOrderPaid(
            @RequestParam String orderId,
            @RequestParam String userId,
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "ALIPAY") String channel) {
        log.info("===== 观察者模式演示 =====");
        eventPublisher.publishOrderPaid(orderId, userId, amount, channel);
        return Map.of("success", true, "message", "支付事件已发布");
    }

    // ==================== 责任链模式 ====================

    /**
     * 责任链模式 — 订单校验
     * GET /api/architecture/chain/validate?userId=U001&productId=P001&quantity=2&amount=199.8&userIp=192.168.1.1
     */
    @GetMapping("/chain/validate")
    public OrderFilterChain.ChainResult chainValidate(
            @RequestParam String userId,
            @RequestParam String productId,
            @RequestParam int quantity,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String couponCode,
            @RequestParam(defaultValue = "192.168.1.1") String userIp) {
        log.info("===== 责任链模式演示 =====");
        OrderSubmitRequest request = new OrderSubmitRequest(userId, productId, quantity, amount, couponCode, userIp);
        return filterChain.doFilter(request);
    }

    // ==================== 模板方法 ====================

    /**
     * 模板方法 — 导出数据
     * GET /api/architecture/template/export?format=excel&fileName=orders
     */
    @GetMapping("/template/export")
    public Map<String, Object> templateExport(
            @RequestParam(defaultValue = "excel") String format,
            @RequestParam(defaultValue = "orders") String fileName) {
        log.info("===== 模板方法模式演示 =====");
        return Map.of(
                "success", true,
                "format", format,
                "fileName", fileName,
                "message", "导出流程: 校验参数 → 查询数据 → 转换格式(" + format + ") → 写入文件 → 上传OSS"
        );
    }

    // ==================== 装饰器模式 ====================

    /**
     * 装饰器模式 — 演示说明
     * GET /api/architecture/decorator/info
     */
    @GetMapping("/decorator/info")
    public Map<String, Object> decoratorInfo() {
        log.info("===== 装饰器模式演示 =====");
        return Map.of(
                "pattern", "装饰器模式",
                "description", "动态给对象添加额外职责",
                "layers", java.util.List.of(
                        Map.of("layer", "RealUserService", "功能", "核心业务逻辑"),
                        Map.of("layer", "CacheDecorator", "功能", "添加缓存能力"),
                        Map.of("layer", "LogDecorator", "功能", "添加日志记录")
                ),
                "combination", "new LogDecorator(new CacheDecorator(realService))"
        );
    }

    // ==================== 分布式 ID ====================

    /**
     * 分布式 ID — 生成演示
     * GET /api/architecture/distributed-id?type=snowflake&count=5
     */
    @GetMapping("/distributed-id")
    public Map<String, Object> distributedId(
            @RequestParam(defaultValue = "snowflake") String type,
            @RequestParam(defaultValue = "5") int count) {
        return idGeneratorDemo.generate(type, count);
    }

    // ==================== DDD ====================

    /**
     * DDD — 创建订单演示
     * GET /api/architecture/ddd/create-order?userId=U001&productId=P001&productName=iPhone&quantity=1&price=7999&userLevel=VIP
     */
    @GetMapping("/ddd/create-order")
    public Map<String, Object> dddCreateOrder(
            @RequestParam String userId,
            @RequestParam String productId,
            @RequestParam(defaultValue = "商品") String productName,
            @RequestParam(defaultValue = "1") int quantity,
            @RequestParam(defaultValue = "99.9") BigDecimal price,
            @RequestParam(defaultValue = "NORMAL") String userLevel,
            @RequestParam(defaultValue = "NORMAL") String productType) {
        return dddDemo.createOrder(userId, productId, productName, quantity, price, userLevel, productType);
    }
}
