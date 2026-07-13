# 现有脚本与命令索引

## Harness 固定入口

下表为简写；在仓库根通过 `.\harness\scripts\commands\agent-do.ps1` 调用。`ReportKey` 必须稳定，`OwnedFiles` 使用分号分隔仓库相对路径。

| 命令 | 用途 | 适用环境 | 是否安全 | 是否改数据 | real-pre 是否允许 | 证据输出 |
| --- | --- | --- | --- | --- | --- | --- |
| `agent-do.ps1 -Env real-pre -Scope docs -ReportKey task-key -OwnedFiles 'path1;path2' -Message "docs: update harness"` | docs-only 总入口 | real-pre | 安全检查后执行 | 不改业务数据 | 可用于 docs scope | `reports/current/latest-task-key.md` |
| `agent-do.ps1 -Env real-pre -Scope backend -ReportKey task-key -OwnedFiles 'path1;path2' -Message "fix: backend"` | 后端构建、重启、健康检查、验证 | real-pre | 会重启服务 | 不应清库；验证以 preflight 为默认 | 默认允许，但必须通过 real-pre 安全检查 | 稳定 evidence |
| `agent-do.ps1 -Env real-pre -Scope frontend -ReportKey task-key -OwnedFiles 'path1;path2' -Message "fix: frontend"` | 前端构建、重启、健康检查、验证 | real-pre | 会重启服务 | 不改业务数据 | 默认允许，但必须通过 real-pre 安全检查 | 稳定 evidence |
| `agent-do.ps1 -Env real-pre -Scope full -ReportKey task-key -OwnedFiles 'path1;path2' -Message "fix: real-pre"` | real-pre 全链路本地验证 | real-pre | 高风险，需安全检查 | 默认 preflight 只读为主；专项 E2E 可能产生真实上游副作用 | 默认允许，但远端部署仍需用户明确要求 | 稳定 evidence、runtime/qa/out |
| `agent-do.ps1 -Env real-pre -Scope apifox -ReportKey task-key -OwnedFiles 'path1;path2' -Message "chore: apifox harness"` | Apifox/OpenAPI 本地 harness | real-pre | 安全检查后执行 | 不改业务数据，不导入云端 | 允许；跳过业务容器重启 | 稳定 evidence |

## 子脚本

| 脚本 | 用途 | 适用环境 | 是否安全 | 是否改数据 | real-pre 是否允许 | 证据输出 |
| --- | --- | --- | --- | --- | --- | --- |
| `harness/scripts/check-ddd-acceptance.ps1` | DDD 收口聚合验收：dirty、白名单、矩阵、架构测试、安全检查、报告 | local / real-pre docs | 安全 | 不改业务数据 | 允许 | `harness/reports/latest-ddd-acceptance-report.md` |
| `scripts/verify-openapi-apifox.sh` | Apifox/OpenAPI 本地校验：OpenAPI 内容、CLI、开发入口、secret scan、dry-run 保护 | local | 安全 | 不改业务数据，不访问云端 | 允许 | 控制台输出；agent-do 写 evidence |
| `scripts/sync-apifox.sh` | Apifox 云端同步：开发入口、targetBranchId、import counters、endpoint/environment 回读 | local | 访问 Apifox 云端 | 不改业务数据；会改 Apifox 项目 | 仅用户明确要求且凭证齐备时执行 | `harness/reports/apifox-*.json/log` |
| `harness/scripts/commands/safety-check.ps1` | 检查敏感文件、real-pre 开关、危险命令引用 | test / real-pre | 安全 | 不改数据 | 允许，建议先跑 | 控制台输出 |
| `harness/scripts/commands/restart-compose.ps1` | 按 env/scope 执行 `docker compose up -d --build` | test / real-pre | 会重启容器 | 不应清库；real-pre 禁止 down -v | 允许但必须有任务理由 | compose ps |
| `harness/scripts/commands/verify-local.ps1` | 本地后端 / 前端健康检查 | test / real-pre | 安全 | 不改数据 | 允许 | HTTP 响应 |
| `harness/scripts/commands/collect-evidence.ps1` | 生成/覆盖稳定 evidence | test / real-pre | 安全 | 写报告文件 | 允许 | `harness/reports/current/latest-<key>.md` |
| `harness/scripts/commands/retire-content.ps1` | 旧内容维护计划、按 manifest 分组归档或删除 | local | Plan 安全；Archive/Delete 有风险 | Archive/Delete 会移动或删除文件 | real-pre 只影响仓库文件，不碰数据；删除需 manifest | `harness/reports/current/latest-content-retire.md` |
| `harness/scripts/commands/new-retro.ps1` | 仅为可执行改进生成独立复盘 | test / real-pre | 安全 | `-Actionable` 时写报告 | 允许 | `harness/reports/current/latest-retro-<key>.md` |
| `harness/scripts/commands/deploy-remote.ps1` | 远端 real-pre 部署 | real-pre | 高风险 | 会更新远端服务 | 仅用户明确要求时允许 | 远端 docker ps、健康检查 |
| `harness/scripts/commands/git-push-safe.ps1` | 只暂存 OwnedFiles、敏感检查、commit、push 当前 upstream | 本地 | 会改 Git 状态 | 不改业务数据 | 不涉及环境 | Git 输出 |

## 项目构建与验收命令

| 命令 | 用途 | 适用环境 | 是否安全 | 是否改数据 | real-pre 是否允许 | 证据输出 |
| --- | --- | --- | --- | --- | --- | --- |
| `cd backend; mvn test` | 后端单元 / 集成回归 | local / test | 安全 | 可能使用测试库 | 不直接证明 real-pre | Maven 输出 |
| `cd backend; mvn -DskipTests package` | 后端打包 | local | 安全 | 不改业务数据 | 可作为部署前构建 | Maven 输出 |
| `cd frontend; npm run build` | 前端类型检查和生产构建 | local | 安全 | 不改业务数据 | 可作为部署前构建 | Vite 输出 |
| `npm run e2e:v1-p0` | test/mock V1 P0 验收 | test | 安全 | 可能写测试数据 | 不能证明 real-pre | Playwright 报告 |
| `npm run e2e:real-pre:p0:preflight` | real-pre 预检 | real-pre | 只读为主 | 不应写业务数据 | 允许 | `runtime/qa/out/*preflight*` |
| `npm run e2e:real-pre:p0` | real-pre P0 验收 | real-pre | 高风险 | 可能触发真实上游或业务写入 | 仅任务需要时允许 | `runtime/qa/out/*real-pre-p0*` |
| `npm run e2e:real-pre:roles` | real-pre 角色与权限验证 | real-pre | 中风险 | 可能写业务操作日志 | 仅任务需要时允许 | `runtime/qa/out/*role*` |

## 启停脚本

| 命令 | 用途 | 适用环境 | 风险 |
| --- | --- | --- | --- |
| `npm run start:test` | 启动 test | test | 会启动 / 重建 test Compose |
| `npm run start:real-pre` | 启动 real-pre | real-pre | 必须保持真实开关，禁止 mock |
| `npm run stop` | 停止本地服务 | test / real-pre | 必须确认脚本未执行 down -v |

## runtime/qa 入口

`runtime/qa/` 下已有 real-pre、权限、业务流、dashboard、mock audit 等脚本。新增验证优先复用已有脚本，证据目录一般进入 `runtime/qa/out/`。

## 旧内容维护命令

```powershell
# 默认只生成候选计划，不移动、不删除
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\retire-content.ps1 -Action Plan

# 按 manifest 归档
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\retire-content.ps1 -Action Archive -Manifest .\harness\reports\retire-manifest-example.json

# 按 manifest 删除；目录项必须 allowRecursive=true
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\retire-content.ps1 -Action Delete -Manifest .\harness\reports\retire-manifest-example.json
```
