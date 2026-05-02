package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.SysRole;
import com.colonel.saas.vo.SysRoleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT r.* FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND r.deleted = 0 AND ur.deleted = 0")
    List<SysRole> findByUserId(@Param("userId") UUID userId);

    @Select("SELECT * FROM sys_role WHERE role_code = #{roleCode} AND deleted = 0 LIMIT 1")
    Optional<SysRole> findByRoleCode(@Param("roleCode") String roleCode);

    @Update("UPDATE sys_role SET deleted = 1, update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 0")
    int softDeleteById(@Param("id") UUID id);

    IPage<SysRoleVO> findPage(
            Page<SysRoleVO> page,
            @Param("keyword") String keyword,
            @Param("status") Integer status
    );

    List<SysRoleVO> findAll(@Param("status") Integer status);
}
