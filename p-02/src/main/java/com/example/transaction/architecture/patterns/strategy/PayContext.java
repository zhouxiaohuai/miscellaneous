package com.example.transaction.architecture.patterns.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ============================================================
 * 策略模式 — 上下文（Context）
 * ============================================================
 *
 * 【角色说明】
 * Context 是策略模式的"调度中心"：
 * 1. 持有所有策略的引用（通过 Spring 自动注入）
 * 2. 根据客户端传入的 channel 选择对应策略
 * 3. 调用策略的 pay() 方法
 *
 * 【为什么用 Map 而不是 if-else？】
 * 传统写法：
 *   if ("ALIPAY".equals(channel)) return alipayStrategy.pay(...);
 *   else if ("WXPAY".equals(channel)) return wxPayStrategy.pay(...);
 *   else if ("BANK".equals(channel)) return bankStrategy.pay(...);
 *
 * 问题：每新增一种支付方式，都要改这个 if-else，违反开闭原则。
 *
 * Map 注入写法：
 *   Spring 会自动把所有 PayStrategy 实现注入到 Map 中，
 *   key 是 Bean 名称，value 是策略实例。
 *   新增支付方式只需加一个 @Component 类，这里完全不用改。
 */
@Slf4j
@Component
public class PayContext {

    /**
     * Spring 自动将所有 PayStrategy 实现注入到 Map
     * key = Bean 名称（如 "alipayStrategy"）
     * value = 策略实例
     */
    private final Map<String, PayStrategy> strategyMap;

    /**
     * 构造器注入 — 支持按 channel 快速查找
     * 用 getChannel() 作为 key，更直观（"ALIPAY" 而不是 "alipayStrategy"）
     */
    public PayContext(List<PayStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(PayStrategy::getChannel, Function.identity()));
        log.info("[PayContext] 已注册 {} 种支付策略: {}", strategyMap.size(), strategyMap.keySet());
    }

    /**
     * 执行支付 — 对外统一入口
     *
     * @param channel 支付渠道（ALIPAY / WXPAY / BANK）
     * @param amount  金额
     * @param orderId 订单号
     * @return 支付结果
     */
    public PayResult executePay(String channel, BigDecimal amount, String orderId) {
        PayStrategy strategy = strategyMap.get(channel.toUpperCase());
        if (strategy == null) {
            log.error("[PayContext] 不支持的支付渠道: {}", channel);
            return PayResult.fail(channel, orderId, "不支持的支付渠道: " + channel);
        }

        log.info("[PayContext] 选择支付策略: channel={}, strategy={}", channel, strategy.getClass().getSimpleName());
        return strategy.pay(amount, orderId);
    }

    /**
     * 获取所有支持的支付渠道
     */
    public java.util.Set<String> getSupportedChannels() {
        return strategyMap.keySet();
    }
}
