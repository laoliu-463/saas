package com.colonel.saas.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.DataScope;
import com.colonel.saas.auth.dto.SysUserPageRequest;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.vo.SysUserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0 LIMIT 1")
    Optional<SysUser> findByUsername(@Param("username") String username);

    @DataScope(userField = "su.id")
    IPage<SysUserVO> findPage(
            Page<SysUserVO> page,
            @Param("request") SysUserPageRequest request,
            @Param(Constants.WRAPPER) QueryWrapper<SysUser> wrapper
    );
}
