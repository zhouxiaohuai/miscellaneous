package com.example.transaction.security.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 登录响应 — 返回 Token + 用户基本信息
 */
@Data
@Builder
public class LoginResponse {

    /** Access Token（短期，30min） */
    private String accessToken;

    /** Refresh Token（长期，7d） */
    private String refreshToken;

    /** Token 类型 */
    @Builder.Default
    private String tokenType = "Bearer";

    /** 过期时间（秒） */
    private Long expiresIn;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 角色标识列表 */
    private List<String> roles;

    /** 权限标识列表（用于前端按钮级控制） */
    private List<String> permissions;
}
