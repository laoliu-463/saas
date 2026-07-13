# Harness 持续迭代规则

## 触发条件

| 现象 | 唯一更新位置 |
| --- | --- |
| Agent 重复询问已知事实 | `harness/rules/state/snapshots/01-当前项目状态.md` |
| 不知道任务 Scope | `harness/rules/governance/task-routing.md` |
| 漏构建、重启或验证 | `harness/rules/policies/agent-contract.md` 或对应脚本 |
| 误判阶段范围 | `docs/决策/ADR-010-仓库阶段口径拍板为V2.md`、`harness/rules/governance/forbidden-scope.md` |
| 环境入口不清 | `harness/rules/environment/README.md` |
| 文档冲突 | `harness/rules/state/snapshots/05-文档债务与冲突台账.md`，必要时补 ADR |
| 旧内容堆积 | `harness/rules/feedback/retire.md`、`harness/scripts/commands/retire-content.ps1` |
| evidence 字段不足 | `harness/templates/evidence-report-template.md` 或 `collect-evidence.ps1` |
| runbook 无法执行 | 修正对应 `harness/rules/runbooks/` 文件并登记 debt |

## 更新原则

- 先补事实和证据，再补规则。
- 能通过脚本验证的约束必须机械执行。
- 同一事实只保留一个主源，其他入口只链接。
- 清理前生成 manifest；清理后执行结构和引用检查。
- Harness 行为变化记录到 `harness/rules/changelog.md`。
