package com.example.transaction.security.config;

import com.example.transaction.security.jwt.JwtAuthenticationEntryPoint;
import com.example.transaction.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 核心配置
 *
 * 生产要点：
 * 1. 禁用 CSRF（前后端分离，使用 JWT 无需 CSRF）
 * 2. 禁用 Session（无状态，每次请求通过 JWT 认证）
 * 3. 白名单放行：登录、注册、公开接口
 * 4. 自定义 JWT 过滤器
 * 5. 自定义 401/403 处理（返回 JSON 而非 HTML）
 * 6. @EnableMethodSecurity 开启 @PreAuthorize 注解
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // 开启方法级权限校验 @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    /**
     * 密码编码器（BCrypt，不可逆加密）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Security 过滤器链
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. 禁用 CSRF（前后端分离，Token 天然防 CSRF）
            .csrf(AbstractHttpConfigurer::disable)

            // 2. 跨域配置
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 3. 无状态 Session（不创建 HttpSession）
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 4. 请求授权规则
            .authorizeHttpRequests(auth -> auth
                // 公开接口（无需认证）
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/auth/refresh").permitAll()

                // 静态资源
                .requestMatchers("/favicon.ico", "/error").permitAll()

                // Actuator 健康检查（生产环境建议限制）
                .requestMatchers("/actuator/health").permitAll()

                // 开发环境放行 Swagger/文档（生产环境应删除）
                // .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // OPTIONS 预检请求放行
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 其他所有请求需要认证
                .anyRequest().authenticated()
            )

            // 5. 异常处理
            .exceptionHandling(exception -> exception
                // 未认证（401）
                .authenticationEntryPoint(authenticationEntryPoint)
                // 未授权（403）— 可自定义 AccessDeniedHandler
            )

            // 6. 添加 JWT 过滤器（在 UsernamePasswordAuthenticationFilter 之前）
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 跨域配置
     * 生产环境应限制 allowedOrigins 为具体域名
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));  // 生产环境改为具体域名
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
