package com.example.transaction.framework;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 自定义事务框架配置类
 *
 * 扫描 framework 包下的组件（TxContext 等）
 * 使用时只需在项目中引入此包即可
 */
@Configuration
@ComponentScan(basePackageClasses = TxConfig.class)
public class TxConfig {
    // TxContext 通过 @Component 自动注册
    // 无需额外 Bean 配置
}
