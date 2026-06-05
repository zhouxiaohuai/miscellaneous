package com.example.transaction.architecture.ddd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DDD 模块 — 公开门面
 *
 * 将 DDD 内部的 package-private 类封装为 public 方法，
 * 供 Controller 调用。保持领域层的封装性。
 */
@Slf4j
@Component
public class DddDemo {

    private final PriceDomainService priceService = new PriceDomainService();
    private final CreateOrderAppService appService = new CreateOrderAppService(priceService);

    /**
     * DDD 下单演示
     */
    public Map<String, Object> createOrder(String userId, String productId, String productName,
                                            int quantity, BigDecimal price,
                                            String userLevel, String productType) {
        log.info("===== DDD 演示 =====");

        CreateOrderCommand cmd = new CreateOrderCommand(
                userId, productId, productName, quantity, price, userLevel, productType);

        String orderId = appService.execute(cmd);

        return Map.of(
                "success", true,
                "orderId", orderId,
                "message", "DDD 下单完成，查看日志了解完整流程"
        );
    }
}
