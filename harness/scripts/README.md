# Scripts

本目录是机器执行区。普通开发者只使用 `run.ps1 inspect` 和 `run.ps1 verify`；其余脚本由 Harness、CI 或维护者调用。

## 公开入口

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 inspect
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\run.ps1 verify
```

## 内部层次

- `commands/`：PowerShell 工作流、evidence、Git 和安全检查。
- `lib/node/`：Node / TypeScript Harness 核心实现。
- `lib/contracts/`：机器可读 JSON Schema 和环境契约。
- `probes/`：只读诊断脚本。
- `tests/`：PowerShell 与 Node Harness 自测。

## 维护规则

- 脚本必须返回可判断的退出码，并在失败时给出根因、可安全重试动作和停止条件。
- 运行产物写入 `runtime/qa/out/`，不得写回 `harness/`。
- 远端部署只能进入 Jenkins 发布队列；脚本不得新增直接 SSH 发布入口。
- 修改脚本后运行 Harness 自测、PowerShell 语法检查和必要的 CI Gate。
