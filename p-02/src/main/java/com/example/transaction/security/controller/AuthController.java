package com.example.transaction.security.controller;

import com.example.transaction.security.dto.LoginRequest;
import com.example.transaction.security.dto.LoginResponse;
import com.example.transaction.security.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器 — 登录/登出/刷新Token/当前用户
 *
 * 接口列表：
 *   POST /api/auth/login          — 用户名密码登录
 *   POST /api/auth/logout         — 退出登录
 *   POST /api/auth/refresh        — 刷新 Token
 *   GET  /api/auth/me             — 获取当前用户信息
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     * @param request 登录请求（username + password）
     * @param servletRequest 用于获取客户端 IP
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest servletRequest) {
        String clientIp = getClientIp(servletRequest);
        LoginResponse response = authService.login(request, clientIp);
        return ResponseEntity.ok(response);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        authService.logout();
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "退出成功"
        ));
    }

    /**
     * 刷新 Token
     * @param body 包含 refreshToken 的请求体
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        LoginResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public ResponseEntity<LoginResponse> getCurrentUser() {
        LoginResponse response = authService.getCurrentUser();
        return ResponseEntity.ok(response);
    }

    /**
     * 获取客户端真实 IP（考虑反向代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
