# real-pre 环境

## 什么时候用

需要真实上游或发布前验证时使用；普通开发任务不因日常流程要求而直接操作远端 real-pre。

## 执行命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 inspect -TargetEnv real-pre
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 verify -TargetEnv real-pre
```

## 成功标准

真实开关、授权、样本、数据库、健康和业务闭环均有证据，远端部署来自 Jenkins 队列。

## 失败回滚

停止操作并标记 `BLOCKED` / `FAIL`；保留现场，禁止清库、删 volume、切 mock 或绕过 Jenkins。
