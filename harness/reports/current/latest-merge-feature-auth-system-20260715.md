# Evidence Report

## Metadata

- Time: 2026-07-15 20:07:44 +08:00
- Environment: real-pre
- Scope: full
- Branch: codex/merge-feature-auth-system-20260715
- Commit: e0948241
- Owned worktree: dirty
- Deploy remote: true

## Owned Files

~~~text
backend/src/main/java/com/colonel/saas/auth/service/AuthService.java
backend/src/main/java/com/colonel/saas/domain/product/application/ProductQuickSampleApplicationService.java
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
backend/src/test/java/com/colonel/saas/architecture/DddProduct003ProductApplicationRoutingTest.java
backend/src/test/java/com/colonel/saas/auth/service/AuthServiceTest.java
backend/src/test/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationATest.java
backend/src/test/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationBTest.java
backend/src/test/java/com/colonel/saas/domain/user/application/SysUserGroupMembershipApplicationTest.java
backend/src/test/java/com/colonel/saas/domain/user/application/SysUserRoleAssignmentApplicationServiceTest.java
backend/src/test/java/com/colonel/saas/domain/user/infrastructure/SysUserCrudMutationStoreAdapterTest.java
backend/src/test/java/com/colonel/saas/mapper/SysUserMapperTest.java
frontend/nginx/default.conf.template
frontend/src/components/product/ProductSelectionCard.test.ts
frontend/src/components/product/ProductSelectionCard.vue
frontend/src/config/nginx-csp.test.ts
frontend/src/views/product/components/QuickSampleModal.test.ts
frontend/src/views/product/components/QuickSampleModal.vue
frontend/src/views/product/components/QuickSampleTalentPicker.test.ts
frontend/src/views/product/components/QuickSampleTalentPicker.vue
frontend/src/views/product/product-library-display.test.ts
frontend/src/views/product/product-library-display.ts
harness/reports/current/latest-admin-login-credential-source.md
harness/reports/current/latest-colonel-sync-schema-20260715.md
harness/reports/current/latest-content-retire.md
harness/reports/current/latest-merge-feature-auth-system-20260715.md
harness/reports/current/latest-product-library-copy-image.md
harness/reports/current/latest-product-library-cross-platform-clipboard.md
harness/reports/current/latest-product-library-cross-platform-evidence-retire.md
harness/reports/current/latest-product-library-image-clipboard.md
harness/reports/current/latest-product-library-image-csp.md
harness/reports/current/latest-product-library-rich-clipboard-evidence-amend.md
harness/reports/current/latest-product-sample-setting-drawer-deploy.md
harness/reports/current/latest-quick-sample-address-modal-layout.md
harness/reports/current/latest-quick-sample-snapshot-address-fix.md
harness/reports/current/latest-quick-sample-snapshot-address-fix-state.md
harness/reports/current/latest-quick-sample-talent-picker.md
harness/reports/current/latest-quick-sample-talent-picker-drawer.md
harness/reports/current/latest-talent-address-product-sample-diagnosis.md
harness/reports/current/latest-talent-address-product-sample-diagnosis-run.md
harness/reports/current/latest-user-lifecycle-audit-20260715.md
harness/rules/state/snapshots/KNOWN_ISSUES.md
runtime/qa/real-pre-env.cjs
runtime/qa/real-pre-env.test.cjs
runtime/qa/real-pre-p0.cjs
runtime/qa/real-pre-preflight.cjs
runtime/qa/real-pre-preflight.test.cjs
~~~

## Owned Git Status

~~~text
M  backend/src/main/java/com/colonel/saas/auth/service/AuthService.java
M  backend/src/main/java/com/colonel/saas/domain/product/application/ProductQuickSampleApplicationService.java
M  backend/src/main/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationA.java
M  backend/src/main/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationB.java
M  backend/src/main/java/com/colonel/saas/domain/user/application/SysUserGroupMembershipApplication.java
M  backend/src/main/java/com/colonel/saas/domain/user/application/SysUserRoleAssignmentApplicationService.java
M  backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserAssignmentLookupAdapter.java
M  backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserCrudMutationStoreAdapter.java
M  backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserDepartmentLookupAdapter.java
M  backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysUserRoleAssignmentStoreAdapter.java
M  backend/src/main/java/com/colonel/saas/domain/user/policy/UserAssignmentPolicy.java
M  backend/src/main/java/com/colonel/saas/domain/user/port/UserAssignmentLookup.java
M  backend/src/main/java/com/colonel/saas/mapper/SysUserMapper.java
M  backend/src/test/java/com/colonel/saas/architecture/DddProduct003ProductApplicationRoutingTest.java
M  backend/src/test/java/com/colonel/saas/auth/service/AuthServiceTest.java
M  backend/src/test/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationATest.java
M  backend/src/test/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationBTest.java
M  backend/src/test/java/com/colonel/saas/domain/user/application/SysUserGroupMembershipApplicationTest.java
M  backend/src/test/java/com/colonel/saas/domain/user/application/SysUserRoleAssignmentApplicationServiceTest.java
M  backend/src/test/java/com/colonel/saas/domain/user/infrastructure/SysUserCrudMutationStoreAdapterTest.java
M  backend/src/test/java/com/colonel/saas/mapper/SysUserMapperTest.java
M  frontend/nginx/default.conf.template
M  frontend/src/components/product/ProductSelectionCard.test.ts
M  frontend/src/components/product/ProductSelectionCard.vue
M  frontend/src/config/nginx-csp.test.ts
M  frontend/src/views/product/components/QuickSampleModal.test.ts
M  frontend/src/views/product/components/QuickSampleModal.vue
A  frontend/src/views/product/components/QuickSampleTalentPicker.test.ts
A  frontend/src/views/product/components/QuickSampleTalentPicker.vue
M  frontend/src/views/product/product-library-display.test.ts
M  frontend/src/views/product/product-library-display.ts
A  harness/reports/current/latest-admin-login-credential-source.md
M  harness/reports/current/latest-colonel-sync-schema-20260715.md
M  harness/reports/current/latest-content-retire.md
A  harness/reports/current/latest-product-library-copy-image.md
A  harness/reports/current/latest-product-library-cross-platform-clipboard.md
A  harness/reports/current/latest-product-library-cross-platform-evidence-retire.md
A  harness/reports/current/latest-product-library-image-clipboard.md
A  harness/reports/current/latest-product-library-image-csp.md
A  harness/reports/current/latest-product-library-rich-clipboard-evidence-amend.md
M  harness/reports/current/latest-product-sample-setting-drawer-deploy.md
A  harness/reports/current/latest-quick-sample-address-modal-layout.md
A  harness/reports/current/latest-quick-sample-snapshot-address-fix-state.md
A  harness/reports/current/latest-quick-sample-snapshot-address-fix.md
A  harness/reports/current/latest-quick-sample-talent-picker-drawer.md
A  harness/reports/current/latest-quick-sample-talent-picker.md
A  harness/reports/current/latest-talent-address-product-sample-diagnosis-run.md
A  harness/reports/current/latest-talent-address-product-sample-diagnosis.md
A  harness/reports/current/latest-user-lifecycle-audit-20260715.md
M  harness/rules/state/snapshots/KNOWN_ISSUES.md
M  runtime/qa/real-pre-env.cjs
M  runtime/qa/real-pre-env.test.cjs
M  runtime/qa/real-pre-p0.cjs
M  runtime/qa/real-pre-preflight.cjs
M  runtime/qa/real-pre-preflight.test.cjs
?? harness/reports/current/latest-merge-feature-auth-system-20260715.md
~~~

## Build Result

~~~text
not collected
Backend build: PASS (mvn -f backend/pom.xml -DskipTests package)
Frontend build: PASS (npm --prefix frontend ci; npm --prefix frontend run build)
~~~

## Docker Status

~~~text
NAME                              IMAGE                            COMMAND                  SERVICE             CREATED          STATUS                    PORTS
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    "sh -c 'java $JAVA_O…"   backend-real-pre    26 seconds ago   Up 22 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   "/docker-entrypoint.…"   frontend-real-pre   24 seconds ago   Up 6 seconds (healthy)    127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               "docker-entrypoint.s…"   postgres-real-pre   4 minutes ago    Up 4 minutes (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   "docker-entrypoint.s…"   redis-real-pre      4 hours ago      Up 4 hours (healthy)      6379/tcp
NAMES                             STATUS                    PORTS
saas-active-frontend-real-pre-1   Up 6 seconds (healthy)    127.0.0.1:3001->80/tcp
saas-active-backend-real-pre-1    Up 22 seconds (healthy)   127.0.0.1:8081->8080/tcp
saas-active-postgres-real-pre-1   Up 4 minutes (healthy)    5432/tcp
saas-active-redis-real-pre-1      Up 4 hours (healthy)      6379/tcp
campus_frontend                   Up 28 hours               0.0.0.0:5173->5173/tcp, [::]:5173->5173/tcp
campus_backend                    Up 28 hours (healthy)     0.0.0.0:8000->8000/tcp, [::]:8000->8000/tcp
campus_postgres                   Up 28 hours (healthy)     0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
saas-test-backend-1               Up 28 hours (unhealthy)   0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
~~~

## Health Check Result

~~~text
Local health verification: PASS
~~~

## Business Validation Result

~~~text
Business validation: PASS (npm run e2e:real-pre:p0:preflight)
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

No actionable Harness improvement was recorded; no standalone retro is required.

## Conclusion

PARTIAL

## Residual Risk

- Items marked as not collected are not proof of success.
