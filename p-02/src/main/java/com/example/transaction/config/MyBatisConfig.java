package com.example.transaction.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置
 *
 * @MapperScan 扫描 MyBatis Mapper 接口
 * 配置文件: application.yml 中的 mybatis.* 配置
 */
@Configuration
@MapperScan("com.example.transaction.mybatis.mapper")
public class MyBatisConfig {
    // MyBatis 配置在 application.yml 中完成
}
