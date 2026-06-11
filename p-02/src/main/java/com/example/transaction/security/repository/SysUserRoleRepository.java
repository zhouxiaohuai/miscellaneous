package com.example.transaction.security.repository;

import com.example.transaction.security.entity.SysUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SysUserRoleRepository extends JpaRepository<SysUserRole, SysUserRole.SysUserRolePK> {

    /**
     * 删除用户的所有角色关联（重新分配前清空）
     */
    @Modifying
    @Query("DELETE FROM SysUserRole ur WHERE ur.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的角色ID列表
     */
    @Query("SELECT ur.roleId FROM SysUserRole ur WHERE ur.userId = :userId")
    List<Long> findRoleIdsByUserId(@Param("userId") Long userId);
}
