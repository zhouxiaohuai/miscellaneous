package com.example.transaction.architecture.patterns.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * 策略实现 — 支付宝支付
 */
@Slf4j
@Component
public class AlipayStrategy implements PayStrategy {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.006"); // 0.6%

    @Override
    public PayResult pay(BigDecimal amount, String orderId) {
        log.info("[支付宝] 发起支付: orderId={}, amount={}", orderId, amount);

        BigDecimal fee = calculateFee(amount);
        // 模拟：金额 > 50000 需要风控审核
        if (amount.compareTo(new BigDecimal("50000")) > 0) {
            log.warn("[支付宝] 大额支付触发风控: orderId={}", orderId);
            return PayResult.fail("ALIPAY", orderId, "大额支付需要风控审核");
        }

        String txnId = "ALI" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        log.info("[支付宝] 支付成功: txnId={}", txnId);
        return PayResult.success("ALIPAY", orderId, amount, fee, txnId);
    }

    @Override
    public String getChannel() {
        return "ALIPAY";
    }

    @Override
    public BigDecimal calculateFee(BigDecimal amount) {
        return amount.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }
}
