package com.colonel.saas.mapper;

import com.colonel.saas.mapper.projection.AuthorizationSnapshotRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

@Mapper
public interface AuthorizationSnapshotMapper {

    @Select("""
            SELECT u.id AS user_id,
                   u.dept_id,
                   u.authz_version,
                   r.id AS role_id,
                   p.permission_code,
                   p.domain_code,
                   p.data_scope_required,
                   rds.scope_code
            FROM sys_user u
            LEFT JOIN sys_user_role ur
              ON ur.user_id = u.id
             AND ur.deleted = 0
            LEFT JOIN sys_role r
              ON r.id = ur.role_id
             AND r.status = 1
             AND r.deleted = 0
            LEFT JOIN sys_role_permission rp ON rp.role_id = r.id
            LEFT JOIN sys_permission p
              ON p.id = rp.permission_id
             AND p.status = 1
             AND p.deleted = 0
            LEFT JOIN sys_role_domain_scope rds
              ON rds.role_id = r.id
             AND rds.domain_code = p.domain_code
            WHERE u.id = #{userId}
              AND u.status = 1
              AND u.deleted = 0
            ORDER BY p.permission_code, r.id
            """)
    List<AuthorizationSnapshotRow> findActiveSnapshotRows(@Param("userId") UUID userId);
}
