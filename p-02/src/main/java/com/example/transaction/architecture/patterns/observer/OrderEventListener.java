package com.example.transaction.architecture.patterns.observer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * ============================================================
 * 观察者模式 — 事件监听器（Spring Event 实现）
 * ============================================================
 *
 * 【Spring Event 核心注解】
 * @EventListener — 标记方法为事件监听器
 * @Async — 异步执行（需配合 @EnableAsync）
 * @TransactionalEventListener — 在事务提交后执行
 *
 * 【执行顺序控制】
 * @Order(1) — 数字越小优先级越高
 *
 * 【同步 vs 异步】
 * 同步：发送方等待所有监听器执行完毕，适合需要保证顺序的场景
 * 异步：发送方立即返回，监听器在独立线程执行，适合通知类操作
 */
@Slf4j
@Component
public class OrderEventListener {

    /**
     * 监听订单创建事件 — 发送短信通知
     *
     * @Async 让这个监听器异步执行，不阻塞订单创建流程
     */
    @Async
    @EventListener
    public void onOrderCreated(OrderEvent.OrderCreated event) {
        log.info("[观察者-短信] 收到订单创建事件: orderId={}, userId={}", event.orderId(), event.userId());
        // 模拟发送短信
        log.info("[观察者-短信] 发送短信: 您的订单 {} 已创建，金额 {} 元", event.orderId(), event.amount());
    }

    /**
     * 监听订单创建事件 — 扣减库存
     *
     * 这个不加 @Async，同步执行，确保库存扣减成功后再返回
     */
    @EventListener
    public void onOrderCreatedDeductStock(OrderEvent.OrderCreated event) {
        log.info("[观察者-库存] 收到订单创建事件: productId={}, quantity={}",
                event.productId(), event.quantity());
        // 模拟扣减库存
        log.info("[观察者-库存] 库存扣减成功: productId={}, 剩余 99", event.productId());
    }

    /**
     * 监听订单创建事件 — 记录操作日志
     */
    @Async
    @EventListener
    public void onOrderCreatedLog(OrderEvent.OrderCreated event) {
        log.info("[观察者-日志] 记录操作日志: 用户 {} 创建了订单 {}，金额 {}",
                event.userId(), event.orderId(), event.amount());
    }

    /**
     * 监听订单支付事件 — 更新支付状态
     */
    @EventListener
    public void onOrderPaid(OrderEvent.OrderPaid event) {
        log.info("[观察者-支付] 订单已支付: orderId={}, 金额={}, 渠道={}",
                event.orderId(), event.paidAmount(), event.payChannel());
    }

    /**
     * 监听订单取消事件 — 恢复库存
     */
    @EventListener
    public void onOrderCancelled(OrderEvent.OrderCancelled event) {
        log.info("[观察者-库存] 订单取消，恢复库存: orderId={}, 原因={}",
                event.orderId(), event.reason());
    }
}
