package com.example.transaction.security.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 角色-权限关联表（多对多中间表）
 */
@Data
@Entity
@Table(name = "sys_role_permission")
@IdClass(SysRolePermission.SysRolePermissionPK.class)
public class SysRolePermission {

    @Id
    @Column(name = "role_id")
    private Long roleId;

    @Id
    @Column(name = "permission_id")
    private Long permissionId;

    public SysRolePermission() {}

    public SysRolePermission(Long roleId, Long permissionId) {
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    /** 联合主键 */
    public static class SysRolePermissionPK implements java.io.Serializable {
        private Long roleId;
        private Long permissionId;

        public SysRolePermissionPK() {}
        public SysRolePermissionPK(Long roleId, Long permissionId) {
            this.roleId = roleId;
            this.permissionId = permissionId;
        }
    }
}
