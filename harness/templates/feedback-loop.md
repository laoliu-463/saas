# Feedback Loop

## 闭环

```text
任务完成
-> content retirement plan
-> evidence report
-> inline retro conclusion
-> 判断 Harness 是否暴露问题
-> 有可执行改进时生成 standalone retro
-> 如果暴露问题，升级对应 Harness 文件
-> 更新 HARNESS_CHANGELOG.md
-> 更新 CURRENT_STATE.md
-> 提交推送
```

## Closeout 检查

| 检查项 | 要求 |
| --- | --- |
| Scope | 明确本轮是 `docs`、`backend`、`frontend` 还是 `full` |
| 构建 / 重启 / 健康 | 代码修改必须执行；`Scope=docs` 必须明确跳过原因 |
| 业务验证 | 必须有脚本、API、SQL、日志或截图证据；样本不足不能写 PASS |
| Evidence | 覆盖 `harness/reports/current/latest-<report-key>.md` |
| Retro | 默认内联；仅有责任人、动作和验证方式时独立生成 |
| 状态更新 | 项目状态变化时更新 `CURRENT_STATE.md` 或 `state/*.md` |
| 旧内容维护 | 运行 `retire-content.ps1 -Action Plan` 或说明本轮无需 GC |

## 旧内容处理

旧内容处理按 `garbage-collection-policy.md` 执行：

- 保留当前事实主源和关键证据。
- 不确定的旧文档先归档，不直接删除。
- 删除必须有 manifest、路径检查和证据报告。
- 禁止删除 `.env*`、密钥、Compose、migration、源码和 Git 元数据。

## 触发 Harness 升级的情况

- Agent 重复试探命令。
- 脚本参数不兼容。
- 环境边界不清。
- evidence report 缺字段。
- eval 无法判定业务通过。
- runbook 无法让新人照做。
- docs 与源码事实冲突。
- 任务后遗留重复文档、旧代码、临时产物或过时证据。
