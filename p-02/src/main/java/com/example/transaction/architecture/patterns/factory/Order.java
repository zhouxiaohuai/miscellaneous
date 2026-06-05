package com.example.transaction.architecture.patterns.factory;

import java.math.BigDecimal;

/**
 * 订单产品 — sealed 接口 + 3 种实现
 *
 * 【sealed 关键字 — Java 17】
 * 限制哪些类可以实现/继承此接口，编译器能检查穷尽性。
 */
public sealed interface Order permits NormalOrder, FlashSaleOrder, GroupOrder {
    String orderId();
    String type();
    BigDecimal totalAmount();
    int quantity();
}

/**
 * 普通订单
 */
record NormalOrder(String orderId, BigDecimal totalAmount, int quantity) implements Order {
    @Override
    public String type() { return "NORMAL"; }
}

/**
 * 秒杀订单
 */
record FlashSaleOrder(String orderId, BigDecimal totalAmount, int quantity,
                      BigDecimal flashPrice, int maxPerUser) implements Order {
    @Override
    public String type() { return "FLASH_SALE"; }
}

/**
 * 拼团订单
 */
record GroupOrder(String orderId, BigDecimal totalAmount, int quantity,
                  String groupId, int requiredMembers) implements Order {
    @Override
    public String type() { return "GROUP"; }
}
