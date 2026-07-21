# real-pre 验证

## 什么时候用

需要真实上游、真实数据、远端预发布或发布前闭环验收时使用。

## 执行命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 inspect
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 verify -TargetEnv real-pre
```

## 成功标准

真实上游开关、授权、样本、数据库状态、健康检查和业务闭环均有证据；缺失条件写 `BLOCKED` / `PENDING`。

## 失败回滚

停止验证升级，保留 evidence、容器日志和请求摘要；远端动作只交给 Jenkins，异常按 [`rollback.md`](rollback.md) 处理。
