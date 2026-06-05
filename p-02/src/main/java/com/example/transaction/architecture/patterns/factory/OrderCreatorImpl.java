package com.example.transaction.architecture.patterns.factory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 工厂模式 — 3 种订单创建器实现
 */

// ==================== 普通订单创建器 ====================

@Slf4j
@Component
class NormalOrderCreator implements OrderCreator {

    @Override
    public String getType() { return "NORMAL"; }

    @Override
    public void validate(CreateOrderRequest request) {
        if (request.quantity() <= 0) {
            throw new IllegalArgumentException("普通订单数量必须大于0");
        }
        if (request.unitPrice() == null || request.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("普通订单单价必须大于0");
        }
    }

    @Override
    public Order create(CreateOrderRequest request) {
        validate(request);
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal total = request.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));
        NormalOrder order = new NormalOrder(orderId, total, request.quantity());
        log.info("[工厂] 创建普通订单: {}", order);
        return order;
    }
}

// ==================== 秒杀订单创建器 ====================

@Slf4j
@Component
class FlashSaleOrderCreator implements OrderCreator {

    @Override
    public String getType() { return "FLASH_SALE"; }

    @Override
    public void validate(CreateOrderRequest request) {
        if (request.flashPrice() == null || request.flashPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("秒杀价格必须大于0");
        }
        if (request.maxPerUser() == null || request.maxPerUser() <= 0) {
            throw new IllegalArgumentException("每人限购数量必须大于0");
        }
        if (request.quantity() > request.maxPerUser()) {
            throw new IllegalArgumentException("超过每人限购数量: " + request.maxPerUser());
        }
    }

    @Override
    public Order create(CreateOrderRequest request) {
        validate(request);
        String orderId = "FLASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal total = request.flashPrice().multiply(BigDecimal.valueOf(request.quantity()));
        FlashSaleOrder order = new FlashSaleOrder(orderId, total, request.quantity(),
                request.flashPrice(), request.maxPerUser());
        log.info("[工厂] 创建秒杀订单: {}", order);
        return order;
    }
}

// ==================== 拼团订单创建器 ====================

@Slf4j
@Component
class GroupOrderCreator implements OrderCreator {

    @Override
    public String getType() { return "GROUP"; }

    @Override
    public void validate(CreateOrderRequest request) {
        if (request.groupId() == null || request.groupId().isBlank()) {
            throw new IllegalArgumentException("拼团订单必须指定团号");
        }
        if (request.requiredMembers() == null || request.requiredMembers() < 2) {
            throw new IllegalArgumentException("成团人数不能少于2人");
        }
    }

    @Override
    public Order create(CreateOrderRequest request) {
        validate(request);
        String orderId = "GROUP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal total = request.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));
        GroupOrder order = new GroupOrder(orderId, total, request.quantity(),
                request.groupId(), request.requiredMembers());
        log.info("[工厂] 创建拼团订单: {}", order);
        return order;
    }
}
