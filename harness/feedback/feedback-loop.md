# Feedback Loop

## 闭环

```text
任务完成
-> content retirement plan
-> evidence report
-> retro summary
-> 判断 Harness 是否暴露问题
-> 如果暴露问题，升级对应 Harness 文件
-> 更新 HARNESS_CHANGELOG.md
-> 更新 CURRENT_STATE.md
-> 提交推送
```

## 触发 Harness 升级的情况

- Agent 重复试探命令。
- 脚本参数不兼容。
- 环境边界不清。
- evidence report 缺字段。
- eval 无法判定业务通过。
- runbook 无法让新人照做。
- docs 与源码事实冲突。
- 任务后遗留重复文档、旧代码、临时产物或过时证据。
