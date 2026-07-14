package com.colonel.saas.mapper;

import com.colonel.saas.mapper.projection.AuthorizationVersionChangeRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface AuthorizationVersionMapper {

    @Select("""
            WITH changed AS (
                UPDATE sys_user
                   SET authz_version = authz_version + 1,
                       update_time = CURRENT_TIMESTAMP
                 WHERE id = #{userId}
                   AND deleted = 0
             RETURNING id AS user_id,
                       authz_version - 1 AS previous_version,
                       authz_version AS current_version
            )
            SELECT user_id, previous_version, current_version
              FROM changed
            """)
    List<AuthorizationVersionChangeRow> incrementUser(@Param("userId") UUID userId);

    @Select("""
            WITH changed AS (
                UPDATE sys_user u
                   SET authz_version = authz_version + 1,
                       update_time = CURRENT_TIMESTAMP
                 WHERE u.deleted = 0
                   AND EXISTS (
                       SELECT 1
                         FROM sys_user_role ur
                        WHERE ur.user_id = u.id
                          AND ur.role_id = #{roleId}
                          AND ur.deleted = 0
                   )
             RETURNING u.id AS user_id,
                       u.authz_version - 1 AS previous_version,
                       u.authz_version AS current_version
            )
            SELECT user_id, previous_version, current_version
              FROM changed
             ORDER BY user_id
            """)
    List<AuthorizationVersionChangeRow> incrementUsersByRole(
            @Param("roleId") UUID roleId);
}
