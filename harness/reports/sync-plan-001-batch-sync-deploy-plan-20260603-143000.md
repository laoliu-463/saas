# SYNC-PLAN-001 本地未推送内容分批同步与部署计划

- 任务编号：SYNC-PLAN-001
- 任务名称：本地未推送内容分批同步到远端仓库并制定部署计划
- 时间：2026-06-03 14:30:00
- 环境：real-pre（本地）
- Completion Gate：Gate 0（Docs Only — 本任务只生成计划报告，不修改业务代码）

---

## 1. 任务概述

当前本地工作区存在大量 dirty / untracked 变更（共 122 个文件），来源于多个已完成但未提交/推送的任务。本任务对这些变更进行完整清点、分类，并制定分批提交与部署计划。

**本任务不提交业务代码，不执行数据库操作，不部署远端。**

---

## 2. Harness 读取情况

| 文件 | 状态 |
| --- | --- |
| `AGENTS.md` | 已读（通过 agents_instructions 注入） |
| `CLAUDE.md` | 已读 |
| `harness/AGENT_CONTRACT.md` | 已读 |
| `harness/TASK_ROUTING.md` | 已读 |
| `harness/FORBIDDEN_SCOPE.md` | 已读 |
| `harness/COMPLETION_GATES.md` | 已读 |
| `harness/CURRENT_STATE.md` | 已读 |
| `harness/state/DOMAIN_STATUS.md` | 已读 |
| `harness/state/KNOWN_ISSUES.md` | 已读 |
| `harness/state/DECISIONS.md` | 已读 |
| `harness/HARNESS_CHANGELOG.md` | 已读 |

以下文件本轮不重复读取（非本任务核心，且近期已读）：
- `harness/plans/DDD_OPTIMIZATION_ROADMAP.md`、`DDD_DOMAIN_TASK_MATRIX.md`
- `harness/instructions/user-domain.md`、`product-domain.md`
- `harness/skills/backend-domain-change.md`、`frontend-domain-change.md`、`real-pre-safe-operation.md`、`docker-restart-validate.md`、`post-task-gc.md`

---

## 3. 当前 Git 分支和 HEAD

- 分支：`feature/auth-system`
- HEAD commit：`dea06e4c` — `fix(product): enable five minute product sync and display dedup`
- HEAD 已推送至 gitee 和 origin

---

## 4. 当前远端 Remote 列表

| Remote | URL | 用途 |
| --- | --- | --- |
| `gitee` | `https://gitee.com/cao-jianing463/saas.git` | 远端服务器代码拉取源（主） |
| `origin` | `https://github.com/laoliu-463/saas.git` | GitHub 备份 |

远端服务器从 Gitee 拉代码，每个 batch 提交后必须 `git push gitee feature/auth-system`。

---

## 5. 当前 Dirty 总数

| 类型 | 数量 |
| --- | --- |
| Modified（已修改未暂存） | 22 |
| Deleted（已删除未暂存） | 55 |
| Untracked（未跟踪新文件） | 33 |
| **总计** | **110** |

注：`git diff --stat` 显示 79 files changed / 859 insertions / 4199 deletions（仅统计 modified + deleted，不含 untracked）。

---

## 6. 文件分类表

### Batch 1：harness-docs（19 文件）

Harness 规则、状态文件、新门禁系统、当日报告。不含 Java / Vue / SQL，不需要部署。

| # | 文件路径 | 状态 | 任务来源 | 建议提交 | 需要测试 | 需要部署 | 风险说明 |
|---|---|---|---|---|---|---|---|
| 1 | `harness/AGENT_CONTRACT.md` | modified | Completion Gate + Session Exit Gate | 是 | 否 | 否 | 混含多任务变更，但均为 Harness 规则演进，可整体提交 |
| 2 | `harness/CURRENT_STATE.md` | modified | 多任务状态累积 | 是 | 否 | 否 | 混含多任务状态，但为顺序追加，内容一致 |
| 3 | `harness/FORBIDDEN_SCOPE.md` | modified | Completion Gate + Session Exit Gate | 是 | 否 | 否 | 同上 |
| 4 | `harness/HARNESS_CHANGELOG.md` | modified | v0.4.1 ~ v0.4.9 变更日志 | 是 | 否 | 否 | 同上 |
| 5 | `harness/TASK_ROUTING.md` | modified | Completion Gate + Session Exit Gate | 是 | 否 | 否 | 同上 |
| 6 | `harness/state/DECISIONS.md` | modified | dept_type 决策追加 | 是 | 否 | 否 | 无 |
| 7 | `harness/state/DOMAIN_STATUS.md` | modified | 多任务领域状态更新 | 是 | 否 | 否 | 混含多任务，但为顺序追加 |
| 8 | `harness/state/KNOWN_ISSUES.md` | modified | 多任务问题登记 | 是 | 否 | 否 | 无 |
| 9 | `harness/COMPLETION_GATES.md` | untracked | Completion Gate 系统新增 | 是 | 否 | 否 | 新文件 |
| 10 | `harness/QUALITY_LEDGER.md` | untracked | Quality Ledger 新增 | 是 | 否 | 否 | 新文件 |
| 11 | `harness/SESSION_EXIT_GATE.md` | untracked | Session Exit Gate 新增 | 是 | 否 | 否 | 新文件 |
| 12 | `harness/reports/completion-gates-update-20260603-100557.md` | untracked | Completion Gate 报告 | 是 | 否 | 否 | 无 |
| 13 | `harness/reports/session-exit-gate-update-20260603-101403.md` | untracked | Session Exit Gate 报告 | 是 | 否 | 否 | 无 |
| 14 | `harness/reports/evidence-20260603-101503.md` | untracked | U-2.5-B evidence | 是 | 否 | 否 | 无 |
| 15 | `harness/reports/evidence-20260603-104232.md` | untracked | DDD 相关 evidence | 是 | 否 | 否 | 无 |
| 16 | `harness/reports/evidence-20260603-122021.md` | untracked | P-FIX-002 evidence | 是 | 否 | 否 | 无 |
| 17 | `harness/reports/retro-20260603-104247.md` | untracked | DDD 相关 retro | 是 | 否 | 否 | 无 |
| 18 | `harness/reports/retro-20260603-122513.md` | untracked | P-FIX-002D retro | 是 | 否 | 否 | 无 |
| 19 | `harness/reports/content-retire-20260603-103207.md` | untracked | 旧内容维护报告 | 是 | 否 | 否 | 无 |

### Batch 2：frontend-product-ui（5 文件）

商品卡片 hover UI（FUNC-001）+ 商品库分页弱化（P-FIX-001C）。不含 Java / SQL，需要 frontend build 验证，部署只重启 frontend。

| # | 文件路径 | 状态 | 任务来源 | 建议提交 | 需要测试 | 需要部署 | 风险说明 |
|---|---|---|---|---|---|---|---|
| 1 | `frontend/src/components/product/ProductSelectionCard.vue` | modified | FUNC-001 商品卡片 hover UI | 是 | 是（build） | 是（frontend） | +428 行大改动，已本地验证通过 |
| 2 | `frontend/src/components/product/ProductSelectionCard.test.ts` | modified | FUNC-001 测试 | 是 | 是（vitest） | 否 | 无 |
| 3 | `frontend/src/views/product/ProductLibrary.vue` | modified | P-FIX-001C 分页弱化 | 是 | 是（build） | 是（frontend） | 无 |
| 4 | `frontend/src/views/product/ProductLibrary.test.ts` | untracked | P-FIX-001C 测试 | 是 | 是（vitest） | 否 | 新文件 |
| 5 | `tests/e2e/03b-product-library-drawer-fields.spec.ts` | modified | FUNC-001 E2E 适配 | 是 | 可选（e2e） | 否 | E2E skip seed 适配 |

### Batch 3：backend-user-domain-u2_5-test1（15 文件）

U-2.5-B dept_type 最小修复 + TEST-1 后端测试修复。不含 Vue，需要全量后端测试通过，部署只更新 backend。

| # | 文件路径 | 状态 | 任务来源 | 建议提交 | 需要测试 | 需要部署 | 风险说明 |
|---|---|---|---|---|---|---|---|
| 1 | `backend/src/main/java/.../constant/DeptType.java` | modified | U-2.5-B dept_type 统一 | 是 | 是（mvn test） | 是（backend） | 核心常量变更 |
| 2 | `backend/src/main/java/.../constant/DeptTypes.java` | deleted | U-2.5-B 删除旧常量 | 是 | 是 | 是 | 确认无残留引用 |
| 3 | `backend/src/main/java/.../service/SysDeptService.java` | modified | U-2.5-B 迁移调用点 | 是 | 是 | 是 | 无 |
| 4 | `backend/src/main/resources/application-real-pre.yml` | modified | P-FIX-002A 同步配置 | 是 | 是 | 是 | 含 P-FIX-002A 配置，但已随 dea06e4c 推送，此处为本地残留 |
| 5 | `backend/src/main/resources/db/alter-sys-dept-uuid-canonical-20260530.sql` | modified | U-2.5-B seed 更新 | 是 | 否 | 是 | 仅 seed/init，非 migration |
| 6 | `backend/src/main/resources/db/alter-sys-dept.sql` | modified | U-2.5-B seed 更新 | 是 | 否 | 是 | 同上 |
| 7 | `backend/src/main/resources/db/init-db.sql` | modified | U-2.5-B seed 更新 | 是 | 否 | 是 | 同上 |
| 8 | `backend/src/main/resources/db/migrate-sys-dept-dept-type.sql` | modified | U-2.5-B 幂等脚本 | 是 | 否 | 是 | 幂等脚本，不自动执行 |
| 9 | `backend/src/test/.../constant/DeptTypeTest.java` | untracked | U-2.5-B 新测试 | 是 | 是 | 否 | 新文件 |
| 10 | `backend/src/test/.../SysUserServiceTest.java` | modified | TEST-1 修复 | 是 | 是 | 否 | 测试夹具修复 |
| 11 | `backend/src/test/.../CommissionRuleControllerTest.java` | modified | TEST-1 修复 | 是 | 是 | 否 | 同上 |
| 12 | `backend/src/test/.../SysDeptControllerTest.java` | modified | U-2.5-B + TEST-1 | 是 | 是 | 否 | 同上 |
| 13 | `backend/src/test/.../SysUserControllerTest.java` | modified | TEST-1 修复 | 是 | 是 | 否 | 同上 |
| 14 | `backend/src/test/.../CommissionRuleServiceTest.java` | modified | TEST-1 修复 | 是 | 是 | 否 | 同上 |
| 15 | `backend/src/test/.../SysDeptServiceTest.java` | modified | U-2.5-B 测试 | 是 | 是 | 否 | 同上 |

**重要说明**：`application-real-pre.yml` 含 P-FIX-002A 同步配置变更（`PRODUCT_ACTIVITY_SYNC_ENABLED` 等），该配置已通过 commit `dea06e4c` 推送到远端。本地残留是因为后续 U-2.5-B 和 TEST-1 任务未提交导致的 dirty 状态。需确认本地此文件内容与已推送版本是否一致；如一致可安全提交，如不一致需单独处理。

### Batch 4：p-fix-002d-remote-report（15 文件）

P-FIX-002D-REMOTE 部署验证报告 + P-FIX-002 系列相关报告 + TEST-1 报告 + FUNC-001 报告 + P-FIX-001C 报告 + U-2.5-B 报告。纯文档，不需要部署。

| # | 文件路径 | 状态 | 任务来源 | 建议提交 | 需要测试 | 需要部署 | 风险说明 |
|---|---|---|---|---|---|---|---|
| 1 | `harness/reports/p-fix-002d-remote-deploy-verify-20260603-132805.md` | untracked | P-FIX-002D-REMOTE | 是 | 否 | 否 | 远端部署验证主报告 |
| 2 | `harness/reports/user-domain-u2_5b-dept-type-minimal-fix-20260603-101503.md` | untracked | U-2.5-B | 是 | 否 | 否 | 无 |
| 3 | `harness/reports/test-1-full-backend-failures-fix-20260603-104601.md` | untracked | TEST-1 | 是 | 否 | 否 | 无 |
| 4 | `harness/reports/evidence-20260603-104601.md` | untracked | TEST-1 evidence | 是 | 否 | 否 | 无 |
| 5 | `harness/reports/retro-20260603-104601.md` | untracked | TEST-1 retro | 是 | 否 | 否 | 无 |
| 6 | `harness/reports/func-001-product-card-hover-ui-20260603-111451.md` | untracked | FUNC-001 | 是 | 否 | 否 | 无 |
| 7 | `harness/reports/evidence-20260603-111733.md` | untracked | FUNC-001 evidence | 是 | 否 | 否 | 无 |
| 8 | `harness/reports/retro-20260603-111824.md` | untracked | FUNC-001 retro | 是 | 否 | 否 | 无 |
| 9 | `harness/reports/p-fix-001c-product-library-pagination-20260603-112740.md` | untracked | P-FIX-001C 早期报告 | 是 | 否 | 否 | 无 |
| 10 | `harness/reports/p-fix-001c-product-library-pagination-20260603-113616.md` | untracked | P-FIX-001C 最终报告 | 是 | 否 | 否 | 无 |
| 11 | `harness/reports/evidence-20260603-113632.md` | untracked | P-FIX-001C evidence | 是 | 否 | 否 | 无 |
| 12 | `harness/reports/retro-20260603-113645.md` | untracked | P-FIX-001C retro | 是 | 否 | 否 | 无 |
| 13 | `harness/reports/content-retire-20260603-111343.md` | untracked | 旧内容维护 | 是 | 否 | 否 | 可并入 Batch 5 |
| 14 | `harness/reports/content-retire-20260603-113617.md` | untracked | 旧内容维护 | 是 | 否 | 否 | 可并入 Batch 5 |
| 15 | `harness/reports/user-domain-u2_5b-dept-type-minimal-fix-20260603-101503.md` | untracked | U-2.5-B 主报告 | 是 | 否 | 否 | 无 |

### Batch 5：cleanup-retire（78 文件）

历史报告归档删除 + 归档副本入库 + .gitignore。纯文档/清理，不需要部署。

| # | 文件路径 | 状态 | 任务来源 | 建议提交 | 需要测试 | 需要部署 | 风险说明 |
|---|---|---|---|---|---|---|---|
| 1 | `.gitignore` | modified | 来源不明 gitignore 变更 | **审查后决定** | 否 | 否 | 必须先 `git diff .gitignore` 审查变更内容 |
| 2-13 | `harness/reports/content-retire-20260602-*.md`（12 个） | deleted | 历史报告清理 | 是 | 否 | 否 | retire-content 执行结果 |
| 14-33 | `harness/reports/evidence-20260602-*.md`（20 个） | deleted | 历史报告清理 | 是 | 否 | 否 | 同上 |
| 34-48 | `harness/reports/retro-20260602-*.md`（15 个） | deleted | 历史报告清理 | 是 | 否 | 否 | 同上 |
| 49-50 | `harness/reports/retire-*-manifest-20260602-*.json`（2 个） | deleted | 历史 manifest 清理 | 是 | 否 | 否 | 同上 |
| 51 | `harness/archive/retired-content/20260603-reports-archive/`（整个目录） | untracked | 归档副本 | 是 | 否 | 否 | retire-content Archive 结果 |

### Batch X：unknown（0 文件）

当前所有文件均可明确归属，无 unknown 文件。

**但需注意**：`.gitignore` 变更内容需审查后才能确认归属。如果含有与业务相关的规则变更，可能需要移到对应批次。

---

## 7. 各批次提交与部署计划

### Batch 1：harness-docs

- **目标**：同步 Harness 规则演进、状态文件更新和新门禁系统到远端仓库
- **文件清单**：19 文件（见分类表）
- **提交前验证命令**：
  ```powershell
  git diff --cached --name-only
  git diff --cached --stat
  git diff --cached --check
  powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun
  ```
- **是否允许提交**：是，可立即提交
- **推荐 commit message**：`docs(harness): add completion gates, session exit gate, quality ledger and sync state updates`
- **推送 remote**：gitee + origin
- **是否需要部署**：否
- **部署方式**：N/A
- **部署后验证**：N/A
- **回滚方式**：`git revert <commit-hash>`
- **阻塞项**：无

### Batch 2：frontend-product-ui

- **目标**：同步商品卡片 hover UI 和商品库分页弱化到远端仓库并部署前端
- **文件清单**：5 文件（见分类表）
- **提交前验证命令**：
  ```powershell
  cd frontend && npm run build
  cd frontend && npx vitest run --reporter=verbose
  git diff --cached --name-only
  git diff --cached --stat
  git diff --cached --check
  ```
- **是否允许提交**：是，build 和测试通过后
- **推荐 commit message**：`feat(product-ui): product card hover expand and library load-more pagination`
- **推送 remote**：gitee + origin
- **是否需要部署**：是，仅 frontend
- **部署方式**：
  1. `git push gitee feature/auth-system`
  2. 远端 `git pull --ff-only gitee feature/auth-system`
  3. 远端 `docker compose -f docker-compose.real-pre.yml up -d --build frontend-real-pre`
  4. 或通过 `agent-do.ps1 -Scope frontend -DeployRemote true`
- **部署后验证**：
  1. `curl http://<remote>:3001/healthz`
  2. 打开商品库页面，验证 hover 展开和加载更多
  3. 生成部署验证报告
- **回滚方式**：`git revert <commit-hash>` + 远端重新 pull + 重启 frontend
- **阻塞项**：frontend build 必须通过

### Batch 3：backend-user-domain-u2_5-test1

- **目标**：同步 U-2.5-B dept_type 修复和 TEST-1 测试修复到远端仓库并部署后端
- **文件清单**：15 文件（见分类表）
- **提交前验证命令**：
  ```powershell
  mvn -f backend/pom.xml test
  git diff --cached --name-only
  git diff --cached --stat
  git diff --cached --check
  ```
- **是否允许提交**：是，全量 `mvn test` 通过后
- **推荐 commit message**：`fix(user-domain): unify dept_type constants and fix backend test fixtures`
- **推送 remote**：gitee + origin
- **是否需要部署**：是，仅 backend
- **部署方式**：
  1. `git push gitee feature/auth-system`
  2. 远端 `git pull --ff-only gitee feature/auth-system`
  3. 远端 Maven 构建 + `docker compose -f docker-compose.real-pre.yml up -d --build backend-real-pre`
  4. 或通过 `agent-do.ps1 -Scope backend -DeployRemote true`
- **部署后验证**：
  1. `curl http://<remote>:8081/api/system/health`
  2. 验证用户域 API（登录、部门列表、数据范围）
  3. 验证 `dept_type` 相关功能无回归
  4. 生成部署验证报告
- **回滚方式**：`git revert <commit-hash>` + 远端重新 pull + 重启 backend
- **阻塞项**：
  1. 全量 `mvn test` 必须 0 failures / 0 errors
  2. 需确认 `application-real-pre.yml` 本地变更与已推送版本是否一致

### Batch 4：p-fix-002d-remote-report

- **目标**：同步 P-FIX-002D-REMOTE 部署验证报告和其他任务报告到远端仓库
- **文件清单**：15 文件（见分类表）
- **提交前验证命令**：
  ```powershell
  git diff --cached --name-only
  git diff --cached --stat
  git diff --cached --check
  ```
- **是否允许提交**：是，可立即提交
- **推荐 commit message**：`docs(reports): add P-FIX-002D-REMOTE, TEST-1, FUNC-001, P-FIX-001C task reports`
- **推送 remote**：gitee + origin
- **是否需要部署**：否
- **部署方式**：N/A
- **部署后验证**：N/A
- **回滚方式**：`git revert <commit-hash>`
- **阻塞项**：无

### Batch 5：cleanup-retire

- **目标**：同步历史报告归档删除和 .gitignore 变更
- **文件清单**：78 文件（见分类表）
- **提交前验证命令**：
  ```powershell
  git diff .gitignore
  git diff --cached --name-only
  git diff --cached --stat
  git diff --cached --check
  ```
- **是否允许提交**：是，但 `.gitignore` 变更必须先审查
- **推荐 commit message**：`chore: archive old reports and clean up retired content`
- **推送 remote**：gitee + origin
- **是否需要部署**：否
- **部署方式**：N/A
- **部署后验证**：N/A
- **回滚方式**：`git revert <commit-hash>`
- **阻塞项**：`.gitignore` 变更内容需审查

---

## 8. 推荐执行顺序

| 顺序 | Batch | 理由 | 预估文件数 | 是否需部署 |
|---|---|---|---|---|
| 1 | **Batch 4**：任务报告 | 纯文档，零风险，先收口报告 | 15 | 否 |
| 2 | **Batch 1**：Harness / docs | 纯文档，收口 Harness 状态 | 19 | 否 |
| 3 | **Batch 5**：cleanup / retire | 清理归档，减少后续干扰 | 78 | 否 |
| 4 | **Batch 2**：前端商品库 UI | build + 测试后提交并部署 frontend | 5 | 是（frontend） |
| 5 | **Batch 3**：后端用户域 + 测试 | 全量测试后提交并部署 backend | 15 | 是（backend） |
| 6 | **Batch X**：unknown | 当前无文件 | 0 | 否 |

**调整说明**：将 Batch 4 和 Batch 5 提前到 Batch 1 之前，原因是这些都是纯文档/清理操作，零业务风险，先处理可以减少后续批次的文件干扰。Batch 4（报告）和 Batch 1（Harness 状态）之间不存在依赖，可以合并为一个 docs commit，但按任务要求分开。

**备选方案**：如果 Batch 1 中的 `CURRENT_STATE.md` / `DOMAIN_STATUS.md` 等状态文件混入太多不同任务的变更导致审查困难，可以考虑只提交报告和新增文件（COMPLETION_GATES.md 等），状态文件留到对应业务批次一起提交。但从当前审查来看，这些状态文件均为顺序追加，内容清晰，可整体提交。

---

## 9. 远端部署策略

### 通用规则

1. 每个 runtime 批次（Batch 2、Batch 3）提交后必须先 `git push gitee feature/auth-system`。
2. 远端服务器从 Gitee 拉取：`git pull --ff-only gitee feature/auth-system`。
3. 远端必须确认 commit hash 与本地一致。
4. 禁止直接复制本地 dirty 文件到远端。
5. Batch 2 只部署 frontend；Batch 3 只部署 backend。
6. 禁止 `docker compose down -v`。
7. 部署后必须 health check + 相关页面/API smoke。
8. 必须生成部署验证报告到 `harness/reports/`。

### Batch 2 部署流程

```powershell
# 本地
git add frontend/src/components/product/ProductSelectionCard.vue \
        frontend/src/components/product/ProductSelectionCard.test.ts \
        frontend/src/views/product/ProductLibrary.vue \
        frontend/src/views/product/ProductLibrary.test.ts \
        tests/e2e/03b-product-library-drawer-fields.spec.ts
git diff --cached --name-only
git diff --cached --stat
git diff --cached --check
git commit -m "feat(product-ui): product card hover expand and library load-more pagination"
git push gitee feature/auth-system
git push origin feature/auth-system

# 远端（通过 deploy-remote.ps1 或手动）
# 1. git pull --ff-only gitee feature/auth-system
# 2. docker compose -f docker-compose.real-pre.yml up -d --build frontend-real-pre
# 3. health check + 商品库页面 smoke
```

### Batch 3 部署流程

```powershell
# 本地
git add backend/src/main/java/com/colonel/saas/constant/DeptType.java \
        backend/src/main/java/com/colonel/saas/constant/DeptTypes.java \
        backend/src/main/java/com/colonel/saas/service/SysDeptService.java \
        backend/src/main/resources/application-real-pre.yml \
        backend/src/main/resources/db/alter-sys-dept-uuid-canonical-20260530.sql \
        backend/src/main/resources/db/alter-sys-dept.sql \
        backend/src/main/resources/db/init-db.sql \
        backend/src/main/resources/db/migrate-sys-dept-dept-type.sql \
        backend/src/test/java/com/colonel/saas/constant/ \
        backend/src/test/java/com/colonel/saas/auth/service/SysUserServiceTest.java \
        backend/src/test/java/com/colonel/saas/controller/CommissionRuleControllerTest.java \
        backend/src/test/java/com/colonel/saas/controller/SysDeptControllerTest.java \
        backend/src/test/java/com/colonel/saas/controller/SysUserControllerTest.java \
        backend/src/test/java/com/colonel/saas/service/CommissionRuleServiceTest.java \
        backend/src/test/java/com/colonel/saas/service/SysDeptServiceTest.java
git diff --cached --name-only
git diff --cached --stat
git diff --cached --check
# 必须先通过: mvn -f backend/pom.xml test
git commit -m "fix(user-domain): unify dept_type constants and fix backend test fixtures"
git push gitee feature/auth-system
git push origin feature/auth-system

# 远端（通过 deploy-remote.ps1 或手动）
# 1. git pull --ff-only gitee feature/auth-system
# 2. Maven build: mvn -f backend/pom.xml -DskipTests package
# 3. docker compose -f docker-compose.real-pre.yml up -d --build backend-real-pre
# 4. health check + 用户域 API smoke
```

---

## 10. 暂不允许提交的文件

当前所有文件均可归属到具体批次，不存在 Batch X 文件。

**但以下文件需要额外审查后才能确定归属**：

| 文件 | 原因 |
|---|---|
| `.gitignore` | 变更来源不明，必须 `git diff .gitignore` 审查后再决定归属 Batch 5 或单独处理 |
| `application-real-pre.yml` | 需确认本地残留变更与已推送 commit `dea06e4c` 中的版本是否一致 |

---

## 11. Verification

### 安全检查

将在报告生成后执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\safety-check.ps1 -Env real-pre -Scope docs -DryRun
```

### 本地验证（如可用）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\verify-local.ps1 -Env real-pre -Scope docs
```

---

## 12. 总结

| 指标 | 值 |
|---|---|
| 当前 dirty 文件总数 | 110 |
| 分成的批次数 | 5（+ Batch X 当前为空） |
| Batch 1 文件数 | 19 |
| Batch 2 文件数 | 5 |
| Batch 3 文件数 | 15 |
| Batch 4 文件数 | 15 |
| Batch 5 文件数 | 78（含 55 deleted + 1 modified .gitignore + 归档目录） |
| Batch X 文件数 | 0 |
| 需要部署的批次 | Batch 2（frontend）、Batch 3（backend） |
| 不需要部署的批次 | Batch 1、Batch 4、Batch 5 |
| 修改业务代码 | **否** |
| 执行数据库操作 | **否** |
| 执行远端部署 | **否** |
| 推荐执行顺序 | Batch 4 → Batch 1 → Batch 5 → Batch 2 → Batch 3 |
| 下一步建议 | 先执行 Batch 4（P-FIX-002D-REMOTE 等任务报告），零风险收口 |
