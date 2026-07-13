# Skill: ddd-post-task-sync

## 使用场景

用于 DDD 任务完成或失败后的 state、feedback、evidence 和 retro 同步。

## 必读文件

- `harness/rules/policies/agent-contract.md`
- `harness/rules/state/snapshots/DOMAIN_STATUS.md`
- `harness/templates/feedback-loop.md`
- `harness/rules/skills/workflow/evidence-report.skill.md`

## 标准流程

1. 更新 `DOMAIN_STATUS.md` 中对应领域的当前状态、已完成能力、待优化能力和下一步。
2. 如果发现阻塞、失败或新风险，写入稳定 evidence；需持续跟踪时登记 `KNOWN_ISSUES.md`。
3. 运行当前 Scope 固定验证。
4. 生成 evidence report。
5. 在 evidence 中写 retro 结论；只有可执行改进才生成独立 retro。
6. 未验证项写 `PENDING` / `BLOCKED` / `PARTIAL`，不得写 `PASS`。

## 输出格式

```md
任务卡：
状态更新：
验证结果：
Evidence：
Retro：
剩余风险：
下一步：
```
