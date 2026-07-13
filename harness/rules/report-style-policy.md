# Harness 报告风格政策

> 适用于当前 evidence、retro、GC、审计和 Session Exit 报告。单文件不得超过 200 行。

## 必须内容

报告首部写明：

- 生成时间。
- 环境与任务范围。
- 分支和 commit。
- 关联任务、manifest 或 run-id。

正文固定包含：

1. **结论**：`PASS`、`PARTIAL`、`BLOCKED` 或 `FAIL`。
2. **关键结果**：构建、测试、健康检查和业务验证的客观结果。
3. **变更或处置**：修改、归档、删除及替代路径。
4. **风险与下一步**：未采集项、阻塞原因和剩余风险。
5. **证据**：命令、接口、SQL、日志或 QA 输出路径。

## 证据要求

- 未执行的检查必须写“未执行”和原因。
- `BLOCKED`、`PENDING`、`PARTIAL` 不得写成 `PASS`。
- 不用“已完成”“应该没问题”替代命令结果。
- 长日志、长 SQL、长 JSON 不粘贴到报告，使用可追溯路径。
- 报告必须绑定本次任务的最终 commit；提交前报告应明确标记为 pre-commit。

## 命名与位置

- 当前摘要：`reports/latest-<topic>.md`。
- 任务原始证据：`runtime/qa/out/<run-id>/`。
- 历史报告：`archive/<date>/<topic>/`。
- 禁止在 `reports/` 根长期保留大量时间戳 evidence、retro 和 content-retire。

## 反模式

- 重复规则或聊天过程。
- 没有证据的结论。
- 多任务混成一份报告。
- 占位符、空章节或未解释的 `SKIP`。
