package com.example.transaction.security.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 *
 * 标注在 Controller 方法上，AOP 切面自动记录操作日志。
 *
 * 使用示例：
 *   @Log(module = "用户管理", description = "新增用户")
 *   @PostMapping
 *   public ResponseEntity<UserDTO> create(@RequestBody SysUser user) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {

    /**
     * 操作模块（如：用户管理、角色管理）
     */
    String module() default "";

    /**
     * 操作描述（如：新增用户、删除角色）
     */
    String description() default "";

    /**
     * 是否保存请求参数（默认 true，敏感操作建议关闭）
     */
    boolean saveParams() default true;

    /**
     * 是否保存响应结果（默认 false，大数据响应建议关闭）
     */
    boolean saveResult() default false;
}
