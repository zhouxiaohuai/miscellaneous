package com.example.transaction.security.service;

import com.example.transaction.security.dto.RoleDTO;
import com.example.transaction.security.dto.UserDTO;
import com.example.transaction.security.entity.SysDept;
import com.example.transaction.security.entity.SysRole;
import com.example.transaction.security.entity.SysUser;
import com.example.transaction.security.entity.SysUserRole;
import com.example.transaction.security.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理服务
 *
 * 职责：
 * 1. 用户 CRUD（分页查询、新增、修改、删除）
 * 2. 用户角色分配
 * 3. 密码重置
 * 4. 用户状态管理（启用/禁用）
 */
@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final SysDeptRepository deptRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String DEFAULT_PASSWORD = "123456";

    /**
     * 分页查询用户（支持按用户名、状态、部门筛选）
     */
    public Page<UserDTO> findUsers(String username, Integer status, Long deptId, Pageable pageable) {
        Specification<SysUser> spec = Specification.where(null);

        if (username != null && !username.isEmpty()) {
            spec = spec.and((root, query, cb) ->
                cb.like(root.get("username"), "%" + username + "%"));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("status"), status));
        }
        if (deptId != null) {
            spec = spec.and((root, query, cb) ->
                cb.equal(root.get("deptId"), deptId));
        }

        return userRepository.findAll(spec, pageable).map(this::toDTO);
    }

    /**
     * 查询用户详情（含角色信息）
     */
    public UserDTO findById(Long id) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + id));
        return toDTO(user);
    }

    /**
     * 新增用户
     */
    @Transactional
    public UserDTO create(SysUser user, List<Long> roleIds) {
        // 检查用户名唯一
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("用户名已存在: " + user.getUsername());
        }

        // 密码加密
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        SysUser saved = userRepository.save(user);

        // 分配角色
        if (roleIds != null && !roleIds.isEmpty()) {
            assignRoles(saved.getId(), roleIds);
        }

        return toDTO(saved);
    }

    /**
     * 修改用户（不修改密码）
     */
    @Transactional
    public UserDTO update(SysUser user, List<Long> roleIds) {
        SysUser existing = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("用户不存在: " + user.getId()));

        existing.setNickname(user.getNickname());
        existing.setEmail(user.getEmail());
        existing.setPhone(user.getPhone());
        existing.setAvatar(user.getAvatar());
        existing.setDeptId(user.getDeptId());
        existing.setStatus(user.getStatus());

        SysUser saved = userRepository.save(existing);

        // 重新分配角色
        if (roleIds != null) {
            assignRoles(saved.getId(), roleIds);
        }

        return toDTO(saved);
    }

    /**
     * 删除用户（同时删除角色关联）
     */
    @Transactional
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("用户不存在: " + id);
        }
        userRoleRepository.deleteByUserId(id);
        userRepository.deleteById(id);
    }

    /**
     * 重置密码（管理员操作）
     */
    @Transactional
    public void resetPassword(Long userId) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + userId));
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        userRepository.save(user);
    }

    /**
     * 修改密码（用户自己操作）
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + userId));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * 启用/禁用用户
     */
    @Transactional
    public void updateStatus(Long userId, Integer status) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + userId));
        user.setStatus(status);
        userRepository.save(user);
    }

    /**
     * 分配角色（先删后增）
     */
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        userRoleRepository.deleteByUserId(userId);
        for (Long roleId : roleIds) {
            userRoleRepository.save(new SysUserRole(userId, roleId));
        }
    }

    // ==================== 私有方法 ====================

    private UserDTO toDTO(SysUser user) {
        // 查询角色
        List<SysRole> roles = roleRepository.findRolesByUserId(user.getId());
        List<RoleDTO> roleDTOs = roles.stream()
                .map(r -> RoleDTO.builder()
                        .id(r.getId())
                        .roleName(r.getRoleName())
                        .roleKey(r.getRoleKey())
                        .dataScope(r.getDataScope())
                        .status(r.getStatus())
                        .remark(r.getRemark())
                        .build())
                .toList();

        // 查询部门名称
        String deptName = null;
        if (user.getDeptId() != null) {
            deptName = deptRepository.findById(user.getDeptId())
                    .map(SysDept::getDeptName)
                    .orElse(null);
        }

        return UserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .deptId(user.getDeptId())
                .deptName(deptName)
                .status(user.getStatus())
                .loginTime(user.getLoginTime())
                .createdAt(user.getCreatedAt())
                .roles(roleDTOs)
                .build();
    }
}
