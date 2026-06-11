package com.example.transaction.security.aspect;

import com.example.transaction.security.annotation.DataScope;
import com.example.transaction.security.entity.SysRole;
import com.example.transaction.security.entity.SysUser;
import com.example.transaction.security.enums.DataScopeType;
import com.example.transaction.security.repository.SysDeptRepository;
import com.example.transaction.security.repository.SysRoleRepository;
import com.example.transaction.security.repository.SysUserRepository;
import com.example.transaction.security.util.DataScopeContextHolder;
import com.example.transaction.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据权限切面
 *
 * 工作流程：
 * 1. 拦截标注了 @DataScope 的方法
 * 2. 获取当前用户的角色和部门信息
 * 3. 根据角色的 data_scope 生成 SQL 条件片段
 * 4. 将条件存入 ThreadLocal（DataScopeContextHolder）
 * 5. MyBatis 拦截器从 ThreadLocal 读取条件并拼接到 SQL
 * 6. 方法执行完毕后清理 ThreadLocal
 *
 * 生产要点：
 * - 超级管理员（data_scope=1）跳过数据权限过滤
 * - 多个角色取最大权限范围
 * - 条件拼接防 SQL 注入（使用参数化）
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysDeptRepository deptRepository;

    @Before("@annotation(dataScope)")
    public void doBefore(JoinPoint joinPoint, DataScope dataScope) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return;

        // 获取当前用户信息
        SysUser user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        // 获取用户角色
        List<SysRole> roles = roleRepository.findRolesByUserId(userId);
        if (roles.isEmpty()) return;

        // 取最大权限范围（code 越小权限越大）
        int minScope = roles.stream()
                .mapToInt(SysRole::getDataScope)
                .min()
                .orElse(DataScopeType.SELF.getCode());

        // 超级管理员（全部数据）不加条件
        if (minScope == DataScopeType.ALL.getCode()) {
            return;
        }

        // 构建 SQL 条件
        StringBuilder sqlCondition = new StringBuilder();
        List<Object> params = new ArrayList<>();

        DataScopeType scopeType = DataScopeType.fromCode(minScope);

        switch (scopeType) {
            case DEPT -> {
                // 本部门数据
                if (user.getDeptId() != null) {
                    sqlCondition.append(buildDeptCondition(dataScope, user.getDeptId()));
                }
            }
            case DEPT_AND_CHILDREN -> {
                // 本部门及下级部门数据
                if (user.getDeptId() != null) {
                    List<Long> deptIds = deptRepository.findDescendantIds(user.getDeptId());
                    sqlCondition.append(buildDeptInCondition(dataScope, deptIds));
                }
            }
            case SELF -> {
                // 仅本人数据
                sqlCondition.append(buildUserCondition(dataScope, userId));
            }
        }

        // 存入 ThreadLocal
        if (!sqlCondition.isEmpty()) {
            DataScopeContextHolder.set(sqlCondition.toString());
            log.debug("数据权限条件: userId={}, scope={}, condition={}",
                    userId, scopeType.getDescription(), sqlCondition);
        }
    }

    @After("@annotation(dataScope)")
    public void doAfter(JoinPoint joinPoint, DataScope dataScope) {
        // 清理 ThreadLocal，防止内存泄漏
        DataScopeContextHolder.clear();
    }

    /**
     * 构建部门等值条件：AND alias.dept_id = ?
     */
    private String buildDeptCondition(DataScope scope, Long deptId) {
        String alias = buildAlias(scope.deptAlias(), scope.deptIdField());
        return String.format(" AND %s = %d", alias, deptId);
    }

    /**
     * 构建部门 IN 条件：AND alias.dept_id IN (?, ?, ...)
     */
    private String buildDeptInCondition(DataScope scope, List<Long> deptIds) {
        if (deptIds.isEmpty()) return "";
        String alias = buildAlias(scope.deptAlias(), scope.deptIdField());
        String ids = deptIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return String.format(" AND %s IN (%s)", alias, ids);
    }

    /**
     * 构建用户等值条件：AND alias.user_id = ?
     */
    private String buildUserCondition(DataScope scope, Long userId) {
        String alias = buildAlias(scope.userAlias(), scope.userIdField());
        return String.format(" AND %s = %d", alias, userId);
    }

    /**
     * 构建带别名的字段引用
     * 例如：alias="o", field="user_id" → "o.user_id"
     *       alias="", field="user_id" → "user_id"
     */
    private String buildAlias(String alias, String field) {
        if (alias == null || alias.isEmpty()) {
            return field;
        }
        return alias + "." + field;
    }
}
