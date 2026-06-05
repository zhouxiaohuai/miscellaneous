package com.example.transaction.architecture.patterns.factory;

/**
 * 订单创建器接口 — 工厂方法的核心
 */
public interface OrderCreator {

    /**
     * 工厂方法 — 由子类决定创建哪种 Order
     */
    Order create(CreateOrderRequest request);

    /**
     * 校验请求 — 每种订单的校验规则不同
     */
    void validate(CreateOrderRequest request);

    /**
     * 获取此创建器处理的订单类型（用于工厂 Map 注册）
     */
    String getType();
}
