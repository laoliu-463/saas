# DDD RBAC Phase 2 Shadow Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** 计划已生成并完成静态自审；等待用户评审与本地 `real-pre` migration 明确授权，尚未开始实施、迁移或运行时激活。

**Goal:** 在不推测角色—权限矩阵、不改变现有业务权限结果的前提下，建立可灰度的 `LEGACY/SHADOW/ENFORCE` 授权运行时、`authzVersion` 令牌失效、版本化授权快照缓存和差异日志，并以默认 `LEGACY` 保持现网兼容。

**Architecture:** 用户域继续拥有授权主体、授权版本和授权决策；安全过滤器只负责认证、令牌版本校验和建立 `AuthorizationPrincipal`。权限运行时通过一个唯一协调器比较 legacy 结果和新决策：`LEGACY` 不调用新链，`SHADOW` 返回 legacy 结果并记录差异，`ENFORCE` 返回新链结果；Phase 2 不把任何业务 Controller、`RoleGuardAspect` 或数据范围切面接入该协调器。授权版本在数据库事务内递增，Redis 只缓存以 `(userId, authzVersion)` 为键的快照；当前版本仍由 PostgreSQL 权威读取，数据库不可用时 fail closed。

**Tech Stack:** Java 17, Spring Boot 3.2.5, Spring Security, MyBatis-Plus 3.5.6, PostgreSQL 15/16, Redis 7, JUnit 5, Mockito, AssertJ, Testcontainers, Maven, PowerShell Harness.

---

## 0. 执行边界与人工门禁

本计划是 Phase 2 的文件级实施入口，不是数据库或运行时激活授权。执行时必须依次通过以下门：

1. **计划评审门**：确认本文件，不得把候选权限矩阵写入 `sys_permission`、`sys_role_permission` 或 `sys_role_domain_scope`。
2. **本地 migration 门**：用户明确批准本地 `real-pre` migration window 后，才执行 Task 7 的 SQL；未批准时允许完成代码和 Testcontainers 测试，但不得重启使用新代码的本地 backend。
3. **本地 SHADOW 门**：migration、全量测试、容器健康检查全部通过后，再由用户明确批准把本地运行模式从 `LEGACY` 改为 `SHADOW`。不得修改或提交 `.env.real-pre`；激活只记录环境变量名称和存在性，不记录值。
4. **远端门**：本计划默认 `DeployRemote=false`，不推送基线分支、不执行远端 migration、不部署远端。
5. **ENFORCE 门**：Phase 2 只实现并测试 `ENFORCE` 语义，不允许任何生产请求消费者进入 `ENFORCE`。首个领域强制切换属于 Phase 4，并要求业务确认权限矩阵和差异清零。

### 已确认的现状证据

- `JwtAuthenticationFilter` 是唯一 JWT 认证入口，当前从令牌读取 `deptId/dataScope/roleCodes/username` 并把 UUID 放入 `SecurityContext`。
- `JwtTokenProvider` 当前 access/refresh token 均不含 `authzVersion`。
- `AuthService.login/refreshToken` 是 access/refresh token 的生产调用点；refresh 接口绕过 JWT filter，因此必须自行校验 refresh token 的授权版本。
- `AuthorizationFacade` 目前只有 Phase 1 单元测试消费者，没有业务请求消费者。
- `SysUserRoleAssignmentApplicationService`、`SysRoleApplication`、`SysMenuService`、`SysUserCRUDApplicationB`、`SysUserGroupMembershipApplication` 是当前已发现的授权事实写入口。
- 本地 `real-pre` 在 Phase 1 evidence 中已只读确认 RBAC 四表数量为 0、`sys_user.authz_version` 列数量为 0；远端状态未知。
- Phase 1 基线在本计划 worktree 已复核：8 份 Surefire 报告，91 tests，0 failures/errors/skips。
- code-review-graph 对 11 个既有认证/授权写入口做 2-hop 分析，结果为 High risk：109 个直接节点、500/574 个受影响节点、185 个附加文件；因此本计划按可独立回归的提交拆分，并在最终门禁要求全量 backend 测试。

### 本阶段明确不做

- 不从 `@RequireRoles`、菜单、JSON permissions 自动生成并写入权限事实表。
- 不新增业务权限码，不 seed 角色权限，不修改业务域状态机、归属、金额、提成或数据范围规则。
- 不把前端菜单/按钮切换到新权限投影；该工作属于 Phase 3。
- 不把 `RoleGuardAspect` 改成“旧链或新链任一允许即放行”。
- 不把 Redis 当作当前授权版本的唯一事实来源；Redis 驱逐失败不能使旧令牌继续有效。

## 1. 文件结构与职责

### 新建文件

- `backend/src/main/java/com/colonel/saas/config/AuthorizationRuntimeProperties.java`：绑定默认模式、领域模式和快照 TTL。
- `backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationRuntimeMode.java`：`LEGACY/SHADOW/ENFORCE` 枚举。
- `backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationPrincipal.java`：数据库确认后的授权主体。
- `backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationTokenRejectedException.java`：账号失效、版本缺失或过旧的 401 语义。
- `backend/src/main/java/com/colonel/saas/domain/user/api/AuthorizationUnavailableException.java`：授权事实不可取得的 503 语义。
- `backend/src/main/java/com/colonel/saas/domain/user/facade/AuthorizationPrincipalFacade.java`：安全层解析可信主体的窄接口。
- `backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationPrincipalStore.java`：读取当前用户状态和授权版本。
- `backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationSnapshotCache.java`：版本化快照缓存端口。
- `backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationVersionStore.java`：事务内递增授权版本的端口。
- `backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationPrincipalApplicationService.java`：比较 token/数据库版本并建立主体。
- `backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationRuntimeService.java`：三态决策与唯一 shadow 比较入口。
- `backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationVersionApplicationService.java`：事务内版本递增并发布提交后驱逐事件。
- `backend/src/main/java/com/colonel/saas/domain/user/domain/AuthorizationComparison.java`：稳定差异分类。
- `backend/src/main/java/com/colonel/saas/domain/user/domain/AuthorizationRuntimeDecision.java`：legacy/new/effective 三方结果。
- `backend/src/main/java/com/colonel/saas/domain/user/event/AuthorizationVersionChangedEvent.java`：提交后缓存驱逐事实。
- `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationPrincipalStoreAdapter.java`：`sys_user` 主体读取适配器。
- `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/RedisAuthorizationSnapshotCacheAdapter.java`：JSON 字符串 Redis 快照缓存。
- `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/VersionedAuthorizationSnapshotStore.java`：Redis-first、PostgreSQL fallback 的 `@Primary` 快照 store。
- `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationVersionStoreAdapter.java`：PostgreSQL `UPDATE ... RETURNING` 适配器。
- `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/AuthorizationVersionCacheEvictListener.java`：`AFTER_COMMIT` 精确驱逐旧版本 key。
- `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/AuthorizationDifferenceLogger.java`：不记录 token/角色列表的结构化差异日志。
- `backend/src/main/java/com/colonel/saas/mapper/AuthorizationVersionMapper.java`：主体版本读取与批量递增 SQL。
- `backend/src/main/java/com/colonel/saas/mapper/projection/AuthorizationVersionChangeRow.java`：版本递增返回行。

### 修改文件

- `backend/src/main/java/com/colonel/saas/entity/SysUser.java`：映射 `authz_version`。
- `backend/src/main/java/com/colonel/saas/security/JwtTokenProvider.java`：access/refresh token 写入版本声明。
- `backend/src/main/java/com/colonel/saas/security/JwtAuthenticationFilter.java`：非 legacy 模式校验版本、建立主体并区分 401/503。
- `backend/src/main/java/com/colonel/saas/auth/service/AuthService.java`：登录/刷新签发当前版本，刷新令牌校验版本。
- `backend/src/main/java/com/colonel/saas/domain/user/facade/AuthorizationFacade.java`：以 `AuthorizationPrincipal` 而非裸 userId 授权。
- `backend/src/main/java/com/colonel/saas/domain/user/port/AuthorizationSnapshotStore.java`：显式接收 expected version。
- `backend/src/main/java/com/colonel/saas/domain/user/application/AuthorizationApplicationService.java`：使用主体版本读取快照。
- `backend/src/main/java/com/colonel/saas/domain/user/infrastructure/SysAuthorizationSnapshotStoreAdapter.java`：数据库层校验 expected version。
- `backend/src/main/java/com/colonel/saas/mapper/AuthorizationSnapshotMapper.java`：查询增加 `u.authz_version = #{authzVersion}`。
- 五个授权事实写应用服务：在原事务内递增版本。
- `backend/src/main/java/com/colonel/saas/common/result/ResultCode.java`、`GlobalExceptionHandler.java`：增加 503。
- 三个 runtime profile YAML：默认 `LEGACY`，不默认开启 shadow。
- Phase 1 相关测试：适配 principal/version 签名。
- `DddAuthorizationDormancyContractTest`：替换为 Phase 2 activation-boundary 守卫。
- `docs/07-权限与数据范围.md`、`docs/领域/用户域.md`：只在 evidence 后更新已验证状态。


## 2. 可执行任务分片

以下分片是同一份计划的组成部分，必须依序执行；不得只读取单个分片后跳过总入口门禁。

1. [Task 1：三态配置与默认 LEGACY](2026-07-13-ddd-rbac-shadow-runtime/task-01-runtime-modes.md)
2. [Task 2A：AuthorizationPrincipal 合同](2026-07-13-ddd-rbac-shadow-runtime/task-02a-principal-contracts.md)
3. [Task 2B：应用服务与数据库适配](2026-07-13-ddd-rbac-shadow-runtime/task-02b-principal-adapter.md)
4. [Task 3：JWT authzVersion](2026-07-13-ddd-rbac-shadow-runtime/task-03-jwt-authz-version.md)
5. [Task 4：JWT filter 版本校验](2026-07-13-ddd-rbac-shadow-runtime/task-04-jwt-filter.md)
6. [Task 5A：版本化缓存合同](2026-07-13-ddd-rbac-shadow-runtime/task-05a-cache-contracts.md)
7. [Task 5B：Redis 缓存适配与装饰器](2026-07-13-ddd-rbac-shadow-runtime/task-05b-cache-adapters.md)
8. [Task 6A：三态授权协调器](2026-07-13-ddd-rbac-shadow-runtime/task-06a-runtime-coordinator.md)
9. [Task 6B：差异日志与 503 语义](2026-07-13-ddd-rbac-shadow-runtime/task-06b-difference-logging.md)
10. [Task 7A：授权版本存储与事务事件](2026-07-13-ddd-rbac-shadow-runtime/task-07a-version-store.md)
11. [Task 7B：授权事实写入口接线](2026-07-13-ddd-rbac-shadow-runtime/task-07b-version-writers.md)
12. [Task 8：激活边界守卫](2026-07-13-ddd-rbac-shadow-runtime/task-08-activation-guard.md)
13. [Task 9：本地 migration 人工门](2026-07-13-ddd-rbac-shadow-runtime/task-09-local-migration-gate.md)
14. [Task 10：全量验证、evidence 与推送](2026-07-13-ddd-rbac-shadow-runtime/task-10-verification.md)

## Verification matrix

| Requirement | Proof | Failure classification |
| --- | --- | --- |
| Default compatibility | Three profile contract tests + resolved local mode | FAIL if any profile defaults SHADOW/ENFORCE |
| Old access/refresh invalidation | Filter/AuthService tests + real local mutation when authorized | BLOCKED if no valid account/mutation window |
| Transactional version bump | PostgreSQL integration + rollback test | FAIL if bump commits separately or rollback leaks |
| Versioned cache | Redis adapter/decorator tests | FAIL if key omits version or cache outage allows stale data |
| 401/403/503 | Filter, runtime coordinator, handler tests | FAIL if store outage is mapped to 401/500 or deny is OR-bypassed |
| Shadow differences | Exact enum/log tests | FAIL if OLD_ALLOW_NEW_DENY and OLD_DENY_NEW_ALLOW collapse |
| No business-rule invention | Empty permission tables + activation guard | FAIL if seed/mapping/request consumer appears |
| Local runtime | backend build/restart/health/API | PARTIAL/BLOCKED if business account unavailable |
| Remote | Explicit deploy evidence | UNKNOWN/NOT EXECUTED by default |

## Known risks and follow-up boundaries

- The current-version lookup is PostgreSQL-authoritative on every non-legacy authenticated request. This is intentional for immediate revocation correctness; collect latency and connection-pool evidence before proposing a Redis current-version cache.
- Versioned snapshot entries may remain until TTL when post-commit Redis eviction fails, but an old token still fails the PostgreSQL version comparison. Do not relax that comparison for availability.
- Switching the first domain out of LEGACY requires versioned tokens globally because the JWT filter cannot know the eventual Controller domain before authentication. Plan an observed re-login window.
- Phase 2 has no approved role-permission seed matrix and therefore cannot produce meaningful business `OLD_ALLOW_NEW_DENY` counts. The logger/coordinator are tested infrastructure; Phase 4 provides the first approved request consumer.
- Phase 3 新增 `sys_role_permission`、`sys_role_domain_scope` 管理写入口时，必须在同一数据库事务内调用本阶段的版本服务；Phase 2 不提前创建这些尚无业务合同的写 API。
- The lexical architecture guard can false-positive comments/strings and cannot detect reflection. It is a regression detector, not a security control.
- Existing `UserDomainEventPublisher` swallows publication failures and must not carry the transactional version increment. The new version service updates PostgreSQL synchronously before publishing an AFTER_COMMIT cache event.
- Historical Harness report-root debt remains outside this RBAC batch unless separately scoped.

## Self-review checklist

- **Spec coverage:** migration, principal, access/refresh versions, cache, three modes, 401/403/503, difference classification, transactional bumps, rollback, architecture guard, local evidence, remote exclusion and business matrix gate all map to Tasks 1-10.
- **No permission invention:** no permission code, role grant, domain scope, seed DML or production request consumer is added.
- **Type consistency:** `AuthorizationPrincipal.authzVersion` is `long`; JWT claim and entity field are `Long`; store/facade methods use the same expected version; cache keys use the same pair.
- **Mode consistency:** LEGACY does not call the new facade; SHADOW returns legacy and logs; ENFORCE returns new and is not connected to production requests in Phase 2.
- **Rollback consistency:** mode rollback keeps additive schema and data; no destructive SQL appears.
- **Unresolved-marker scan:** the implementation steps contain no unresolved markers; all blocked work is expressed as an explicit human authorization gate with a defined evidence state.
