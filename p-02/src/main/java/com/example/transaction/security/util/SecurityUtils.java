package com.example.transaction.security.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * Security 工具类 — 获取当前登录用户信息
 *
 * 使用方式：
 *   Long userId = SecurityUtils.getCurrentUserId();
 *   String username = SecurityUtils.getCurrentUsername();  // 需配合 UserDetailsService
 *   boolean hasRole = SecurityUtils.hasRole("ROLE_ADMIN");
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * 获取当前用户ID（principal 存储的是 userId）
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    /**
     * 获取当前 Token（credentials 存储的是 token）
     */
    public static String getCurrentToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof String token) {
            return token;
        }
        return null;
    }

    /**
     * 判断当前用户是否已认证
     */
    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated();
    }

    /**
     * 判断当前用户是否拥有指定角色
     */
    public static boolean hasRole(String roleKey) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(roleKey::equals);
    }

    /**
     * 判断当前用户是否拥有任一角色
     */
    public static boolean hasAnyRole(String... roleKeys) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        List<String> authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        for (String roleKey : roleKeys) {
            if (authorities.contains(roleKey)) return true;
        }
        return false;
    }

    /**
     * 获取当前用户的所有角色
     */
    public static List<String> getCurrentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return List.of();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
