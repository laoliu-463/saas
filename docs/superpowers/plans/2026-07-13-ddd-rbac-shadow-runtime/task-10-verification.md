# Phase 2 Task 10：全量验证、evidence 与推送

> 返回[Phase 2 总入口](../2026-07-13-ddd-rbac-shadow-runtime.md)。本分片必须按总入口的人工门禁和任务顺序执行。

## Task 10: Full verification, local SHADOW gate, docs, evidence and push

**Files:**

- Modify after evidence: `docs/07-权限与数据范围.md`
- Modify after evidence: `docs/领域/用户域.md`
- Create/update: `harness/reports/current/latest-rbac-phase2-shadow-runtime.md`

- [ ] **Step 1: Run full backend tests and package before container restart**

```powershell
Push-Location backend
mvn test
mvn -DskipTests package
Pop-Location
```

Expected: Maven exit 0. Count tests/failures/errors/skips from all Surefire XML files. Scheduled-task shutdown noise is not a failure unless Maven or XML reports a failure/error.

- [ ] **Step 2: Run code-review-graph change review**

Run `build_or_update_graph_tool` incrementally, then `detect_changes` and `get_impact_radius` for all changed Java/security/SQL files. Resolve every High/Critical finding or record it as a genuine blocker; do not downgrade it in prose.

- [ ] **Step 3: Run the fixed backend Harness entry in LEGACY mode**

Review every path returned by `git diff --name-only` and pass only current-task paths to `OwnedFiles`. Use stable report key `rbac-phase2-shadow-runtime`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope backend -ReportKey rbac-phase2-shadow-runtime -OwnedFiles 'backend/src/main/java/com/colonel/saas/config/AuthorizationRuntimeProperties.java;backend/src/main/java/com/colonel/saas/domain/user;backend/src/main/java/com/colonel/saas/security/JwtTokenProvider.java;backend/src/main/java/com/colonel/saas/security/JwtAuthenticationFilter.java;backend/src/main/java/com/colonel/saas/auth/service/AuthService.java;backend/src/main/java/com/colonel/saas/auth/service/SysMenuService.java;backend/src/main/java/com/colonel/saas/entity/SysUser.java;backend/src/main/java/com/colonel/saas/mapper/AuthorizationSnapshotMapper.java;backend/src/main/java/com/colonel/saas/mapper/AuthorizationVersionMapper.java;backend/src/main/java/com/colonel/saas/mapper/projection/AuthorizationVersionChangeRow.java;backend/src/main/java/com/colonel/saas/common/result/ResultCode.java;backend/src/main/java/com/colonel/saas/common/exception/GlobalExceptionHandler.java;backend/src/main/resources/application.yml;backend/src/main/resources/application-real-pre.yml;backend/src/main/resources/application-test.yml;backend/src/test/java/com/colonel/saas;docs/07-权限与数据范围.md;docs/领域/用户域.md;docs/superpowers/plans/2026-07-13-ddd-rbac-shadow-runtime.md;harness/reports/current/latest-rbac-phase2-shadow-runtime.md' -Message "feat(auth): add versioned RBAC shadow runtime"
```

The script rebuilds/restarts backend, performs health checks, generates stable evidence, commits, and pushes the feature branch. Do not add `-DeployRemote true`.

If the script rejects directory-valued `OwnedFiles`, expand the reviewed `git diff --name-only` list into semicolon-delimited files before execution; do not use `git add .` or include unrelated dirty files.

- [ ] **Step 4: Verify local LEGACY compatibility**

Required evidence:

- `/api/system/health` reports `UP`.
- An unauthenticated protected endpoint still returns HTTP 401 with the unified envelope.
- `AUTHORIZATION_RUNTIME_DEFAULT_MODE` is absent or resolves to `LEGACY`; record only the resolved mode, never environment values.
- Database post-migration query from Task 9 remains valid.
- Redis contains no unversioned `authz:snapshot:{userId}` keys; inspect key names only, never values.
- Existing login/business preflight is executed. If admin credentials still return HTTP 401, classify business E2E and old-token runtime proof as `BLOCKED_AUTH`, not PASS and not an RBAC code root cause without further evidence.

- [ ] **Step 5: Stop and obtain explicit local SHADOW activation authorization**

Only after LEGACY evidence is stable may the user authorize local `SHADOW`. Activation is an external configuration change; do not edit or commit `.env.real-pre`.

After the authorized operator sets `AUTHORIZATION_RUNTIME_DEFAULT_MODE=SHADOW`, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\restart-compose.ps1 -Env real-pre -Scope backend
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\verify-local.ps1 -Env real-pre -Scope backend
```

Then prove with a real login account:

1. A newly issued access token contains a positive version without printing the token.
2. The matching token reaches an authenticated protected endpoint.
3. An approved role/group/status mutation increments `sys_user.authz_version` in the same transaction.
4. The pre-change access token returns 401 after the mutation.
5. The pre-change refresh token returns 401 after the mutation.
6. A new login issues the incremented version and authenticates.
7. No business permission shadow diff is claimed until a reviewed permission matrix and an approved request consumer exist.

If no valid account/mutation window is available, keep SHADOW runtime verification `PENDING/BLOCKED`; do not create mock data in `real-pre` to manufacture proof.

- [ ] **Step 6: Update docs from evidence, not expectation**

Update `docs/07-权限与数据范围.md` and `docs/领域/用户域.md` with separate labels:

- `[已验证代码资产]` for tests/build.
- `[本地 migration 已验证]` only if Task 9 ran successfully.
- `[本地 LEGACY 运行已验证]` only after restart/health/API proof.
- `[本地 SHADOW PENDING/BLOCKED/PASS]` according to actual token/version evidence.
- `[远端 UNKNOWN / 未部署]` unless separately authorized and verified.
- `[业务矩阵待确认]` until the business owner approves permissions and role scopes.

- [ ] **Step 7: Validate Harness limits and evidence truthfulness**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\check-harness-limits.ps1 -BaselineRef HEAD
git diff --check
git status --short --branch
```

The stable evidence report must include time, environment, branch, commit, worktree status, build, Docker, health, business validation, migration status, runtime mode, remote deploy=false, conclusion, residual risks, and inline retro. `TASK_GATE` may be PASS while `REPOSITORY_HEALTH` remains PARTIAL from historical report-root debt; do not conflate them.

- [ ] **Step 8: Finish the branch through the required skill**

Use `superpowers:verification-before-completion`, then `superpowers:requesting-code-review`, and finally `superpowers:finishing-a-development-branch`. Do not merge or push the base branch unless the user selects that option explicitly.

