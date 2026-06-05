package com.example.transaction.architecture.patterns.observer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 观察者模式 — 事件发布者
 *
 * 【解耦的关键】
 * 发布者（OrderEventPublisher）只依赖 ApplicationEventPublisher，
 * 完全不知道有哪些监听器、监听器做什么。
 * 这就是观察者模式的核心价值：发布者和订阅者互不知道对方的存在。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 发布订单创建事件
     */
    public void publishOrderCreated(String orderId, String userId,
                                     String productId, int quantity, java.math.BigDecimal amount) {
        OrderEvent.OrderCreated event = new OrderEvent.OrderCreated(orderId, userId, productId, quantity, amount, null);
        log.info("[事件发布] 发布订单创建事件: orderId={}", orderId);
        eventPublisher.publishEvent(event);
    }

    /**
     * 发布订单支付事件
     */
    public void publishOrderPaid(String orderId, String userId,
                                  java.math.BigDecimal paidAmount, String payChannel) {
        OrderEvent.OrderPaid event = new OrderEvent.OrderPaid(orderId, userId, paidAmount, payChannel, null);
        log.info("[事件发布] 发布订单支付事件: orderId={}", orderId);
        eventPublisher.publishEvent(event);
    }

    /**
     * 发布订单取消事件
     */
    public void publishOrderCancelled(String orderId, String userId, String reason) {
        OrderEvent.OrderCancelled event = new OrderEvent.OrderCancelled(orderId, userId, reason, null);
        log.info("[事件发布] 发布订单取消事件: orderId={}", orderId);
        eventPublisher.publishEvent(event);
    }
}
