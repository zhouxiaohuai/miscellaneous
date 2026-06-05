package com.example.transaction.config;

import org.springframework.context.annotation.Configuration;

/**
 * 数据源配置
 *
 * Spring Boot 自动配置数据源，这里可以自定义：
 * - 多数据源配置
 * - 读写分离配置
 * - 连接池参数调优
 *
 * 默认使用 HikariCP 连接池（Spring Boot 内置）
 */
@Configuration
public class DataSourceConfig {

    // Spring Boot 自动配置，无需额外配置
    // 如需自定义，可注入 DataSource 并配置
}
