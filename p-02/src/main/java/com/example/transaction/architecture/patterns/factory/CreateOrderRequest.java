package com.example.transaction.architecture.patterns.factory;

import java.math.BigDecimal;

/**
 * 订单创建请求
 */
public record CreateOrderRequest(
        String type,
        String userId,
        String productId,
        int quantity,
        BigDecimal unitPrice,
        // 秒杀专用
        BigDecimal flashPrice,
        Integer maxPerUser,
        // 拼团专用
        String groupId,
        Integer requiredMembers
) {}
