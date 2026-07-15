# Evidence Report

## Metadata

- Time: 2026-07-15 15:47:11 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/ddd-user-role-application
- Commit: 311e14ef
- Owned worktree: dirty
- Deploy remote: false

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/auth/service/AuthService.java
backend/src/main/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationA.java
backend/src/main/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationB.java
backend/src/main/java/com/colonel/saas/domain/user/application/SysUserGroupMembershipApplication.java
backend/src/main/java/com/colonel/saas/domain/user/application/SysUserRoleAssignmentApplicationService.java
backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserAssignmentLookupAdapter.java
backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserCrudMutationStoreAdapter.java
backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserDepartmentLookupAdapter.java
backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserRoleAssignmentStoreAdapter.java
backend/src/main/java/com/colonel/saas/domain/user/policy/UserAssignmentPolicy.java
backend/src/main/java/com/colonel/saas/domain/user/port/UserAssignmentLookup.java
backend/src/main/java/com/colonel/saas/mapper/SysUserMapper.java
backend/src/test/java/com/colonel/saas/auth/service/AuthServiceTest.java
backend/src/test/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationATest.java
backend/src/test/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationBTest.java
backend/src/test/java/com/colonel/saas/domain/user/application/SysUserGroupMembershipApplicationTest.java
backend/src/test/java/com/colonel/saas/domain/user/application/SysUserRoleAssignmentApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/domain/user/infrastructure/SysUserCrudMutationStoreAdapterTest.java
backend/src/test/java/com/colonel/saas/mapper/SysUserMapperTest.java
~~~

## Owned Git Status

~~~text
M backend/src/main/java/com/colonel/saas/auth/service/AuthService.java
 M backend/src/main/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationA.java
 M backend/src/main/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationB.java
 M backend/src/main/java/com/colonel/saas/domain/user/application/SysUserGroupMembershipApplication.java
 M backend/src/main/java/com/colonel/saas/domain/user/application/SysUserRoleAssignmentApplicationService.java
 M backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserAssignmentLookupAdapter.java
 M backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserCrudMutationStoreAdapter.java
 M backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserDepartmentLookupAdapter.java
 M backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserRoleAssignmentStoreAdapter.java
 M backend/src/main/java/com/colonel/saas/domain/user/policy/UserAssignmentPolicy.java
 M backend/src/main/java/com/colonel/saas/domain/user/port/UserAssignmentLookup.java
 M backend/src/main/java/com/colonel/saas/mapper/SysUserMapper.java
 M backend/src/test/java/com/colonel/saas/auth/service/AuthServiceTest.java
 M backend/src/test/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationATest.java
 M backend/src/test/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationBTest.java
 M backend/src/test/java/com/colonel/saas/domain/user/application/SysUserGroupMembershipApplicationTest.java
 M backend/src/test/java/com/colonel/saas/domain/user/application/SysUserRoleAssignmentApplicationServiceTest.java
 M backend/src/test/java/com/colonel/saas/domain/user/infrastructure/SysUserCrudMutationStoreAdapterTest.java
 M backend/src/test/java/com/colonel/saas/mapper/SysUserMapperTest.java
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                        PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    29 seconds ago   Up 26 seconds (healthy)       127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   2 minutes ago    Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   2 minutes ago    Up 2 minutes (healthy)        5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      5 minutes ago    Up 5 minutes (healthy)        6379/tcp
NAMES                             STATUS                        PORTS
saas-active-backend-real-pre-1    Up 26 seconds (healthy)       127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up About a minute (healthy)   127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   Up 2 minutes (healthy)        5432/tcp
saas-active-redis-real-pre-1      Up 5 minutes (healthy)        6379/tcp
campus_frontend                   Up 24 hours                   0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 24 hours (healthy)         0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 24 hours (healthy)         0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 24 hours (unhealthy)       0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation skipped by -SkipBusinessValidation; not a full PASS.
~~~

## Content Maintenance Result

~~~text
Content maintenance skipped by -ContentMaintenance off.
~~~

## Remote Deploy Result

~~~text
remote not deployed
~~~

## Retro Summary

用户生命周期全量审计发现按 ID 管理入口和 refresh token 未过滤软删除记录；已补活动用户查询与应用层防御校验，并以失败测试固定回归边界。前端列表、全局用户名唯一约束、并发重复映射和历史展示路径已复核。real-pre 业务预检因本地 admin 凭据 HTTP 401 阻塞，未伪造业务 PASS；前端全量测试另有既有路由懒加载超时。

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
