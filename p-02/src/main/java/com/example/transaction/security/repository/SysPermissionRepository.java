package com.example.transaction.security.repository;

import com.example.transaction.security.entity.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SysPermissionRepository extends JpaRepository<SysPermission, Long> {

    /**
     * 根据角色ID列表查询权限（通过中间表关联）
     * 只查 type=3(按钮) 或 type=4(接口) 的权限标识
     */
    @Query("SELECT p FROM SysPermission p, SysRolePermission rp " +
           "WHERE p.id = rp.permissionId AND rp.roleId IN :roleIds AND p.status = 1")
    List<SysPermission> findByRoleIds(@Param("roleIds") List<Long> roleIds);

    /**
     * 根据角色ID列表查询菜单/目录权限（用于前端路由）
     * type=1(目录) 或 type=2(菜单)
     */
    @Query("SELECT p FROM SysPermission p, SysRolePermission rp " +
           "WHERE p.id = rp.permissionId AND rp.roleId IN :roleIds AND p.type IN (1, 2) AND p.status = 1 " +
           "ORDER BY p.sort")
    List<SysPermission> findMenusByRoleIds(@Param("roleIds") List<Long> roleIds);

    /**
     * 查询所有有效权限（按父ID排序，用于构建树）
     */
    List<SysPermission> findByStatusOrderBySortAsc(Integer status);

    /**
     * 根据父ID查询子权限
     */
    List<SysPermission> findByParentIdAndStatusOrderBySortAsc(Long parentId, Integer status);
}
