# AGENTS.md — 抖音团长 SaaS Harness 强制执行协议

## 1. 项目定位

本仓库是抖音团长内部 SaaS V2 工程，服务商品管理、达人 CRM、寄样、订单归因、业绩统计和看板分析。

当前实际技术栈以源码和部署脚本为准：

- 后端：Spring Boot / Java 17
- 前端：Vue 3 / Vite / Pinia / Naive UI / TypeScript
- 数据库：PostgreSQL
- 缓存：Redis
- 部署：Docker Compose
- 环境：`test`、`real-pre`、远端 `real-pre`

默认工程修改环境为本地 `real-pre`。`test` 仅在用户明确要求或专项测试需要时使用；远端 `real-pre` 只能由 Jenkins 从 `release/real-pre` 串行部署，普通 Agent 不拥有直接部署权限。

旧文档中的 FastAPI、Celery、Python 爬虫、旧 V2.2 全量方案只作为历史背景，不作为当前运行事实。

V2 核心闭环以三条主链为准：

- 渠道链：认领达人 -> 商品库选品 -> 复制讲解 / 转链 -> 寄样申请 -> 订单同步 -> 渠道业绩 -> 寄样自动完成。
- 招商链：同步活动 -> 活动商品入库 -> 商品上架 -> 审核寄样 -> 订单同步 -> 招商业绩。
- 管理链：用户角色 -> 数据范围 -> 规则配置 -> 各领域读取配置 -> 权限生效。

详细状态与执行入口见 `harness/INDEX.md`，领域职责见 `docs/领域/*.md`。

## 2. 最高优先级规则

后续 AI Agent 执行任务必须遵守：

1. 修改代码后必须构建。
2. 修改代码后必须重启对应 Docker 容器。
3. 修改代码后必须执行健康检查。
4. 修改代码后必须执行相关业务验证。
5. 修改代码后必须生成 evidence report。
6. 需要远端部署时必须进入 Jenkins 唯一发布队列，禁止 Agent 直接 SSH 部署。
7. 禁止只修改代码后声明完成。
8. 禁止口头说“建议重启”，必须实际执行可用脚本。
9. 禁止把未验证项写成已完成。
10. 禁止把 `BLOCKED`、`PENDING`、`PARTIAL` 写成 `PASS`。
11. 每次任务的 evidence 必须包含 retro 结论；只有存在可执行改进时才生成独立 retro。

文档 / Harness 变更可使用 `Scope=docs`，此时跳过构建、重启和健康检查，但仍必须执行安全检查、生成 evidence report，并说明未执行项。

## 3. 必读顺序

任务开始必须先读：

1. `CLAUDE.md`
2. `docs/README.md`
3. `harness/README.md` 与 `harness/INDEX.md`
4. `harness/rules/` 下的核心约束与规范
5. 当前任务对应的领域、流程、接口、数据、权限、验收和部署文档

涉及 real-pre 必须补读：

- `docs/08-第三方对接总览.md`
- `docs/10-部署运行总览.md`
- `docs/验收/real-pre联调手册.md`

涉及订单归因、寄样、商品库、业绩或看板，必须检索并读取相关领域文档及 `harness/rules/` 中的对应内容。

## 4. code-review-graph 先行

本项目已有 code-review-graph 知识图谱。探索代码前必须先使用 code-review-graph MCP：

- 了解变更影响：`detect_changes`
- 查找函数 / 类 / 文件：`semantic_search_nodes`
- 查依赖关系：`query_graph`
- 查影响半径：`get_impact_radius`
- 做代码审查：`get_review_context`

只有图谱不能覆盖或结果不足时，才回退到 `rg`、文件读取和手工追踪。

## 5. 唯一执行入口

默认执行入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -ReportKey task-key -OwnedFiles 'path1;path2' -Message "说明本次修改"
```

文档 / Harness 变更：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope docs -ReportKey task-key -OwnedFiles 'path1;path2' -Message "docs: update harness"
```

其他任务的 Scope 与必读文档见 `harness/INDEX.md`，后续 Agent 不允许临时发明构建、重启、部署流程。若确需绕过，必须说明原因和风险。

`agent-do.ps1 -DeployRemote true` 已停用并会失败。发布候选必须先合并到 `main`，再通过 PR 串行提升到 `release/real-pre`，最后由 Jenkins `saas-real-pre-cd` 队列执行。

## 6. 禁止事项

### real-pre 禁止

- 禁止清库。
- 禁止 `docker compose down -v`。
- 禁止删除 PostgreSQL / Redis volume。
- 禁止把 real-pre 改成 test / mock。
- 禁止开启 `APP_TEST_ENABLED=true`。
- 禁止开启 `DOUYIN_TEST_ENABLED=true`。
- 禁止把 `DOUYIN_REAL_UPSTREAM_MODE` 改为非 `live`。
- 禁止关闭真实抖音 API 开关后仍声明真实闭环通过。
- 禁止用 mock 数据证明 real-pre 业务闭环。


### 模块边界禁止

- 前端不得直接调用抖音 / 抖店开放接口。
- 前端不得硬编码核心业务规则、权限规则或状态机。
- 订单域不得计算提成或最终归属。
- 配置域不得执行具体业务规则。
- 分析模块不得重算业绩归属。
- SDK / Gateway 不得泄漏业务语义到第三方接口适配层。

### Git 与密钥禁止

- 禁止提交 `.env`、`.env.real-pre`、`.env.test`。
- 禁止提交 `*.pem`、`*.key`、凭证、私钥、证书。
- 禁止输出或提交密钥、Token、密码、OAuth code。

### 商品 backfill / dry-run 禁止

- 禁止用同步 HTTP 长请求（`POST /api/product-sync/admin/backfill-activity-products`
  直接 `Invoke-WebRequest`）跑 `RECENT_30D maxActivities>=10` 任务。
  任务绑死在 HTTP 连接上，客户端超时或断连 → `ClientAbortException` →
  `product_sync_job_log` 标 `ABANDONED`（但实际任务仍在跑），
  出现"ABANDONED 状态但实际 SUCCESS 落库"的不一致（成功时间晚 14-26 分钟）。
- 商品 backfill / dry-run 必须走异步 job 模式：
  1. `POST /api/product-sync/admin/backfill-activity-products/async`
     立即返回 `jobId`（不阻塞 HTTP）。
  2. 后端 `TaskExecutor` / MQ 异步执行原 backfill service。
  3. `GET /api/product-sync/admin/backfill-jobs/{jobId}` 查进度。
  4. job_log 每个 activity / page / batch flush 一次进度。
  5. 保留现有 lock owner / deadlock retry / stale reconcile / dryRun 边界。
  6. `confirm=true` 才走真实写库；`dryRun=true` 永不写业务表。
- 轻量探针（只读）用 `POST /api/product-sync-probes/full-products-dry-run`，
  参数 `scope=RECENT_30D activityIds=[] pageSize=20 maxActivities=20
  maxPagesPerActivity=1000 maxRowsPerActivity=50000 dryRun=true`。
  即使是探针，单次同步请求仍不应超过 5-10 分钟；超过则必须分批或异步。
- 杀 RUNNING 之前必须先 `SELECT job_id, dry_run FROM product_sync_job_log
  WHERE status='RUNNING'` 确认是 dry-run（`dry_run=t`），防止误杀实跑。
- 杀完后必须 `redis-cli KEYS '*backfill*'` 确认业务锁释放。

## 7. Definition of Done

任务同时满足以下全部条件，才允许声明完成：代码或文档已修改；构建通过（或 `Scope=docs` 明确跳过）；对应 Docker 容器已重启（或 `Scope=docs` 明确不需要）；健康检查通过（或明确说明阻塞原因）；相关业务验证通过（或明确说明 `BLOCKED` / `PENDING` / `FAIL` 证据）；稳定 evidence 已生成并含 retro 结论；Git commit 已生成并 push 到当前分支上游（或用户明确要求本轮不提交 / 不推送）；如要求发布则 Jenkins 队列结果与远端版本证据已记录；剩余风险已列出。

没有完成这些步骤，不允许声明任务完成。

## 8. 证据报告

证据报告统一生成到：

```text
harness/reports/current/latest-<report-key>.md
```

`ReportKey` 必须稳定且不含路径；`OwnedFiles` 必须逐项列出当前任务拥有的仓库相对路径，多个路径用分号分隔。报告覆盖同主题当前摘要，不再在 `reports/` 根生成时间戳副本。Retro 默认内联 evidence；只有存在责任人、改进动作和验证方式时才单独生成。

报告必须包含（脚本能采集多少写多少，采集不到必须写"未采集 / 阻塞原因"，不得编造）：

时间、环境、分支、commit hash、工作区是否干净、构建结果、Docker 状态、健康检查结果、业务验证结果、是否部署远端、远端健康检查结果、结论（`PASS` / `PARTIAL` / `FAIL`）、剩余风险。

## 9. 当前文档关系

- `CLAUDE.md`：仓库地图。
- `docs/`：事实主源、领域合同、流程、接口、数据、验收、部署和 ADR。
- `.claude/`：保留为 Claude 工作台和历史 Agent 工作流文档。
- `harness/`：统一执行系统与工程化基座，负责门禁规则、任务卡、报告、自动化脚本和资产清理清单。

## 10. Harness 分层文件门禁

所有 Agent 修改 `harness/` 时必须遵守 ADR-013 的分层、基线感知门禁：

1. **结构限制**：依据 ADR-014 对 ADR-013 的扩展，`harness/` 当前白名单为 13 个一级目录：`rules/`、`tasks/`、`probes/`、`reports/`、`scripts/`、`manifests/`、`archive/`、`templates/`、`engineering/`、`src/`、`contracts/`、`state/`、`tests/`。新增目录仅在有真实职责和内容时创建；`state/` 按需创建且只存稳定基线，运行时产物禁止进入。
2. **活跃知识预算**：直接文件和子目录 40 预警、50 硬上限；非脚本文本 160 行预警、200 行硬上限。脚本由测试、语法检查和职责边界约束。
3. **报告预算**：`reports/` 根目标不超过 20 个直接文件；当前摘要写入 `reports/current/latest-<topic>.md`，原始输出写入 `runtime/qa/out/<run-id>/`。
4. **基线语义**：本地以 `HEAD` 为基线。历史超限保持或减少不阻断当前任务，新增或恶化必须失败；报告同时区分 `TASK_GATE` 和 `REPOSITORY_HEALTH`。
5. **归档职责**：归档或删除必须有 manifest；archive 分桶继续遵守 50/50。历史不可变证据原样迁移可豁免行数追溯，新摘要仍不得超过 200 行。
6. **合规自检**：声明完成前执行 `powershell -ExecutionPolicy Bypass -File harness/scripts/check-harness-limits.ps1 -BaselineRef HEAD`。

如果发现旧文档与当前事实冲突，写入 `docs/决策/ADR-010-仓库阶段口径拍板为V2.md`（阶段口径）或 `docs/决策/ADR-002-V1范围优先级.md`（范围标记），不要自行拍板。

## 11. Agent skills

本仓库接入了 Matt Pocock 18 项 KEEP skills（执行方法，非业务总指挥）。项目级规则优先级：用户当前直接要求 > 本协议 > 当前阶段相关 `docs/*.md` > `CONTEXT.md` > skill 默认流程。

> **变更说明（2026-06-19）**：原 `docs/agents/` 已**合并重构到 `harness/engineering/`**。本节内容相应更新，指向 harness 工程配置目录。

- **Issue tracker**：GitHub Issues（`origin` = `https://github.com/laoliu-463/saas.git`）；`gitee` 为只读镜像，外部 PR 不作为 triage 源；harness 端通过 `harness/engineering/issues-index.md` 维护镜像。详见 `harness/engineering/issue-tracker.md`。
- **Triage labels**：五项 canonical 标签（`needs-triage` / `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`）按默认同名映射（GitHub Labels 已建立）。详见 `harness/engineering/triage-labels.md`。
- **Domain docs**：Single-context，主入口 `AGENTS.md` + `CONTEXT.md` + `docs/README.md`；ADR 收口在 `docs/决策/`（已有 ADR-001~010），`docs/adr/` 不启用；harness 工程 Skill 配置全部在 `harness/engineering/`。详见 `harness/engineering/context.md`。

**harness engineering 目录结构**：

```text
harness/engineering/
├── issue-tracker.md     ← Issue tracker 配置
├── triage-labels.md     ← Triage 标签映射
├── context.md           ← 上下文文档消费规则
└── issues-index.md      ← GitHub Issues 本地镜像
```

**历史位置**：原 `docs/agents/{issue-tracker,triage-labels,domain}.md` 已合并重构。后续如再启用新 skill，请在 `harness/engineering/` 下添加对应配置。
