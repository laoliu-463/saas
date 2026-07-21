# 后端变更

## 什么时候用

修改 `backend/`、后端接口、领域服务、权限或数据库访问时使用。

## 执行命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 inspect
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 verify
```

## 成功标准

后端相关编译、测试、静态检查和必要的接口验证通过；Evidence 与 CI 指向同一提交 SHA。

## 失败回滚

停止提交或部署，保留失败日志；代码回滚通过 PR 完成，远端环境按 [`rollback.md`](rollback.md) 处理。
