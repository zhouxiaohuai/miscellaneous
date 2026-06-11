package com.example.transaction.security.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 权限表（菜单/按钮/接口统一管理）
 * type=1 目录，2 菜单，3 按钮，4 接口
 * 形成树形结构，parent_id 关联
 */
@Data
@Entity
@Table(name = "sys_permission")
public class SysPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long parentId = 0L;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 100)
    private String permKey;

    /** 1=目录 2=菜单 3=按钮 4=接口 */
    @Column(nullable = false)
    private Integer type;

    @Column(length = 200)
    private String path;

    @Column(length = 200)
    private String component;

    @Column(length = 100)
    private String icon;

    private Integer sort = 0;

    /** 0=隐藏 1=可见 */
    @Column(nullable = false)
    private Integer visible = 1;

    /** 0=禁用 1=正常 */
    @Column(nullable = false)
    private Integer status = 1;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
