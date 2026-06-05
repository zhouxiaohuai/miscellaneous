package com.example.transaction.architecture.ddd;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * ============================================================
 * DDD（领域驱动设计）— 订单聚合根
 * ============================================================
 *
 * 【DDD 核心概念】
 *
 * 1. 实体（Entity）
 *    - 有唯一标识（ID）
 *    - 有生命周期（创建→修改→删除）
 *    - 通过 ID 判断相等性
 *    - 示例：Order、User、Product
 *
 * 2. 值对象（Value Object）
 *    - 没有唯一标识
 *    - 不可变（immutable）
 *    - 通过属性值判断相等性
 *    - 示例：Money、Address、OrderItem
 *
 * 3. 聚合（Aggregate）
 *    - 一组相关对象的集合
 *    - 有一个聚合根（Aggregate Root）
 *    - 外部只能通过聚合根访问内部对象
 *    - 保证业务不变量（invariants）
 *
 * 4. 聚合根（Aggregate Root）
 *    - 聚合的入口
 *    - 有全局唯一标识
 *    - 负责维护业务规则
 *    - 示例：Order（聚合根）包含 OrderItem（内部实体）
 *
 * 5. 领域服务（Domain Service）
 *    - 不属于任何实体的业务逻辑
 *    - 通常是跨聚合的操作
 *    - 示例：价格计算服务、库存检查服务
 *
 * 6. 领域事件（Domain Event）
 *    - 表示领域中发生的有意义的事情
 *    - 用于解耦聚合之间的通信
 *    - 示例：OrderCreatedEvent、PaymentCompletedEvent
 *
 * 【贫血模型 vs 充血模型】
 *
 * 贫血模型（传统 MVC）：
 *   Entity 只有 getter/setter，逻辑在 Service 中
 *   → Service 臃肿，Entity 是"数据容器"
 *
 * 充血模型（DDD）：
 *   Entity 包含业务逻辑，Service 只做编排
 *   → 内聚性高，职责清晰
 *
 * 【本示例】
 * 用一个订单聚合演示 DDD 的核心概念：
 * - Order（聚合根）：管理订单生命周期
 * - OrderItem（实体）：订单项
 * - Money（值对象）：金额
 * - OrderCreatedEvent（领域事件）
 */

// ==================== 值对象 ====================

/**
 * 金额值对象
 *
 * 【值对象特点】
 * - 不可变（record 天然不可变）
 * - 无 ID
 * - 通过值判断相等
 * - 自包含业务逻辑
 */
record Money(BigDecimal amount, String currency) {

    Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("金额不能为负数");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("币种不能为空");
        }
    }

    static Money of(BigDecimal amount) {
        return new Money(amount, "CNY");
    }

    static Money zero() {
        return new Money(BigDecimal.ZERO, "CNY");
    }

    Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("币种不同，无法相加");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    Money multiply(int quantity) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)), this.currency);
    }

    boolean isGreaterThan(Money other) {
        return this.amount.compareTo(other.amount) > 0;
    }
}

// ==================== 实体 ====================

/**
 * 订单项实体
 *
 * 【实体特点】
 * - 有唯一标识（id）
 * - 有业务逻辑
 * - 可变状态
 */
@Slf4j
class OrderItem {
    private final String id;
    private final String productId;
    private final String productName;
    private final int quantity;
    private final Money unitPrice;

    OrderItem(String productId, String productName, int quantity, Money unitPrice) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("数量必须大于0");
        }
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    /**
     * 计算小计（业务逻辑在实体内部）
     */
    Money subtotal() {
        return unitPrice.multiply(quantity);
    }

    // Getters
    String id() { return id; }
    String productId() { return productId; }
    String productName() { return productName; }
    int quantity() { return quantity; }
    Money unitPrice() { return unitPrice; }
}

// ==================== 聚合根 ====================

/**
 * 订单聚合根
 *
 * 【聚合根职责】
 * 1. 维护业务不变量（invariants）
 *    - 订单必须有至少一个订单项
 *    - 订单金额必须大于 0
 *    - 已确认的订单不能修改
 *
 * 2. 控制内部对象的访问
 *    - 外部不能直接修改 OrderItem
 *    - 必须通过 Order 的方法操作
 *
 * 3. 发布领域事件
 *    - 状态变更时发布事件
 */
@Slf4j
class OrderAggregate {

    // ==================== 状态枚举 ====================
    enum Status { DRAFT, CONFIRMED, PAID, SHIPPED, CANCELLED }

    // ==================== 领域事件 ====================
    sealed interface OrderDomainEvent
            permits OrderDomainEvent.Created, OrderDomainEvent.Paid, OrderDomainEvent.Cancelled {
        record Created(String orderId, String userId, Money totalAmount) implements OrderDomainEvent {}
        record Paid(String orderId, Money paidAmount) implements OrderDomainEvent {}
        record Cancelled(String orderId, String reason) implements OrderDomainEvent {}
    }

    // ==================== 字段 ====================
    private final String id;
    private final String userId;
    private final List<OrderItem> items;
    private Status status;
    private Money totalAmount;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 领域事件列表（待发布）
    private final List<OrderDomainEvent> events = new ArrayList<>();

    // ==================== 构造器（工厂方法） ====================

    private OrderAggregate(String userId) {
        this.id = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.userId = userId;
        this.items = new ArrayList<>();
        this.status = Status.DRAFT;
        this.totalAmount = Money.zero();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 工厂方法 — 创建订单
     *
     * 【为什么用工厂方法而不是构造器？】
     * 1. 可以包含业务逻辑（如校验）
     * 2. 可以发布领域事件
     * 3. 语义更清晰（OrderAggregate.create vs new OrderAggregate）
     */
    static OrderAggregate create(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        OrderAggregate order = new OrderAggregate(userId);

        // 发布领域事件
        order.events.add(new OrderDomainEvent.Created(order.id, userId, order.totalAmount));
        log.info("[DDD] 订单创建: orderId={}, userId={}", order.id, userId);

        return order;
    }

    // ==================== 业务方法 ====================

    /**
     * 添加订单项
     *
     * 【业务规则】只有 DRAFT 状态可以添加订单项
     */
    void addItem(String productId, String productName, int quantity, Money unitPrice) {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("只有草稿状态可以添加订单项，当前状态: " + status);
        }

        OrderItem item = new OrderItem(productId, productName, quantity, unitPrice);
        items.add(item);
        recalculateTotal();
        updatedAt = LocalDateTime.now();

        log.info("[DDD] 添加订单项: orderId={}, productId={}, subtotal={}",
                id, productId, item.subtotal().amount());
    }

    /**
     * 确认订单
     *
     * 【业务规则】
     * 1. 必须有至少一个订单项
     * 2. 只有 DRAFT 状态可以确认
     */
    void confirm() {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("只有草稿状态可以确认，当前状态: " + status);
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("订单必须有至少一个订单项");
        }

        this.status = Status.CONFIRMED;
        this.updatedAt = LocalDateTime.now();
        log.info("[DDD] 订单确认: orderId={}, totalAmount={}", id, totalAmount.amount());
    }

    /**
     * 支付
     *
     * 【业务规则】只有 CONFIRMED 状态可以支付
     */
    void pay() {
        if (status != Status.CONFIRMED) {
            throw new IllegalStateException("只有已确认状态可以支付，当前状态: " + status);
        }

        this.status = Status.PAID;
        this.updatedAt = LocalDateTime.now();

        // 发布领域事件
        events.add(new OrderDomainEvent.Paid(id, totalAmount));
        log.info("[DDD] 订单支付: orderId={}, amount={}", id, totalAmount.amount());
    }

    /**
     * 取消订单
     *
     * 【业务规则】已支付的订单不能取消
     */
    void cancel(String reason) {
        if (status == Status.PAID || status == Status.SHIPPED) {
            throw new IllegalStateException("已支付/已发货的订单不能取消");
        }

        this.status = Status.CANCELLED;
        this.updatedAt = LocalDateTime.now();

        // 发布领域事件
        events.add(new OrderDomainEvent.Cancelled(id, reason));
        log.info("[DDD] 订单取消: orderId={}, reason={}", id, reason);
    }

    // ==================== 内部方法 ====================

    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money::add)
                .orElse(Money.zero());
    }

    /**
     * 获取并清空待发布的领域事件
     */
    List<OrderDomainEvent> pullEvents() {
        List<OrderDomainEvent> result = new ArrayList<>(events);
        events.clear();
        return Collections.unmodifiableList(result);
    }

    // ==================== Getters ====================

    String id() { return id; }
    String userId() { return userId; }
    List<OrderItem> items() { return Collections.unmodifiableList(items); }
    Status status() { return status; }
    Money totalAmount() { return totalAmount; }
    LocalDateTime createdAt() { return createdAt; }
}
