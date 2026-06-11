package com.example.transaction.security.service;

import com.example.transaction.security.dto.RoleDTO;
import com.example.transaction.security.entity.SysRole;
import com.example.transaction.security.entity.SysRolePermission;
import com.example.transaction.security.repository.SysRolePermissionRepository;
import com.example.transaction.security.repository.SysRoleRepository;
import com.example.transaction.security.repository.SysUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色管理服务
 *
 * 职责：
 * 1. 角色 CRUD
 * 2. 角色权限分配
 * 3. 数据范围管理
 */
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleRepository roleRepository;
    private final SysRolePermissionRepository rolePermissionRepository;
    private final SysUserRoleRepository userRoleRepository;

    /**
     * 分页查询角色
     */
    public Page<RoleDTO> findRoles(String roleName, Integer status, Pageable pageable) {
        Page<SysRole> page;
        if (roleName != null && !roleName.isEmpty()) {
            page = roleRepository.findAll(pageable);  // 简化：实际可用 Specification
        } else {
            page = roleRepository.findAll(pageable);
        }
        return page.map(this::toDTO);
    }

    /**
     * 查询角色详情（含权限ID列表）
     */
    public RoleDTO findById(Long id) {
        SysRole role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("角色不存在: " + id));
        return toDTO(role);
    }

    /**
     * 查询所有有效角色（用于下拉选择）
     */
    public List<RoleDTO> findAllActive() {
        return roleRepository.findAll().stream()
                .filter(r -> r.getStatus() == 1)
                .map(this::toDTO)
                .toList();
    }

    /**
     * 新增角色
     */
    @Transactional
    public RoleDTO create(SysRole role, List<Long> permissionIds) {
        role.setStatus(1);
        SysRole saved = roleRepository.save(role);

        if (permissionIds != null && !permissionIds.isEmpty()) {
            assignPermissions(saved.getId(), permissionIds);
        }

        return toDTO(saved);
    }

    /**
     * 修改角色
     */
    @Transactional
    public RoleDTO update(SysRole role, List<Long> permissionIds) {
        SysRole existing = roleRepository.findById(role.getId())
                .orElseThrow(() -> new RuntimeException("角色不存在: " + role.getId()));

        existing.setRoleName(role.getRoleName());
        existing.setDataScope(role.getDataScope());
        existing.setSort(role.getSort());
        existing.setRemark(role.getRemark());
        existing.setStatus(role.getStatus());

        SysRole saved = roleRepository.save(existing);

        if (permissionIds != null) {
            assignPermissions(saved.getId(), permissionIds);
        }

        return toDTO(saved);
    }

    /**
     * 删除角色（检查是否有关联用户）
     */
    @Transactional
    public void delete(Long id) {
        // 检查是否有用户关联此角色
        List<Long> userIds = userRoleRepository.findAll().stream()
                .filter(ur -> ur.getRoleId().equals(id))
                .map(ur -> ur.getUserId())
                .toList();
        if (!userIds.isEmpty()) {
            throw new RuntimeException("该角色下有 " + userIds.size() + " 个用户，无法删除");
        }

        rolePermissionRepository.deleteByRoleId(id);
        roleRepository.deleteById(id);
    }

    /**
     * 分配权限（先删后增）
     */
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        rolePermissionRepository.deleteByRoleId(roleId);
        for (Long permId : permissionIds) {
            rolePermissionRepository.save(new SysRolePermission(roleId, permId));
        }
    }

    // ==================== 私有方法 ====================

    private RoleDTO toDTO(SysRole role) {
        List<Long> permIds = rolePermissionRepository.findPermissionIdsByRoleId(role.getId());

        return RoleDTO.builder()
                .id(role.getId())
                .roleName(role.getRoleName())
                .roleKey(role.getRoleKey())
                .dataScope(role.getDataScope())
                .status(role.getStatus())
                .remark(role.getRemark())
                .permissionIds(permIds)
                .build();
    }
}
