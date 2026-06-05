package com.example.transaction.architecture.patterns.strategy;

import java.math.BigDecimal;

/**
 * 支付结果 — 策略模式的输出对象
 */
public record PayResult(
        boolean success,
        String channel,
        String orderId,
        BigDecimal amount,
        BigDecimal fee,
        String transactionId,
        String message
) {
    public static PayResult success(String channel, String orderId, BigDecimal amount,
                                     BigDecimal fee, String transactionId) {
        return new PayResult(true, channel, orderId, amount, fee, transactionId, "支付成功");
    }

    public static PayResult fail(String channel, String orderId, String message) {
        return new PayResult(false, channel, orderId, null, null, null, message);
    }
}
