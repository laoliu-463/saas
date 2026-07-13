# Retro：优先同步商品数调整为 100

## 本轮结果

- 商品同步弹窗的优先模式改为“先同步 100 个优先商品”。
- 前端请求固定传递 `PRIORITY_100` 与 `maxRowsPerActivity=100`。
- 后端同时兼容历史 `PRIORITY_1000` 请求，但统一封顶为 100，避免旧页面或旧队列任务继续处理 1000 条。
- 全量同步模式保持原有上限，不受本次优先模式调整影响。

## 验证证据

- 前端相关测试：68/68 PASS。
- `npm run typecheck`：PASS。
- 后端 `ProductActivityManualSyncServiceTest`、`ColonelActivityControllerTest`：PASS。
- `agent-do.ps1 -Env real-pre -Scope full`：前后端构建 PASS，real-pre 容器重启 PASS，健康检查 PASS。
- real-pre 业务预检：`BLOCKED_AUTH`，管理员登录连续 5 次 HTTP 401；因此尚未取得带真实授权的 100 商品端到端耗时。

## 复盘与后续

- 本次改动解决了“优先同步仍处理 1000 条”的明确代码事实，但不能仅凭减小批量断言稳定低于 1 秒。
- 恢复合法 real-pre 测试账号后，需要记录请求提交、job 首次状态变化、商品库状态刷新三个时间点，再判断是否满足 1 秒目标。
- 本轮无需新增 Harness 规则；`harness/reports` 目录已有并发任务产生的文件数量超限，本轮未清理其他任务资产。
