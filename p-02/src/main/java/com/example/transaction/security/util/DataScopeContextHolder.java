package com.example.transaction.security.util;

/**
 * 数据权限条件上下文（ThreadLocal）
 *
 * 用于在 AOP 切面和 MyBatis 拦截器之间传递 SQL 条件片段。
 * 切面设置条件，拦截器读取并拼接到 SQL，方法结束后清理。
 *
 * 使用方式：
 *   // 切面中设置
 *   DataScopeContextHolder.set(" AND o.dept_id = 1");
 *
 *   // MyBatis 拦截器中读取
 *   String condition = DataScopeContextHolder.get();
 *
 *   // 方法结束后清理
 *   DataScopeContextHolder.clear();
 */
public final class DataScopeContextHolder {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    private DataScopeContextHolder() {}

    /**
     * 设置数据权限 SQL 条件
     */
    public static void set(String sqlCondition) {
        CONTEXT.set(sqlCondition);
    }

    /**
     * 获取数据权限 SQL 条件
     */
    public static String get() {
        return CONTEXT.get();
    }

    /**
     * 清理（防止内存泄漏）
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
