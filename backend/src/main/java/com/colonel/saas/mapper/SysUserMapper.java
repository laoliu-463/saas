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
import org.apache.ibatis.annotations.Update;

import java.util.Optional;
import java.util.UUID;

/**
 * 系统用户数据访问层
 * <p>
 * 对应数据库表：sys_user
 * 所属业务领域：用户域 - 用户管理
 * 主要操作：用户的 CRUD 操作、软删除，按用户名查询，渠道编码唯一性校验，
 * 带数据权限的分页查询
 * </p>
 *
 * @see com.colonel.saas.entity.SysUser
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 包含用户信息的 Optional，不存在时为空
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0 LIMIT 1")
    Optional<SysUser> findByUsername(@Param("username") String username);

    /**
     * 检查渠道编码是否已存在（包含已删除的记录）
     * <p>
     * 用于创建用户时的唯一性校验，已软删除的渠道编码也不允许重复使用。
     * </p>
     *
     * @param channelCode 渠道编码
     * @return true 表示已存在，false 表示可用
     */
    @Select("SELECT COUNT(1) > 0 FROM sys_user WHERE channel_code = #{channelCode}")
    boolean existsByChannelCodeIncludingDeleted(@Param("channelCode") String channelCode);

    /**
     * 软删除用户（逻辑删除）
     *
     * @param id 用户主键 UUID
     * @return 受影响行数
     */
    @Update("UPDATE sys_user SET deleted = 1, update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 0")
    int softDeleteById(@Param("id") UUID id);

    /**
     * 带数据权限范围的分页查询用户
     * <p>
     * 通过 @DataScope 注解按 su.id 字段注入数据权限过滤条件，
     * 根据当前用户的数据范围（self/group/all）过滤用户列表。
     * 支持请求参数中的各种筛选条件。
     * </p>
     *
     * @param page    分页参数
     * @param request 用户分页查询请求参数
     * @param wrapper 查询条件构造器
     * @return 分页结果
     * @see com.colonel.saas.vo.SysUserVO
     * @see com.colonel.saas.auth.dto.SysUserPageRequest
     */
    @DataScope(userField = "su.id")
    IPage<SysUserVO> findPage(
            Page<SysUserVO> page,
            @Param("request") SysUserPageRequest request,
            @Param(Constants.WRAPPER) QueryWrapper<SysUser> wrapper
    );
}
