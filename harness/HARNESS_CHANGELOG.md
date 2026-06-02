# Harness Changelog

## v0.1.7

- 修正 `CODEX.md` 默认入口为本地 `real-pre`，与 `AGENTS.md`、Harness 命令默认值保持一致。
- 将 `CONTEXT.md` 标题从 V2.2 改为 V1 术语上下文，明确旧 V2.2 仅作历史参考。
- 压缩 `AGENTS.md` 执行入口示例，保持入口文件短小，并把 Scope 细节交给 `harness/TASK_ROUTING.md`。
- 扩展 `TASK_ROUTING.md`，覆盖数据库变更、接口联调、第三方联调、Docker、部署、测试验收、Bug、性能、权限、数据问题和任务收尾。
- 新增环境索引、local dev 环境事实、状态索引和变更类 runbook，补齐 Harness 五子系统可发现性。
- 新增 `harness/feedback/garbage-collection-policy.md`，明确保留、归档、删除、合并和删除前检查规则。

## v0.1.6

- `deploy-remote.ps1` 在远端构建和重启后端前，先启动 `postgres-real-pre` 并执行活动商品依赖的幂等结构迁移 `V20260529_001__alter-colonel-activity-add-recruiter-fields.sql`。
- 新增远端活动商品 schema guard：校验 `colonel_activity` 已存在 `recruiter_user_id`、`recruiter_dept_id`、`assigned_at`、`assigned_by`、`activity_status_code`、`activity_status_text` 6 个字段，否则中止远端部署。
- 暂不将 `scripts/run-real-pre-db-migrations.sh` 的聚合 `migrate-all.sql` 接入每次 Harness 远端部署；该文件仍含历史非幂等 DML，重复执行存在数据漂移风险。

## v0.1.5

- 将 `agent-do.ps1`、`safety-check.ps1`、`restart-compose.ps1`、`verify-local.ps1`、`collect-evidence.ps1` 和 `new-retro.ps1` 的默认环境切换为本地 `real-pre`；`test` 仅作为显式专项环境。
- 调整 `agent-do.ps1` 顺序为安全检查 -> 构建 -> Compose 重建 -> 健康检查 -> 业务验证，避免业务验证失败时跳过重启和健康证据。
- `agent-do.ps1` 成功路径按实际验证状态写入 evidence conclusion：docs / 跳过业务验证 / 待远端部署为 `PARTIAL`，本地完整验证通过为 `PASS`。
- 修复 `Get-HarnessChangedFiles` 和 `collect-evidence.ps1` 对 `git status` 首行前导空格的处理，避免首个 modified 文件名被截断。
- `deploy-remote.ps1` 在远端 `git pull --ff-only` 后通过 `maven:3.9.10-eclipse-temurin-17` Docker 镜像执行 `mvn -f backend/pom.xml -DskipTests package`，适配后端 Dockerfile 需要预构建 `backend/target/*.jar` 且服务器未安装 Maven 的场景。
- 更新 AGENTS、Task Routing、Tools、Runbook 和 Harness 文档中的默认入口示例，明确远端部署仍必须显式传 `-DeployRemote true`。

## v0.1.4

- 修复 `git-push-safe.ps1` 对非 ASCII 文件名的兼容性：`Get-ChangedFiles` 和 `git diff --cached --name-only` 改用 `git -c core.quotepath=false` 输出原始 UTF-8 路径，避免 octal 转义导致 `Test-Path` 报错。
- `Assert-NoPlainSecrets` 对 `Test-Path` 增加 try-catch 容错，跳过无法解析的路径而非中断流程。
- `verify-local.ps1` 后端健康检查从单次尝试改为重试机制（最多 12 次，间隔 10 秒，总计最长 120 秒），适配 Spring Boot 容器启动延迟。
- 新增已知风险：test 环境 E2E auth setup 可能因后端容器初始化未完成而超时。

## v0.1.3

- 新增 `harness/commands/retire-content.ps1`，提供旧内容维护计划、manifest 驱动归档和 manifest 驱动删除能力。
- `agent-do.ps1` 默认在任务后执行 `ContentMaintenance=plan`，生成旧内容候选报告；归档和删除必须显式传 manifest。
- `collect-evidence.ps1` 新增 Content Maintenance Result 字段，用于记录旧内容维护结果。
- 新增旧内容生命周期规则，明确 keep / update / archive / delete 的判断口径和受保护路径。

## v0.1.2

- 新增 `harness/doc/` Harness Engineering 聚合文档入口，按 Instructions、Tools、Environment、State、Feedback 五个子模型组织。
- 将旧文档冲突、real-pre 安全边界、当前项目状态、业务闭环验证标准和任务证据模板集中到 `harness/doc/`，供后续 Agent 快速读取。
- 本次仅做文档层重构；原 `docs/` 和既有 `harness/` 仍作为事实主源与执行入口，不删除旧文档。

## v0.1.0

- 初始化 Instructions / Tools / Environment / State / Feedback 五个子系统。
- 建立 `AGENTS.md` 强制执行协议、`harness/AGENT_CONTRACT.md`、`CURRENT_STATE.md`、`TASK_ROUTING.md`、`FORBIDDEN_SCOPE.md` 和 `DOMAIN_MAP.md`。
- 新增 PowerShell 固定命令入口：`agent-do.ps1`、`safety-check.ps1`、`restart-compose.ps1`、`verify-local.ps1`、`collect-evidence.ps1`、`git-push-safe.ps1`、`deploy-remote.ps1`。
- 新增 skills、evals、runbooks、prompts 和 reports 输出目录。

## v0.1.1

- 补齐五子系统分层目录：`instructions/`、`tools/`、`environment/`、`state/`、`feedback/`。
- 新增 `new-retro.ps1`，将每次任务后的 Harness 复盘纳入默认闭环。
- 升级 docs-only 脚本行为：`restart-compose.ps1` 和 `verify-local.ps1` 支持 `Scope=docs`。
- 将 evidence report 和 retro summary 纳入 `agent-do.ps1` 默认流程。
