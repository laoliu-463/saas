package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SysDept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {

    @Select("SELECT * FROM sys_dept WHERE deleted = 0 ORDER BY sort_order ASC, dept_name ASC")
    List<SysDept> findAllActive();

    @Select("SELECT * FROM sys_dept WHERE dept_code = #{deptCode} AND deleted = 0 LIMIT 1")
    Optional<SysDept> findByDeptCode(@Param("deptCode") String deptCode);

    @Update("UPDATE sys_dept SET deleted = 1, update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 0")
    int softDeleteById(@Param("id") UUID id);

    @Select("SELECT COUNT(1) FROM sys_user WHERE deleted = 0 AND dept_id = #{deptId}")
    long countUsersByDeptId(@Param("deptId") UUID deptId);

    @Select("""
            SELECT COUNT(1) FROM sys_dept
            WHERE deleted = 0 AND parent_id = #{parentId}
            """)
    long countChildGroups(@Param("parentId") UUID parentId);

    @Select("""
            SELECT COUNT(1) FROM sys_dept
            WHERE deleted = 0 AND parent_id = #{parentId} AND dept_type = #{deptType}
            """)
    long countChildGroupsByType(@Param("parentId") UUID parentId, @Param("deptType") String deptType);

    @Select("""
            SELECT * FROM sys_dept
            WHERE deleted = 0 AND parent_id = #{parentId}
            ORDER BY sort_order ASC, dept_name ASC
            """)
    List<SysDept> findByParentId(@Param("parentId") UUID parentId);

    @Select("""
            SELECT COUNT(1) FROM sys_user su
            LEFT JOIN sys_dept d ON d.id = su.dept_id AND d.deleted = 0
            WHERE su.deleted = 0
              AND (su.dept_id = #{deptId} OR d.parent_id = #{deptId})
            """)
    long countMembersUnderDept(@Param("deptId") UUID deptId);
}
