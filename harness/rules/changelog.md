# Harness Changelog（索引）

> 任务 ID：HARNESS-REDUNDANCY-CLEANUP-20260713
> 更新时间：2026-07-15
> 详细历史（含每版修改文件、行为变化、证据）：`archive/20260610/harness-changelog-full.md`
> 治理政策：`file-retention-policy.md`（changelog 索引 ≤200 行）

## 最近版本摘要

### v0.8.2 — 2026-07-18
- 修复商品库“复制链接”按钮：删除前端角色推导和点击拦截，转链权限完全由后端接口统一校验，管理员沿用后端全局放行语义。
- 合作单工作台新增竖排七项操作：通过、拒绝、修改订单、查看进度、复制链接、复制订单、私有备注；投诉达人按最终范围明确不实现。
- 代码已合并至 `feature/auth-system` 并部署远端 `real-pre`：运行源码、前后端镜像及 OCI revision 均为 `498e1719`；迁移清单与 schema 守卫辅助脚本已补齐，迁移、只读 schema 守卫和四个核心容器健康检查均通过。
- 私有备注表及唯一索引已在远端核验；完整真实业务 E2E 因抖音令牌缺失为 `BLOCKED_AUTH`，最终全量 Maven 复跑受主机 native-memory 限制，部署结论保持 `PARTIAL`。

### v0.8.1 — 2026-07-15
- `deploy-remote.ps1` 绑定本地完整 commit，远端拉取后必须与期望 commit 完全一致，避免并发推进时部署错误版本。
- 远端 `.env.real-pre` 必须收敛为指向 `/opt/saas/env/.env.real-pre` 的软链接；普通文件或目标不一致时停止部署。
- Compose 固定使用 `saas-active` project，并先收敛 PostgreSQL、Redis 的 working dir、配置文件和 env-file 来源；不删除 volume、不强制重建数据容器。
- 增加部署脚本 Pester 契约测试，并同步远端部署 runbook 与部署运行总览。
- `git-push-safe.ps1` 显式将 `HEAD` 推送到当前分支配置的 upstream ref，支持隔离工作树分支名与远端主线名不同的场景，仍保持非 force 推送。
- `agent-do` 远端部署后保留 `SkipBusinessValidation` 的 `PARTIAL` 结论，不再把未验证业务链误写为 `PASS`。

### v0.8.0 — 2026-07-13
- 实施 ADR-013：活跃目录 40/50、非脚本文本 160/200、reports 根目标 20，并区分 `TASK_GATE` 与 `REPOSITORY_HEALTH`。
- Evidence 与 content-retire 改为 `reports/current/latest-<key>.md` 稳定路径；retro 默认内联，仅可执行改进单独落盘。
- `agent-do` / `git-push-safe` 只处理显式 OwnedFiles，并自动合并本轮生成的 evidence、content-retire 报告和归档目标；gitee 保持只读。
- 使用 manifest 将 reports 根 75 份时间戳报告分三组归档，直接文件数从 87 降至 12；关闭 DEBT-014、DEBT-026、DEBT-027。
- 实现 commits：`2c304397`、`b38c9405`、`d1ef7289`、`b3140bb0`；最终证据：`reports/current/latest-harness-file-governance.md`。

### v0.7.7 — 2026-07-13
- 实现 commit：`7ca6d5ff`；证据：`reports/current/latest-evidence-20260713-harness-redundancy-cleanup.md`。
- 删除 26 个已被主源替代、未接入运行时或零引用的 Harness/.claude 文件，删除依据见 `manifests/harness-redundancy-cleanup-20260713.json`。
- 结构、保留和报告政策各收敛为 `rules/` 根目录单一主源；环境说明收敛为 `rules/environment/envs/` 下 5 个入口。
- 当前文档和模板统一使用 `harness/rules/`、`harness/scripts/commands/` 路径；历史 ADR 中明确描述旧记录的路径保持不变。
- 修正 `retire-content.ps1` 的文档债务扫描路径和 Harness 脚本保护路径。

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

### v0.7.2 — 2026-07-14
- 商品编辑开始/结束时间改为可选择日期时间；新增本系统推广时间覆盖的后端校验、回显、清空回退和派生状态一致性处理，保持 `product_snapshot` 上游事实不变。
- 验证：后端定向 57 tests、前端编辑抽屉 3 tests、前后端构建、real-pre 容器重启与本地健康通过；真实业务 preflight 因管理员登录 HTTP 401 为 BLOCKED_AUTH。

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
