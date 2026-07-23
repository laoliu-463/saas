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

变更范围的最小验证边界如下：

- `backend`：Node Harness 只执行一次 `mvn -f backend/pom.xml package`；不重建容器、不做健康检查、不跑跨服务业务 E2E。
- `frontend`：Node Harness 只执行前端依赖安装、typecheck 和测试；不重建容器、不做健康检查；前端生产构建由 Docker 镜像阶段唯一执行。
- `full`：执行后端、前端、Docker、健康检查和业务验证完整链路。
- `deploy`：只检查部署脚本、Compose 配置和 Jenkins release-queue 契约，不构建应用、不重启容器。
- `ci`：只运行 Harness Pester，不触发应用构建、Docker 构建或业务验证。
