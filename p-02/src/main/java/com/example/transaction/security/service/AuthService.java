package com.example.transaction.security.service;

import com.example.transaction.security.dto.LoginRequest;
import com.example.transaction.security.dto.LoginResponse;
import com.example.transaction.security.entity.SysPermission;
import com.example.transaction.security.entity.SysRole;
import com.example.transaction.security.entity.SysUser;
import com.example.transaction.security.jwt.JwtTokenProvider;
import com.example.transaction.security.repository.SysPermissionRepository;
import com.example.transaction.security.repository.SysRoleRepository;
import com.example.transaction.security.repository.SysUserRepository;
import com.example.transaction.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务 — 登录/登出/刷新Token
 *
 * 生产要点：
 * 1. 密码 BCrypt 验证（不可逆）
 * 2. 登录失败次数限制（Redis 计数）
 * 3. 用户状态检查（禁用不可登录）
 * 4. Token 黑名单（登出时失效）
 * 5. 登录成功记录 IP 和时间
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;

    private static final String LOGIN_FAIL_PREFIX = "login:fail:";
    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";
    private static final int MAX_LOGIN_FAIL_COUNT = 5;
    private static final int LOGIN_LOCK_MINUTES = 30;

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest request, String clientIp) {
        String username = request.getUsername();

        // 1. 检查登录失败次数
        checkLoginFailCount(username);

        // 2. 查询用户
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    recordLoginFail(username);
                    return new RuntimeException("用户名或密码错误");
                });

        // 3. 检查用户状态
        if (user.getStatus() != 1) {
            throw new RuntimeException("账号已被禁用，请联系管理员");
        }

        // 4. 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordLoginFail(username);
            throw new RuntimeException("用户名或密码错误");
        }

        // 5. 清除登录失败记录
        clearLoginFail(username);

        // 6. 查询角色和权限
        List<SysRole> roles = roleRepository.findRolesByUserId(user.getId());
        List<String> roleKeys = roles.stream().map(SysRole::getRoleKey).toList();

        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
        List<SysPermission> permissions = permissionRepository.findByRoleIds(roleIds);
        List<String> permKeys = permissions.stream()
                .map(SysPermission::getPermKey)
                .filter(pk -> pk != null && !pk.isEmpty())
                .distinct()
                .toList();

        // 7. 生成 Token
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), username, roleKeys);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), username, roleKeys);

        // 8. 记录登录信息
        user.setLoginIp(clientIp);
        user.setLoginTime(LocalDateTime.now());
        userRepository.save(user);

        log.info("用户登录成功: username={}, ip={}", username, clientIp);

        // 9. 构建响应
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(1800L)  // 30 分钟
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roles(roleKeys)
                .permissions(permKeys)
                .build();
    }

    /**
     * 退出登录（Token 加入黑名单）
     */
    public void logout() {
        String token = SecurityUtils.getCurrentToken();
        if (token != null) {
            // 将 Token 加入 Redis 黑名单，过期时间 = Token 剩余有效期
            long ttl = 1800;  // 默认 30 分钟
            try {
                var claims = jwtTokenProvider.parseClaims(token);
                long expireTime = claims.getExpiration().getTime();
                ttl = Math.max((expireTime - System.currentTimeMillis()) / 1000, 1);
            } catch (Exception ignored) {
                // Token 已过期，无需加入黑名单
            }
            redisTemplate.opsForValue().set(TOKEN_BLACKLIST_PREFIX + token, "1", ttl, TimeUnit.SECONDS);
            log.info("用户登出，Token 已加入黑名单");
        }
    }

    /**
     * 刷新 Token（用 Refresh Token 换新的 Access Token）
     */
    public LoginResponse refreshToken(String refreshToken) {
        // 1. 验证 Refresh Token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("Refresh Token 无效或已过期，请重新登录");
        }

        // 2. 检查是否在黑名单中
        if (Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + refreshToken))) {
            throw new RuntimeException("Refresh Token 已失效，请重新登录");
        }

        // 3. 解析用户信息
        Long userId = jwtTokenProvider.getUserId(refreshToken);
        String username = jwtTokenProvider.getUsername(refreshToken);
        List<String> roles = jwtTokenProvider.getRoles(refreshToken);

        // 4. 查询最新权限（角色可能已变更）
        List<Long> roleIds = roleRepository.findRolesByUserId(userId).stream()
                .map(SysRole::getId).toList();
        List<SysPermission> permissions = permissionRepository.findByRoleIds(roleIds);
        List<String> permKeys = permissions.stream()
                .map(SysPermission::getPermKey)
                .filter(pk -> pk != null && !pk.isEmpty())
                .distinct()
                .toList();

        // 5. 生成新的 Access Token
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, username, roles);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)  // Refresh Token 不变
                .expiresIn(1800L)
                .userId(userId)
                .username(username)
                .roles(roles)
                .permissions(permKeys)
                .build();
    }

    /**
     * 获取当前登录用户信息（含角色和权限）
     */
    public LoginResponse getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("未登录");
        }

        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        List<SysRole> roles = roleRepository.findRolesByUserId(userId);
        List<String> roleKeys = roles.stream().map(SysRole::getRoleKey).toList();

        List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
        List<SysPermission> permissions = permissionRepository.findByRoleIds(roleIds);
        List<String> permKeys = permissions.stream()
                .map(SysPermission::getPermKey)
                .filter(pk -> pk != null && !pk.isEmpty())
                .distinct()
                .toList();

        return LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roles(roleKeys)
                .permissions(permKeys)
                .build();
    }

    // ==================== 私有方法 ====================

    private void checkLoginFailCount(String username) {
        String key = LOGIN_FAIL_PREFIX + username;
        String countStr = redisTemplate.opsForValue().get(key);
        if (countStr != null) {
            int count = Integer.parseInt(countStr);
            if (count >= MAX_LOGIN_FAIL_COUNT) {
                throw new RuntimeException("登录失败次数过多，账号已锁定 " + LOGIN_LOCK_MINUTES + " 分钟");
            }
        }
    }

    private void recordLoginFail(String username) {
        String key = LOGIN_FAIL_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // 首次失败，设置过期时间
            redisTemplate.expire(key, LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
        }
        log.warn("登录失败: username={}, 已失败 {} 次", username, count);
    }

    private void clearLoginFail(String username) {
        redisTemplate.delete(LOGIN_FAIL_PREFIX + username);
    }
}
