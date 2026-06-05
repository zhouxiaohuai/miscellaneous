package com.example.transaction.architecture.patterns.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * 策略实现 — 微信支付
 */
@Slf4j
@Component
public class WxPayStrategy implements PayStrategy {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.006"); // 0.6%

    @Override
    public PayResult pay(BigDecimal amount, String orderId) {
        log.info("[微信支付] 发起支付: orderId={}, amount={}", orderId, amount);

        BigDecimal fee = calculateFee(amount);
        // 模拟：微信单笔限额 5000
        if (amount.compareTo(new BigDecimal("5000")) > 0) {
            log.warn("[微信支付] 超过单笔限额: orderId={}", orderId);
            return PayResult.fail("WXPAY", orderId, "微信支付单笔限额5000元");
        }

        String txnId = "WX" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        log.info("[微信支付] 支付成功: txnId={}", txnId);
        return PayResult.success("WXPAY", orderId, amount, fee, txnId);
    }

    @Override
    public String getChannel() {
        return "WXPAY";
    }

    @Override
    public BigDecimal calculateFee(BigDecimal amount) {
        return amount.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }
}
