package com.example.transaction.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 未认证处理（401）
 * 当用户未登录或 Token 无效时，返回统一 JSON 格式而非默认 HTML 页面
 */
@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("未认证访问: {} {}, 原因: {}", request.getMethod(), request.getRequestURI(),
                 authException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 401);
        result.put("message", "未认证，请先登录");
        result.put("path", request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), result);
    }
}
