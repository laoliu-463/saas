# Evidence - DDD100 User CRUD Issue #34

## Metadata

| Field | Value |
|---|---|
| Date | 2026-06-27 |
| Env | local real-pre |
| Branch | `feature/ddd/DDD-VERIFY-001` |
| Report Base Commit | `9d5790e6` before #34 commit |
| Worktree Status | DIRTY with planned #34 files; unrelated untracked `harness/reports/2026-06-21/ddd-order-amount-046/` observed and excluded |
| Scope | backend user-domain boundary |
| Issue | #34 `[DDD100-USER-CRUD] SysUser CRUD Application 收口` |

## Changed Files

- `backend/src/main/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationA.java`
- `backend/src/main/java/com/colonel/saas/domain/user/application/SysUserCRUDApplicationB.java`
- `backend/src/test/java/com/colonel/saas/auth/service/SysUserServiceAssignableBoundaryTest.java`
- `harness/reports/2026-06-21/ddd-user-crud-034/evidence-20260627-123000-sysuser-crud-application.md`
- `harness/reports/2026-06-21/ddd-user-crud-034/retro-20260627-123000-sysuser-crud-application.md`
- `harness/engineering/issues-index.md`
- `harness/rules/changelog.md`
- `harness/rules/state/snapshots/01-当前项目状态.md`
- `harness/rules/state/snapshots/DOMAIN_STATUS.md`

## Evidence Chain

- Phenomenon: #34 requires SysUser CRUD use cases to be closed into the user-domain Application while preserving existing API and Legacy compatibility.
- Code fact: `SysUserController` still uses compatibility `SysUserService`; `SysUserService` delegates `getById/create` to `SysUserCRUDApplicationA` and `update/delete/resetPassword` to `SysUserCRUDApplicationB`.
- Gap found: `SysUserServiceAssignableBoundaryTest` covered query/assignable/role assignment delegation, but not CRUD true-route delegation. `SysUserCRUDApplicationB` also had a stale class comment saying it was not connected to Controller/Service.
- Change: added CRUD delegation coverage and corrected stale Application A/B comments. No runtime behavior, API, DB schema, default config, or feature flag changed.

## Verification

| Check | Result | Evidence |
|---|---|---|
| code-review-graph intake | PASS | 13,459 nodes, 153,431 edges; low risk for user CRUD boundary slice |
| Targeted service boundary test | PASS | `mvn -q -f backend/pom.xml -Dtest=SysUserServiceAssignableBoundaryTest test` |
| User CRUD application/controller suite | PASS | `SysUserServiceAssignableBoundaryTest`, `SysUserCRUDApplicationATest`, `SysUserCRUDApplicationBTest`, `SysUserCRUDApplicationABoundaryTest`, `SysUserCRUDApplicationBBoundaryTest`, `SysUserCrudMutationStoreAdapterTest`, `SysUserControllerTest` |
| Backend compile | PASS | `mvn -q -f backend/pom.xml -DskipTests compile` |
| real-pre safety check | PASS | `safety-check.ps1 -Env real-pre -Scope backend` |
| Backend health | PASS | `verify-local.ps1 -Env real-pre -Scope backend`, health returned `{"status":"UP"}` |
| Docker status | PASS | `docker compose -f docker-compose.real-pre.yml ps`: backend/frontend/postgres/redis all `Up` and `healthy` |
| Harness limits | PASS | `check-harness-limits.ps1` returned `PASS`; `DOMAIN_STATUS.md` 187 lines, `01-当前项目状态.md` 40 lines |
| Docker restart | SKIP | test/comment/docs-only slice; no production runtime artifact changed |
| Business E2E | SKIP | no behavior change; authenticated real-pre user CRUD write E2E intentionally not executed without a disposable account window |

## Result

Status: PASS for #34 boundary/evidence slice.

## Remaining Risks

- This proves the compatibility service delegates CRUD use cases into user-domain Applications; it does not prove authenticated real-pre create/update/delete/password flows against live users.
- `SysUserController` remains a compatibility controller using `SysUserService`; replacing that route directly with user-domain API/query layers is still future work.
- Unrelated untracked #46 report directory must remain excluded from the #34 commit.
