package com.example.transaction.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 配置参数（从 application.yml 读取）
 *
 * 配置示例：
 * jwt:
 *   secret: <base64编码的256位密钥>
 *   access-token-expiration: 1800000   # 30分钟
 *   refresh-token-expiration: 604800000 # 7天
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    /** 签名密钥（Base64 编码，至少 256 位） */
    private String secret;

    /** Access Token 过期时间（毫秒），默认 30 分钟 */
    private long accessTokenExpiration = 1800000;

    /** Refresh Token 过期时间（毫秒），默认 7 天 */
    private long refreshTokenExpiration = 604800000;
}
