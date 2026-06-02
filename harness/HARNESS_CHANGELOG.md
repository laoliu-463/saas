# Harness Changelog

## v0.1.5

- 将 `agent-do.ps1`、`safety-check.ps1`、`restart-compose.ps1`、`verify-local.ps1`、`collect-evidence.ps1` 和 `new-retro.ps1` 的默认环境切换为本地 `real-pre`；`test` 仅作为显式专项环境。
- 调整 `agent-do.ps1` 顺序为安全检查 -> 构建 -> Compose 重建 -> 健康检查 -> 业务验证，避免业务验证失败时跳过重启和健康证据。
- `agent-do.ps1` 成功路径按实际验证状态写入 evidence conclusion：docs / 跳过业务验证 / 待远端部署为 `PARTIAL`，本地完整验证通过为 `PASS`。
- 修复 `Get-HarnessChangedFiles` 和 `collect-evidence.ps1` 对 `git status` 首行前导空格的处理，避免首个 modified 文件名被截断。
- `deploy-remote.ps1` 在远端 `git pull --ff-only` 后通过 Docker Maven 镜像执行 `mvn -f backend/pom.xml -DskipTests package`，适配后端 Dockerfile 需要预构建 `backend/target/*.jar` 且服务器未安装 Maven 的场景。
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
