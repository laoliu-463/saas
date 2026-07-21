# 远端部署

## 什么时候用

只有候选变更已合并到 `main`，需要提升到 `release/real-pre` 并由 Jenkins 发布时使用。

## 执行命令

```text
提交 PR → 合并 main → 提升 release/real-pre → 进入 Jenkins saas-real-pre-cd 队列
```

普通 Agent 不执行 SSH、服务器现场构建或 `-DeployRemote true`。

## 成功标准

Jenkins 队列、构建 SHA、部署版本、锁、健康检查和验收结果可追溯；失败项保持 `FAIL` / `BLOCKED`。

## 失败回滚

停止重复发布，保留 Jenkins artifact 和运行状态；由值班人员按 [`rollback.md`](rollback.md) 使用同一发布入口回滚。
