# AGENTS.md — 抖音团长 SaaS Harness 强制执行协议

## 1. 项目定位

本仓库是抖音团长内部 SaaS V1 工程，服务商品管理、达人 CRM、寄样、订单归因、业绩统计和看板分析。

当前实际技术栈以源码和部署脚本为准：

- 后端：Spring Boot / Java 17
- 前端：Vue 3 / Vite / Pinia / Naive UI / TypeScript
- 数据库：PostgreSQL
- 缓存：Redis
- 部署：Docker Compose
- 环境：`test`、`real-pre`、远端 `real-pre`

默认工程修改环境为本地 `real-pre`。`test` 仅在用户明确要求或专项测试需要时使用；远端 `real-pre` 部署仍必须由用户明确要求后才允许执行。

旧文档中的 FastAPI、Celery、Python 爬虫、旧 V2.2 全量方案只作为历史背景，不作为当前运行事实。

V1 核心闭环以三条主链为准：

- 渠道链：认领达人 -> 商品库选品 -> 复制讲解 / 转链 -> 寄样申请 -> 订单同步 -> 渠道业绩 -> 寄样自动完成。
- 招商链：同步活动 -> 活动商品入库 -> 商品上架 -> 审核寄样 -> 订单同步 -> 招商业绩。
- 管理链：用户角色 -> 数据范围 -> 规则配置 -> 各领域读取配置 -> 权限生效。

详细状态见 `harness/CURRENT_STATE.md`，领域职责见 `harness/DOMAIN_MAP.md` 和 `docs/领域/*.md`。

## 2. 最高优先级规则

后续 AI Agent 执行任务必须遵守：

1. 修改代码后必须构建。
2. 修改代码后必须重启对应 Docker 容器。
3. 修改代码后必须执行健康检查。
4. 修改代码后必须执行相关业务验证。
5. 修改代码后必须生成 evidence report。
6. 需要远端部署时必须执行固定部署脚本。
7. 禁止只修改代码后声明完成。
8. 禁止口头说“建议重启”，必须实际执行可用脚本。
9. 禁止把未验证项写成已完成。
10. 禁止把 `BLOCKED`、`PENDING`、`PARTIAL` 写成 `PASS`。
11. 每次任务后必须生成 retro summary，或明确说明本次无需 Harness 升级。

文档 / Harness 变更可使用 `Scope=docs`，此时跳过构建、重启和健康检查，但仍必须执行安全检查、生成 evidence report，并说明未执行项。

## 3. 必读顺序

任务开始必须先读：

1. `CLAUDE.md`
2. `docs/README.md`
3. `harness/CURRENT_STATE.md`
4. `harness/TASK_ROUTING.md`
5. 当前任务对应的领域、流程、接口、数据、权限、验收和部署文档

涉及 real-pre 必须补读：

- `docs/08-第三方对接总览.md`
- `docs/10-部署运行总览.md`
- `docs/验收/real-pre联调手册.md`
- `harness/skills/real-pre-debug.skill.md`

涉及订单归因、寄样、商品库、业绩或看板，必须读取 `harness/skills/` 下对应 skill。

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
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1
```

常用示例：

```powershell
# 文档 / Harness 变更
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope docs -Message "docs: update harness"

# 后端变更
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope backend -Message "fix: update backend"

# 前端变更
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope frontend -Message "fix: update frontend"

# real-pre 全链路
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope full -Message "fix: update real-pre flow"

# 用户明确要求远端部署时
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env real-pre -Scope full -DeployRemote true -Message "deploy: real-pre update"
```

后续 Agent 不允许临时发明构建、重启、部署流程。若确需绕过，必须说明原因和风险。

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

### V1 禁止

- 不做独家达人。
- 不做独家商家。
- 不做毛利口径扩展。
- 不做个别品负责人覆盖。
- 不做商品负责人变更历史重算。
- 不做差异化提成。
- 不做 Cookie 池 / 代理池。
- 不做物流 API 自动跟踪作为 V1 必需能力。
- 不做外部抖店快速寄样作为 V1 必需能力。
- 不做数据平台整体导出。

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

完整禁止清单见 `harness/FORBIDDEN_SCOPE.md`。

## 7. Definition of Done

一个任务只有同时满足以下条件，才允许声明完成：

- 代码或文档已修改。
- 构建通过，或 `Scope=docs` 明确跳过构建。
- 对应 Docker 容器已重启，或 `Scope=docs` 明确不需要重启。
- 健康检查通过，或明确说明阻塞原因。
- 相关业务验证通过，或明确说明 `BLOCKED` / `PENDING` / `FAIL` 证据。
- evidence report 已生成。
- retro summary 已生成，或明确说明本次无需 Harness 升级。
- Git commit 已生成并 push 到当前分支上游，或用户明确要求本轮不提交 / 不推送。
- 如果用户要求远端部署，远端部署已完成。
- 剩余风险已列出。

没有完成这些步骤，不允许声明任务完成。

## 8. 证据报告

证据报告统一生成到：

```text
harness/reports/evidence-YYYYMMDD-HHMMSS.md
```

报告必须包含：

- 时间
- 环境
- 分支
- commit hash
- 工作区是否干净
- 构建结果
- Docker 状态
- 健康检查结果
- 业务验证结果
- 是否部署远端
- 远端健康检查结果
- 结论：`PASS` / `PARTIAL` / `FAIL`
- 剩余风险

脚本能采集多少写多少；采集不到必须写“未采集 / 阻塞原因”，不得编造。

## 9. 当前文档关系

- `CLAUDE.md`：仓库地图。
- `docs/`：事实主源、领域合同、流程、接口、数据、验收、部署和 ADR。
- `.claude/`：保留为 Claude 工作台和历史 Agent 工作流文档。
- `scripts/`：保留现有启动、QA 和部署辅助脚本。
- `harness/`：新增统一执行系统，负责固定入口、脚本、skills、evals、runbooks、prompts 和 reports。

## 10. Harness 五子系统

- Instructions：`harness/instructions/`、`AGENT_CONTRACT.md`、`FORBIDDEN_SCOPE.md`、`TASK_ROUTING.md`。
- Tools：`harness/commands/`、`harness/tools/README.md`。
- Environment：`harness/environment/`。
- State：`harness/CURRENT_STATE.md`、`harness/state/`、`HARNESS_CHANGELOG.md`。
- Feedback：`harness/feedback/`、`harness/evals/`、`harness/reports/`。

发现旧文档与当前事实冲突时，不得自行拍板，写入 `docs/决策/ADR-002-V1范围优先级.md`。
