package com.example.transaction.architecture.patterns.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * 策略实现 — 银行卡支付
 */
@Slf4j
@Component
public class BankPayStrategy implements PayStrategy {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.01"); // 1.0%

    @Override
    public PayResult pay(BigDecimal amount, String orderId) {
        log.info("[银行卡] 发起支付: orderId={}, amount={}", orderId, amount);

        BigDecimal fee = calculateFee(amount);
        String txnId = "BANK" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        log.info("[银行卡] 支付成功: txnId={}, fee={}", txnId, fee);
        return PayResult.success("BANK", orderId, amount, fee, txnId);
    }

    @Override
    public String getChannel() {
        return "BANK";
    }

    @Override
    public BigDecimal calculateFee(BigDecimal amount) {
        return amount.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }
}
