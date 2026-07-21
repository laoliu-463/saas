# 本地环境

## 什么时候用

开发者在本机运行 inspect、verify 或定位环境问题时使用。

## 执行命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 inspect
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 verify
```

## 成功标准

工具链、配置敏感性、Git 工作区和适用检查有明确结果；本地证据写入 `runtime/qa/out/`。

## 失败回滚

停止后续命令，修复本地配置或依赖后重试；不得把本地配置文件提交到仓库。
