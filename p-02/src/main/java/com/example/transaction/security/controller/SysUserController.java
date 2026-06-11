package com.example.transaction.security.controller;

import com.example.transaction.security.dto.UserDTO;
import com.example.transaction.security.entity.SysUser;
import com.example.transaction.security.service.SysUserService;
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
 * 用户管理控制器
 *
 * 接口列表：
 *   GET    /api/sys/users                    — 分页查询用户
 *   GET    /api/sys/users/{id}               — 查询用户详情
 *   POST   /api/sys/users                    — 新增用户
 *   PUT    /api/sys/users                    — 修改用户
 *   DELETE /api/sys/users/{id}               — 删除用户
 *   PUT    /api/sys/users/{id}/reset-password — 重置密码
 *   PUT    /api/sys/users/{id}/status        — 启用/禁用
 */
@RestController
@RequestMapping("/api/sys/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    /**
     * 分页查询用户
     */
    @GetMapping
    @PreAuthorize("hasAuthority('sys:user:list')")
    public ResponseEntity<Page<UserDTO>> findUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long deptId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("id").descending());
        Page<UserDTO> result = userService.findUsers(username, status, deptId, pageRequest);
        return ResponseEntity.ok(result);
    }

    /**
     * 查询用户详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:user:list')")
    public ResponseEntity<UserDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    /**
     * 新增用户
     */
    @PostMapping
    @PreAuthorize("hasAuthority('sys:user:add')")
    public ResponseEntity<UserDTO> create(@RequestBody SysUser user,
                                          @RequestParam(required = false) List<Long> roleIds) {
        return ResponseEntity.ok(userService.create(user, roleIds));
    }

    /**
     * 修改用户
     */
    @PutMapping
    @PreAuthorize("hasAuthority('sys:user:edit')")
    public ResponseEntity<UserDTO> update(@RequestBody SysUser user,
                                          @RequestParam(required = false) List<Long> roleIds) {
        return ResponseEntity.ok(userService.update(user, roleIds));
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:user:delete')")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    /**
     * 重置密码
     */
    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('sys:user:reset-pwd')")
    public ResponseEntity<Map<String, Object>> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return ResponseEntity.ok(Map.of("code", 200, "message", "密码已重置为 123456"));
    }

    /**
     * 启用/禁用用户
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('sys:user:edit')")
    public ResponseEntity<Map<String, Object>> updateStatus(@PathVariable Long id,
                                                            @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return ResponseEntity.ok(Map.of("code", 200, "message", "状态更新成功"));
    }
}
