# CI/CD 与 Harness 收敛 evidence

## 基本信息

- 时间：2026-07-21 Asia/Shanghai
- 任务：核验并落地附件中 CI/CD、real-pre 发布入口和部署文档收敛建议
- 环境：本地工作区；未连接或修改远端 real-pre
- 分支：`codex/183-talent-claim-oom-guard-release`
- HEAD：`2bd6494afefc6d3f4533c0b6cef3dae8dd948336`
- 工作区：DIRTY；存在现有用户改动和 `harness/reports/current/latest-content-retire.md` 未解决冲突
- 本轮 OwnedFiles：`.github/workflows/ci.yml`、`README.md`、`docs/development-flow.md`、`docs/deploy/README.md`、`docs/deploy/00-服务器部署总览.md`、`docs/deploy/02-Docker手动部署real-pre.md`、`docs/deploy/07-Jenkins自动化部署规划.md`、`docs/deploy/08-real-pre全过程命令清单.md`、`harness/reports/current/latest-ci-cd-convergence.md`

## 本轮改动

- 新增 `docs/development-flow.md`，固定 `main -> PR + CI -> release/real-pre -> Jenkins` 黄金路径。
- README 和部署入口不再把 Gitee、任务分支、root SSH、IdentityFile 和现场构建作为日常发布教程。
- 手工 SSH、服务器 `git pull` 和 Docker 现场构建明确降级为经批准的 Break-glass 紧急恢复。
- CI 取消任务分支 `push` 触发，仅保留 `main` / `release/real-pre` push、`main` PR、merge queue 和手动触发。
- CI 增加变更范围检测和稳定名称 `CI Gate`；后端、前端按范围条件执行，治理检查结果由 Gate 汇总。
- Jenkins 治理检查改为排除行首注释后再验证可执行 `lock(resource: 'saas-real-pre-deploy')`。

## 验证

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| CI YAML 解析 | PASS | `python -c "import yaml; yaml.safe_load(...)"` |
| 变更空白检查 | PASS | `git diff --check`，本轮文件无输出 |
| Jenkins 可执行 lock 合同 | PASS | 本地 PowerShell 检查 `Jenkinsfile` 非注释行 |
| docs safety check | PASS | `harness/scripts/commands/safety-check.ps1 -Env real-pre -Scope docs -DryRun` |
| 文档 Pester | FAIL / 环境阻塞 | `safety-check-docs.Tests.ps1` 检测到工作区已有 `.env.real-pre` |
| Harness 限制 | FAIL / 现有工作区阻塞 | 最终检查显示 `harness/reports/current` 基线 78、当前 85；已有 `latest-sop-refactor-design.md` 470 行。本 evidence 文件使当前摘要数增加 1，未将其冒充为门禁通过 |
| 应用构建 | SKIP | 本轮仅流程、CI 和部署文档；且工作区已有冲突，未调用构建入口 |
| Docker 重启 | SKIP | 未修改应用代码，未触碰本地或远端容器 |
| 健康检查 | SKIP | 未执行部署或容器重启 |
| 业务验证 | SKIP | 未执行 test / real-pre E2E |
| 远端部署 | NO | 未使用 SSH、Jenkins 或远端发布队列 |

## 结论

`PARTIAL`。本轮已完成第一批低风险的文档和 CI 入口收敛，但不能声明完整工程交付：未完成跨 Jenkins、Break-glass、迁移和回滚入口的统一主机文件锁；未完成构建一次、按 digest 部署；未完成 Harness 验证与 Git 操作解耦；也未通过当前工作区的文档 Pester 和 Harness 限制门禁。

## Retro

可执行改进：为 `scripts/cd/` 建立统一的发布执行脚本和 host lock contract，并在 CI 中增加针对 Jenkins、Break-glass、迁移、回滚入口的行为级测试；验证方式是同一锁下启动两个入口，第二个必须阻塞或拒绝，同时检查失败恢复和 scheduler 恢复证据。责任人和排期尚未指定，因此本轮不生成独立 retro 文件。

## Git

- 未提交、未 push。
- 原因：当前分支上存在非本轮用户改动、未解决冲突，且上游分支已不存在；本轮不具备安全 Git 收尾条件。
