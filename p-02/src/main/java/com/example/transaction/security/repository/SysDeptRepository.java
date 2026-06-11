package com.example.transaction.security.repository;

import com.example.transaction.security.entity.SysDept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SysDeptRepository extends JpaRepository<SysDept, Long> {

    List<SysDept> findByParentIdAndStatusOrderBySortAsc(Long parentId, Integer status);

    List<SysDept> findByStatusOrderBySortAsc(Integer status);

    /**
     * 查询指定部门及其所有下级部门ID（递归查询，用于数据权限）
     * MySQL 8.0+ 递归 CTE
     */
    @Query(value = "WITH RECURSIVE dept_tree AS (" +
                   "  SELECT id FROM sys_dept WHERE id = :deptId " +
                   "  UNION ALL " +
                   "  SELECT d.id FROM sys_dept d INNER JOIN dept_tree dt ON d.parent_id = dt.id" +
                   ") SELECT id FROM dept_tree", nativeQuery = true)
    List<Long> findDescendantIds(@Param("deptId") Long deptId);
}
