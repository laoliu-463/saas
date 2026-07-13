# Harness 分层文件门禁设计证据

## 元数据

- 时间：2026-07-13 22:30:18 +08:00
- 环境：本地 `real-pre`
- Scope：`docs`
- 分支：`codex/ddd-user-role-application`
- 设计提交：`53ccc9df`
- 远端部署：未执行，文档设计不涉及部署

## 结论

`PARTIAL`

分层文件门禁设计已批准、写入 ADR 并提交；门禁脚本、报告生成流程和历史报告清理尚未实施，因此不得把目标状态写成已落地。

## 本次文件

- `docs/决策/ADR-013-Harness分层文件门禁.md`
- `docs/README.md`
- 本 evidence

工作区同时存在其他任务的后端、前端和报告变更，本次提交未暂存或修改这些文件。

## 验证结果

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| ADR 状态 | PASS | 标记“已批准设计，尚未实施” |
| ADR 行数 | PASS | 134 行，小于 200 |
| 占位符扫描 | PASS | `TBD`、`TODO`、`待定`、`占位` 命中 0 |
| 设计完整性 | PASS | 包含备选方案、边界、失败处理、实施范围和验收场景 |
| 文档地图链接 | PASS | `docs/README.md` 指向 ADR，目标存在 |
| Git 差异检查 | PASS | `git diff --cached --check` 无输出 |
| `Scope=docs` 安全检查 | PASS | `agent-do.ps1 -DryRun` 返回 0，Safety check passed |
| 构建、容器、健康检查 | 未执行 | `Scope=docs` 按规则跳过 |
| 业务验证 | 不适用 | 本次未修改业务代码或业务规则 |
| 提交钩子 | PASS | 提交 `53ccc9df` 成功 |
| code-review-graph | PASS | 0 个受影响流程、0 个测试缺口、风险分数 0.00 |

## 执行入口偏离说明

已先运行唯一入口的 `Scope=docs` dry-run。结果显示当前 `git-push-safe.ps1` 会把整个脏工作区列入待暂存集合，其中包含其他并发任务文件。为避免误提交，正式提交没有运行非 dry-run 的 `agent-do.ps1`，而是显式暂存上述两份设计文档。

该风险已写入 ADR：后续实现必须让 `agent-do.ps1` 和 `git-push-safe.ps1` 接收任务拥有的显式文件集合。

## Retro 摘要

- 暴露问题：统一 50/50/200 与时间戳报告生成冲突；`agent-do` 默认接管整个脏工作区。
- 已固化改进：分层门禁、基线感知、稳定报告路径、evidence/retro 合并、显式文件所有权。
- 本次不实施脚本：按照设计评审门禁，先等待用户复核 ADR，再进入实施计划。
- 无需修改业务 AGENTS 或领域文档；需要实施后再更新 Harness 规则、脚本和债务状态。

## 剩余风险

- `harness/reports/` 根目录仍超过现行 50 文件限制。
- 当前检查器尚不能区分历史债务与本次新增债务。
- evidence、retro、content-retire 仍会向根目录生成时间戳文件。
- 非 dry-run 的 `agent-do` 在并发脏工作区仍有误提交风险。
