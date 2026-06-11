package com.example.transaction.security.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 部门表（数据权限用）
 * 树形结构，parent_id 关联
 */
@Data
@Entity
@Table(name = "sys_dept")
public class SysDept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long parentId = 0L;

    @Column(nullable = false, length = 50)
    private String deptName;

    private Integer sort = 0;

    @Column(length = 50)
    private String leader;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    /** 0=禁用 1=正常 */
    @Column(nullable = false)
    private Integer status = 1;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
