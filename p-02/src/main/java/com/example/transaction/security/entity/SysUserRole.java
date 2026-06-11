package com.example.transaction.security.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 用户-角色关联表（多对多中间表）
 */
@Data
@Entity
@Table(name = "sys_user_role")
@IdClass(SysUserRole.SysUserRolePK.class)
public class SysUserRole {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "role_id")
    private Long roleId;

    public SysUserRole() {}

    public SysUserRole(Long userId, Long roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    /** 联合主键 */
    public static class SysUserRolePK implements java.io.Serializable {
        private Long userId;
        private Long roleId;

        public SysUserRolePK() {}
        public SysUserRolePK(Long userId, Long roleId) {
            this.userId = userId;
            this.roleId = roleId;
        }
    }
}
