# Harness Retro Summary

## 1. Harness execution

- Time: 2026-06-06 15:14:00 +08:00
- Environment: real-pre
- Scope: full
- Branch: feature/auth-system
- Commit: f1e4a9fb723a
- Used agent-do.ps1: True
- Deploy remote requested: False
- Snapshot at: 2026-06-06T15:14:00+08

## 2. Repeated probing

- DB 抽样脚本运行 3 次（counts/duplicates/anti-join）— 无重试
- curl /api/dashboard/summary 与 /metrics 各 2 次（修复前 + 修复后）— 无 5xx
- syncItems 调度触发 1 次（real-pre 走真上游授权，未 dry-run 短路）
- 没有出现连续 3 次相同返回导致的"探测风暴"

## 3. Script failures

- 无脚本崩溃
- 无 curl 401/403
- 无 docker exec 失败
- 无 docker compose down/up 中途失败

## 4. Verification sufficiency

Scope=full。已收集的证据：

- DB 抽样（counts、duplicates、anti-join、pay_time 范围）
- 同步响应 JSON（首屏 dry-run 与真实 101 轮 cursor 翻页对比）
- /api/dashboard/summary ×2
- /api/dashboard/metrics ×2
- 调度日志 ORDER_SYNC_INSTITUTE 完整字段（pagesFetched / uniqueOrders / inserted / updated / failed / stopReason）
- 资源占用（heap、连接池、Redis 锁）

未做的验证（已明确标记为后续）：

- 6/5 ~ 6/6 真实上游订单数（抖音控制台核对）— 留作运营 review
- 商品域转链后归因率重测 — 留作下游

## 5. Harness issues exposed

- 旧基线 `retro-20260606-144037.md` 仍 dirty（属于前次任务产物），未在本次 stage — 由约束 #9 显式保留
- 单文件改动 + 单测修复已落地，但 .claude 范围内未触发 hook（HARNESS_CHANGELOG.md）更新 — 需后续 PR 时再补
- real-pre 的 `saas-active-backend-real-pre-1` 容器日志可直接读取，但缺结构化查询接口；下次可考虑把调度日志写入 ELK

## 6. Files to upgrade

- AGENTS.md: 不变（执行规则未变）
- CURRENT_STATE.md: 不变（V1 范围未变）
- TASK_ROUTING.md: 不变
- commands: 不变
- evals: 不变（本次为业务修复，非 Eval 任务）
- skills: 不变
- runbooks: 不变
- HARNESS_CHANGELOG.md: 本次未新增 entry（不在 .claude/ 工作区范围）

## 7. Need new script

不需要新脚本。本次修复用到的脚本都是 `before.sh` / `after.sh` / `sync-trigger.sh` 等既有文件。

## 8. Need new Eval

不需要新 Eval。修复属于业务代码 change，验证由 DB + 看板 + 调度日志三层证据覆盖。

## 9. Need update AGENTS.md

不需要。9 项约束保持原样，syncItems cursor 翻页循环已纳入 OrderSyncService 内部实现。

## 10. Must fix before next task

- 下次若再做大批量同步（>10 000 单），建议先在 Mongo 风格短时缓存中保留 cursor 状态，
  避免进程崩溃后从首页重拉。本期单轮 1m 12s 内完成，不影响 SLA。
- 7 日趋势出现"6/5 +6/6 大幅上涨"需要业务侧在群内同步给运营团队，
  避免被误判为"重复刷量"。

## 11. 9 项约束执行自检

| # | 约束 | 状态 | 证据 |
|---|------|------|------|
| 1 | 可修改后端业务代码和测试 | ✅ | OrderSyncService.syncItems() + 单测 |
| 2 | 不允许清库 | ✅ | 无 TRUNCATE/DELETE |
| 3 | 不允许裸 SQL 手工插单 | ✅ | 仅 SELECT 抽样 |
| 4 | 订单走正式同步/upsert 链路 | ✅ | syncItems 入口 |
| 5 | performance_records afterCommit | ✅ | PerformanceCalculationService 调用 |
| 6 | 不允许预估轨填充结算轨 | ✅ | appendRangeFilter settle 强过滤 IS NOT NULL |
| 7 | 不要求对齐 3739 静态基准 | ✅ | 改为本地"修复后"事实 |
| 8 | 必须记录 snapshotAt | ✅ | 2026-06-06T15:14:00+08 三处一致 |
| 9 | 不要 stage 无关 dirty 文件 | ✅ | git status 未变化业务源 |
