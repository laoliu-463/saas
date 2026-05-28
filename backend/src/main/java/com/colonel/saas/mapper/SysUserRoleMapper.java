package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * 用户-角色关联数据访问层
 * <p>
 * 对应数据库表：sys_user_role
 * 所属业务领域：用户域 - 用户角色管理
 * 主要操作：用户与角色的关联关系管理，支持按用户/角色删除、查询，批量查询
 * </p>
 *
 * @see com.colonel.saas.entity.SysUserRole
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 删除指定用户的所有角色关联关系（物理删除）
     * <p>
     * 用于用户重新分配角色时先清空旧关联，再批量插入新关联。
     * 注意：此操作为物理删除，非软删除。
     * </p>
     *
     * @param userId 用户主键 UUID
     * @return 受影响行数
     */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserIdPhysical(@Param("userId") UUID userId);

    /**
     * 查询指定用户关联的所有角色关系
     *
     * @param userId 用户主键 UUID
     * @return 用户角色关联列表
     */
    @Select("SELECT * FROM sys_user_role WHERE user_id = #{userId} AND deleted = 0")
    List<SysUserRole> findByUserId(@Param("userId") UUID userId);

    /**
     * 查询指定角色关联的所有用户关系
     *
     * @param roleId 角色主键 UUID
     * @return 用户角色关联列表
     */
    @Select("SELECT * FROM sys_user_role WHERE role_id = #{roleId} AND deleted = 0")
    List<SysUserRole> findByRoleId(@Param("roleId") UUID roleId);

    /**
     * 批量查询多个用户的角色关联关系
     * <p>
     * 使用 MyBatis foreach 动态 SQL 实现 IN 查询。
     * </p>
     *
     * @param userIds 用户主键 UUID 列表
     * @return 用户角色关联列表
     */
    @Select({
            "<script>",
            "SELECT * FROM sys_user_role",
            "WHERE deleted = 0",
            "AND user_id IN",
            "<foreach item='userId' collection='userIds' open='(' separator=',' close=')'>",
            "#{userId}",
            "</foreach>",
            "</script>"
    })
    List<SysUserRole> findByUserIds(@Param("userIds") List<UUID> userIds);
}
