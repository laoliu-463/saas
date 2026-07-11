# Harness Changelog（索引）

> 任务 ID：HARNESS-DOC-GC-OPTIMIZE-002
> 更新时间：2026-07-11
> 详细历史（含每版修改文件、行为变化、证据）：`archive/20260610/harness-changelog-full.md`
> 治理政策：`rules/policies/retention-policy.md` 第 2 节（changelog 索引 ≤200 行）

## 最近版本摘要

### v0.7.8 — 2026-07-11
- 修复 `check-harness-limits.ps1` 的主工作区绝对路径依赖：现在从 `$PSScriptRoot` 解析当前 worktree 的 `harness` 根目录，避免隔离分支误读主工作区并返回错误 `PASS`。
- 新增 `check-harness-limits-path.test.ps1`，用临时 Harness 的 51 文件溢出场景验证检查目标和报告落点；测试按 RED（错误返回主工作区 `PASS`）→ GREEN 通过。
- 通过显式 manifest 归档 12 份过期报告，并将 517 行旧 evidence 打包为 ZIP；当前 `harness/reports` 直属文件数为 50，50/50/200 检查通过。范围仅限 Harness，不改变业务逻辑、API、权限、状态机或数据。

### v0.7.7 — 2026-07-11
- 完成寄样查询剩余读入口的端口化迁移：看板、导出和物流读取经 `SampleQueryApplicationService` 调用独立 Query Port，并由 Legacy adapter 保持行为兼容。
- 补齐 E-7 promotion event 的干净检出生产基线；定向测试、寄样全域回归、DDD redline 与宽口径架构测试通过。
- 本轮未改变 Harness 执行能力、API、权限、数据范围、状态机、CSV 或真实数据；仅同步状态和可复验证据。

### v0.7.6 — 2026-07-10
- 完成 137 项 dirty 的路径级 Git Intake 分类并固化单任务批次顺序，`unknown_paths=0`，仍按受保护 previous-partial 处理。
- 机械重算 178 卡矩阵并将证据不足的 Y-4 / E-7 暂降 PARTIAL；修正 Validation State 的当前脚本路径。
- 同日后续以行为测试、local real-pre migration/health/API 证据恢复 Y-4 为 DONE；E-7 仍保持 PARTIAL。本项仅更新状态证据，不升级 Harness 执行能力。
- 补录 DEBT-026（agent-do 自动暂存/推送冲突）与 DEBT-027（超长报告归档未打包）。
- 通过 manifest、SHA-256 校验和 ZIP 打包恢复 Harness 50/50/200 检查；不改业务生产逻辑。

### v0.7.5 — 2026-07-04
- 新增 Apifox OpenAPI 同步红线：创建分支不等于导入成功，Open API import 必须显式指定 `APIFOX_BRANCH` 对应的 `targetBranchId`。
- Evidence 必须记录 branch source、import target branch、target branch id、import API、endpoint/schema import counters 和 `endpoint list/get` 结果。
- 新增开发入口门禁：云端导入前必须校验 `APIFOX_DEV_BASE_URL`、`APIFOX_DEV_PORT`、OpenAPI `servers`，可用时回读 Apifox environment Base URL。
- 新增 `agent-do.ps1 -Scope apifox` 和 `scripts/verify-openapi-apifox.sh`，默认只做 local verification，不发布 docs-site/shared-doc。
- 范围：OpenAPI/Apifox harness；不修改业务逻辑、API、schema、权限、状态机、金额、佣金、提成或归因规则。

### v0.7.4 — 2026-07-04
- 新增 DDD 收口聚合验收脚本：`harness/scripts/check-ddd-acceptance.ps1`，覆盖 dirty 分类、白名单 debt、证据矩阵、Maven 架构测试、通用安全检查和 latest report。
- 新增 `docs/ddd-validation-guide.md`，说明 DDD 1.0 收口、七层验收、每轮 Codex 流程和 redline debt 逐步清零口径。
- 范围：Harness/docs；不修改业务逻辑、API、schema、权限、状态机、金额、佣金、提成或归因规则。

### v0.7.3 — 2026-06-22
- 新增 Jenkins real-pre CD 规范：固定 job、源码分支、Preflight、后端测试、前端构建、镜像标签、Compose 校验、real-pre 部署、健康检查、回滚和 evidence report。
- 明确生产环境不由 `saas-real-pre-cd` 自动触碰，后续生产接入必须另建审批型 job。
- 基线证据：`saas-real-pre-cd #9`，commit `e248b611698e56e1e1e924fc65e79bee0fcb8fac`，Result `SUCCESS`。

### v0.7.2 — 2026-06-21
- `git-push-safe.ps1` 明文密钥扫描在代码文件中跳过运行时对象属性 / 函数调用表达式，避免把 `useAuthStore().token`、`form.value.password` 误判为硬编码密钥，同时保留配置类文件的未加引号密钥检查。
- 修正 dry-run 文案，不再输出被规则禁止的 `git add -A` 示例。
- 范围：Harness 脚本；已用 `git-push-safe.ps1 -DryRun` 和 `agent-do.ps1 -Scope docs -DryRun` 验证。

### v0.7.1 — 2026-06-20
- Harness/docs 路径口径校正：DDD 路线图、任务矩阵、Gate、Domain Map、multi-agent 提示词索引统一指向 `harness/rules/...` 与 `harness/scripts/commands/...`。
- 范围：docs / harness 规则；不修改业务代码，不声明 DDD 代码重构完成。

### v0.7.0 — 2026-06-06
- 服务费收入双轨公式后端代码对齐（`OrderDualTrackAmountResolver` / `PerformanceCalculationService` 按预估 / 结算分轨）。
- 数据页订单明细、dashboard metrics、订单汇总和业绩汇总 DTO 服务费支出公式按双轨分轨。
- 范围：后端 + harness 同步；real-pre 业务验证待 evidence gate。

### v0.6.9 — 2026-06-06
- 服务费收益双轨公式口径更新（预估 / 结算分轨；结算轨不扣技术服务费）。
- 范围：docs / harness 口径同步；不宣称业务代码一致性修复。

### v0.6.8 — 2026-06-06
- DASH-CHANNEL-COMMISSION-LABEL-001 渠道提成文案（媒介 → 渠道）。
- 范围：frontend label；622 tests passed，0 错误。

### v0.6.7 — 2026-06-05
- 毛利纳入 V1 交付范围（撤销"不做毛利"；P0-004 降级为前端补齐）。

### v0.6.6 — 2026-06-05
- 远端 JAR 刷新门禁（deploy-remote 刷新前必须 SQL guard + health check）。

### v0.6.5 — 2026-06-04
- Git 推送 helper（UTF-8 编码修正；attribution 关闭）。

### v0.6.4 — 2026-06-04
- 订单明细表字段对齐 real-pre（外露字段经审核映射）。

### v0.6.3 — 2026-06-04
- OrderDetailVO 后端重排（仅含 order 主表 + 关键汇总字段）。

### v0.6.2 — 2026-06-03
- DASHBOARD-MONEY-AUDIT-001 启动（dashboard metrics 真实数据回归）。

### v0.6.1 — 2026-06-03
- HARNESS-DEBT-GC-001（结构治理前奏）。

### v0.6.0 — 2026-06-02
- HARNESS-DEBT-GOVERNANCE-ITERATION（治理标准 + 工具脚本第一版）。

## 历史版本（按日期倒序）

| 版本 | 日期 | 主题 |
|---|---|---|
| v0.5.7 | 2026-05-30 | GIT-BATCH-C（commands/ 全部迁移 scripts/commands/） |
| v0.5.5 | 2026-05-28 | TALENT-ADDRESS（达人地址域 P0 收尾） |
| v0.5.6 | 2026-05-29 | GIT-INTAKE（仓库 intake 流程） |
| v0.5.4 | 2026-05-27 | P0-SAMPLE-001-REMOTE-VERIFY（寄样远端验收） |
| v0.5.3 | 2026-05-25 | ORDER-P0-DUAL-SOURCE（订单双源数据验证） |
| v0.5.2 | 2026-05-24 | P0-ORDER-001（订单 P0 上线） |
| v0.5.1 | 2026-05-22 | GIT-BATCH-4-REPORTS（reports 目录二次治理） |
| v0.5.0 | 2026-05-20 | GIT-HARNESS-001（harness 入 git 第一批） |
| v0.4.11 | 2026-05-18 | GIT-BATCH-2（harness 第二批入 git） |
| v0.4.10 | 2026-05-16 | SYNC-PLAN-001（订单同步部署计划） |
| v0.4.9 | 2026-05-14 | P-FIX-002D-REMOTE（远端修复批次 D） |
| v0.4.8 | 2026-05-13 | P-FIX-002D-LOCAL（本地修复批次 D） |
| v0.4.7 | 2026-05-12 | P-FIX-002（业绩 P0 修复） |
| v0.4.6 | 2026-05-11 | P-FIX-002A（业绩 P0 修复 A 轮） |
| v0.4.5 | 2026-05-10 | P-DIAG-002（业绩诊断） |
| v0.4.4 | 2026-05-09 | P-FIX-001C（订单 P0 修复 C 轮） |
| v0.4.3 | 2026-05-08 | FUNC-001（功能首版） |
| v0.4.2 | 2026-05-07 | TEST-1（首批单元测试基线） |
| v0.4.1 | 2026-05-05 | U-2.5-B（用户域迭代 B） |
| v0.4.0 | 2026-05-03 | Session Exit Gate（会话退出门禁） |
| v0.3.0 | 2026-04-28 | Completion Gate（完成门禁） |
| v0.2.1 | 2026-04-22 | U-2.5-A（用户域迭代 A） |
| v0.2.0 | 2026-04-15 | U-2（用户域第二版） |
| v0.1.9 | 2026-04-08 | U-1（用户域第一版） |
| v0.1.8 | 2026-04-01 | DDD 路线图引入 |
| v0.1.7 | 2026-03-25 | CODEX 默认 real-pre 验证 |
| v0.1.6 | 2026-03-18 | deploy-remote SQL guard |
| v0.1.5 | 2026-03-10 | real-pre 默认环境 |
| v0.1.4 | 2026-03-05 | git-push-safe UTF-8 |
| v0.1.3 | 2026-02-28 | retire-content.ps1 脚本 |
| v0.1.2 | 2026-02-20 | harness/doc 聚合 |
| v0.1.1 | 2026-02-10 | 五子系统分层 |
| v0.1.0 | 2026-01-30 | 初始化 |

> 详细历史（含每版修改文件清单、行为变化、evidence 引用）见 `archive/20260610/harness-changelog-full.md`。
