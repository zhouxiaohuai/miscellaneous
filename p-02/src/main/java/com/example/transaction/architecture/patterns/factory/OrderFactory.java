package com.example.transaction.architecture.patterns.factory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单工厂 — 统一创建入口
 *
 * 【设计要点】
 * 1. 通过构造器注入所有 OrderCreator 实现
 * 2. 用 getType() 作为 Map 的 key（"NORMAL" / "FLASH_SALE" / "GROUP"）
 * 3. 调用方只需传 type，工厂自动选择对应的创建器
 */
@Slf4j
@Component
public class OrderFactory {

    private final Map<String, OrderCreator> creatorMap;

    public OrderFactory(List<OrderCreator> creators) {
        // 用每个创建器的 getType() 作为 key
        this.creatorMap = creators.stream()
                .collect(Collectors.toMap(OrderCreator::getType, c -> c));
        log.info("[OrderFactory] 已注册 {} 种订单创建器: {}", creatorMap.size(), creatorMap.keySet());
    }

    /**
     * 创建订单 — 统一入口
     */
    public Order createOrder(CreateOrderRequest request) {
        String type = request.type().toUpperCase();
        OrderCreator creator = creatorMap.get(type);

        if (creator == null) {
            throw new IllegalArgumentException("不支持的订单类型: " + type
                    + "，支持的类型: " + creatorMap.keySet());
        }

        log.info("[OrderFactory] 选择订单创建器: type={}, creator={}",
                type, creator.getClass().getSimpleName());
        return creator.create(request);
    }
}
