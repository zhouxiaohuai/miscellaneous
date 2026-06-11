package com.example.transaction.security.controller;

import com.example.transaction.security.dto.RoleDTO;
import com.example.transaction.security.entity.SysRole;
import com.example.transaction.security.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色管理控制器
 *
 * 接口列表：
 *   GET    /api/sys/roles                   — 分页查询角色
 *   GET    /api/sys/roles/all               — 查询所有有效角色
 *   GET    /api/sys/roles/{id}              — 查询角色详情
 *   POST   /api/sys/roles                   — 新增角色
 *   PUT    /api/sys/roles                   — 修改角色
 *   DELETE /api/sys/roles/{id}              — 删除角色
 *   PUT    /api/sys/roles/{id}/permissions   — 分配权限
 */
@RestController
@RequestMapping("/api/sys/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    /**
     * 分页查询角色
     */
    @GetMapping
    @PreAuthorize("hasAuthority('sys:role:list')")
    public ResponseEntity<Page<RoleDTO>> findRoles(
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("sort").ascending());
        Page<RoleDTO> result = roleService.findRoles(roleName, status, pageRequest);
        return ResponseEntity.ok(result);
    }

    /**
     * 查询所有有效角色（用于下拉选择）
     */
    @GetMapping("/all")
    public ResponseEntity<List<RoleDTO>> findAllActive() {
        return ResponseEntity.ok(roleService.findAllActive());
    }

    /**
     * 查询角色详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:role:list')")
    public ResponseEntity<RoleDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(roleService.findById(id));
    }

    /**
     * 新增角色
     */
    @PostMapping
    @PreAuthorize("hasAuthority('sys:role:add')")
    public ResponseEntity<RoleDTO> create(@RequestBody SysRole role,
                                          @RequestParam(required = false) List<Long> permissionIds) {
        return ResponseEntity.ok(roleService.create(role, permissionIds));
    }

    /**
     * 修改角色
     */
    @PutMapping
    @PreAuthorize("hasAuthority('sys:role:edit')")
    public ResponseEntity<RoleDTO> update(@RequestBody SysRole role,
                                          @RequestParam(required = false) List<Long> permissionIds) {
        return ResponseEntity.ok(roleService.update(role, permissionIds));
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:role:delete')")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    /**
     * 分配权限
     */
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('sys:role:assign')")
    public ResponseEntity<Map<String, Object>> assignPermissions(@PathVariable Long id,
                                                                  @RequestBody List<Long> permissionIds) {
        roleService.assignPermissions(id, permissionIds);
        return ResponseEntity.ok(Map.of("code", 200, "message", "权限分配成功"));
    }
}
