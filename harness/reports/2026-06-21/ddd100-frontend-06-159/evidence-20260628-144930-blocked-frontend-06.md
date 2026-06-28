# Evidence: 前端全领域 Playwright E2E (#159) - PENDING

## Basic Info

- Time: 2026-06-28 14:53:24 Asia/Shanghai
- Issue: #159 前端全领域 Playwright E2E
- Type: E2E
- Command: `npm run e2e:real-pre:p0`
- RunId: `QA20260628_144627`
- Status: **PENDING**

## 运行环境

- frontend: `http://localhost:3001`
- backend: `http://localhost:8081`
- systemEnv: `REAL-PRE`
- appTestEnabled: `false`
- douyinTestEnabled: `false`
- database: `saas_real_pre`

## 结果明细

| Step | Status | Evidence |
| --- | --- | --- |
| preflight | PASS | `runtime/qa/out/real-pre-p0-20260628-144627/steps/01-preflight` |
| 08 抖店接入回归 | PASS | `runtime/qa/out/real-pre-p0-20260628-144627/steps/02-08-douyin-integration` |
| 31 商品链 | PASS | `runtime/qa/out/real-pre-p0-20260628-144627/steps/03-31-product-chain` |
| 32 订单归因 | PENDING | `runtime/qa/out/real-pre-p0-20260628-144627/steps/04-32-order-attribution` |
| 33 寄样链 | PENDING | `runtime/qa/out/real-pre-p0-20260628-144627/steps/05-33-sample-chain` |
| 34 业绩看板 | PASS | `runtime/qa/out/real-pre-p0-20260628-144627/steps/06-34-performance-dashboard` |
| 35 RBAC 越权负例 | PASS | `runtime/qa/out/real-pre-p0-20260628-144627/steps/07-35-rbac-scope` |
| 36 清理计划 | PASS_NEEDS_CLEANUP | `runtime/qa/out/real-pre-p0-20260628-144627/steps/08-36-cleanup-plan` |

## PENDING 原因

- `32-order-attribution`: `PENDING_NO_UPSTREAM_ORDERS`，当前真实订单全部未归因，无法证明归因链；30 分钟窗口同步 totalFetched=64、created=1、updated=63、attributed=0、unattributed=64。
- `33-sample-chain`: `PENDING_REAL_ORDER_FOR_HOMEWORK`，当前无真实成交订单可触发寄样自动完成；手动状态机、重复申请拦截和寄样创建已验证。

## 关键证据

- 商品链：真实上游活动商品 20 条，商品 `3790002585986007208` 入库且 `bizStatus=APPROVED`，商品图片 loaded=5、failed=0。
- 订单归因：存在 reusable mapping `f678fe4d-f22a-4ae9-bee7-f713e07250ff` / `pickSource=v.MAhq5U`，但回流订单未命中归因。
- 寄样链：创建样品单 `7f358a65-89ab-4a56-a9aa-1a2850d9d200` / `SM20260628706E9D55`，重复申请拦截 code=462，最终停在 `PENDING_TASK`。
- 业绩看板：估算轨与结算轨服务费公式检查无 violations。
- RBAC：admin/biz/channel/ops 角色页面与 API 越权负例覆盖，关键禁用接口返回 403。

## 产物

- P0 report: `runtime/qa/out/real-pre-p0-20260628-144627/report.md`
- P0 summary: `runtime/qa/out/real-pre-p0-20260628-144627/summary.json`
- Playwright HTML report: `playwright-report/index.html`
- Trace zip: `test-results/playwright/36-real-pre-cleanup-plan-real-pre-P0-36-清理计划-real-pre-p0/trace.zip`

## 总结

#159 已生成真实 real-pre P0 Playwright evidence，但最终状态是 **PENDING**，不是 PASS。不能宣称 real-pre P0 全链路通过；解除条件是等待真实 `pick_source` 订单和真实成交寄样样本后重跑。
