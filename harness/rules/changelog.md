# Harness Changelog（索引）

> 任务 ID：GH-180-REAL-PRE-RELEASE-QUEUE；Harness CLI 实施：HARNESS-NODE-VERIFY-20260718
> 更新时间：2026-07-20
> 详细历史（含每版修改文件、行为变化、证据）：`archive/20260610/harness-changelog-full.md`
> 治理政策：`file-retention-policy.md`（changelog 索引 ≤200 行）

## 最近版本摘要

### v0.8.5 — 2026-07-20
- Issue #182 将达人认领到期判断从全量订单实体/JSONB 分页累积改为订单域 `SELECT 1 / LIMIT 1` 有界存在性查询，保持 `author_id OR talent_uid` 原匹配语义。
- `TalentClaimReleaseJob` 的 Cron 显式固定 `Asia/Shanghai`，业务比较时间继续与无时区数据库字段使用同一 JVM 默认时钟。
- real-pre 后端 JVM 增加 `ExitOnOutOfMemoryError`，Compose 治理测试限定后端服务块，避免半死 JVM 长期保持 running/unhealthy。
- 本轮无数据库 migration；只生成候选提交、PR/CI 和本地 evidence，不由 Agent 直接部署远端。

### v0.8.4 — 2026-07-19
- real-pre 唯一部署来源固定为 `release/real-pre`，Jenkins 同 Job 排队且使用 `saas-real-pre-deploy` 跨 Job 全局锁。
- 发布前校验目标 release tree 来自 `main`，并拒绝非当前部署后继提交；回滚必须显式设置 `ROLLBACK_APPROVED=true`。
- 数据库备份、迁移和 Schema 预检改为 migration diff 驱动；无迁移输入变化时明确 `SKIPPED`，纯 Harness / 文档变更不触碰远端数据库。
- 后端健康接口增加 `gitSha` / `imageDigest`，前端镜像生成 `/version.json`；Jenkins 核对运行 SHA、Docker 内容摘要、OCI revision 与 Flyway 后才更新不可变发布清单。
- `agent-do -DeployRemote` 和直接 SSH 部署脚本已退休；普通 Agent 只能提交候选和 evidence，不能绕过 Jenkins。
- 新增发布队列契约测试与分支治理 manifest；历史分叉分支只允许能力切片移植，脏 Worktree 全部保留。

### v0.8.3 — 2026-07-19
- 以服务器实际运行提交 `db930364f577f965f93601297e5e9854b4ff1813` 为发布基线，建立 `main` 与 `release/real-pre`，GitHub 默认分支切换为 `main`。
- `main` 与 `release/real-pre` 启用 PR、禁止强推、禁止删除和管理员同样受约束的基础保护；旧分叉分支进入分批核对，不做无证据整支合并。
- 建立 Issue → 独立 worktree/分支 → Draft PR → CI → 串行合并的 GitHub 协作合同，普通任务不再拥有直接合并或部署权限。
- 增加 CODEOWNERS、中文友好的 PR/Issue 模板、Dependabot、贡献指南和私密安全报告入口。
- CI 增加 merge queue 触发、完整 SHA Action 固定、Job 超时、Node 20、后端 PostgreSQL/Redis 依赖和仓库治理检查。
- 增加可执行 Pester 契约测试；本次不触发远端部署、容器重启或数据库迁移。
- 刷新 `harness/engineering/issues-index.md`，当前 open issue 镜像与 GitHub #165、#166、#168 一致；#168 跟踪后端 CI 基线与隔离数据库 bootstrap，旧 Sprint 排期明确标记为历史快照。
- 修正 docs/governance 统一入口：无本地运行环境文件时仍可执行安全扫描、Harness 门禁与 evidence 收口，并跳过 evidence 的运行时采集，不触发应用构建、容器或数据库操作。
- 修复 scoped push 对 `.github/` 的路径截断与未跟踪目录折叠问题，并让 Harness 文件路径按平台分隔符解析；保证 dot-prefixed Owned files 被逐文件暂存且 Linux 治理 Job 可执行。

### v0.8.2 — 2026-07-18
- 修正 `git-push-safe.ps1` 明文密钥扫描：仅将带引号的字面量或配置文件行识别为候选值，避免把 Java 函数调用、变量赋值和 Redis key 名误报为密钥。
- 范围：Harness Git 安全门禁；保留真实配置字面量扫描，需用 `git-push-safe.ps1 -DryRun` 回归。
- real-pre safety-check 新增 DOUYIN_APP_ID / DOUYIN_CLIENT_KEY / DOUYIN_CLIENT_SECRET 占位值门禁，避免 Redis 仍有旧 Token 时掩盖上游签名配置缺失。

### v0.8.1 — 2026-07-18
- real-pre CD 迁移路径统一到 Spring Boot/Flyway：移除独立 `schema_migration_log` 执行器，调度暂停后由应用启动迁移并只读核验 `flyway_schema_history`。
- CD 预检不再落盘渲染后的 Compose 环境值；证据结果由 readiness、镜像 ID、OCI revision 和迁移版本共同决定，取证失败不得写 `PASS`。
- 远端部署增加 checkout SHA 与 `IMAGE_TAG` 一致性、镜像 OCI revision、数据库备份和恢复前置校验。
### v0.9.0 — 2026-07-18
- 依据 ADR-014 将 Harness 一级目录白名单由 9 个扩展为 13 个，新增 `src/`、`contracts/`、`state/`、`tests/`；未知目录继续阻断。
- 保留 40/50/200、报告生命周期和基线感知语义；仅标准 `harness/package-lock.json` 精确豁免行数预算，Git 忽略的 `harness/node_modules/` 不计入结构健康，其他 JSON/lockfile 和未知目录仍阻断。
- `state/` 按需创建且禁止接收运行时产物，本批次不创建空目录。

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
