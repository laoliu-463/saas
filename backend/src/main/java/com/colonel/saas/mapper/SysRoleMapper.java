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

/**
 * 系统角色数据访问层
 * <p>
 * 对应数据库表：sys_role
 * 所属业务领域：用户域 - 角色权限管理
 * 主要操作：角色的 CRUD 操作、软删除，按用户查询角色，分页和全量查询角色列表
 * </p>
 *
 * @see com.colonel.saas.entity.SysRole
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 根据用户 ID 查询该用户关联的角色列表
     * <p>
     * 通过 INNER JOIN sys_user_role 关联表查询用户拥有的角色。
     * </p>
     *
     * @param userId 用户主键 UUID
     * @return 该用户关联的角色列表
     */
    @Select("SELECT r.* FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND r.deleted = 0 AND ur.deleted = 0")
    List<SysRole> findByUserId(@Param("userId") UUID userId);

    /**
     * 根据角色编码查询角色
     *
     * @param roleCode 角色编码
     * @return 包含角色信息的 Optional，不存在时为空
     */
    @Select("SELECT * FROM sys_role WHERE role_code = #{roleCode} AND deleted = 0 LIMIT 1")
    Optional<SysRole> findByRoleCode(@Param("roleCode") String roleCode);

    /**
     * 软删除角色（逻辑删除）
     *
     * @param id 角色主键 UUID
     * @return 受影响行数
     */
    @Update("UPDATE sys_role SET deleted = 1, update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = 0")
    int softDeleteById(@Param("id") UUID id);

    /**
     * 分页查询角色列表
     * <p>
     * 支持按关键词模糊搜索和状态筛选。
     * </p>
     *
     * @param page    分页参数
     * @param keyword 搜索关键词（角色名称或编码），为 null 时不过滤
     * @param status  角色状态筛选，为 null 时不过滤
     * @return 分页结果
     * @see com.colonel.saas.vo.SysRoleVO
     */
    IPage<SysRoleVO> findPage(
            Page<SysRoleVO> page,
            @Param("keyword") String keyword,
            @Param("status") Integer status
    );

    /**
     * 查询所有角色列表（不分页）
     *
     * @param status 角色状态筛选，为 null 时不过滤
     * @return 角色列表
     * @see com.colonel.saas.vo.SysRoleVO
     */
    List<SysRoleVO> findAll(@Param("status") Integer status);
}
