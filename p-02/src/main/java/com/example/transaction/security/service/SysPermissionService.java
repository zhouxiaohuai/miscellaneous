package com.example.transaction.security.service;

import com.example.transaction.security.entity.SysPermission;
import com.example.transaction.security.repository.SysPermissionRepository;
import com.example.transaction.security.repository.SysRolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限/菜单管理服务
 *
 * 职责：
 * 1. 权限树构建（前端菜单渲染用）
 * 2. 权限 CRUD
 * 3. 根据用户角色查询权限
 */
@Service
@RequiredArgsConstructor
public class SysPermissionService {

    private final SysPermissionRepository permissionRepository;
    private final SysRolePermissionRepository rolePermissionRepository;

    /**
     * 查询所有有效权限（平铺列表）
     */
    public List<SysPermission> findAll() {
        return permissionRepository.findByStatusOrderBySortAsc(1);
    }

    /**
     * 构建权限树（递归，前端菜单用）
     * 返回树形结构，每个节点包含 children 列表
     */
    public List<Map<String, Object>> buildPermissionTree() {
        List<SysPermission> all = findAll();
        return buildTree(all, 0L);
    }

    /**
     * 根据角色ID列表查询菜单权限树（前端动态路由用）
     */
    public List<Map<String, Object>> buildMenuTreeByRoleIds(List<Long> roleIds) {
        List<SysPermission> menus = permissionRepository.findMenusByRoleIds(roleIds);
        return buildTree(menus, 0L);
    }

    /**
     * 查询权限详情
     */
    public SysPermission findById(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("权限不存在: " + id));
    }

    /**
     * 新增权限
     */
    @Transactional
    public SysPermission create(SysPermission permission) {
        permission.setStatus(1);
        return permissionRepository.save(permission);
    }

    /**
     * 修改权限
     */
    @Transactional
    public SysPermission update(SysPermission permission) {
        SysPermission existing = findById(permission.getId());
        existing.setName(permission.getName());
        existing.setPermKey(permission.getPermKey());
        existing.setType(permission.getType());
        existing.setPath(permission.getPath());
        existing.setComponent(permission.getComponent());
        existing.setIcon(permission.getIcon());
        existing.setSort(permission.getSort());
        existing.setVisible(permission.getVisible());
        existing.setStatus(permission.getStatus());
        existing.setParentId(permission.getParentId());
        return permissionRepository.save(existing);
    }

    /**
     * 删除权限（检查是否有子权限）
     */
    @Transactional
    public void delete(Long id) {
        List<SysPermission> children = permissionRepository.findByParentIdAndStatusOrderBySortAsc(id, 1);
        if (!children.isEmpty()) {
            throw new RuntimeException("该权限下有 " + children.size() + " 个子权限，请先删除子权限");
        }
        // 删除角色-权限关联
        // 实际项目中应该级联删除，这里简化处理
        permissionRepository.deleteById(id);
    }

    // ==================== 私有方法 ====================

    /**
     * 递归构建树形结构
     */
    private List<Map<String, Object>> buildTree(List<SysPermission> all, Long parentId) {
        return all.stream()
                .filter(p -> Objects.equals(p.getParentId(), parentId))
                .sorted(Comparator.comparingInt(SysPermission::getSort))
                .map(p -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", p.getId());
                    node.put("parentId", p.getParentId());
                    node.put("name", p.getName());
                    node.put("permKey", p.getPermKey());
                    node.put("type", p.getType());
                    node.put("path", p.getPath());
                    node.put("component", p.getComponent());
                    node.put("icon", p.getIcon());
                    node.put("sort", p.getSort());
                    node.put("visible", p.getVisible());
                    node.put("children", buildTree(all, p.getId()));
                    return node;
                })
                .toList();
    }
}
