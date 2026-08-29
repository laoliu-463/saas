# Harness 冗余清理 Evidence

> 生成时间：2026-07-13 22:13 +08:00
> 环境：本地 real-pre
> Scope：docs / Harness
> 分支：codex/ddd-user-role-application
> 实现 commit：7ca6d5ff
> 任务：HARNESS-REDUNDANCY-CLEANUP-20260713

## 结论

`PARTIAL`。本轮确认删除 26 个已被主源替代、未接入运行时或零引用的文件，并修正当前入口路径和 `retire-content.ps1` 债务扫描。提交后审计时 `harness/reports` 根目录有 89 个直接文件，超过 50 文件限制；这些文件包含并发任务产物，本轮未擅自清理。

## 关键结果

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 删除 manifest | PASS | `harness/manifests/harness-redundancy-cleanup-20260713.json` 可解析 |
| 删除目标残留 | PASS | manifest 中 26 个目标 `existing_deleted_targets=0` |
| 当前入口引用 | PASS | 被删除文件引用 `NO_MATCH` |
| 旧路径扫描 | PASS | 仅 changelog/ADR 的明确历史记录仍保留旧路径 |
| 本轮修改文本行数 | PASS | 本轮现存修改文件均不超过 200 行 |
| docs 安全检查 | PASS | `agent-do.ps1 -Scope docs -DryRun` exit 0，Safety check passed |
| retire 自动候选 | PASS | dry-run 能读取 `snapshots/05-*.md` 并列出 5 个文档债务候选 |
| Git diff 格式 | PASS | `git diff --check` exit 0 |
| Harness 全局结构 | FAIL | `harness/reports` 直接文件 89；并发生成的 `evidence-20260713-221229.md` 为 283 行 |

## 主要变更

- 结构、保留、报告政策各收敛为一个主源。
- test、real-pre、remote real-pre、Compose 地图各保留一个环境入口。
- 删除 9 份重复治理说明和旧 Agent workflow。
- 删除 6 份未在 `.claude/settings.json` 注册的伪 Hook 文档。
- 删除过期状态索引、旧文档风格指南和 legacy 脚本 README。
- 当前规则、模板和 runbook 统一到 `harness/rules/` 与 `harness/scripts/commands/`。
- `retire-content.ps1` 改用 ASCII 路径和 `05-*.md` 过滤，兼容 Windows PowerShell 5.1。

## 未执行项

- 构建、容器重启、HTTP 健康检查、业务 E2E：Scope=docs，不适用。
- 远端部署：用户未要求，未执行。
- `agent-do.ps1` 非 DryRun：未执行，因为 DEBT-026 会把并发任务 dirty 全量暂存和推送。

## 风险与下一步

- `harness/reports` 根目录数量超限，必须由当前报告清理任务按归属和 retention manifest 收口。
- 工作区存在并发前端修改和其他报告产物，本轮提交必须使用显式文件清单。
- DEBT-026、DEBT-027 未在本轮修复，不能把 Harness 总体验证写成 PASS。
