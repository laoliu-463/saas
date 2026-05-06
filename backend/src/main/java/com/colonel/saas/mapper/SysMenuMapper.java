package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.colonel.saas.entity.SysMenu;
import com.colonel.saas.vo.SysMenuVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    @Select("SELECT * FROM sys_menu WHERE deleted = 0 ORDER BY sort_order ASC, create_time ASC")
    List<SysMenu> findAllActive();

    @Select("SELECT m.* FROM sys_menu m " +
            "INNER JOIN sys_role_menu rm ON rm.menu_id = m.id " +
            "WHERE rm.role_id = #{roleId} AND m.deleted = 0 " +
            "ORDER BY m.sort_order ASC, m.create_time ASC")
    List<SysMenu> findByRoleId(@Param("roleId") UUID roleId);

    List<SysMenuVO> findAllAsTree(@Param("status") Integer status);
}
