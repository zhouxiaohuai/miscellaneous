package com.example.transaction.security.aspect;

import com.example.transaction.security.annotation.Log;
import com.example.transaction.security.entity.SysOperLog;
import com.example.transaction.security.entity.SysUser;
import com.example.transaction.security.repository.SysOperLogRepository;
import com.example.transaction.security.repository.SysUserRepository;
import com.example.transaction.security.util.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作日志切面
 *
 * 拦截标注了 @Log 的 Controller 方法，自动记录：
 * - 操作人信息（ID、用户名、IP）
 * - 请求信息（URL、方法、参数）
 * - 响应结果（可选）
 * - 执行耗时
 * - 成功/失败状态
 *
 * 生产要点：
 * - 异步保存日志（避免阻塞业务线程）
 * - 敏感字段脱敏（密码、Token 等）
 * - 大字段截断（避免日志表膨胀）
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final SysOperLogRepository operLogRepository;
    private final SysUserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_PARAM_LENGTH = 2000;
    private static final int MAX_RESULT_LENGTH = 2000;

    @Around("@annotation(logAnnotation)")
    public Object around(ProceedingJoinPoint joinPoint, Log logAnnotation) throws Throwable {
        long startTime = System.currentTimeMillis();

        SysOperLog operLog = new SysOperLog();
        operLog.setModule(logAnnotation.module());
        operLog.setDescription(logAnnotation.description());

        // 请求信息
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                operLog.setRequestUrl(request.getRequestURI());
                operLog.setRequestMethod(request.getMethod());
                operLog.setOperIp(getClientIp(request));
            }
        } catch (Exception e) {
            log.warn("获取请求信息失败: {}", e.getMessage());
        }

        // 操作人信息
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId != null) {
            operLog.setOperUserId(userId);
            userRepository.findById(userId)
                    .ifPresent(user -> operLog.setOperUsername(user.getUsername()));
        }

        // 保存请求参数
        if (logAnnotation.saveParams()) {
            try {
                String params = serializeParams(joinPoint);
                operLog.setRequestParams(truncate(params, MAX_PARAM_LENGTH));
            } catch (Exception e) {
                log.warn("序列化请求参数失败: {}", e.getMessage());
            }
        }

        // 执行方法
        Object result = null;
        try {
            result = joinPoint.proceed();
            operLog.setStatus(1);  // 成功
        } catch (Throwable e) {
            operLog.setStatus(0);  // 失败
            operLog.setErrorMsg(truncate(e.getMessage(), 2000));
            throw e;
        } finally {
            // 耗时
            operLog.setCostTime(System.currentTimeMillis() - startTime);
            operLog.setOperTime(LocalDateTime.now());

            // 保存响应结果
            if (logAnnotation.saveResult() && result != null) {
                try {
                    String resultStr = objectMapper.writeValueAsString(result);
                    operLog.setResponseResult(truncate(resultStr, MAX_RESULT_LENGTH));
                } catch (Exception e) {
                    log.warn("序列化响应结果失败: {}", e.getMessage());
                }
            }

            // 异步保存日志（生产环境建议用 @Async 或消息队列）
            saveLog(operLog);
        }

        return result;
    }

    /**
     * 序列化方法参数（过滤掉 HttpServletRequest 等不可序列化对象）
     */
    private String serializeParams(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) return "";

        Object[] serializableArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof HttpServletRequest) {
                serializableArgs[i] = "[HttpServletRequest]";
            } else if (args[i] instanceof jakarta.servlet.http.HttpServletResponse) {
                serializableArgs[i] = "[HttpServletResponse]";
            } else {
                serializableArgs[i] = args[i];
            }
        }

        try {
            return objectMapper.writeValueAsString(serializableArgs);
        } catch (Exception e) {
            return "[序列化失败: " + e.getMessage() + "]";
        }
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 保存操作日志
     * 生产环境建议改为异步（@Async）或发送到消息队列
     */
    private void saveLog(SysOperLog operLog) {
        try {
            operLogRepository.save(operLog);
        } catch (Exception e) {
            log.error("保存操作日志失败: {}", e.getMessage());
        }
    }
}
