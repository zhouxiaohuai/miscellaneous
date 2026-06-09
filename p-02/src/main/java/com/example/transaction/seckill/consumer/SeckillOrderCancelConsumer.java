package com.example.transaction.seckill.consumer;

import com.example.transaction.seckill.service.SeckillDelayQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 消费者：秒杀订单超时取消
 *
 * @RocketMQMessageListener 参数说明：
 *   topic = 消息主题（和发送方一致）
 *   consumerGroup = 消费者组（同一组的消息只会被消费一次）
 *
 * 消费流程：
 *   1. RocketMQ 在延迟时间到达后，投递消息给消费者
 *   2. 消费者解析消息（orderNo:productId）
 *   3. 调用 SeckillDelayQueueService 处理超时取消
 *   4. 如果消费失败，RocketMQ 会自动重试
 */
@Slf4j
// @Component  // 暂时禁用 Consumer，先测试 Producer
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "seckill-order-cancel",
        consumerGroup = "seckill-cancel-consumer-group",
        nameServer = "localhost:9876"
)
public class SeckillOrderCancelConsumer implements RocketMQListener<String> {

    private final SeckillDelayQueueService delayQueueService;

    @Override
    public void onMessage(String body) {
        log.info("[消费者] 收到延迟消息: {}", body);

        try {
            // 解析消息：orderNo:productId
            String[] parts = body.split(":");
            if (parts.length != 2) {
                log.error("[消费者] 消息格式错误: {}", body);
                return; // 格式错误，不重试
            }

            String orderNo = parts[0];
            Long productId = Long.parseLong(parts[1]);

            // 处理超时取消
            boolean success = delayQueueService.processTimeoutOrder(orderNo, productId);

            if (success) {
                log.info("[消费者] 订单 {} 超时取消处理成功", orderNo);
            } else {
                log.warn("[消费者] 订单 {} 超时取消处理失败，等待重试", orderNo);
                throw new RuntimeException("处理失败，触发重试");
            }

        } catch (NumberFormatException e) {
            log.error("[消费者] 消息解析失败: {}", body, e);
            // 解析失败，不重试
        } catch (Exception e) {
            log.error("[消费者] 处理异常: {}", body, e);
            throw e; // 抛出异常，触发 RocketMQ 重试
        }
    }
}
