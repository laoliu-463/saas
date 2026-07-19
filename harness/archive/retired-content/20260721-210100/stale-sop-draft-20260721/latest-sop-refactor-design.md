# SOP 重构设计（agent-do dev/close + 定向测试三层选择）

> 状态：设计草稿（待拍板）
> 目标：把"修改代码、验证、提交 PR、远端部署"解耦成三个独立动作。

---

## 一、最终决策矩阵（基于历次拍板）

| 维度 | 决策 | 来源 |
|---|---|---|
| 入口唯一性 | `agent-do.ps1` 是唯一入口；`change.ps1` 不引入（如果存在则作 deprecation wrapper，最终删除） | 第三轮拍板 |
| Phase | `dev` / `close` 必填 | 第三轮拍板 |
| Scope | `auto / backend / frontend / full / docs / apifox` | 第三轮拍板 |
| Risk | `auto / R0 / R1 / R2 / R3`，自动按路径规则判定 | 第三轮拍板 |
| Publish | `git-push-safe` 必须显式 `-Publish`；dev 默认不推送 | 第三轮拍板 |
| Jenkins 路径 | 严格按 PR：先改 main → PR → 合 release/real-pre | 第二轮拍板 |
| Jenkins Backend Test | 改 `RUN_BACKEND_TEST=false` 默认 + `when` 条件，不删 stage | 第二轮拍板 |
| GHA SHA Gate | 不开新 stage，塞进 Preflight Guard 内（独立脚本 + Jenkins credentials） | 第二轮纠正后 |
| DeployRemote=true | 保留参数硬失败（不删除，文档统一写明） | 仓库现状 + 第三轮 |
| ContentMaintenance 默认 | `off`（从 `plan` 改） | 第三轮拍板 |
| dev 阶段定向测试算法 | 三层选择 + 配置表驱动 + Fail Closed | 第四轮拍板 |
| Safety-check 在 dev 阶段 | 跑轻量子集（敏感文件 / git add 检查 / owned-files），不读 .env.real-pre | 本轮锁定 |
| Risk 自动判定时机 | `-Phase` 一开始算一次，所有子调用共享 | 本轮锁定 |
| Surefire `-Dtest=` 排除 MapperTest | dev 阶段显式过滤 `**/mapper/*MapperTest*`；close 全量按默认 | 本轮锁定 |

---

## 二、`agent-do.ps1` 新参数模型

```powershell
param(
    [Parameter(Mandatory)]
    [ValidateSet("dev", "close")]
    [string]$Phase,

    [ValidateSet("auto", "backend", "frontend", "full", "docs", "apifox")]
    [string]$Scope = "auto",

    [ValidateSet("auto", "R0", "R1", "R2", "R3")]
    [string]$Risk = "auto",

    [string]$BaseRef = "origin/main",

    [switch]$Publish,

    [switch]$RunFullTests,

    [string]$BusinessCommand = "",

    [string]$ReportKey = "",

    [AllowEmptyCollection()][string[]]$OwnedFiles = @(),

    # 旧参数保留一个过渡周期，输出弃用警告
    [object]$DeployRemote = $false,
    [ValidateSet("off", "plan", "archive", "delete")]
    [string]$ContentMaintenance = "off",
    [switch]$SkipRemoteBackup,
    [switch]$SkipBusinessValidation,
    [switch]$DryRun
)
```

旧参数处理：
- `-DeployRemote true` → 抛错（与现状一致："Direct remote deployment is disabled"）
- `-ContentMaintenance plan|archive|delete` → 仅当用户显式传入时生效；默认 `off`
- 其他旧参数仍兼容，不报错；输出弃用警告到 stderr

---

## 三、Phase 行为矩阵

| 动作 | dev | close | 备注 |
|---|---|---|---|
| 变更范围分析（四 diff 合并去重） | ✓ | ✓ | 复用并扩展 `Get-HarnessChangedFiles` |
| Risk 自动判定 | ✓ | ✓ | 在 Phase 顶部算一次 |
| Safety-check 轻量子集 | ✓ | – | dev 专用：敏感文件 / git add 检查 |
| Safety-check 全量 | – | ✓ | 包含 env/compose/抖音凭证 |
| 定向测试（三层选择） | ✓ | – | 见 §四 |
| 全量测试（按 RunFullTests / Risk≥R2） | – | ✓ | Risk=R3 自动全量 |
| 后端 `mvn -DskipTests compile` | ✓ | ✓ | 始终执行 |
| 前端 `pnpm typecheck` | ✓（.ts/.vue 变化时） | ✓ | 始终执行 |
| clean package/build | – | ✓ | 仅 close |
| Docker 重建 | – | ✓ | 仅 close；按 scope 仅重建 backend-real-pre 或 frontend-real-pre |
| HTTP 健康检查 | – | ✓ | 仅 close |
| 业务验证 | 可选定向探针 | R2/R3 必须 | 未提供则记 PARTIAL |
| Content Maintenance | – | 仅显式开 | 默认 off |
| Evidence | – | 一次 | 写 `harness/reports/current/latest-<key>.md` |
| commit | – | ✓ | 条件：必须 owned-files 全部 staged |
| push | – | 仅 `-Publish` | close 默认不推送 |
| 远端部署 | ✗ | ✗ | 由 Jenkins 接管，永远不在 agent-do 内 |

---

## 四、dev 阶段定向测试三层选择算法

### 4.1 改动文件合并（四路）

```powershell
$changed = @()
$changed += & git -c core.quotepath=false diff --name-only "$BaseRef...HEAD"
$changed += & git -c core.quotepath=false diff --name-only
$changed += & git -c core.quotepath=false diff --cached --name-only
$changed += & git -c core.quotepath=false ls-files --others --exclude-standard
$changed = @($changed | Where-Object { $_ } | Sort-Object -Unique)
```

落盘：
```
runtime/qa/out/<run-id>/changed-files.txt
runtime/qa/out/<run-id>/test-plan.json
```

### 4.2 测试选择三层（后端）

**第一层：本次新增或修改的测试**

- 改动命中 `backend/src/test/**/*Test*.java` → 直接加入 mvn `-Dtest=` 列表
- `Surefire` 支持逗号分隔 + glob：`mvn -Dtest=OrderQueryServiceTest,OrderFilterTest test`

**第二层：生产类的直接测试**

- 改动命中 `backend/src/main/**/<X>.java` → 找下列候选：
  - 同包 `<X>Test.java`
  - 同包 `<X>Tests.java`
  - 同包 `<X>IntegrationTest.java`
  - 同包 `<X>ContractTest.java`
  - 全仓 `rg "<X>" backend/src/test` 命中的测试文件
- 全部加入 `-Dtest=` 列表

**第三层：领域最低保护集（按配置表追加）**

- 读 `harness/rules/test-impact-map.json` 中 `paths` 命中的所有 rule
- 合并其 `backendTests` 字段（去重）
- **追加在第一、二层之后**，不去重覆盖

### 4.3 测试选择三层（前端）

仓库实际是 `*.test.ts`（105 个），不是 `*.spec.ts`。配置表规则：

- 命中 `frontend/src/**/*.test.ts` 改动 → 第一层
- 命中 `frontend/src/**/<X>.vue` / `<X>.ts` → 第二层（同名 `.test.ts` + `rg "<X>" frontend/src`）
- 第三层同配置表

执行命令：
```bash
pnpm --dir frontend exec vitest run \
  <path1> <path2> ...
```
（vitest 3 支持仓库相对路径）

### 4.4 配置表 `harness/rules/test-impact-map.json`

```json
{
  "rules": [
    {
      "id": "order",
      "paths": [
        "backend/src/main/**/order/**",
        "backend/src/main/**/*Order*.java"
      ],
      "backendTests": [
        "*Order*Test",
        "*Attribution*Test"
      ],
      "frontendPaths": [
        "frontend/src/**/order/**",
        "frontend/src/**/*Order*.vue"
      ],
      "frontendTests": [
        "frontend/src/**/*Order*.test.ts",
        "frontend/src/**/order/**/*.test.ts"
      ],
      "risk": "R2"
    },
    {
      "id": "database-schema",
      "paths": [
        "backend/src/main/resources/db/**",
        "backend/src/main/**/mapper/**",
        "backend/src/main/**/*Mapper.java"
      ],
      "backendTests": [
        "RoleAwareAttributionSchemaContractTest",
        "RoleAwareAttributionFlywayIntegrationTest",
        "CoreEntityDatabaseSchemaContractTest",
        "ColonelsettlementOrderMapperSchemaIntegrationTest",
        "ColonelsettlementOrderMapperDualDimensionContractTest",
        "RealPreMigrationContractTest"
      ],
      "risk": "R3"
    },
    {
      "id": "performance",
      "paths": [
        "backend/src/main/**/performance/**",
        "backend/src/main/**/*Performance*.java",
        "backend/src/main/**/*Commission*.java"
      ],
      "backendTests": [
        "*Performance*Test",
        "*Commission*Test",
        "*Attribution*Test"
      ],
      "risk": "R2"
    },
    {
      "id": "product",
      "paths": [
        "backend/src/main/**/product/**",
        "backend/src/main/**/*Product*.java",
        "backend/src/main/**/*Activity*.java",
        "backend/src/main/**/*Promotion*.java",
        "backend/src/main/**/*Sync*.java"
      ],
      "backendTests": [
        "*Product*Test",
        "*Activity*Test",
        "*Promotion*Test",
        "*Sync*Test"
      ],
      "risk": "R2"
    },
    {
      "id": "sample-logistics",
      "paths": [
        "backend/src/main/**/sample/**",
        "backend/src/main/**/logistics/**",
        "backend/src/main/**/*Sample*.java",
        "backend/src/main/**/*Logistics*.java",
        "backend/src/main/**/*Kuaidi*.java"
      ],
      "backendTests": [
        "*Sample*Test",
        "*Logistics*Test",
        "*Kuaidi*Test"
      ],
      "risk": "R2"
    },
    {
      "id": "user-permission",
      "paths": [
        "backend/src/main/**/user/**",
        "backend/src/main/**/rbac/**",
        "backend/src/main/**/security/**",
        "backend/src/main/**/auth/**",
        "backend/src/main/**/*User*.java",
        "backend/src/main/**/*Role*.java",
        "backend/src/main/**/*Permission*.java",
        "backend/src/main/**/*DataScope*.java"
      ],
      "backendTests": [
        "*User*Test",
        "*Role*Test",
        "*Permission*Test",
        "*DataScope*Test"
      ],
      "risk": "R3"
    },
    {
      "id": "douyin-gateway",
      "paths": [
        "backend/src/main/**/douyin/**",
        "backend/src/main/**/*Douyin*.java",
        "backend/src/main/**/gateway/**",
        "backend/src/main/**/*Token*.java"
      ],
      "backendTests": [
        "*Douyin*Test",
        "*Gateway*Test",
        "*Token*Test"
      ],
      "risk": "R3"
    },
    {
      "id": "ddd-architecture",
      "paths": [
        "backend/src/main/**/architecture/**",
        "backend/src/test/**/architecture/**"
      ],
      "backendTests": [
        "*Architecture*Test",
        "*Ddd*Test",
        "*Guard*Test"
      ],
      "risk": "R3"
    },
    {
      "id": "shared-infrastructure",
      "paths": [
        "backend/pom.xml",
        "backend/src/main/**/exception/**",
        "backend/src/main/**/config/**",
        "backend/src/main/**/security/**/filter/**",
        "backend/src/main/**/datascope/**",
        "frontend/package.json",
        "frontend/pnpm-lock.yaml",
        "frontend/vite.config.*",
        "frontend/tsconfig*",
        "frontend/src/router/**",
        "frontend/src/permission/**",
        "frontend/src/utils/request.ts",
        "frontend/src/api/http.ts"
      ],
      "risk": "R3",
      "forceFullBackendTests": true,
      "forceFullFrontendTests": true
    }
  ]
}
```

### 4.5 共享基础设施升级规则

命中 `shared-infrastructure` 的 path → dev 阶段自动升级：
- 后端：`mvn -f backend/pom.xml test`（全量）
- 前端：`pnpm --dir frontend test && pnpm --dir frontend typecheck`

不静默跳过。

### 4.6 Surefire `-Dtest=` 的 MapperTest 过滤

dev 阶段构造 `-Dtest=` 列表后，必须显式 exclude：

```bash
mvn -f backend/pom.xml \
  "-Dtest=<sellected>" \
  "-Dsurefire.failIfNoSpecifiedTests=true" \
  "-Dtest.exclude.**/mapper/*MapperTest.java=<don't include>" \
  test
```

更安全的实现：在 PowerShell 端**过滤掉**所有路径匹配 `**/mapper/*MapperTest*.java` 的项，再传给 `-Dtest=`。这是因为 `-Dtest=` 会绕过 `<excludes>`。

### 4.7 Fail Closed 矩阵

| 情况 | 结果 |
|---|---|
| 改后端业务代码，但第一+二+三层选出 0 个测试 | FAIL（除非 `-RunFullTests`） |
| 改 R2/R3 前端业务逻辑，但 0 个 .test.ts 命中 | FAIL（除非 `-RunFullTests`） |
| 选出的测试类不存在 | FAIL（Surefire `failIfNoSpecifiedTests=true`） |
| 只改文案 / 样式，0 测试 | 允许，仅跑 typecheck |
| 改 Bug 但没有新增 / 更新回归测试 | FAIL（必须第一层有改动测试） |
| 命中 shared-infrastructure | 自动升级全量 |
| 测试类路径含 `**/mapper/*MapperTest*` | 显式过滤，不传给 `-Dtest=` |
| 配置表所有 rule 都 0 命中 | 视为 0 测试，按上面规则处理 |

---

## 五、Risk 自动判定

### 5.1 路径匹配规则（R3 升 R0 默认）

```text
R3 路径（精确）：
- backend/src/main/resources/db/**
- backend/src/main/**/mapper/**
- backend/src/main/**/*Mapper.java
- backend/src/main/**/rbac/**/**
- backend/src/main/**/security/**
- backend/src/main/**/user/**
- Jenkinsfile
- docker-compose*.yml
- .github/workflows/**
- backend/src/main/**/architecture/**

R2 路径（按 test-impact-map 中未标 R3 的 rule）：
- order/** / *Order*.java
- performance/** / *Performance*.java
- product/** / *Activity*.java
- sample/** / *Logistics*.java
- douyin/** / *Douyin*.java
- config-domain/** / *Config*.java

R1：frontend/src/**/*.{vue,ts}（非 backend 改动）
R0：仅 docs / harness / 报告
```

### 5.2 冲突解决

- 任一改动命中 R3 → Risk=R3
- 否则任一命中 R2 → Risk=R2
- 否则任一命中 R1 → Risk=R1
- 否则 R0
- **只能升不能降**：`Risk=auto` 算出 R3 后，用户传 `-Risk R2` → 抛错

---

## 六、Safety-check 分层

### 6.1 dev 阶段（轻量子集）

只跑：
- `Assert-HarnessNoSensitiveChangedFiles`（.env / .pem / .key / credentials）
- `git add .` / `git add -A` / `git add <dir>/` 禁用检查（看 staged 内容）
- Owned-files 一致性（如传了 `-OwnedFiles`，检查 staged ⊆ owned）

不跑：
- env 文件存在性 / 值校验
- 抖音凭证
- compose config
- 端口冲突

### 6.2 close 阶段（全量）

恢复现状全部检查。

---

## 七、改造顺序（按风险递增，最终修订版）

| PR | 内容 | 行为 | 合并条件 |
|---|---|---|---|
| #1 | impact map、risk-routing、配置契约测试 | **零行为** | Pester + Harness limits PASS 即可 |
| #2 | 变更分析、测试计划、定向测试脚本、Push Range 纯函数 | **现有入口零行为** | 手测 3 场景全过 |
| #3 | agent-do 接线、git-push-safe 接线、双层 Evidence、最小权威文档（AGENTS.md / harness/README.md / agent-contract.md） | 行为增量 | 手测 4 场景全过 |
| #4 | 全仓文档清理、旧调用消除、过渡规则完善 | 文档收口 | 全文搜无旧调用 |
| 后续 PR | GHA SHA Gate、Jenkins 可选 Backend Test、CI/CD Policy 同步 | 发布链优化 | 必须代码 + 策略同一 PR 生效 |

### 修订后的硬约束

1. **PR #1 不调用 Maven / Vitest**，仅静态校验配置。
2. **配置表允许规则重叠**：合并时 Risk 取最高、Test 集合并去重、Scope 合并、Flags OR；JSON 加 `priority` 字段仅用于互斥字段仲裁。
3. **Risk 比较用数字**：`$riskRank = @{ R0=0; R1=1; R2=2; R3=3 }`。
4. **找不到测试按组件分别判断**：后端改了 + 后端测试 0 → FAIL；前端 R2/R3 改了 + 前端测试 0 → FAIL。**不能合并成 `Backend.Count -eq 0 -and Frontend.Count -eq 0`**。
5. **PR #2 改 git-push-safe 时仅新增 `Assert-HarnessPushRange` 纯函数 + 独立契约测试，不接现有调用路径**；接线放到 PR #3。
6. **PR #3 持久 Evidence 事务顺序**：START_HEAD 记录 → 验证/测试/构建 → Commit A (业务) → 生成 Evidence 引用 Commit A → Commit B (持久 Evidence，仅 R2/R3) → 再次 fetch → 校验 START_HEAD..HEAD 只含本任务 Commit A/B → 一次 fast-forward push。R0/R1 只有 Commit A。
7. **PR #4 不能写 cicd-real-pre-policy 的未来状态**（不能写"Backend Test 默认跳过"）；GHA SHA Gate + Jenkins 可选测试必须后续独立 PR。
8. **PR #3 必须同步最小权威文档**：`AGENTS.md` / `harness/README.md` / `harness/rules/policies/agent-contract.md`，否则 Agent 会按旧 SOP 行事。

### Pester 调用规范（PR #1 起生效）

```powershell
$result = Invoke-Pester `
    -Script ./harness/scripts/tests/<file>.Tests.ps1 `
    -PassThru
if ($result.FailedCount -gt 0) {
    throw "<name> contract failed"
}
```

不再使用 `pwsh -File xxx.Tests.ps1` 直接执行。

---

## 八、最终拍板（历次确认）

### 8.1 dev 阶段"找不到测试"时怎么提示

**拍板**：输出结构化错误 `TEST_MAPPING_MISSING`，列出反查候选，按风险决定 WARN / FAIL：

```text
TEST_MAPPING_MISSING

Changed production files:
- backend/.../OrderQueryService.java

Matched rules:
- domain=order
- risk=R2

Expected test patterns:
- OrderQueryServiceTest
- *Order*Test
- *Attribution*Test

Candidate existing tests:
1. OrderApplicationServiceTest
2. OrderQueryFacadeTest
3. OrderAttributionServiceTest

Resolution:
- Add/update a regression test
- Update test-impact-map.json
- Or rerun with -RunFullTests
```

候选最多 20 个，排序：
```text
同名测试 → 引用该类的测试 → 同包测试 → 同领域测试 → 配置表反向匹配测试
```

处理规则：
- **R0 / R1** 纯文案、样式、资源 → 允许无测试，**WARN**。
- **R2 / R3** 业务代码 → 无测试直接 **FAIL**。
- `-RunFullTests` 可升级全量。
- 不提供 `-SkipTests` 绕过选项。

### 8.2 R3 前端全量

**拍板**：任务总体风险为 R3 时，无论主要修改在后端还是前端，dev 阶段执行：

```bash
pnpm --dir frontend test
pnpm --dir frontend typecheck
```

dev 不执行 `pnpm build`；构建留到 close 和 GHA。

理由：本项目 R3 通常涉及权限、角色、数据范围、API 字段或状态变化、订单归因、业绩、数据库迁移、环境/Compose/CI-CD——这些改动容易通过接口契约影响前端。R3 数量少，安全优先。

### 8.3 `-Publish` 提交范围门禁（不是文件门禁）

**拍板**：Git push 推的是**提交链**而非文件，必须校验 push range。

**核心规则**：
> `OwnedFiles` 控制本次提交内容；**Push Range Gate** 保证远端只新增本次任务创建并审核过的提交。

**改造后的顺序**：
```text
fetch upstream
→ 记录 START_HEAD 和 REMOTE_HEAD
→ 要求本地不存在历史未推送提交（即 START_HEAD == REMOTE_HEAD）
→ 只 stage OwnedFiles
→ 创建本次提交 TASK_COMMIT
→ 校验 START_HEAD..HEAD 只包含 TASK_COMMIT
→ 校验 TASK_COMMIT 文件全部属于 OwnedFiles
→ 普通 fast-forward push
```

**已有未推送提交时**：
```text
PUBLISH_RANGE_DIRTY:
Current branch already contains commits not created by this task.
Refusing to push them implicitly.
```

**新分支必须满足**：
- `START_HEAD` 可达于 `origin/main`
- 本次新增提交全部由当前 agent-do 记录

**禁止**：
- 自动 rebase
- force push
- 把历史未推送提交默认为本任务所有

### 8.4 Evidence 双层写入

**拍板**：拆成"临时运行摘要"和"持久 Evidence"。

#### 临时运行摘要（所有 close 都写，**不**进 Git）

```text
runtime/qa/out/<run-id>/change-summary.json
runtime/qa/out/<run-id>/test-plan.json
runtime/qa/out/<run-id>/changed-files.txt
```

R0、R1 也有本地证据，但不污染仓库历史。

#### 持久 Evidence

| 风险 | `harness/reports/current` |
|---|---|
| R0 普通文档、注释、测试说明 | 不写 |
| R1 文案、样式、孤立前端组件 | 不写，依赖 GHA 证据 |
| R2 普通业务功能 / Bug | **必须写** |
| R3 DB、权限、订单、业绩、外部写、CI/CD | **必须写** |
| 用户明确要求审计报告 | 强制写 |
| Harness 规则、ADR、部署策略 | 自动升级 R2/R3，必须写 |

> **注意**：修改 `AGENTS.md`、Jenkins、GHA、Harness 规则的"文档"不是 R0，因为它们会改变工程行为——按 R2/R3 处理。

#### Evidence 与 Commit 的顺序

R2/R3 持久报告需要引用真实代码提交 SHA，因此分两次提交：

```text
Commit A：OwnedFiles 业务修改
生成 Evidence，引用 Commit A
Commit B：Evidence 报告
校验两个提交均由本次任务生成
一次性 push Commit A + Commit B
```

R0/R1 没有持久报告：

```text
Commit A：OwnedFiles 修改
push Commit A
```

Push Range Gate 允许"本任务记录的一个或两个提交"，**绝不允许夹带任务开始前的未推送提交**。

#### 必须补的契约测试

- `TEST_MAPPING_MISSING` 候选输出和 R2/R3 失败
- R3 必跑前端 test + typecheck
- 分支已有未推送提交时拒绝 Publish
- R0/R1 只写临时摘要，R2/R3 写持久 Evidence

---

## 九、文件清单（实施时落地）

| 类型 | 路径 | 状态 |
|---|---|---|
| 新增 | `harness/rules/test-impact-map.json` | 待新建 |
| 新增 | `harness/rules/governance/risk-routing.md` | 待新建 |
| 新增 | `harness/scripts/commands/invoke-change-tests.ps1` | 待新建 |
| 新增 | `harness/scripts/tests/agent-do-phase.Tests.ps1` | 待新建 |
| 修改 | `harness/scripts/commands/_lib.ps1` | 扩展 Get-HarnessChangedFiles |
| 修改 | `harness/scripts/commands/agent-do.ps1` | 重构 |
| 修改 | `harness/scripts/commands/safety-check.ps1` | dev 模式参数 |
| 修改 | `harness/rules/environment/envs/remote-real-pre-env.md` | 删 DeployRemote=true |
| 修改 | `harness/rules/cicd-real-pre-policy.md` | Backend Test 描述更新 |
| 修改 | `harness/rules/governance/COMPLETION_GATES.md` | 加 Phase 维度 |
| 修改 | `harness/rules/policies/agent-contract.md` | 入口参数更新 |
| 修改 | `harness/rules/governance/task-routing.md` | Risk 规则说明 |
| 修改 | `CONTRIBUTING.md` | 新参数示例 |

不动：
- `Jenkinsfile`（PR 流程已锁；本次仅设计，不动 Jenkins）
- `.github/workflows/ci.yml`（GHA 现状已正确）
- `frontend/package.json`（pnpm 统一放到下一个独立 PR）

---

## 十、风险与回滚

- **最大风险**：dev 阶段三层选择算法可能误选测试，导致开发反馈漏问题。**缓解**：`harness/scripts/tests/agent-do-phase.Tests.ps1` 覆盖关键路径。
- **回滚**：因为旧参数保留一个过渡周期 + Phase 默认未强制（首版 PR 后才转 Mandatory），出问题可立刻回退到旧 agent-do 行为。
- **不影响**：Jenkins / GHA / 数据库迁移 / 远程部署——这些层完全独立。