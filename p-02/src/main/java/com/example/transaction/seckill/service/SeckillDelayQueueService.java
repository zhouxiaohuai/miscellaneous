package com.example.transaction.seckill.service;

import com.example.transaction.seckill.entity.SeckillOrder;
import com.example.transaction.seckill.entity.SeckillProduct;
import com.example.transaction.seckill.repository.SeckillOrderRepository;
import com.example.transaction.seckill.repository.SeckillProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * P0-1：超时未支付 + 库存回补
 *
 * 方案：Redis ZSet 延迟队列（本地模式）
 *
 * 说明：
 *   由于 Podman 网络问题导致 RocketMQ 连接超时
 *   先用 Redis ZSet 实现延迟队列，保证功能可用
 *   后续可以升级为 RocketMQ 版本
 *
 * 流程：
 *   1. 秒杀成功 → 订单入队 Redis ZSet（score = 过期时间戳）
 *   2. 定时任务每秒扫描 → 找到已过期的订单
 *   3. 取消订单 → 回补 Redis 库存 → 回补数据库库存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillDelayQueueService {

    private final StringRedisTemplate redisTemplate;
    private final SeckillOrderRepository orderRepository;
    private final SeckillProductRepository productRepository;

    /** 延迟队列 key */
    private static final String DELAY_QUEUE_KEY = "seckill:delay:queue";

    /** 订单超时时间：10 分钟（毫秒） */
    private static final long ORDER_TIMEOUT_MS = 10 * 60 * 1000;

    // ============================
    // 入队
    // ============================

    /**
     * 订单入队
     *
     * @param orderNo   订单号
     * @param productId 商品ID
     */
    public void sendDelayMessage(String orderNo, Long productId) {
        // score = 当前时间戳 + 超时时间 = 过期时间戳
        long expireTime = System.currentTimeMillis() + ORDER_TIMEOUT_MS;

        // value = 订单号:商品ID
        String value = orderNo + ":" + productId;

        redisTemplate.opsForZSet().add(DELAY_QUEUE_KEY, value, expireTime);
        log.info("[延迟队列] 订单入队: orderNo={}, 过期时间={}", orderNo, expireTime);
    }

    // ============================
    // 定时扫描
    // ============================

    /**
     * 定时任务：每秒扫描延迟队列，处理过期订单
     */
    @Scheduled(fixedRate = 1000)
    public void processExpiredOrders() {
        long now = System.currentTimeMillis();

        // 取出 score <= 当前时间戳 的所有元素
        var expiredOrders = redisTemplate.opsForZSet()
                .rangeByScore(DELAY_QUEUE_KEY, 0, now);

        if (expiredOrders == null || expiredOrders.isEmpty()) {
            return;
        }

        log.info("[延迟队列] 发现 {} 个过期订单，开始处理", expiredOrders.size());

        for (String item : expiredOrders) {
            try {
                String[] parts = item.split(":");
                if (parts.length != 2) {
                    log.error("[延迟队列] 格式错误: {}", item);
                    removeItem(item);
                    continue;
                }

                String orderNo = parts[0];
                Long productId = Long.parseLong(parts[1]);

                boolean success = processTimeoutOrder(orderNo, productId);
                if (success) {
                    removeItem(item);
                    log.info("[延迟队列] 订单 {} 超时取消完成", orderNo);
                }
            } catch (Exception e) {
                log.error("[延迟队列] 处理失败: {}", item, e);
            }
        }
    }

    // ============================
    // 核心逻辑
    // ============================

    /**
     * 处理超时订单
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean processTimeoutOrder(String orderNo, Long productId) {
        SeckillOrder order = orderRepository.findAll().stream()
                .filter(o -> o.getOrderNo().equals(orderNo))
                .findFirst()
                .orElse(null);

        if (order == null) {
            log.warn("[超时取消] 订单不存在: {}", orderNo);
            return true;
        }

        if (order.getStatus() != 0) {
            log.info("[超时取消] 订单状态非待支付，跳过: orderNo={}, status={}", orderNo, order.getStatus());
            return true;
        }

        // 取消订单
        order.setStatus(3);
        orderRepository.save(order);

        // 回补 Redis 库存
        String stockKey = "seckill:stock:" + productId;
        redisTemplate.opsForValue().increment(stockKey);

        // 回补数据库库存
        SeckillProduct product = productRepository.findById(productId).orElse(null);
        if (product != null) {
            product.setStock(product.getStock() + 1);
            productRepository.save(product);
        }

        // 从已购用户集合中移除
        String boughtKey = "seckill:bought:" + productId;
        redisTemplate.opsForSet().remove(boughtKey, String.valueOf(order.getUserId()));

        log.info("[超时取消] 完成: orderNo={}, userId={}, productId={}", orderNo, order.getUserId(), productId);
        return true;
    }

    // ============================
    // 工具方法
    // ============================

    private void removeItem(String item) {
        redisTemplate.opsForZSet().remove(DELAY_QUEUE_KEY, item);
    }

    public Long getPendingCount() {
        return redisTemplate.opsForZSet().zCard(DELAY_QUEUE_KEY);
    }
}
