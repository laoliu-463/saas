package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SysMenu;
import com.colonel.saas.vo.SysMenuVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * 系统菜单数据访问层
 * <p>
 * 对应数据库表：sys_menu
 * 所属业务领域：用户域 - 菜单/权限管理
 * 主要操作：菜单的 CRUD 操作，按角色查询菜单，菜单树形结构查询
 * </p>
 *
 * @see com.colonel.saas.entity.SysMenu
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 查询所有未删除的菜单
     *
     * @return 菜单列表，按排序号和创建时间升序排列
     */
    @Select("SELECT * FROM sys_menu WHERE deleted = 0 ORDER BY sort_order ASC, create_time ASC")
    List<SysMenu> findAllActive();

    /**
     * 根据角色 ID 查询该角色关联的菜单列表
     * <p>
     * 通过 INNER JOIN sys_role_menu 关联表查询角色拥有的菜单权限。
     * </p>
     *
     * @param roleId 角色主键 UUID
     * @return 该角色关联的菜单列表
     */
    @Select("SELECT m.* FROM sys_menu m " +
            "INNER JOIN sys_role_menu rm ON rm.menu_id = m.id " +
            "WHERE rm.role_id = #{roleId} AND m.deleted = 0 " +
            "ORDER BY m.sort_order ASC, m.create_time ASC")
    List<SysMenu> findByRoleId(@Param("roleId") UUID roleId);

    /**
     * 查询菜单树形结构
     * <p>
     * 返回构建好的树形菜单 VO 列表，包含父子层级关系。
     * </p>
     *
     * @param status 菜单状态筛选条件，为 null 时不过滤
     * @return 菜单树形结构列表
     * @see com.colonel.saas.vo.SysMenuVO
     */
    List<SysMenuVO> findAllAsTree(@Param("status") Integer status);
}
