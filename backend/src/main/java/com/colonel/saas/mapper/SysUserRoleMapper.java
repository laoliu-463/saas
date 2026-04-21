package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Select("SELECT * FROM sys_user_role WHERE user_id = #{userId} AND deleted = 0")
    List<SysUserRole> findByUserId(@Param("userId") UUID userId);

    @Select("SELECT * FROM sys_user_role WHERE role_id = #{roleId} AND deleted = 0")
    List<SysUserRole> findByRoleId(@Param("roleId") UUID roleId);

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
