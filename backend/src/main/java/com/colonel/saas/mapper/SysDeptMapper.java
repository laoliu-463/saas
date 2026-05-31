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

/**
 * 系统部门/组织数据访问层
 * <p>
 * 对应数据库表：sys_dept
 * 所属业务领域：用户域 - 部门/组织管理
 * 主要操作：部门的 CRUD 操作、软删除、层级关系查询、成员数量统计
 * </p>
 *
 * @see com.colonel.saas.entity.SysDept
 */
@Mapper
public interface SysDeptMapper extends BaseMapper<SysDept> {

    /**
     * 查询所有未删除的部门列表
     *
     * @return 部门列表，按排序号和名称升序排列
     */
    @Select("SELECT * FROM sys_dept WHERE deleted = 0 ORDER BY sort_order ASC, dept_name ASC")
    List<SysDept> findAllActive();

    /**
     * 查询所有未删除的部门列表。
     *
     * @return 部门列表，按排序号和名称升序排列
     */
    @Select("SELECT * FROM sys_dept WHERE deleted = 0 ORDER BY sort_order ASC, dept_name ASC")
    List<SysDept> findAllNonDeleted();

    /**
     * 按部门类型查询未删除的部门列表。
     *
     * @param deptType 部门类型
     * @return 部门列表，按排序号和名称升序排列
     */
    @Select("""
            SELECT * FROM sys_dept
            WHERE deleted = 0 AND dept_type = #{deptType}
            ORDER BY sort_order ASC, dept_name ASC
            """)
    List<SysDept> findByDeptType(@Param("deptType") String deptType);

    /**
     * 根据部门编码查询部门
     *
     * @param deptCode 部门编码
     * @return 包含部门信息的 Optional，不存在时为空
     */
    @Select("SELECT * FROM sys_dept WHERE dept_code = #{deptCode} AND deleted = 0 LIMIT 1")
    Optional<SysDept> findByDeptCode(@Param("deptCode") String deptCode);

    /**
     * 软删除部门（逻辑删除）
     *
     * @param id 部门主键 UUID
     * @return 受影响行数
     */
    @Update("UPDATE sys_dept SET deleted = 1, update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 0")
    int softDeleteById(@Param("id") UUID id);

    /**
     * 统计指定部门直属的用户数量
     *
     * @param deptId 部门主键 UUID
     * @return 该部门下的用户数量
     */
    @Select("SELECT COUNT(1) FROM sys_user WHERE deleted = 0 AND dept_id = #{deptId}")
    long countUsersByDeptId(@Param("deptId") UUID deptId);

    /**
     * 统计指定父部门下的子部门数量
     *
     * @param parentId 父部门主键 UUID
     * @return 子部门数量
     */
    @Select("""
            SELECT COUNT(1) FROM sys_dept
            WHERE deleted = 0 AND parent_id = #{parentId}
            """)
    long countChildGroups(@Param("parentId") UUID parentId);

    /**
     * 统计指定父部门下的子部门数量。
     *
     * @param parentId 父部门主键 UUID
     * @return 子部门数量
     */
    @Select("""
            SELECT COUNT(1) FROM sys_dept
            WHERE deleted = 0 AND parent_id = #{parentId}
            """)
    long countChildrenByParentId(@Param("parentId") UUID parentId);

    /**
     * 按类型统计指定父部门下的子部门数量
     *
     * @param parentId 父部门主键 UUID
     * @param deptType 部门类型
     * @return 指定类型的子部门数量
     */
    @Select("""
            SELECT COUNT(1) FROM sys_dept
            WHERE deleted = 0 AND parent_id = #{parentId} AND dept_type = #{deptType}
            """)
    long countChildGroupsByType(@Param("parentId") UUID parentId, @Param("deptType") String deptType);

    /**
     * 查询指定父部门下的所有子部门
     *
     * @param parentId 父部门主键 UUID
     * @return 子部门列表，按排序号和名称升序排列
     */
    @Select("""
            SELECT * FROM sys_dept
            WHERE deleted = 0 AND parent_id = #{parentId}
            ORDER BY sort_order ASC, dept_name ASC
            """)
    List<SysDept> findByParentId(@Param("parentId") UUID parentId);

    /**
     * 统计部门及其直接子部门下的成员总数
     * <p>
     * 包含两部分：直接属于该部门的用户 + 属于该部门直接子部门的用户。
     * 通过 LEFT JOIN sys_dept 实现两级成员统计。
     * </p>
     *
     * @param deptId 部门主键 UUID
     * @return 该部门及其子部门下的成员总数
     */
    @Select("""
            SELECT COUNT(1) FROM sys_user su
            LEFT JOIN sys_dept d ON d.id = su.dept_id AND d.deleted = 0
            WHERE su.deleted = 0
              AND (su.dept_id = #{deptId} OR d.parent_id = #{deptId})
            """)
    long countMembersUnderDept(@Param("deptId") UUID deptId);
}
