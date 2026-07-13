# Harness 冗余清理 Retro

> 生成时间：2026-07-13 22:13 +08:00
> 任务：HARNESS-REDUNDANCY-CLEANUP-20260713

## 结论

本轮需要 Harness 升级，已完成“主源收敛、当前引用校正、retire 债务扫描恢复”；报告根目录治理和安全 Git 提交仍需独立任务。

## 发现

- 重复内容主要来自目录迁移后旧文件未删除，而不是缺少新规则。
- `.claude/hooks/*.md` 只是文档清单，并未在运行时注册，却被多个入口当作强制 Hook。
- `retire-content.ps1` 的旧债务目录不存在；改为中文文件名字面量后又暴露 Windows PowerShell 5.1 编码问题。
- `agent-do.ps1 -DryRun` 会正确暴露全工作区文件，验证了 DEBT-026 在 dirty 工作区中的实际风险。

## 已采取措施

- 只删除存在明确替代主源且引用已迁移的文件。
- 删除动作记录在单一 manifest，不处理业务源码和并发报告。
- 脚本使用 ASCII 目录和通配文件名，避免依赖 BOM 或系统代码页。
- 本轮 evidence 放入 `reports/current/`，避免继续增加报告根目录文件。

## 后续 Harness 任务

1. 修复 DEBT-026：提交脚本必须接收显式 allowlist，遇到 unknown dirty 立即停止。
2. 修复 reports retention：以 run-id 或归档包收口时间戳报告。
3. 修复 DEBT-027：超长报告归档前先打包或生成摘要，不原样迁移。
