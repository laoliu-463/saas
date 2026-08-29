# Spec — 后端单测 + 前端单测 + 构建（合并 commit）

**Task key**：`full-test-unit-and-build-20260829`
**类型**：测试 / 构建门禁
**Plan stop point**：2026-08-29
**Owner**：Codex DDD Migrator

---

## 1. Goal

按用户指示"对仓库测试所有内容"→ 澄清后范围 = **后端单测 + 前端单测 + 后端构建 + 前端构建**。不跑 E2E、不跑 real-pre 真实闭环。

**判定通过条件**（Do 阶段必须逐项采集到 evidence）：

1. `mvn -f backend/pom.xml test` 全绿，且 JaCoCo LINE 覆盖率 ≥ 0.80（满足 pom.xml rule）。
2. `npm --prefix frontend run test` 全绿。
3. `mvn -f backend/pom.xml -DskipTests package` 产物 `backend/target/colonel-saas.jar` 生成。
4. `npm --prefix frontend run build` 产物 `frontend/dist/` 生成。
5. evidence 报告生成到 `harness/reports/current/latest-full-test-unit-and-build-20260829.md`。
6. 工作区现有未提交 harness diff（changelog + 状态快照）+ evidence 报告一次性 commit & push。

---

## 2. Scope & Non-Goals

**In scope**

- 后端 Spring Boot 工程的 Maven 单元测试（Surefire 3.1.2）。
- 前端 Vitest 单元测试（Vitest 3.2.4 / happy-dom / @vue/test-utils）。
- 后端 production 构建（`-DskipTests package`）。
- 前端 production 构建（`vue-tsc -b && vite build`）。
- agent-do 标准入口的 evidence 收集与 git-push-safe 提交。

**Out of scope**

- E2E（Playwright / `e2e:v1-p0` / `e2e:real-pre:p0`）。
- real-pre 真实抖音 API 联调（AGENTS.md 第 6 节禁止项不触发）。
- 商品 backfill / dry-run（与本任务无关）。
- 远端 Jenkins 部署（`-DeployRemote` 已停用，不调用）。

---

## 3. Approach（执行入口与参数）

### 3.1 入口选择

按用户澄清，使用 AGENTS.md 标准入口 `harness/scripts/commands/agent-do.ps1`。

**关键参数决策**：

| 参数 | 值 | 理由 |
|---|---|---|
| `-Env` | `test` | AGENTS.md 默认 real-pre；纯单测场景不依赖真实抖音 API 与生产 schema，test/mock 更轻量；符合 AGENTS.md "test 仅在用户明确要求或专项测试需要时使用" |
| `-Scope` | `full` | 同时覆盖 backend 构建 + frontend 构建 |
| `-SkipBusinessValidation` | **不传**（默认 false） | 因为我们要用 BusinessCommand 跑单测，必须让 BusinessCommand 真正执行 |
| `-BusinessCommand` | `mvn -f backend/pom.xml test && npm --prefix frontend run test` | 替换默认的 `e2e:real-pre:p0:preflight`（跳过 E2E） |
| `-ReportKey` | `full-test-unit-and-build-20260829` | 稳定 key，evidence 落到 `harness/reports/current/latest-full-test-unit-and-build-20260829.md` |
| `-Message` | `test: full backend+frontend unit tests and build verification` | commit message |
| `-OwnedFiles` | 见 §3.2 | 工作区有未提交 diff，agent-do line 54-56 强制要求 OwnedFiles |
| `-RetroSummary` | 见 §3.3 | 默认无改动的 retro，按真实情况填写 |
| `-DryRun` | 不传 | 实际执行 |
| `-DeployRemote` | 不传（默认 false） | 不远端部署 |

### 3.2 OwnedFiles（合并 commit 必需）

当前工作区已修改但未提交（按 `git status`）：

- `harness/rules/changelog.md`
- `harness/rules/state/snapshots/01-当前项目状态.md`
- `harness/rules/state/snapshots/DOMAIN_STATUS.md`

agent-do 内部会追加新生成的 evidence 报告路径到 `commitOwnedFiles`，所以 OwnedFiles 只列已存在的改动：

```
harness/rules/changelog.md;harness/rules/state/snapshots/01-当前项目状态.md;harness/rules/state/snapshots/DOMAIN_STATUS.md
```

最终 commit 包含：
1. 上述 3 个已有改动。
2. 新生成的 `harness/reports/current/latest-full-test-unit-and-build-20260829.md`（由 `collect-evidence.ps1` 追加到 commitOwnedFiles）。

### 3.3 RetroSummary 草案

> "本次为后端单测 + 前端单测 + 构建门禁回归（test/mock 环境）；无 harness 行为变更；不产生独立 retro。覆盖率满足 pom.xml JaCoCo 规则即通过。"

执行时若发现 JaCoCo 阈值未通过、单测失败、构建失败等阻塞项，由 Do 阶段回写实际内容。

### 3.4 完整执行命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File harness/scripts/commands/agent-do.ps1 `
  -Env test `
  -Scope full `
  -BusinessCommand "mvn -f backend/pom.xml test && npm --prefix frontend run test" `
  -ReportKey "full-test-unit-and-build-20260829" `
  -OwnedFiles "harness/rules/changelog.md;harness/rules/state/snapshots/01-当前项目状态.md;harness/rules/state/snapshots/DOMAIN_STATUS.md" `
  -Message "test: full backend+frontend unit tests and build verification" `
  -RetroSummary "本次为后端单测 + 前端单测 + 构建门禁回归（test/mock 环境）；无 harness 行为变更；不产生独立 retro。"
```

---

## 4. 关键版本（来自仓库源码）

| 组件 | 版本 |
|---|---|
| Java | 17 |
| Spring Boot | 3.2.5 |
| MyBatis-Plus | 3.5.6 |
| Hutool | 5.8.26 |
| Knife4j | 4.5.0 |
| Lombok | 1.18.32 |
| Testcontainers | 1.19.8 |
| JJWT | 0.12.5 |
| Jsoup | 1.17.2 |
| Apache POI | 5.2.5 |
| Doudian Open-SDK | 1.1.0 |
| ArchUnit JUnit5 | 1.3.0 |
| Maven Surefire | 3.1.2 |
| JaCoCo | 0.8.11 |
| JaCoCo LINE 阈值 | 0.80（pom.xml `jacoco-maven-plugin` check rule） |
| Vue | ^3.5.32 |
| Vite | ^8.0.9 |
| TypeScript | ~6.0.2（仓库内写法；未与 npm 官方核对） |
| Vitest | ^3.2.4 |
| vue-tsc | ^3.2.7 |
| happy-dom | ^20.9.0 |
| Naive UI | ^2.44.1 |
| Pinia | ^3.0.4 |
| vue-router | ^5.0.4 |
| ECharts | ^6.0.0 |
| Axios | ^1.15.1 |

---

## 5. Acceptance Criteria（Do 阶段必须达成）

| # | 条件 | 证据来源 |
|---|---|---|
| AC-1 | backend `mvn test` exit code = 0 | agent-do stdout + evidence 报告 BusinessResult |
| AC-2 | backend JaCoCo LINE ≥ 0.80（覆盖 service / controller / auth / douyin / crawler / security / config / aspect / job） | pom.xml check 规则 + JaCoCo 报告 `backend/target/site/jacoco/index.html` |
| AC-3 | frontend `vitest run` exit code = 0 | evidence 报告 BusinessResult |
| AC-4 | backend `mvn package` 产物存在 `backend/target/colonel-saas.jar` | file check |
| AC-5 | frontend `npm run build` 产物存在 `frontend/dist/index.html` 与 `frontend/dist/assets/*` | file check |
| AC-6 | evidence 报告写入 `harness/reports/current/latest-full-test-unit-and-build-20260829.md` | collect-evidence.ps1 |
| AC-7 | harness 文件门禁 `check-harness-limits.ps1` 通过 | agent-do 末尾 harness governance |
| AC-8 | 工作区未提交 3 个 harness diff + 新 evidence 报告合并 commit | git log + git show |
| AC-9 | push 到当前分支 `codex/183-talent-claim-oom-guard-release` 上游 | git-push-safe.ps1 |

---

## 6. Risks / Known Issues

| 风险 | 缓解 |
|---|---|
| 后端 Testcontainers 需要 Docker daemon 运行 | Do 阶段先 `docker info` 探测；若 Docker 不可用，标记 BLOCKED 并停在 evidence 报告 |
| JaCoCo 阈值不达 0.80 | 当前 `ExclusiveTalentService` / `ExclusiveMerchantService` / `OrderDecryptService` / `MerchantService` / `SampleController` / `DataController` / `DouyinActivityTestController` / `ColonelsettlementActivityController` / `TalentController` / `CrawlerBase` / `DouyinTalentCrawler` / `CustomMetaObjectHandler` / `DataScopeAspect` / `SampleLifecycleJob` / `ExclusiveEvaluateJob` / `OrderSyncJob` 已 exclude；其他 service 需 ≥ 0.80 |
| agent-do `Scope=full` + `Env=test` 仍会触发 `restart-compose.ps1`（line 143-150） | 接受副作用：test/mock 环境重启轻量；如果用户不希望重启，回退 Plan 改为手动 + collect-evidence + git-push-safe |
| Compose restart 失败但单测已通过 | Do 阶段不阻断整体结论；evidence 报告 healthResult 标注 `compose restart FAIL`，结论调整为 `PARTIAL` |
| Vue / TS / Vitest 版本在 npm 官方尚未确认一致 | Step 3 跳过外部检索；执行失败时按仓库版本报错处理 |

---

## 7. Implementation Decisions

- **决策 ID**：DEC-001-20260829-full-test-unit-and-build
- **类型**：测试执行口径
- **决定**：在 test/mock 环境跑后端单测 + 前端单测 + 双端构建；通过 agent-do 标准入口；现有未提交 harness diff 与本次 evidence 合并单次 commit。
- **可逆性**：可逆（不影响代码，只影响 commit 粒度）。
- **记录位置**：本 spec 即 ADR；不另写 `docs/决策/ADR-011-*.md`，避免无新增业务事实的 ADR 膨胀。

---

## 8. Sources

| 链接 / 路径 | 类型 | 与项目版本一致性 |
|---|---|---|
| `backend/pom.xml` | 仓库源码 | — |
| `frontend/package.json` | 仓库源码 | — |
| `harness/scripts/commands/agent-do.ps1` | 仓库源码 | — |
| `AGENTS.md` | 仓库文档 | — |

Step 3 跳过外部检索：本任务全在仓内执行，未引入新外部 API / 框架说法。

---

## 9. 计划停点

本 spec 即阶段 1（Plan）的产出。停在 Plan 停点；等用户确认 Plan 并宣布「进入阶段 2」或「开始实现」后再进入 Do。

读取顺序（Do 阶段）：
1. `references/implementation-loop.md` 阶段 2（Do）
2. 按 AC-1 ~ AC-9 顺序执行 §3.4 命令
3. Do 末尾生成 retro 摘要并按 agent-do 完成 git-push
