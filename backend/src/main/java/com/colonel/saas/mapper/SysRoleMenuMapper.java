package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") UUID roleId);

    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<UUID> findMenuIdsByRoleId(@Param("roleId") UUID roleId);

    @Select({
            "<script>",
            "SELECT COUNT(*) FROM sys_role_menu",
            "WHERE menu_id IN",
            "<foreach item='menuId' collection='menuIds' open='(' separator=',' close=')'>",
            "#{menuId}",
            "</foreach>",
            "</script>"
    })
    int countByMenuIds(@Param("menuIds") List<UUID> menuIds);
}
