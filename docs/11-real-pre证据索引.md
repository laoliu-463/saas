# real-pre 证据索引

更新时间：2026-05-14

本文只索引已经落盘的 real-pre 收口证据，不新增推断性样本，不补伪造数据。所有结论均以 `runtime/qa/out/` 下目录和文件为准。

导航：

- 上游状态：[04-开发进度.md](/D:/Projects/SAAS/docs/04-开发进度.md)
- 当前清单：[10-上线前验收清单.md](/D:/Projects/SAAS/docs/10-上线前验收清单.md)

## 目录索引

- watcher 复跑证据目录：
  - [real-pre-order-settlements-watch-20260514-142923](/D:/Projects/SAAS/runtime/qa/out/real-pre-order-settlements-watch-20260514-142923)
  - 用途：确认 `buyin.colonelMultiSettlementOrders` 最近窗口内仍为 `status=success / orderCount=0`，同时保留主订单 raw probe 与 DB 侧对照
- final regression 证据目录：
  - [real-pre-final-regression-20260514-143347](/D:/Projects/SAAS/runtime/qa/out/real-pre-final-regression-20260514-143347)
  - 用途：统一收录健康检查、授权主体、活动、商品 refresh、既有转链、`pick_source_mapping`、订单同步、归因 dry-run、Dashboard、Webhook 接收 / 重放 / 定向同步
- P2 首轮硬化证据目录：
  - [pre-launch-hardening-20260514-145503](/D:/Projects/SAAS/runtime/qa/out/pre-launch-hardening-20260514-145503)
  - 用途：统一收录配置硬化、日志脱敏、Webhook 安全、订单同步保护、Dashboard 口径、数据库迁移的首轮上线前检查结果
- P2 修复后复验证据目录：
  - [pre-launch-hardening-fix-20260514-153151](/D:/Projects/SAAS/runtime/qa/out/pre-launch-hardening-fix-20260514-153151)
  - 用途：统一收录 real-pre / real / prod 配置 fail-fast、Webhook 默认验签、`order_sync_dedup_claim` 原子去重与 migration 幂等的修复后复验结果
- 三方未接入手动兜底证据目录：
  - [third-party-fallback-check-20260514-150444](/D:/Projects/SAAS/runtime/qa/out/third-party-fallback-check-20260514-150444)
  - 用途：统一收录物流 API、达人外部数据未接入时的手动兜底验收结论；这是手动兜底验收，不是真实物流 / 达人三方接入证据

## watcher 核心文件

- watcher 摘要：
  - [summary.json](/D:/Projects/SAAS/runtime/qa/out/real-pre-order-settlements-watch-20260514-142923/summary.json)
  - 关键事实：
    - `buyin.colonelMultiSettlementOrders -> status=success / remoteCode=10000 / orderCount=0`
    - 主订单 raw probe 仍抓到 `10` 条真实未结算订单
    - 本地库历史 `settle_time IS NOT NULL` 记录数为 `48`
- watcher 报告：
  - [report.md](/D:/Projects/SAAS/runtime/qa/out/real-pre-order-settlements-watch-20260514-142923/report.md)
  - 用途：人类可读版摘要，便于快速复核 watcher 本轮观察结论

## final regression 核心文件

- final regression 摘要：
  - [summary.json](/D:/Projects/SAAS/runtime/qa/out/real-pre-final-regression-20260514-143347/summary.json)
  - 关键事实：
    - `POST /api/orders/sync` 最近 30 分钟结果：`totalFetched=97 / created=3 / updated=94 / attributed=1 / unattributed=96 / failed=0`
    - Webhook replay：`scanned=1 / consumed=1 / failed=0`
    - 定向同步收件箱：`COLONEL_OPEN_EVENT_SYNCED:fetched=0,created=0,updated=0,failed=0`
- final regression 报告：
  - [report.md](/D:/Projects/SAAS/runtime/qa/out/real-pre-final-regression-20260514-143347/report.md)
  - 用途：收口阶段的人类可读索引，概览通过项与分类结论
- 转链 / 映射 / 商品状态数据库证据：
  - [product-state-db.json](/D:/Projects/SAAS/runtime/qa/out/real-pre-final-regression-20260514-143347/product-state-db.json)
  - 关键事实：
    - 样本活动 / 商品：`3223881 / 3814081914181124118`
    - `pick_source_mapping` 命中 `sourceType=NATIVE / pickSource=v.MxZLIw / colonelBuyinId=7109679864001364265`
    - `promotion_link` 命中 `linkStatus=ACTIVE`
    - `product_operation_state` 命中 `bizStatus=LINKED / selectedToLibrary=true`

## 当前可引用结论

- 当前 `buyin.colonelMultiSettlementOrders` 最近观察窗口仍无非空样本；这是上游样本等待项，不是本地伪造样本或 SDK 逻辑回避出来的结果
- real-pre 主链路 final regression 已完成，覆盖范围和结果以 `real-pre-final-regression-20260514-143347` 目录为准
- P2-FIX 两个硬阻塞已完成复验：配置 fail-fast / Webhook 默认验签、`order_sync_dedup_claim` 订单去重原子性均为 `PASS`；证据以 `pre-launch-hardening-fix-20260514-153151` 为准
- 后续若需要继续补“多结算非空样本”证据，只能基于真实上游返回追加新目录，不能改写现有证据结论

## P2-FIX 复验证据明细

- 证据目录：[pre-launch-hardening-fix-20260514-153151](/D:/Projects/SAAS/runtime/qa/out/pre-launch-hardening-fix-20260514-153151)
- 核心文件：
  - [summary.json](/D:/Projects/SAAS/runtime/qa/out/pre-launch-hardening-fix-20260514-153151/summary.json)
  - [report.md](/D:/Projects/SAAS/runtime/qa/out/pre-launch-hardening-fix-20260514-153151/report.md)
  - [test-output.txt](/D:/Projects/SAAS/runtime/qa/out/pre-launch-hardening-fix-20260514-153151/test-output.txt)
- 定向测试合计：`31 tests / 0 failures / 0 errors`
- Webhook 验签运行态探针：无签名 `401`、错签 `401`、正确 HMAC 签名 `200 success`
- `order_sync_dedup_claim` 并发 claim 探针：同一个 `order_id` 最终只落 `1` 条
- migration 幂等复验：连续执行两次退出码均为 `0`
- 提交前审计短语：`Webhook 401 / 401 / 200`、`order_sync_dedup_claim 并发 claim 只落 1 条`、`migration 连续两次退出码 0`

## 三方未接入说明

- 抖音联盟主链路已有 real-pre 证据，仍以 `real-pre-final-regression-20260514-143347` 为主索引
- 物流接口当前暂无真实三方证据；本轮仅完成手动兜底验收，证据目录为 `third-party-fallback-check-20260514-150444`
- 达人自动采集 / 爬虫 / 第三方达人数据当前暂无真实三方证据；本轮仅完成手动兜底验收，证据目录为 `third-party-fallback-check-20260514-150444`
- 后续如接入真实物流或真实达人外部数据，需要单独新增证据目录，不复用本轮手动兜底结论
