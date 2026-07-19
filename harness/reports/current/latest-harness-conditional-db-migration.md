# Harness 验证证据

- 运行 ID：` run-20260719054440131-35b3484c-cfe7-4a09-8624-8fb6ae271bed `
- 环境：` real-pre `
- 范围：` full `
- 分支：` codex/harness-node-verify-phase1 `
- HEAD：` b8cb837b02fba1c61d0d1f29e325d0497ec9bdf6 `
- 证据身份：` WORKTREE `
- 开始时间：2026-07-19 13:44:40 Asia/Shanghai（` 2026-07-19T05:44:40.131Z `）
- 完成时间：2026-07-19 14:03:44 Asia/Shanghai（` 2026-07-19T06:03:44.750Z `）
- 运行结论：通过（PASS）

## 结果摘要

全部阻断检查均已通过。

## 检查结果

| 检查项 | 状态 | 阻断 | 摘要 |
| --- | --- | --- | --- |
| 检查仓库结构 | 通过（PASS） | 是 | 仓库结构与当前 Harness 入口完整。 |
| 检查环境文件 | 通过（PASS） | 是 | Compose、示例文件与实际 &#46;env&#46;real&#45;pre 均存在。 |
| 检查必需配置 | 通过（PASS） | 是 | 必需配置键均已填写；未输出任何配置值。 |
| 检查环境硬开关 | 通过（PASS） | 是 | 环境硬开关符合当前环境基线。 |
| 检查密钥存在性 | 警告（WARN） | 否 | 密钥只报告存在性。存在：DB&#95;PASSWORD、REDIS&#95;PASSWORD、JWT&#95;SECRET、DOUYIN&#95;CLIENT&#95;SECRET、LOGISTICS&#95;KD100&#95;KEY；缺失：TALENT&#95;PROFILE&#95;HTTP&#95;TOKEN、TALENT&#95;PROFILE&#95;HTTP&#95;AUTHORIZATION。 |
| 扫描破坏性命令 | 通过（PASS） | 是 | scripts 与 harness 中未发现被禁止的破坏性命令引用。 |
| 检查版本控制边界 | 通过（PASS） | 是 | Node inspect 未调用 Git；敏感变更与候选提交由 agent&#45;do 的 PowerShell 门禁负责。 |
| 检查 Node&#46;js | 通过（PASS） | 是 | 检查 Node&#46;js只读版本探测通过。 |
| 检查 npm | 通过（PASS） | 是 | 检查 npm只读版本探测通过。 |
| 检查 Java | 通过（PASS） | 是 | 检查 Java只读版本探测通过。 |
| 检查 Maven | 通过（PASS） | 是 | 检查 Maven只读版本探测通过。 |
| 检查 Docker | 通过（PASS） | 是 | 检查 Docker只读版本探测通过。 |
| 检查 Docker Compose | 通过（PASS） | 是 | 检查 Docker Compose只读版本探测通过。 |
| 验证后端构建 | 通过（PASS） | 是 | 后端测试与打包均已通过。 |
| 验证前端构建 | 通过（PASS） | 是 | 前端依赖安装、类型检查、测试与构建均已通过。 |
| 验证 Docker 运行环境 | 通过（PASS） | 是 | 目标应用服务已完成安全重建，Compose 状态查询成功。 |
| 验证应用健康状态 | 通过（PASS） | 是 | 所选应用健康探测均已通过。 |
| 验证业务链路 | 通过（PASS） | 是 | 业务验证命令已通过（环境默认命令）。 |
| 检查 Git 证据身份 | 通过（PASS） | 是 | 已消费 agent&#45;do 注入的 Git 快照，Node 未执行 Git 命令。 |

## 工作区身份

- 补丁指纹：` sha256:4b7a5a5b3c30562b022ea64e1780de2a6781e90f14c11045960340b3b09baad6 `
- ` docs/10-部署运行总览.md `
- ` docs/决策/ADR-015-real-pre单通道CD与不可变发布.md `
- ` harness/rules/changelog.md `
- ` harness/rules/cicd-real-pre-policy.md `
- ` harness/rules/environment/envs/remote-real-pre-env.md `
- ` harness/rules/runbooks/remote-deploy.md `
- ` harness/rules/runbooks/rollback.md `
- ` harness/rules/skills/git/git-change-control.commit.md `
- ` harness/rules/state/snapshots/DEPLOYMENT_STATE.md `
- ` harness/scripts/tests/release-channel.Tests.ps1 `
- ` Jenkinsfile `
- ` scripts/deploy-release.sh `
- ` scripts/detect-release-db-migration.sh `

## 证据路径

- 原始 JSON：` runtime/qa/out/run-20260719054440131-35b3484c-cfe7-4a09-8624-8fb6ae271bed/run.json `
- 稳定 Markdown：` harness/reports/current/latest-harness-conditional-db-migration.md `

## 发布与数据库操作

- 远端部署：未执行（`DeployRemote=false`）。
- 远端数据库迁移：未执行。
- 本次验证仅在本地 `real-pre` 完成构建、容器重建、健康检查和业务验证。

## 剩余风险

- 本次未验证远端 Jenkins 实际发布；需在候选提交进入唯一发布队列后，由发布控制器验证。
- `TALENT_PROFILE_HTTP_TOKEN` 与 `TALENT_PROFILE_HTTP_AUTHORIZATION` 缺失仅为非阻断警告，与本次条件迁移逻辑无关。

## Retro

- 结论：远端数据库迁移必须由“当前已部署 SHA 到目标 SHA”的累计差异触发；Harness、文档或普通应用变更不命中迁移路径时，只记录数据库/Flyway 只读观测值，不执行数据库写操作。
- 改进动作：Jenkins 在全局发布锁内生成并固化 `databaseMigration` 决策，部署脚本按该决策切换严格版本门禁或只读观测门禁。
- 验证方式：发布通道 Pester 测试覆盖无迁移、迁移变更、首次发布和已授权回滚四种 Git 历史场景；完整 `agent-do` 阻断检查全部通过。
