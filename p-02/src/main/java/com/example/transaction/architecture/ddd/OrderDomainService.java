package com.example.transaction.architecture.ddd;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * ============================================================
 * DDD — 领域服务 + 应用服务
 * ============================================================
 *
 * 【领域服务 vs 应用服务】
 *
 * 领域服务（Domain Service）：
 * - 包含不属于任何实体的业务逻辑
 * - 通常是跨聚合的操作
 * - 无状态
 * - 示例：价格计算、库存校验
 *
 * 应用服务（Application Service）：
 * - 编排领域对象完成用例
 * - 事务管理
 * - 调用外部服务（MQ、第三方API）
 * - 不包含业务逻辑
 * - 示例：下单用例、支付用例
 *
 * 【DDD 分层架构】
 * ┌─────────────────────────────────────────┐
 * │ 用户接口层（Controller / API）            │
 * ├─────────────────────────────────────────┤
 * │ 应用服务层（Application Service）         │  ← 编排用例
 * ├─────────────────────────────────────────┤
 * │ 领域层（Domain）                          │  ← 核心业务
 * │   ├── 实体（Entity）                      │
 * │   ├── 值对象（Value Object）              │
 * │   ├── 聚合（Aggregate）                   │
 * │   ├── 领域服务（Domain Service）           │
 * │   └── 领域事件（Domain Event）             │
 * ├─────────────────────────────────────────┤
 * │ 基础设施层（Repository / MQ / 外部服务）    │  ← 技术实现
 * └─────────────────────────────────────────┘
 */

// ==================== 领域服务 ====================

/**
 * 价格领域服务
 *
 * 【什么时候用领域服务？】
 * 当业务逻辑不属于任何一个实体时：
 * - 计算折扣（涉及用户等级 + 商品 + 活动）
 * - 库存校验（跨聚合）
 * - 价格比较（涉及多个订单）
 */
@Slf4j
class PriceDomainService {

    /**
     * 计算折扣价格
     *
     * 这个逻辑不属于 Order 也不属于 Product，
     * 它涉及用户等级、商品类型、促销活动等多个因素，
     * 所以放在领域服务中。
     */
    Money calculateDiscountedPrice(Money originalPrice, String userLevel, String productType) {
        BigDecimal discountRate = switch (userLevel) {
            case "VIP" -> new BigDecimal("0.90");   // 9 折
            case "SVIP" -> new BigDecimal("0.85");  // 85 折
            default -> BigDecimal.ONE;                // 无折扣
        };

        // 特殊商品类型额外折扣
        if ("CLEARANCE".equals(productType)) {
            discountRate = discountRate.multiply(new BigDecimal("0.80")); // 再打 8 折
        }

        BigDecimal discounted = originalPrice.amount().multiply(discountRate);
        Money result = Money.of(discounted);
        log.info("[领域服务] 价格计算: 原价={}, 用户等级={}, 折后价={}",
                originalPrice.amount(), userLevel, result.amount());
        return result;
    }
}

// ==================== 应用服务 ====================

/**
 * 下单应用服务
 *
 * 【应用服务职责】
 * 1. 编排领域对象完成用例
 * 2. 管理事务边界
 * 3. 调用外部服务（库存检查、消息发送）
 * 4. 不包含业务逻辑（业务逻辑在领域层）
 */
@Slf4j
class CreateOrderAppService {

    private final PriceDomainService priceService;

    CreateOrderAppService(PriceDomainService priceService) {
        this.priceService = priceService;
    }

    /**
     * 下单用例
     *
     * 【编排流程】
     * 1. 创建订单聚合根
     * 2. 添加订单项（调用领域服务计算价格）
     * 3. 确认订单
     * 4. 保存到数据库（Repository）
     * 5. 发布领域事件
     */
    String execute(CreateOrderCommand cmd) {
        log.info("[应用服务] 开始下单: userId={}, productId={}", cmd.userId(), cmd.productId());

        // Step 1: 创建订单聚合根
        OrderAggregate order = OrderAggregate.create(cmd.userId());

        // Step 2: 计算折扣价格（领域服务）
        Money originalPrice = Money.of(cmd.unitPrice());
        Money discountedPrice = priceService.calculateDiscountedPrice(
                originalPrice, cmd.userLevel(), cmd.productType());

        // Step 3: 添加订单项
        order.addItem(cmd.productId(), cmd.productName(), cmd.quantity(), discountedPrice);

        // Step 4: 确认订单
        order.confirm();

        // Step 5: 保存到数据库（模拟）
        log.info("[应用服务] 保存订单到数据库: orderId={}", order.id());

        // Step 6: 发布领域事件
        order.pullEvents().forEach(event ->
                log.info("[应用服务] 发布领域事件: {}", event));

        log.info("[应用服务] 下单完成: orderId={}, 总金额={}", order.id(), order.totalAmount().amount());
        return order.id();
    }
}

/**
 * 下单命令（用例入参）
 */
record CreateOrderCommand(
        String userId,
        String productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        String userLevel,
        String productType
) {}
