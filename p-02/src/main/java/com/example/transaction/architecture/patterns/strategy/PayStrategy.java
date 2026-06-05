package com.example.transaction.architecture.patterns.strategy;

import java.math.BigDecimal;

/**
 * ============================================================
 * 策略模式 — 支付策略接口
 * ============================================================
 *
 * 【定义】
 * 定义一系列算法，把它们一个个封装起来，并且使它们可以互相替换。
 * 策略模式让算法独立于使用它的客户端而变化。
 *
 * 【适用场景】
 * - 多种支付方式（支付宝、微信、银行卡）
 * - 多种折扣策略（满减、折扣、会员价）
 * - 多种排序/导出方式
 *
 * 【本示例场景】
 * 电商系统支持多种支付方式，每种支付方式的手续费计算、
 * 调用接口、回调处理都不同，但对外暴露统一的 pay() 方法。
 *
 * 【类图】
 * ┌─────────────┐       ┌──────────────────┐
 * │  PayContext  │──────▶│  <<interface>>    │
 * │  (客户端)    │       │  PayStrategy      │
 * └─────────────┘       │  + pay(amount)    │
 *                       │  + getChannel()   │
 *                       └────────┬─────────┘
 *                    ┌───────────┼───────────┐
 *                    ▼           ▼           ▼
 *             AlipayStrategy  WxPayStrategy  BankPayStrategy
 */
public interface PayStrategy {

    /**
     * 执行支付
     * @param amount 支付金额
     * @param orderId 订单号
     * @return 支付结果
     */
    PayResult pay(BigDecimal amount, String orderId);

    /**
     * 获取支付渠道标识
     */
    String getChannel();

    /**
     * 计算手续费
     * 不同渠道费率不同：支付宝 0.6%，微信 0.6%，银行卡 1.0%
     */
    BigDecimal calculateFee(BigDecimal amount);
}
