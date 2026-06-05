package com.example.transaction.architecture.patterns.chain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 责任链模式 — 过滤链管理器
 *
 * 【设计要点】
 * 1. 通过 Spring 自动注入所有 OrderFilter 实现
 * 2. 按 getOrder() 排序，形成有序的过滤链
 * 3. 依次执行每个过滤器，遇到失败立即中断（短路）
 *
 * 【扩展方式】
 * 新增校验器只需：
 * 1. 新建类实现 OrderFilter 接口
 * 2. 加 @Component 注解
 * 3. 通过 getOrder() 控制执行顺序
 * 完全不用改 OrderFilterChain 的代码。
 */
@Slf4j
@Component
public class OrderFilterChain {

    private final List<OrderFilter> filters;

    public OrderFilterChain(List<OrderFilter> filters) {
        // 按优先级排序
        this.filters = filters.stream()
                .sorted(Comparator.comparingInt(OrderFilter::getOrder))
                .toList();
        log.info("[FilterChain] 已注册 {} 个过滤器: {}", filters.size(),
                filters.stream().map(OrderFilter::name).toList());
    }

    /**
     * 执行过滤链
     *
     * @return 所有过滤器都通过返回 true，任一失败返回 false
     */
    public ChainResult doFilter(OrderSubmitRequest request) {
        log.info("[FilterChain] 开始执行过滤链, userId={}, productId={}", request.userId(), request.productId());

        for (OrderFilter filter : filters) {
            FilterResult result = filter.filter(request);
            log.info("[FilterChain] 过滤器 [{}] 结果: passed={}, msg={}",
                    filter.name(), result.passed(), result.message());

            if (!result.passed()) {
                log.warn("[FilterChain] 过滤链中断于: {}", filter.name());
                return new ChainResult(false, result.filterName(), result.message());
            }
        }

        log.info("[FilterChain] 所有过滤器通过");
        return new ChainResult(true, null, "所有校验通过");
    }

    /**
     * 过滤链执行结果
     */
    public record ChainResult(boolean passed, String failedFilter, String message) {}
}
