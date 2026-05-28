package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * 角色-菜单关联数据访问层
 * <p>
 * 对应数据库表：sys_role_menu
 * 所属业务领域：用户域 - 角色权限管理
 * 主要操作：角色与菜单的关联关系管理，支持按角色删除、查询菜单 ID 列表、批量统计
 * </p>
 *
 * @see com.colonel.saas.entity.SysRoleMenu
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    /**
     * 删除指定角色的所有菜单关联关系（物理删除）
     * <p>
     * 用于角色重新分配菜单权限时先清空旧关联，再批量插入新关联。
     * </p>
     *
     * @param roleId 角色主键 UUID
     * @return 受影响行数
     */
    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") UUID roleId);

    /**
     * 查询指定角色关联的所有菜单 ID
     *
     * @param roleId 角色主键 UUID
     * @return 菜单 ID 列表
     */
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<UUID> findMenuIdsByRoleId(@Param("roleId") UUID roleId);

    /**
     * 统计指定菜单 ID 列表中的关联记录总数
     * <p>
     * 用于判断菜单是否被任何角色引用，防止删除正在使用的菜单。
     * 使用 MyBatis foreach 动态 SQL 实现 IN 查询。
     * </p>
     *
     * @param menuIds 菜单 ID 列表
     * @return 关联记录总数
     */
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
