package com.example.transaction.security.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解
 *
 * 标注在 Service 方法上，AOP 切面会根据当前用户角色的数据范围，
 * 自动拼接 SQL 条件（通过 ThreadLocal 传递给 MyBatis 拦截器）。
 *
 * 使用示例：
 *   @DataScope(deptAlias = "d", userAlias = "u")
 *   public List<Order> findOrders(Long userId) { ... }
 *
 * 生成的 SQL 条件：
 *   全部数据 → 无额外条件
 *   本部门 → AND d.dept_id = #{currentDeptId}
 *   本部门及下级 → AND d.dept_id IN (#{deptIds})
 *   仅本人 → AND u.user_id = #{currentUserId}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * 部门表的别名（用于拼接 SQL 条件）
     * 例如：SELECT * FROM orders o LEFT JOIN sys_user u ON o.user_id = u.id
     *       此时 deptAlias = "u"（用户表别名，通过用户表关联部门）
     */
    String deptAlias() default "";

    /**
     * 用户表的别名（用于拼接 "仅本人" 条件）
     * 例如：SELECT * FROM orders WHERE user_id = ?
     *       此时 userAlias = ""（直接用字段名）
     */
    String userAlias() default "";

    /**
     * 部门ID字段名（默认 "dept_id"）
     */
    String deptIdField() default "dept_id";

    /**
     * 用户ID字段名（默认 "user_id" 或 "create_by"）
     */
    String userIdField() default "user_id";
}
