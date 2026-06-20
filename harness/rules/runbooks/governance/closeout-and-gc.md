# Runbook: closeout and garbage collection

## 适用场景

每次任务收尾、证据固化、状态更新、旧内容维护计划、归档或删除候选整理。

## 前置检查

1. 确认本轮是否修改代码、文档、脚本、配置或生成报告。
2. 读取 `harness/rules/policies/agent-contract.md`、`harness/rules/feedback/iteration.md`、`harness/rules/feedback/retire.md`。
3. 确认是否存在用户禁止提交/推送的明确要求。

## 操作步骤

1. 运行对应 Scope 的 `agent-do.ps1`；docs-only 使用 `-Scope docs`。
2. 若只需要旧内容候选，执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\retire-content.ps1 -Action Plan
```

3. 如需归档或删除，先生成 manifest，再显式执行 `Archive` 或 `Delete`。
4. 更新必要状态文档：`harness/rules/state/snapshots/01-当前项目状态.md`、`harness/rules/state/*.md`、`harness/rules/changelog.md`。
5. 最终报告列出已完成验证、未验证项和剩余风险。

## 验证标准

- evidence report 已生成。
- retro summary 已生成。
- 旧内容候选或“本轮无需 GC”的理由已记录。
- 没有把未执行构建/重启/E2E 写成通过。

## 常见失败原因

- 只改文档但没有运行 safety-check。
- 把 `retire-content` 计划误当成已归档/已删除。
- 没有记录本次暴露的 Harness 缺口。

## 禁止事项

- 不删除 `.env*`、密钥、证书、Docker Compose、Git 元数据、数据库 migration。
- 不移动不确定是否仍有价值的业务需求文档。
- 不覆盖历史 evidence report。

## 产出物位置

- `harness/reports/evidence-*.md`
- `harness/reports/retro-*.md`
- `harness/reports/content-retire-*.md`
- 必要时 `harness/archive/retired-content/**`
