# dashboard metric copy fix

- 时间：2026-06-09 13:24
- 环境：本地 real-pre
- Scope：frontend
- 分支：feature/auth-system
- 基线 commit：7f72e51c DDD-BASE-001 add refactor safety toggles
- 远端部署：否
- 结论：PASS

## 修改前问题

1. `frontend/src/views/data/OrderList.vue` 中 `timeFieldOptions` 把 `createTime` 显示为“付款时间”。
2. 同文件 `activeTimeTitle` 在 `createTime` 下显示“付款时间”，导致汇总维度和表格首列也误导为付款口径。
3. `frontend/src/views/data/index.vue` 顶部创建轨卡片已有“创建轨”标签，但缺少“不能与付款/退款相减对账”的显式说明。

## 修改后文案

1. 订单汇总页时间筛选：
   - `createTime`：创建时间
   - `settleTime`：结算时间
2. 订单汇总页汇总维度：
   - `createTime`：创建时间按日
   - `settleTime`：结算时间按日
3. 数据看板创建轨说明：
   - 按订单创建时间统计，仅统计有效订单，不等于付款订单额 - 退款订单额；付费/退款为状态或付款退款口径，与创建轨卡片不可直接相减对账。

## 字段绑定确认

| 文件 | 字段 / 状态 | 展示文案 | 结论 |
| --- | --- | --- | --- |
| `frontend/src/views/data/OrderList.vue` | `timeField=createTime` | 创建时间 | PASS |
| `frontend/src/views/data/OrderList.vue` | `timeField=settleTime` | 结算时间 | PASS |
| `frontend/src/views/data/index.vue` | `/dashboard/metrics?timeField=createTime` | 今日订单数（创建轨）、今日 GMV（创建轨） | PASS |
| `frontend/src/views/data/index.vue` | `amountTrack=estimate` | 今日服务费净收·预估金额、今日提成·预估金额 | PASS |

## 测试结果

- TDD RED：`npm --prefix frontend run test -- OrderList.test.ts index.test.ts` 首次失败 2 项，失败点分别为旧“付款时间”和缺失创建轨说明。
- TDD GREEN：`npm --prefix frontend run test -- OrderList.test.ts index.test.ts` 通过，4 test files / 58 tests PASS。
- 前端构建：`npm --prefix frontend run build` PASS；仅存在既有 Vite chunk size warning。
- 安全检查：`safety-check.ps1 -Env real-pre -Scope frontend -DryRun` PASS。
- diff 空白检查：`git diff --check -- frontend/src/views/data/OrderList.vue frontend/src/views/data/OrderList.test.ts frontend/src/views/data/index.vue frontend/src/views/data/index.test.ts` PASS。

## Docker 与页面验证

- `restart-compose.ps1 -Env real-pre -Scope frontend` PASS。
- `verify-local.ps1 -Env real-pre -Scope frontend` PASS，`http://127.0.0.1:3001/healthz` 返回 200。
- `npm run e2e:real-pre:p0:preflight` PASS，证据目录：`runtime/qa/out/real-pre-preflight-20260609-132148`。
- 页面 smoke PASS，结果：`runtime/qa/out/dashboard-metric-copy-fix-20260609T052324/result.json`。
- 页面 smoke 截图：
  - `runtime/qa/out/dashboard-metric-copy-fix-20260609T052324/dashboard-create-track-copy.png`
  - `runtime/qa/out/dashboard-metric-copy-fix-20260609T052324/orders-create-time-copy.png`
- 页面 smoke 记录到 Google Fonts CSP 和页面跳转 `ERR_ABORTED`，已按既有外部字体 / 导航噪声记录，`criticalFailedRequests=[]`。

## 写库 / 重算 / 部署

- 是否写库：否。
- 是否重算：否。
- 是否修改后端 SQL：否。
- 是否修改订单同步：否。
- 是否新建 `agg_daily_performance_create`：否。
- 是否远端部署：否。

## Harness 执行说明

本轮没有直接执行 `agent-do.ps1`，原因是 `agent-do.ps1` 固定调用 `git-push-safe.ps1`，而该脚本会 stage 当前工作区所有 dirty 文件。当前工作区存在本任务外的后端测试改动与历史未跟踪报告，直接运行会污染提交范围。

本轮改为手工执行 Harness 子步骤：

1. safety-check
2. targeted Vitest
3. frontend build
4. restart-compose frontend
5. verify-local frontend
6. real-pre preflight
7. 页面 smoke
8. collect-evidence
9. new-retro

通用 evidence：`harness/reports/evidence-20260609-132352.md`。
Retro：`harness/reports/retro-20260609-132414.md`。

## 剩余风险

1. 本轮只修展示文案和说明，不落地汇总表。
2. `agg_daily_performance_create` 缺失仍应作为单独 P1 技术债审查。
3. 当前工作区仍有本任务外 dirty，提交时必须逐文件 stage，禁止 `git add .`。
