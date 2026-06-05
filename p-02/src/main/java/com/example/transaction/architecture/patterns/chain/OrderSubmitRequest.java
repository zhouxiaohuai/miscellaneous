package com.example.transaction.architecture.patterns.chain;

import java.math.BigDecimal;

/**
 * 订单提交请求（责任链模式）
 *
 * @param userId     用户ID
 * @param productId  商品ID
 * @param quantity   数量
 * @param amount     金额
 * @param couponCode 优惠券编码（可选）
 * @param userIp     用户IP
 */
public record OrderSubmitRequest(
        String userId,
        String productId,
        int quantity,
        BigDecimal amount,
        String couponCode,
        String userIp
) {}
