# Runbook: closeout and garbage collection

## 适用场景

每次任务收尾、证据固化、状态更新、旧内容维护计划、归档或删除候选整理。

## 前置检查

1. 确认本轮是否修改代码、文档、脚本、配置或生成报告。
2. 读取 `docs/harness-maintenance/legacy-rules/policies/agent-contract.md`、`docs/harness-maintenance/legacy-rules/feedback/iteration.md`、`docs/harness-maintenance/legacy-rules/feedback/retire.md`。
3. 确认是否存在用户禁止提交/推送的明确要求。

## 操作步骤

1. 运行对应 Scope 的 `agent-do.ps1`；必须提供稳定 `-ReportKey` 和显式 `-OwnedFiles`，docs-only 使用 `-Scope docs`。
2. 若只需要旧内容候选，执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\retire-content.ps1 -Action Plan
```

3. 如需归档或删除，先生成 manifest，再显式执行 `Archive` 或 `Delete`。
4. 更新必要状态文档：`docs/harness-maintenance/legacy-rules/state/snapshots/01-当前项目状态.md`、`docs/harness-maintenance/legacy-rules/state/*.md`、`docs/harness-maintenance/legacy-rules/changelog.md`。
5. 最终报告列出已完成验证、未验证项和剩余风险。

## 验证标准

- 稳定 evidence 已生成并包含内联 retro 结论。
- 只有存在可执行改进时才生成独立 retro。
- 旧内容候选或“本轮无需 GC”的理由已记录。
- 没有把未执行构建/重启/E2E 写成通过。

## 常见失败原因

- 只改文档但没有运行 safety-check。
- 把 `retire-content` 计划误当成已归档/已删除。
- 没有记录本次暴露的 Harness 缺口。

## 禁止事项

- 不删除 `.env*`、密钥、证书、Docker Compose、Git 元数据、数据库 migration。
- 不移动不确定是否仍有价值的业务需求文档。
- 不在 `reports/` 根生成时间戳 evidence/retro/content-retire。

## 产出物位置

- `runtime/qa/out/latest-<report-key>.md`
- 可选 `runtime/qa/out/latest-retro-<report-key>.md`
- `runtime/qa/out/latest-content-retire.md`
- 必要时 `Git history/retired-content/**`
