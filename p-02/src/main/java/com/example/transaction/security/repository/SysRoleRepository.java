package com.example.transaction.security.repository;

import com.example.transaction.security.entity.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SysRoleRepository extends JpaRepository<SysRole, Long> {

    /**
     * 根据用户ID查询角色列表（通过中间表关联）
     */
    @Query("SELECT r FROM SysRole r, SysUserRole ur WHERE r.id = ur.roleId AND ur.userId = :userId AND r.status = 1")
    List<SysRole> findRolesByUserId(@Param("userId") Long userId);
}
