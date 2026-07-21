# Gate 0 CI/CD 收敛 evidence

## Metadata

- 生成时间：2026-07-21 Asia/Shanghai
- 任务：CI/CD、real-pre 发布入口和 Harness no-regression 收敛
- 环境：本地 clean worktree + GitHub Actions；未部署远端 real-pre
- 分支：`chore/gate0-ci-cd-convergence`
- 实现提交：`8060a4f9` (`ci(gate0): converge CI and release entrypoints`)
- Pull Request：[PR #208](https://github.com/laoliu-463/saas/pull/208)
- CI run：[29817421336](https://github.com/laoliu-463/saas/actions/runs/29817421336)
- CI Gate：[job 88592107970](https://github.com/laoliu-463/saas/actions/runs/29817421336/job/88592107970)
- Evidence commit：本文件提交后生成；本报告验证的实现提交为 `8060a4f9`

## Scope

- 统一 `main -> PR + CI -> release/real-pre -> Jenkins` 流程。
- 取消任务分支 Push CI，增加变更范围检测和稳定 `CI Gate`。
- 旧 Gitee、直接 SSH、现场构建降级为 Break-glass。
- Jenkins 发布阶段使用可执行 `lock(resource: 'saas-real-pre-deploy')` 合同。
- Harness 输出 `HARNESS_NO_REGRESSION`，历史债务只保留 `REPOSITORY_HEALTH=PARTIAL`。
- docs safety 测试改为检查 `.env.real-pre` 是否被 Git 跟踪并被忽略，不依赖本地文件是否存在。

## Verification

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 29 个治理 Pester | PASS | 本地 `Invoke-Pester`：29 passed / 0 failed |
| CI YAML 解析 | PASS | PyYAML `safe_load` |
| `git diff --check` | PASS | 实现提交无空白错误 |
| docs safety | PASS | `safety-check.ps1 -Env real-pre -Scope docs -DryRun` |
| Harness no-regression | PASS | `TASK_GATE=PASS`、`HARNESS_NO_REGRESSION=PASS` |
| Jenkins active lock contract | PASS | 非注释行包含 `lock(resource: 'saas-real-pre-deploy')` |
| GitHub Changes | PASS | CI job `88591922304` |
| GitHub Repository governance | PASS | CI job `88591956804` |
| GitHub CI Gate | PASS | CI job `88592107970` |
| Backend / Frontend | NOT_REQUIRED | 变更范围仅流程、文档、CI 和 Harness 治理 |
| 应用构建 / 容器重启 / 健康检查 / E2E | NOT_REQUIRED | 本 PR 未修改应用业务代码，未接触 real-pre |
| 远端部署 | NO | 未使用 SSH、Jenkins 发布队列或数据库操作 |

## Conclusion

`PASS`（仅 Gate 0）。本 PR 已从最新 `origin/main` 建立干净分支，工作区无冲突，PR 已建立且远端稳定 `CI Gate` 通过。它不表示 Gate 1、Gate 2 或 Gate 3 已完成。

## Remaining Risks / Next Gates

- Gate 1：Jenkins、Break-glass、数据库迁移和回滚入口仍需统一竞争主机级 `flock`，并验证中断释放锁和 scheduler 恢复。
- Gate 2：仍是 Jenkins 现场构建镜像，尚未切换为构建一次、按 digest 部署。
- Gate 3：Harness 的 verify、evidence、git commit/push、release verify 尚未完全解耦。
- 历史 Harness 报告债务仍使 `REPOSITORY_HEALTH=PARTIAL`，本 PR 未扩大该债务。

## Retro

下一步应由发布工程负责人建立 `scripts/cd/with-real-pre-lock.sh`，让 Jenkins、Break-glass、迁移和回滚调用同一主机锁，并用并发测试证明第二入口被阻塞或拒绝；本 PR 不提前实现该 Gate 1 变更。
