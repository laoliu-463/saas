# Harness 验证证据

- 运行 ID：` run-20260719051158132-8430f38f-de50-4a7d-8bb1-6b0d52d728b7 `
- 环境：` real-pre `
- 范围：` full `
- 分支：` codex/harness-node-verify-phase1 `
- HEAD：` 651945d3444dc734f9d07ed0275f983e21aad7ee `
- 证据身份：` WORKTREE `
- 开始时间：2026-07-19 13:11:58 Asia/Shanghai（` 2026-07-19T05:11:58.132Z `）
- 完成时间：2026-07-19 13:21:36 Asia/Shanghai（` 2026-07-19T05:21:36.523Z `）
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

- 补丁指纹：` sha256:4e521c1993fa49e5f267e15d607ac39f04473b62faee0f3948b82835cba56bcc `
- ` .env.real-pre.example `
- ` .github/workflows/ci.yml `
- ` .github/workflows/release-images.yml `
- ` AGENTS.md `
- ` backend/Dockerfile `
- ` backend/pom.xml `
- ` backend/src/main/java/com/colonel/saas/config/RuntimeVersionProvider.java `
- ` backend/src/main/java/com/colonel/saas/controller/SystemEnvController.java `
- ` backend/src/main/resources/application-real-pre.yml `
- ` backend/src/main/resources/application-test.yml `
- ` backend/src/main/resources/db/migrate-all.sql `
- ` backend/src/test/java/com/colonel/saas/config/RuntimeVersionProviderTest.java `
- ` backend/src/test/java/com/colonel/saas/controller/SystemEnvControllerTest.java `
- ` backend/src/test/java/com/colonel/saas/service/data/DataApplicationServiceOrderSummaryCacheTest.java `
- ` docker-compose.real-pre.release.yml `
- ` docker-compose.real-pre.yml `
- ` docs/10-部署运行总览.md `
- ` docs/deploy/00-服务器部署总览.md `
- ` docs/deploy/06-回滚与故障排查.md `
- ` docs/deploy/07-Jenkins自动化部署规划.md `
- ` docs/deploy/README.md `
- ` docs/README.md `
- ` docs/决策/ADR-015-real-pre单通道CD与不可变发布.md `
- ` frontend/Dockerfile `
- ` frontend/package-lock.json `
- ` harness/archive/retired-content/20260718-232348/standalone-retro/latest-retro-evidence-20260713.md `
- ` harness/archive/retired-content/20260718-232348/standalone-retro/retro-20260713-harness-redundancy-cleanup.md `
- ` harness/archive/retired-content/20260718-232348/standalone-retro/retro-user-soft-deleted-restore.md `
- ` harness/archive/retired-content/20260718-232348/standalone-retro/retro-xiangyun-biz-staff-talent-20260717.md `
- ` harness/archive/retired-content/20260718-232348/superseded-evidence-maintenance/latest-product-library-cross-platform-evidence-retire.md `
- ` harness/archive/retired-content/20260718-232348/superseded-evidence-maintenance/latest-product-library-rich-clipboard-evidence-amend.md `
- ` harness/archive/retired-content/20260718-232743/superseded-harness-design/latest-evidence-20260713-harness-layered-file-governance-design.md `
- ` harness/archive/retired-content/20260718-232743/superseded-harness-design/latest-evidence-20260713.md `
- ` harness/manifests/single-channel-cd-current-report-retirement-20260718.json `
- ` harness/manifests/single-channel-cd-current-report-retirement-extra-20260718.json `
- ` harness/reports/current/latest-content-retire.md `
- ` harness/reports/current/latest-evidence-20260713-harness-layered-file-governance-design.md `
- ` harness/reports/current/latest-evidence-20260713.md `
- ` harness/reports/current/latest-harness-limits-check.md `
- ` harness/reports/current/latest-harness-single-channel-cd.json `
- ` harness/reports/current/latest-harness-single-channel-cd.md `
- ` harness/reports/current/latest-product-library-cross-platform-evidence-retire.md `
- ` harness/reports/current/latest-product-library-rich-clipboard-evidence-amend.md `
- ` harness/reports/current/latest-retro-evidence-20260713.md `
- ` harness/reports/current/retro-20260713-harness-redundancy-cleanup.md `
- ` harness/reports/current/retro-user-soft-deleted-restore.md `
- ` harness/reports/current/retro-xiangyun-biz-staff-talent-20260717.md `
- ` harness/rules/changelog.md `
- ` harness/rules/cicd-real-pre-policy.md `
- ` harness/rules/environment/CHEATSHEET.md `
- ` harness/rules/environment/envs/remote-real-pre-env.md `
- ` harness/rules/governance/completion-gates-git.md `
- ` harness/rules/governance/task-routing.md `
- ` harness/rules/policies/agent-contract.md `
- ` harness/rules/runbooks/governance/scope-command-matrix.md `
- ` harness/rules/runbooks/governance/task-lifecycle.md `
- ` harness/rules/runbooks/remote-deploy.md `
- ` harness/rules/runbooks/rollback.md `
- ` harness/rules/skills/git/git-change-control.commit.md `
- ` harness/rules/state/snapshots/DEPLOYMENT_STATE.md `
- ` harness/scripts/commands/_lib.ps1 `
- ` harness/scripts/commands/agent-do.ps1 `
- ` harness/scripts/commands/deploy-remote.ps1 `
- ` harness/scripts/commands/git-push-safe.ps1 `
- ` harness/scripts/modules/HarnessFileGovernance.psm1 `
- ` harness/scripts/tests/agent-do-node-delegation.Tests.ps1 `
- ` harness/scripts/tests/check-harness-limits.Tests.ps1 `
- ` harness/scripts/tests/release-channel.Tests.ps1 `
- ` harness/scripts/tests/report-lifecycle.Tests.ps1 `
- ` Jenkinsfile `
- ` scripts/check-acr-creds.ps1 `
- ` scripts/deploy-real-pre.sh `
- ` scripts/deploy-release.sh `
- ` scripts/health-check.sh `
- ` scripts/rollback-real-pre.sh `

## 证据路径

- 原始 JSON：` runtime/qa/out/run-20260719051158132-8430f38f-de50-4a7d-8bb1-6b0d52d728b7/run.json `
- 稳定 JSON：` harness/reports/current/latest-harness-single-channel-cd.json `
- 稳定 Markdown：` harness/reports/current/latest-harness-single-channel-cd.md `
