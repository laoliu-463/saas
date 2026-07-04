# Harness Changelog（索引）

> 任务 ID：HARNESS-DOC-GC-OPTIMIZE-002
> 更新时间：2026-06-20
> 详细历史（含每版修改文件、行为变化、证据）：`archive/20260610/harness-changelog-full.md`
> 治理政策：`rules/policies/retention-policy.md` 第 2 节（changelog 索引 ≤200 行）

## 最近版本摘要

### v0.7.5 — 2026-07-04
- 新增 Apifox OpenAPI 同步红线：创建分支不等于导入成功，`apifox import` 必须显式指定 `APIFOX_BRANCH`。
- Evidence 必须记录 branch source、import target branch 与 endpoint/schema import counters。
- 范围：OpenAPI/Apifox harness；不修改业务逻辑、API、schema、权限、状态机、金额、佣金、提成或归因规则。

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
