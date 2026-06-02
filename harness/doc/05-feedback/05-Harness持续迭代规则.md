# Harness 持续迭代规则

## 触发条件

| 现象 | 应更新位置 |
| --- | --- |
| AI 重复问已知信息 | `harness/CURRENT_STATE.md` 或 `harness/doc/04-state/` |
| AI 不知道任务该走哪个 scope | `harness/TASK_ROUTING.md` 或 `harness/doc/02-tools/02-后续Agent默认执行流程.md` |
| AI 漏构建 | `harness/AGENT_CONTRACT.md`、`harness/doc/01-instructions/01-项目执行协议.md` |
| AI 漏重启 | `harness/commands/restart-compose.ps1`、执行协议 |
| AI 漏验证 | `harness/evals/`、`harness/doc/05-feedback/02-业务闭环验证清单.md` |
| AI 误判 V1 / V2 | `harness/FORBIDDEN_SCOPE.md`、`harness/doc/01-instructions/04-文档优先级与冲突处理.md` |
| AI 不知道环境信息 | `harness/environment/`、`harness/doc/03-environment/` |
| AI 发现文档冲突 | `harness/doc/04-state/05-文档债务与冲突台账.md`、必要时 ADR-002 |
| AI 完成任务后遗留重复旧内容 | `harness/commands/retire-content.ps1`、`harness/doc/05-feedback/06-旧内容生命周期规则.md` |
| evidence report 缺字段 | `harness/feedback/evidence-report-template.md`、`collect-evidence.ps1` |
| runbook 无法照做 | `harness/runbooks/` |

## 更新原则

- 先补事实，再补规则。
- 能通过脚本验证的，不只写自然语言。
- 只把已验证事实写成结论。
- 旧文档冲突先登记，不直接删除。
- 旧内容清理先生成计划，再按 manifest 归档或删除。
- 更新 Harness 行为时同步考虑 `HARNESS_CHANGELOG.md`。

## 每次任务后必须判断

- 是否需要更新当前状态。
- 是否需要更新 P0/P1 台账。
- 是否需要新增或调整 eval。
- 是否需要新增 runbook。
- 是否需要更新禁止范围。
- 是否需要更新工具索引。
- 是否需要归档或删除旧内容。
