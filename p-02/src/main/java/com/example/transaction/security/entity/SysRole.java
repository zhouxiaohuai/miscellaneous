package com.example.transaction.security.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 角色表
 * data_scope 控制数据权限范围：全部/本部门/本部门及以下/仅本人
 */
@Data
@Entity
@Table(name = "sys_role")
public class SysRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String roleName;

    @Column(nullable = false, unique = true, length = 50)
    private String roleKey;

    /** 数据范围：1=全部 2=本部门 3=本部门及以下 4=仅本人 */
    @Column(nullable = false)
    private Integer dataScope = 1;

    private Integer sort = 0;

    /** 0=禁用 1=正常 */
    @Column(nullable = false)
    private Integer status = 1;

    @Column(length = 500)
    private String remark;

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
