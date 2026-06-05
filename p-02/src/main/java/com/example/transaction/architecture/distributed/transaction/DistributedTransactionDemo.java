package com.example.transaction.architecture.distributed.transaction;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * ============================================================
 * 分布式事务 — 方案对比与演示
 * ============================================================
 *
 * 【为什么需要分布式事务？】
 * 单体应用：一个数据库，本地事务（@Transactional）就够了
 * 微服务：多个服务、多个数据库，本地事务无法跨服务保证一致性
 *
 * 【CAP 定理回顾】
 * C（一致性）：所有节点看到相同数据
 * A（可用性）：每个请求都能得到响应
 * P（分区容忍）：网络分区时系统仍能运行
 * 三者只能选其二，分布式系统必须容忍 P，所以在 C 和 A 之间选择。
 *
 * 【常见方案对比】
 * ┌──────────────┬──────────┬──────────┬──────────┬──────────┐
 * │ 方案          │ 一致性   │ 性能     │ 复杂度   │ 适用场景  │
 * ├──────────────┼──────────┼──────────┼──────────┼──────────┤
 * │ 2PC（XA）     │ 强一致   │ 低       │ 低       │ 传统金融  │
 * │ TCC           │ 最终一致 │ 高       │ 高       │ 核心交易  │
 * │ Saga          │ 最终一致 │ 高       │ 中       │ 长流程    │
 * │ 本地消息表    │ 最终一致 │ 高       │ 中       │ 异步场景  │
 * │ 事务消息      │ 最终一致 │ 高       │ 中       │ MQ 场景   │
 * └──────────────┴──────────┴──────────┴──────────┴──────────┘
 *
 * 【本示例场景】
 * 下单流程涉及三个服务：
 * 1. 订单服务 — 创建订单
 * 2. 库存服务 — 扣减库存
 * 3. 账户服务 — 扣减余额
 *
 * 任何一个失败都需要回滚其他操作。
 */

// ==================== 业务模型 ====================

/**
 * 下单请求
 */
record CreateOrderDTO(
        String userId,
        String productId,
        int quantity,
        BigDecimal amount
) {}

/**
 * 下单结果
 */
record OrderResult(boolean success, String orderId, String message) {}

// ==================== 方案 1: TCC 模式 ====================

/**
 * ============================================================
 * TCC（Try-Confirm-Cancel）模式
 * ============================================================
 *
 * 【原理】
 * 将一个分布式事务拆分为三个阶段：
 * 1. Try：预留资源（冻结库存、冻结余额）
 * 2. Confirm：确认执行（真正扣减）
 * 3. Cancel：取消回滚（释放冻结的资源）
 *
 * 【流程图】
 * ┌──────────┐     Try      ┌──────────┐ ┌──────────┐ ┌──────────┐
 * │  事务管理器 │──────────▶│ 订单服务   │ │ 库存服务  │ │ 账户服务  │
 * │  (TM)     │             │ 创建预订单 │ │ 冻结库存  │ │ 冻结余额  │
 * └──────────┘             └──────────┘ └──────────┘ └──────────┘
 *      │                        │            │            │
 *      │  全部成功？              │            │            │
 *      │  Yes                   ▼            ▼            ▼
 *      │────────────▶  Confirm: 确认订单  扣减库存    扣减余额
 *      │
 *      │  有失败？                ▼            ▼            ▼
 *      └────────────▶  Cancel:  取消订单  释放库存    释放余额
 *
 * 【优点】
 * - 不锁数据库行，性能高
 * - 业务侵入性大（每个服务都要实现 Try/Confirm/Cancel）
 *
 * 【缺点】
 * - 代码复杂（三个接口都要实现）
 * - Confirm/Cancel 需要幂等（可能被重试调用）
 * - 空回滚问题（Try 没执行，Cancel 被调用）
 */

@Slf4j
class TccOrderService {

    /**
     * Try 阶段 — 预留资源
     */
    public boolean tryCreateOrder(CreateOrderDTO dto) {
        log.info("[TCC-Try] 创建预订单: userId={}, productId={}, amount={}",
                dto.userId(), dto.productId(), dto.amount());
        // 1. 创建预订单（状态：待确认）
        // 2. 冻结库存（stock_frozen += quantity）
        // 3. 冻结余额（balance_frozen += amount）
        log.info("[TCC-Try] 资源预留成功");
        return true;
    }

    /**
     * Confirm 阶段 — 确认执行
     */
    public boolean confirmCreateOrder(String orderId) {
        log.info("[TCC-Confirm] 确认订单: orderId={}", orderId);
        // 1. 更新订单状态为"已确认"
        // 2. 真正扣减库存（stock -= quantity, stock_frozen -= quantity）
        // 3. 真正扣减余额（balance -= amount, balance_frozen -= amount）
        log.info("[TCC-Confirm] 订单确认成功");
        return true;
    }

    /**
     * Cancel 阶段 — 取消回滚
     */
    public boolean cancelCreateOrder(String orderId) {
        log.info("[TCC-Cancel] 取消订单: orderId={}", orderId);
        // 1. 更新订单状态为"已取消"
        // 2. 释放冻结库存（stock_frozen -= quantity）
        // 3. 释放冻结余额（balance_frozen -= amount）
        log.info("[TCC-Cancel] 订单取消成功，资源已释放");
        return true;
    }
}

// ==================== 方案 2: Saga 模式 ====================

/**
 * ============================================================
 * Saga 模式
 * ============================================================
 *
 * 【原理】
 * 将长事务拆分为多个本地事务，每个本地事务有对应的补偿操作。
 * 顺序执行所有本地事务，如果某个失败，逆序执行补偿操作。
 *
 * 【与 TCC 的区别】
 * TCC：Try 阶段预留资源，Confirm 才真正执行
 * Saga：每个步骤直接执行，失败时用补偿操作回滚
 *
 * 【流程图（正向）】
 * 创建订单 → 扣减库存 → 扣减余额 → 发送通知
 *
 * 【流程图（补偿）】
 * 创建订单 ← 恢复库存 ← 恢复余额 ← (通知无需补偿)
 *
 * 【适用场景】
 * - 长流程业务（如旅行预订：机票→酒店→租车）
 * - 第三方服务无法提供 Try/Cancel 接口
 * - 业务上能接受短暂不一致
 */
@Slf4j
class SagaOrderService {

    /**
     * 正向操作：创建订单
     */
    public boolean createOrder(CreateOrderDTO dto) {
        log.info("[Saga] 创建订单: userId={}, amount={}", dto.userId(), dto.amount());
        return true;
    }

    /**
     * 补偿操作：取消订单
     */
    public boolean compensateCreateOrder(String orderId) {
        log.info("[Saga-补偿] 取消订单: orderId={}", orderId);
        return true;
    }

    /**
     * 正向操作：扣减库存
     */
    public boolean deductStock(String productId, int quantity) {
        log.info("[Saga] 扣减库存: productId={}, quantity={}", productId, quantity);
        return true;
    }

    /**
     * 补偿操作：恢复库存
     */
    public boolean compensateDeductStock(String productId, int quantity) {
        log.info("[Saga-补偿] 恢复库存: productId={}, quantity={}", productId, quantity);
        return true;
    }

    /**
     * 正向操作：扣减余额
     */
    public boolean deductBalance(String userId, BigDecimal amount) {
        log.info("[Saga] 扣减余额: userId={}, amount={}", userId, amount);
        return true;
    }

    /**
     * 补偿操作：恢复余额
     */
    public boolean compensateDeductBalance(String userId, BigDecimal amount) {
        log.info("[Saga-补偿] 恢复余额: userId={}, amount={}", userId, amount);
        return true;
    }
}

// ==================== 方案 3: 本地消息表 ====================

/**
 * ============================================================
 * 本地消息表模式
 * ============================================================
 *
 * 【原理】
 * 1. 业务操作和消息写入在同一个本地事务中
 * 2. 后台任务轮询消息表，发送到消息队列
 * 3. 消费者处理消息，实现最终一致性
 *
 * 【流程图】
 * ┌──────────┐  本地事务  ┌──────────┐  轮询  ┌──────────┐  消费  ┌──────────┐
 * │ 订单服务   │─────────▶│ 本地消息表 │──────▶│ 消息队列  │──────▶│ 库存服务  │
 * │ 创建订单   │ 同一事务  │ 待发送    │       │ RocketMQ │       │ 扣减库存  │
 * └──────────┘          └──────────┘       └──────────┘       └──────────┘
 *
 * 【优点】
 * - 可靠性高（消息不会丢）
 * - 实现简单（不需要分布式事务框架）
 *
 * 【缺点】
 * - 有延迟（轮询间隔）
 * - 需要额外的消息表
 * - 消费者需要幂等
 */
@Slf4j
class LocalMessageService {

    /**
     * 步骤 1: 创建订单 + 写消息（同一事务）
     */
    public void createOrderWithMessage(CreateOrderDTO dto) {
        log.info("[本地消息表] 创建订单: userId={}, amount={}", dto.userId(), dto.amount());

        // 同一事务中：
        // 1. INSERT INTO t_order (user_id, product_id, amount) VALUES (...)
        // 2. INSERT INTO t_outbox_message (id, topic, payload, status)
        //    VALUES ('xxx', 'order.created', '{"orderId":"xxx"}', 'PENDING')

        log.info("[本地消息表] 订单和消息写入成功（同一事务）");
    }

    /**
     * 步骤 2: 后台任务轮询发送消息
     */
    public void pollAndSend() {
        // 定时任务：SELECT * FROM t_outbox_message WHERE status = 'PENDING' LIMIT 100
        // 发送到 RocketMQ
        // 更新状态为 SENT
        log.info("[本地消息表] 轮询发送消息...");
    }

    /**
     * 步骤 3: 消费者处理（幂等）
     */
    public void onMessage(String messageId, String payload) {
        // 1. 检查消息是否已处理（防重复消费）
        // 2. 执行业务逻辑（扣减库存）
        // 3. 标记消息为已处理
        log.info("[本地消息表] 消费消息: messageId={}", messageId);
    }
}
