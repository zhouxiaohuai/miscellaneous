package com.example.transaction.security.controller;

import com.example.transaction.security.entity.SysPermission;
import com.example.transaction.security.service.SysPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 权限/菜单管理控制器
 *
 * 接口列表：
 *   GET    /api/sys/permissions/tree     — 权限树
 *   GET    /api/sys/permissions          — 权限列表
 *   GET    /api/sys/permissions/{id}     — 权限详情
 *   POST   /api/sys/permissions          — 新增权限
 *   PUT    /api/sys/permissions          — 修改权限
 *   DELETE /api/sys/permissions/{id}     — 删除权限
 */
@RestController
@RequestMapping("/api/sys/permissions")
@RequiredArgsConstructor
public class SysPermissionController {

    private final SysPermissionService permissionService;

    /**
     * 查询权限树（用于菜单管理和权限分配）
     */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('sys:perm:list')")
    public ResponseEntity<List<Map<String, Object>>> getPermissionTree() {
        return ResponseEntity.ok(permissionService.buildPermissionTree());
    }

    /**
     * 查询所有权限（平铺列表）
     */
    @GetMapping
    @PreAuthorize("hasAuthority('sys:perm:list')")
    public ResponseEntity<List<SysPermission>> findAll() {
        return ResponseEntity.ok(permissionService.findAll());
    }

    /**
     * 查询权限详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:perm:list')")
    public ResponseEntity<SysPermission> findById(@PathVariable Long id) {
        return ResponseEntity.ok(permissionService.findById(id));
    }

    /**
     * 新增权限
     */
    @PostMapping
    @PreAuthorize("hasAuthority('sys:perm:add')")
    public ResponseEntity<SysPermission> create(@RequestBody SysPermission permission) {
        return ResponseEntity.ok(permissionService.create(permission));
    }

    /**
     * 修改权限
     */
    @PutMapping
    @PreAuthorize("hasAuthority('sys:perm:edit')")
    public ResponseEntity<SysPermission> update(@RequestBody SysPermission permission) {
        return ResponseEntity.ok(permissionService.update(permission));
    }

    /**
     * 删除权限
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:perm:delete')")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }
}
