package com.colonel.saas.mapper;

import com.colonel.saas.vo.AuthorizationPermissionVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface SysRolePermissionMapper {

    @Select("""
            SELECT permission_code, domain_code, resource_code, action_code
            FROM sys_permission
            WHERE status = 1 AND deleted = 0
            ORDER BY domain_code, resource_code, action_code
            """)
    List<AuthorizationPermissionVO> findCatalog();

    @Select("""
            SELECT p.permission_code
            FROM sys_role_permission rp
            JOIN sys_permission p ON p.id = rp.permission_id
            WHERE rp.role_id = #{roleId} AND p.status = 1 AND p.deleted = 0
            ORDER BY p.permission_code
            """)
    List<String> findPermissionCodesByRoleId(@Param("roleId") UUID roleId);

    @Select("""
            SELECT id FROM sys_permission
            WHERE permission_code = #{permissionCode} AND status = 1 AND deleted = 0
            """)
    Optional<UUID> findPermissionIdByCode(@Param("permissionCode") String permissionCode);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") UUID roleId);

    @Insert("""
            INSERT INTO sys_role_permission (role_id, permission_id, create_by)
            VALUES (#{roleId}, #{permissionId}, #{operatorId})
            """)
    int insertGrant(
            @Param("roleId") UUID roleId,
            @Param("permissionId") UUID permissionId,
            @Param("operatorId") UUID operatorId);

    @Update("""
            UPDATE sys_user u
            SET authz_version = authz_version + 1,
                update_time = CURRENT_TIMESTAMP
            WHERE EXISTS (
                SELECT 1 FROM sys_user_role ur
                WHERE ur.user_id = u.id
                  AND ur.role_id = #{roleId}
                  AND ur.deleted = 0
            )
            """)
    int bumpAssignedUserAuthorizationVersion(@Param("roleId") UUID roleId);
}
