# 前端变更

## 什么时候用

修改 `frontend/`、页面交互、状态展示或前端测试时使用。

## 执行命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 inspect
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 verify
```

## 成功标准

前端类型检查、构建、相关测试和必要的浏览器验证通过；未适用的后端或 E2E 检查明确标记 `NOT_REQUIRED`。

## 失败回滚

停止发布，保留浏览器和 CI 证据；通过 PR 恢复前端变更，不直接修改远端容器文件。
