package com.example.transaction.architecture.patterns.chain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 责任链模式 — 过滤器实现
 */
public class OrderFilterImpl {

    // ==================== 过滤器 1: 参数校验 ====================

    @Slf4j
    @Component
    static class ParamValidationFilter implements OrderFilter {

        @Override
        public FilterResult filter(OrderSubmitRequest request) {
            log.info("[{}] 校验参数...", name());

            if (request.userId() == null || request.userId().isBlank()) {
                return FilterResult.reject(name(), "用户ID不能为空");
            }
            if (request.productId() == null || request.productId().isBlank()) {
                return FilterResult.reject(name(), "商品ID不能为空");
            }
            if (request.quantity() <= 0) {
                return FilterResult.reject(name(), "数量必须大于0");
            }
            if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
                return FilterResult.reject(name(), "金额必须大于0");
            }

            return FilterResult.pass(name());
        }

        @Override
        public int getOrder() { return 10; } // 最先执行

        @Override
        public String name() { return "参数校验"; }
    }

    // ==================== 过滤器 2: 库存校验 ====================

    @Slf4j
    @Component
    static class StockCheckFilter implements OrderFilter {

        // 模拟库存数据
        private static final java.util.Map<String, Integer> STOCK = java.util.Map.of(
                "P001", 100,
                "P002", 0,   // 已售罄
                "P003", 5
        );

        @Override
        public FilterResult filter(OrderSubmitRequest request) {
            log.info("[{}] 校验库存...", name());

            Integer stock = STOCK.getOrDefault(request.productId(), 0);
            if (stock <= 0) {
                return FilterResult.reject(name(), "商品已售罄: " + request.productId());
            }
            if (stock < request.quantity()) {
                return FilterResult.reject(name(), "库存不足: 需要 " + request.quantity() + "，剩余 " + stock);
            }

            return FilterResult.pass(name());
        }

        @Override
        public int getOrder() { return 20; }

        @Override
        public String name() { return "库存校验"; }
    }

    // ==================== 过滤器 3: 风控校验 ====================

    @Slf4j
    @Component
    static class RiskCheckFilter implements OrderFilter {

        // 模拟黑名单 IP
        private static final Set<String> BLACKLIST_IP = Set.of("192.168.1.100", "10.0.0.50");

        @Override
        public FilterResult filter(OrderSubmitRequest request) {
            log.info("[{}] 风控校验...", name());

            // 校验 IP 黑名单
            if (BLACKLIST_IP.contains(request.userIp())) {
                return FilterResult.reject(name(), "IP 在黑名单中: " + request.userIp());
            }

            // 校验大额订单
            if (request.amount().compareTo(new BigDecimal("100000")) > 0) {
                return FilterResult.reject(name(), "大额订单需要人工审核: " + request.amount());
            }

            return FilterResult.pass(name());
        }

        @Override
        public int getOrder() { return 30; }

        @Override
        public String name() { return "风控校验"; }
    }

    // ==================== 过滤器 4: 优惠券校验 ====================

    @Slf4j
    @Component
    static class CouponCheckFilter implements OrderFilter {

        private static final Set<String> VALID_COUPONS = Set.of("SAVE50", "NEWUSER", "VIP100");

        @Override
        public FilterResult filter(OrderSubmitRequest request) {
            log.info("[{}] 优惠券校验...", name());

            if (request.couponCode() != null && !request.couponCode().isBlank()) {
                if (!VALID_COUPONS.contains(request.couponCode())) {
                    return FilterResult.reject(name(), "无效的优惠券: " + request.couponCode());
                }
                log.info("[{}] 优惠券有效: {}", name(), request.couponCode());
            }

            return FilterResult.pass(name());
        }

        @Override
        public int getOrder() { return 40; } // 最后执行

        @Override
        public String name() { return "优惠券校验"; }
    }
}
