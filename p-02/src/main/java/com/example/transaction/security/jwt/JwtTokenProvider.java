package com.example.transaction.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

/**
 * JWT Token 生成/解析/验证工具
 *
 * 生产要点：
 * 1. Access Token 短期（30min），Refresh Token 长期（7d）
 * 2. 签名用 HMAC-SHA256，密钥至少 256 位
 * 3. Token 中存储 userId + username + roles，避免每次查库
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration:1800000}")
    private long accessTokenExpiration;  // 默认 30 分钟

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;  // 默认 7 天

    /**
     * 生成 Access Token
     */
    public String generateAccessToken(Long userId, String username, List<String> roles) {
        return buildToken(userId, username, roles, accessTokenExpiration);
    }

    /**
     * 生成 Refresh Token
     */
    public String generateRefreshToken(Long userId, String username, List<String> roles) {
        return buildToken(userId, username, roles, refreshTokenExpiration);
    }

    private String buildToken(Long userId, String username, List<String> roles, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 从 Token 中解析 Claims
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取用户ID
     */
    public Long getUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    /**
     * 获取用户名
     */
    public String getUsername(String token) {
        return parseClaims(token).get("username", String.class);
    }

    /**
     * 获取角色列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return parseClaims(token).get("roles", List.class);
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT Token 已过期: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT Token 格式错误: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("不支持的 JWT Token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT Claims 为空: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 判断 Token 是否已过期（不管签名是否正确）
     */
    public boolean isTokenExpired(String token) {
        try {
            return parseClaims(token).getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
