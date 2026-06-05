package com.example.transaction.architecture.patterns.observer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ============================================================
 * 观察者模式 — 订单事件对象
 * ============================================================
 *
 * 【定义】
 * 定义对象间的一种一对多的依赖关系，当一个对象的状态发生改变时，
 * 所有依赖于它的对象都得到通知并被自动更新。
 *
 * 【适用场景】
 * - 事件驱动：订单创建后发短信、扣库存、记日志
 * - 消息通知：状态变更通知多个下游系统
 * - 解耦发布者和订阅者
 *
 * 【本示例场景】
 * 订单状态变更时，需要：
 * 1. 发送短信通知用户
 * 2. 扣减库存
 * 3. 记录操作日志
 * 4. 更新统计报表
 *
 * 如果把这些逻辑都写在订单 Service 里，会非常臃肿。
 * 用观察者模式（Spring Event），订单 Service 只负责发事件，
 * 各个监听器独立处理自己的逻辑。
 *
 * 【Spring Event vs 消息队列】
 * Spring Event：进程内、同步/异步、轻量、适合单体应用
 * 消息队列（RocketMQ/Kafka）：跨进程、可靠投递、适合微服务
 */
public sealed interface OrderEvent permits OrderEvent.OrderCreated, OrderEvent.OrderPaid, OrderEvent.OrderCancelled {

    String orderId();
    String userId();
    LocalDateTime occurredAt();

    /**
     * 订单创建事件
     */
    record OrderCreated(
            String orderId,
            String userId,
            String productId,
            int quantity,
            BigDecimal amount,
            LocalDateTime occurredAt
    ) implements OrderEvent {
        public OrderCreated {
            if (occurredAt == null) occurredAt = LocalDateTime.now();
        }
    }

    /**
     * 订单支付事件
     */
    record OrderPaid(
            String orderId,
            String userId,
            BigDecimal paidAmount,
            String payChannel,
            LocalDateTime occurredAt
    ) implements OrderEvent {
        public OrderPaid {
            if (occurredAt == null) occurredAt = LocalDateTime.now();
        }
    }

    /**
     * 订单取消事件
     */
    record OrderCancelled(
            String orderId,
            String userId,
            String reason,
            LocalDateTime occurredAt
    ) implements OrderEvent {
        public OrderCancelled {
            if (occurredAt == null) occurredAt = LocalDateTime.now();
        }
    }
}
