package com.example.transaction.security.controller;

import com.example.transaction.security.entity.SysDept;
import com.example.transaction.security.repository.SysDeptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理控制器
 *
 * 接口列表：
 *   GET    /api/sys/depts/tree   — 部门树
 *   GET    /api/sys/depts        — 部门列表
 *   POST   /api/sys/depts        — 新增部门
 *   PUT    /api/sys/depts        — 修改部门
 *   DELETE /api/sys/depts/{id}   — 删除部门
 */
@RestController
@RequestMapping("/api/sys/depts")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptRepository deptRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('sys:dept:list')")
    public ResponseEntity<List<SysDept>> findAll() {
        return ResponseEntity.ok(deptRepository.findByStatusOrderBySortAsc(1));
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('sys:dept:list')")
    public ResponseEntity<List<SysDept>> findTree() {
        return ResponseEntity.ok(deptRepository.findByStatusOrderBySortAsc(1));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sys:dept:add')")
    public ResponseEntity<SysDept> create(@RequestBody SysDept dept) {
        dept.setStatus(1);
        return ResponseEntity.ok(deptRepository.save(dept));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('sys:dept:edit')")
    public ResponseEntity<SysDept> update(@RequestBody SysDept dept) {
        return ResponseEntity.ok(deptRepository.save(dept));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:dept:delete')")
    public ResponseEntity<java.util.Map<String, Object>> delete(@PathVariable Long id) {
        deptRepository.deleteById(id);
        return ResponseEntity.ok(java.util.Map.of("code", 200, "message", "删除成功"));
    }
}
