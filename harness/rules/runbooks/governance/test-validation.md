# Runbook: test validation

## 适用场景

执行构建、单元测试、集成测试、E2E、smoke、real-pre preflight 或权限验收。

## 前置检查

1. 读取 `docs/09-测试验收总览.md` 和对应 `docs/验收/*.md`。
2. 确认验证目标是 test/mock 还是 real-pre 真实上游。
3. 确认失败时需要保留日志、截图、trace、SQL/API 证据。

## 操作步骤

按任务选择最小充分命令：

```powershell
mvn -f backend/pom.xml test
npm --prefix frontend run build
npm --prefix frontend run test
npm run e2e:v1-p0
npm run e2e:real-pre:p0:preflight
npm run e2e:real-pre:p0
npm run e2e:real-pre:roles
```

代码修改优先走 `agent-do.ps1`，不要只手工跑单条命令后声明完成。

## 验证标准

- 命令真实执行，退出码和关键输出有记录。
- 失败项有复现路径和证据。
- real-pre 样本不足只能写 `PENDING` 或 `BLOCKED`。
- docs-only 只证明结构/安全，不证明业务闭环。

## 常见失败原因

- 容器刚启动未就绪导致 E2E auth 超时。
- test/mock 结果被误写成 real-pre 真实闭环。
- 只记录截图，不保留日志/API/SQL 证据。

## 禁止事项

- 不把 `PARTIAL` 写成 `PASS`。
- 不跳过失败项继续宣称完成。
- 不用手动刷新页面替代 E2E/接口验证。

## 产出物位置

- `runtime/qa/out/**`
- `playwright-report/**`
- `test-results/playwright/**`
- `harness/reports/current/latest-<report-key>.md`
