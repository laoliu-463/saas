# 后续 Agent 默认执行流程

## 1. 读文档

1. `harness/doc/00-HARNESS-README.md`
2. `AGENTS.md`
3. `CLAUDE.md`
4. `docs/README.md`
5. `harness/CURRENT_STATE.md`
6. `harness/TASK_ROUTING.md`
7. 任务对应领域、流程、接口、数据、权限、验收和部署文档

## 2. 判断任务类型

按 `harness/TASK_ROUTING.md` 判断：

- 默认环境：`Env=real-pre`
- 后端修复：`Scope=backend`
- 前端修复：`Scope=frontend`
- 全链路修复：`Scope=full`
- real-pre 排障：`Scope=full`
- 文档 / Harness 调整：`Scope=docs`
- `test`：只有用户明确要求或专项验证需要时显式指定
- 远端部署：只有用户明确要求时执行 `DeployRemote=true`

## 3. 修改前确认

- 当前现象和预期是否清楚。
- 是否有日志、接口、SQL、构建或运行证据。
- 是否涉及 V1 不做范围。
- 是否涉及 real-pre 安全边界。
- 是否存在用户未提交或无关变更。

## 4. 修改代码或文档

- 只做最小必要修改。
- 不凭空创建不存在的字段、接口、配置或业务规则。
- 发现旧文档冲突时登记，不自行裁决。

## 5. 构建

- `Scope=docs`：跳过构建，并在报告中说明。
- `Scope=backend`：执行后端构建。
- `Scope=frontend`：执行前端构建。
- `Scope=full`：后端和前端都构建。

## 6. 重启对应容器

通过 `harness/commands/restart-compose.ps1` 重启对应服务。real-pre 禁止使用 `down -v` 或删除 volume。

## 7. 健康检查

通过 `harness/commands/verify-local.ps1` 验证：

- 后端：`/api/system/health`
- test 前端：`/healthz`、`/favicon.svg` 或 `/`
- real-pre 前端：`/healthz`、`/login` 或 `/`

## 8. 业务验证

- 默认 real-pre：`npm run e2e:real-pre:p0:preflight`
- 显式 `-Env test`：`npm run e2e:v1-p0`
- 业务专项按领域 skill / eval / runbook 补充 API、SQL 或 E2E 证据。

## 9. 旧内容维护

生成 evidence report 前先执行旧内容维护：

- 默认：`retire-content.ps1 -Action Plan`，只生成候选清理报告。
- 归档：必须提供 manifest，目标进入 `harness/archive/retired-content/<timestamp>/`。
- 删除：必须提供 manifest；目录删除必须显式 `allowRecursive=true`。

## 10. 生成证据报告

必须生成 `harness/reports/evidence-*.md`，未采集项写明原因，不能编造。

## 11. 提交推送

如工作区存在无关变更，不能把无关文件一起提交。无法安全提交时，应在最终报告写明“未提交 / 阻塞原因”。

## 12. 远端部署

只有用户明确要求时执行远端部署。部署后必须检查远端 docker 状态、后端健康和前端健康。

## 13. 任务后复盘

生成 retro summary，判断是否需要更新：

- `harness/CURRENT_STATE.md`
- `harness/TASK_ROUTING.md`
- `harness/FORBIDDEN_SCOPE.md`
- `harness/HARNESS_CHANGELOG.md`
- P0/P1 台账
- 验收清单或脚本
