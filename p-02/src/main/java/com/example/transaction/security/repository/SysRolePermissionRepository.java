package com.example.transaction.security.repository;

import com.example.transaction.security.entity.SysRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SysRolePermissionRepository extends JpaRepository<SysRolePermission, SysRolePermission.SysRolePermissionPK> {

    /**
     * 删除角色的所有权限关联（重新分配前清空）
     */
    @Modifying
    @Query("DELETE FROM SysRolePermission rp WHERE rp.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 查询角色的权限ID列表
     */
    @Query("SELECT rp.permissionId FROM SysRolePermission rp WHERE rp.roleId = :roleId")
    List<Long> findPermissionIdsByRoleId(@Param("roleId") Long roleId);
}
