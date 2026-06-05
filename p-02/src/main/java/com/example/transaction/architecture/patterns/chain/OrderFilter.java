package com.example.transaction.architecture.patterns.chain;

/**
 * 责任链模式 — 过滤器接口 + 请求/结果类型
 */

// ==================== 过滤结果 ====================

/**
 * 过滤结果
 */
record FilterResult(boolean passed, String message, String filterName) {
    static FilterResult pass(String filterName) {
        return new FilterResult(true, "通过", filterName);
    }

    static FilterResult reject(String filterName, String message) {
        return new FilterResult(false, message, filterName);
    }
}

// ==================== 过滤器接口 ====================

/**
 * 订单过滤器接口
 *
 * getOrder() 返回优先级，数字越小越先执行
 */
interface OrderFilter {

    FilterResult filter(OrderSubmitRequest request);

    /**
     * 过滤器优先级（越小越先执行）
     */
    default int getOrder() {
        return 100;
    }

    /**
     * 过滤器名称
     */
    String name();
}
