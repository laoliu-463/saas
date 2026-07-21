# CI/CD 不可变发布链路证据

- 时间：2026-07-21 18:46:30 +08:00
- 环境：本地 Windows worktree；目标环境 real-pre；本轮未连接远端服务器
- 分支：`codex/ci-cd-immutable-release`
- 代码提交：`750b8be1`（已 rebase 到 `main@4ea8e832`；修复 report lifecycle 测试跨平台执行器，并补齐 Jenkins `IMAGE_TAG` 40 位 SHA 校验）
- 工作区：代码提交后仅有本报告待提交；证据提交后干净

## 变更结果

- GitHub Actions：按 changed scope 执行检查，统一 `CI Gate`，main 合并后构建并推送 SHA 标记镜像，记录镜像 digest。
- release：增加 `release/real-pre.json` 合同、迁移输入指纹和发布前校验；实际 manifest 只应进入 `release/real-pre` 提升 PR，当前分支仅保留 shape example。
- Jenkins：只允许 `release/real-pre`，消费 `repository@sha256:digest`，持有全局部署锁，并通过 `scripts/cd/release-real-pre.sh` 的 `flock` wrapper 执行变更；无服务器源码构建。
- 失败回滚：新增 `scripts/cd/rollback-real-pre.sh`；部署开始后，任一 readiness、前端、P0/E2E、调度恢复、最终健康检查、超时或中止都在锁内统一恢复旧 digest，并使用 `deployment-started`、`rollback-completed`、`release-completed`、`schedulers-restored` 状态文件防止锁外用新镜像补救。
- Harness：拆分 inspect、verify、evidence、release verify、显式 commit、显式 push；`agent-do` 不再隐藏提交、推送或远端部署。
- 直连部署脚本：保留 break-glass，但必须显式提供 `BREAK_GLASS_APPROVED=true` 和原因；日常路径不使用 SSH。

## 验证证据

- `CI YAML`：PASS（PyYAML 解析）。
- Jenkins 镜像身份合同：PASS；`Preflight Guard` 现在显式拒绝非 40 位 `IMAGE_TAG`。
- PR / GitHub Actions：PASS；PR #212 的 run `29823060050` 绑定 head `0097299d`，`Changes`、`Backend tests`、`Repository governance`、`CI Gate` 全部通过；前端、Immutable images、Release manifest 因变更范围正确跳过。
- 发布清单示例与迁移输入哈希：PASS；当前迁移输入哈希为 `sha256:809df060f465d33ffc7c692ef1c0f76cad8eac5c6c0e12beb0589157abbf0b6e`。
- Shell 语法：PASS（数据库迁移、部署、回滚脚本）。
- PowerShell AST：PASS（harness/scripts 下脚本）。
- Docker Compose config：PASS；使用 `.env.real-pre.example` 仅做配置解析，未启动容器。
- Pester：PASS；按 GitHub Actions 完整治理测试集本地 80 passed / 0 failed / 0 skipped，其中 report lifecycle 9/9；新增五类锁内失败回滚合同测试。
- Harness limits：`TASK_GATE=PASS`；`REPOSITORY_HEALTH=PARTIAL`，仅存在历史报告数量与行数债务，本次未新增。
- 应用构建：未执行；本轮是 CI/CD、部署合同和 Harness 变更，GitHub main workflow 负责实际后端/前端镜像构建。
- Docker 重启、健康检查、业务验证：未执行；本轮未修改应用业务代码，也未启动本地 real-pre。
- 远端部署与远端健康检查：未执行；没有 SSH 部署。
- Jenkins 语法/真实流水线：未执行；本地没有 Groovy 或 Jenkins 控制器，仅完成静态合同审计。Jenkins post 回滚不依赖已结束的 GHCR 登录凭证，只使用发布前已验证且仍在本机的旧 digest。

## 未闭环项与风险

- GitHub 分支保护是否已将 `CI Gate` 设为 required，未通过本地代码验证；需要仓库管理员在 GitHub 设置并用真实 PR 验证。
- Jenkins 需要配置 `saas-container-registry` 凭证和 Lockable Resources 的 `saas-real-pre-deploy` 资源；本地没有 Jenkins 控制器，未执行流水线语法与真实部署验证。
- PR #212 已创建并通过 GitHub Actions。历史首轮 run `29822336993` 暴露了 Ubuntu Runner 的 `powershell`/`pwsh` 跨平台问题；第二轮 run `29822753328` 的 Pester 已 80/80 通过但暴露 Jenkins `IMAGE_TAG` 合同缺口；第三轮 run `29823060050` 在 `0097299d` 上全部必需检查通过。
- 首次切换到 digest 发布前，必须为 `release/real-pre.json` 填入真实 main SHA、GHCR 两个 digest、迁移版本和可回滚的 previous manifest；不能直接使用 example 中的零值。
- `READY_FOR_REVIEW`：代码、合同、本地验证和 PR CI 均通过；仍未完成 GHCR 不可变镜像生成、发布提升 PR、Jenkins 故障注入或首次 real-pre 发布。

## Retro

改进动作：在合并本分支后先做一次“非生产发布演练”，由仓库管理员确认 branch protection、GHCR 权限、Jenkins 凭证与部署锁，再用一份真实 manifest 完成 release/real-pre 提升；验收标准是 Jenkins 能拉取两个 digest、完成 readiness/P0、生成远端 release record，并能演练回滚。
