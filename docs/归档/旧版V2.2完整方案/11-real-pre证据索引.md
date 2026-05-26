# real-pre 证据索引

更新时间：2026-05-25

本文主要索引已经落盘的 real-pre 收口证据；如收录 TEST/mock 基线证据，必须显式标注环境边界，不新增推断性样本，不补伪造数据。所有结论均以 `runtime/qa/out/` 下目录和文件为准。

导航：

- 上游状态：[04-开发进度.md](/D:/Projects/SAAS/docs/04-开发进度.md)
- 当前清单：[10-上线前验收清单.md](/D:/Projects/SAAS/docs/10-上线前验收清单.md)

## real-pre P0 统一报告（必读）

- 报告目录模式：`runtime/qa/out/real-pre-p0-YYYYMMDD-HHmmss/`
  - `summary.json`：runId、环境、各步骤状态、BLOCKED/PENDING/FAIL 明细、cleanup 计划路径、`finalStatus`。
  - `report.md`：人类可读版本。
  - `steps/`：各步骤独立证据（`01-preflight/` 与 `02-08-douyin-integration/` ... `08-36-cleanup-plan/`）。
- 入口脚本：`npm run e2e:real-pre:p0`（统一入口；不允许用 `e2e:real-pre`/`e2e:real-pre:business` 替代 P0 验收证据）。
- 结论分级（强制四态 + PASS_NEEDS_CLEANUP）：
  - `PASS` / `PASS_NEEDS_CLEANUP`：上线前可用，区别只在 cleanup 是否已审核并执行。
  - `BLOCKED`：Token / 授权 / mapping / 关键账号等外部前置缺失，需业务侧补样本后重跑。
  - `PENDING`：环境与代码链路 OK，但真实订单 / `pick_source` / 寄样成交样本缺失。
  - `FAIL`：硬阻塞，必须先修复。

## 目录索引

- 商品订单归因逻辑排查（2026-05-21，仅 real-pre）：
  - Runbook：[archive/runbooks/20-real-pre商品订单归因逻辑排查.md](/D:/Projects/SAAS/docs/archive/runbooks/20-real-pre商品订单归因逻辑排查.md)
  - 一键脚本：[audit-product-order-attribution-real-pre.ps1](/D:/Projects/SAAS/scripts/qa/audit-product-order-attribution-real-pre.ps1)
  - 用途：快照 → `pick_source_mapping` → `colonelsettlement_order` 归因 reason → 看板对账；编排 `check-real-pre-real-data` + `audit-product-chain` + `run-real-pre-attribution-evidence`
- 商品链路本地排查（2026-05-21）：
  - Runbook：[archive/runbooks/19-商品链路本地排查.md](/D:/Projects/SAAS/docs/archive/runbooks/19-商品链路本地排查.md)
  - 脚本：[audit-product-chain-real-pre.ps1](/D:/Projects/SAAS/scripts/qa/audit-product-chain-real-pre.ps1)
  - 证据目录：[product-chain-audit-20260521-122559](/D:/Projects/SAAS/runtime/qa/out/product-chain-audit-20260521-122559)
  - 用途：对照 `alliance.colonelActivityProduct`、转链、`pick_source_mapping`、主订单 GMV 与看板活动商品接口做 real-pre 只读排查；结论 `WARN`（同步 PASS，转链/归因待样本补证）
  - 样本：`activityId=3920684`，`productId=3771167367585988821`
- 达人真实资料接入 E2E（2026-05-20）：
  - 脚本：`runtime/qa/e2e/domains/talent-profile-real-data.e2e.cjs`
  - 输出目录模式：`runtime/qa/out/e2e-talent-profile-real-data-YYYYMMDD-HHmmss/`
  - 说明：[达人真实数据接入说明.md](/D:/Projects/SAAS/docs/达人真实数据接入说明.md)
  - 口径：真实链接成功则校验 `nickname/fans_count/like_count/works_count` 等字段、`sync_status`、`raw_payload`、`talent_profile_sync_log`；失败分支校验 `failed` + `error_message`；人工补充必须 `data_source=manual`
- real-pre 准入基线证据目录：
  - [qa-real-pre-preflight-20260517-090308-762](/D:/Projects/SAAS/runtime/qa/out/qa-real-pre-preflight-20260517-090308-762)
  - [real-pre-e2e-20260517-090457-069](/D:/Projects/SAAS/runtime/qa/out/real-pre-e2e-20260517-090457-069)
  - 用途：确认 real-pre 准入已通过，Token、授权主体、活动商品、SKU、订单同步、Dashboard 与系统配置页刷新链路可进入下一阶段业务闭环验证
- P3 real-pre 业务闭环 smoke 证据目录：
  - [real-pre-business-e2e-20260517-091828](/D:/Projects/SAAS/runtime/qa/out/real-pre-business-e2e-20260517-091828)
  - 用途：确认单样本真实业务链路 smoke 已通过，覆盖活动商品读取、SKU 探针、商品本地状态、转链、`pick_source_mapping`、订单同步、Dashboard API 与前端页面打开
- P3-5 全角色账号业务流程验收证据目录：
  - [real-pre-role-business-e2e-20260517-101101](/D:/Projects/SAAS/runtime/qa/out/real-pre-role-business-e2e-20260517-101101)
  - 用途：确认管理员、招商组长、招商、渠道、运营、渠道组长六类账号的菜单权限、数据范围、核心业务动作、越权拦截和退出保护
- P3 浏览器可视化全业务剧本回归证据目录：
  - [real-pre-full-business-journey-20260517-151152](/D:/Projects/SAAS/runtime/qa/out/real-pre-full-business-journey-20260517-151152)
  - 用途：确认按账号顺序登录、完成上一环数据、退出、再由下一账号继续处理的可视化全业务剧本；覆盖角色权限、商品库、达人 CRM、寄样台、推广转链、数据平台等主线
- P3-6 真实订单归因样本首轮证据目录：
  - [real-pre-attribution-evidence-20260517-104656](/D:/Projects/SAAS/runtime/qa/out/real-pre-attribution-evidence-20260517-104656)
  - 用途：记录一次真实订单窗口扫描、`/api/orders/sync`、raw probe、本地订单抽样、`pick_source_mapping` 校验和 Dashboard 解释层；若无样本，允许输出 `SYNC_OK_NO_SAMPLE` 而不是伪造通过
- P3-6 真实 `pick_source` 订单轻量观察脚本：
  - [watch-real-pre-pick-source-orders.ps1](/D:/Projects/SAAS/scripts/qa/watch-real-pre-pick-source-orders.ps1)
  - 用途：只做最近订单窗口同步、`pick_source` 检查与 mapping 安全校验；用于等待真实样本出现后的快速补证
- P3 Pending Evidence 统一观察证据目录：
  - [real-pre-pending-evidence-watch-20260517-125831](/D:/Projects/SAAS/runtime/qa/out/real-pre-pending-evidence-watch-20260517-125831)
  - 用途：统一观察 `P3-6-E`、`EXC-004`、`EXC-005`、`EXC-006` 四个待补证项；本轮结果为 `P3-6=SYNC_OK_NO_SAMPLE / EXC-004=NO_LIVE_SAMPLE / EXC-005=READ_ONLY_CANDIDATE_FOUND / EXC-006=NO_HISTORICAL_SAMPLE`
- P3 Pending Evidence 统一观察脚本：
  - [watch-real-pre-pending-evidence.ps1](/D:/Projects/SAAS/scripts/qa/watch-real-pre-pending-evidence.ps1)
  - 用途：包装 Node 版 `runtime/qa/real-pre-pending-evidence-watch.cjs`，统一观察真实 `pick_source` 样本、live unsafe 样本、重复转链只读候选和 SKU 异常历史样本
- P3-7 Dashboard 数据库口径对账证据目录（首轮）：
  - [real-pre-dashboard-reconcile-20260517-110015](/D:/Projects/SAAS/runtime/qa/out/real-pre-dashboard-reconcile-20260517-110015)
  - 用途：对齐 `GET /api/dashboard/summary` 与 PostgreSQL 聚合值，覆盖 `orderCount`、`orderAmount`、`serviceFee`、`attributedOrderCount`、`unattributedOrderCount` 和 `5` 个异常解释指标
- P3-7 Dashboard 数据库口径对账证据目录（当前主引用）：
  - [real-pre-dashboard-reconcile-20260517-122911](/D:/Projects/SAAS/runtime/qa/out/real-pre-dashboard-reconcile-20260517-122911)
  - 用途：复跑只读 Dashboard Summary 对账，`orderCount=808 / orderAmount=1548347 / serviceFee=22935`，`10` 个核心指标逐项一致，`status=PASS`，`diff.json` 为空，`candidateReasons=[]`
- P3-7 Dashboard 数据库口径对账脚本：
  - [run-real-pre-dashboard-reconcile.ps1](/D:/Projects/SAAS/scripts/qa/run-real-pre-dashboard-reconcile.ps1)
  - 用途：在 real-pre 环境做只读 Dashboard Summary 对账，不写业务数据，不触发 seed
- P3-8 异常分支只读审计证据目录：
  - [real-pre-exception-audit-20260517-124432](/D:/Projects/SAAS/runtime/qa/out/real-pre-exception-audit-20260517-124432)
  - 用途：首轮收集 real-pre 下“无订单同步成功、无 `pick_source` 订单保留、无映射 dry-run 不写回”等异常分支证据；对必须写动作或缺 live 样本的分支显式标记 `DEFERRED / NO_LIVE_SAMPLE`
- P3-8 异常分支只读审计脚本：
  - [run-real-pre-exception-audit.ps1](/D:/Projects/SAAS/scripts/qa/run-real-pre-exception-audit.ps1)
  - 用途：包装 Node 版 `runtime/qa/real-pre-exception-audit.cjs`，在 real-pre 环境做只读 / dry-run 异常分支审计
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
- TEST/mock 主流程覆盖收口证据目录：
  - [qa-mock-data-audit-20260516-194040-956](/D:/Projects/SAAS/runtime/qa/out/qa-mock-data-audit-20260516-194040-956)（最新）
  - [qa-mock-data-audit-20260516-193210-553](/D:/Projects/SAAS/runtime/qa/out/qa-mock-data-audit-20260516-193210-553)（首轮同日较早）
  - 用途：统一收录 2026-05-16 TEST/mock 数据硬缺口清零证据；这是 TEST/mock 主流程覆盖收口，不代表 real-pre 或真实三方联调完成

## TEST/mock 主流程覆盖收口

- 报告目录：[qa-mock-data-audit-20260516-194040-956](/D:/Projects/SAAS/runtime/qa/out/qa-mock-data-audit-20260516-194040-956)
- 核心文件：
  - [summary.json](/D:/Projects/SAAS/runtime/qa/out/qa-mock-data-audit-20260516-194040-956/summary.json)
  - [report.md](/D:/Projects/SAAS/runtime/qa/out/qa-mock-data-audit-20260516-194040-956/report.md)
  - [api-errors.json](/D:/Projects/SAAS/runtime/qa/out/qa-mock-data-audit-20260516-194040-956/api-errors.json)
- 结论：`overallPass=true`，`coverage=84.58%`，`hardMissing=0`，`api-errors=[]`。
- 范围：仅 TEST/mock；本轮未切换 real-pre，未访问真实三方接口，未执行清库操作。
- 剩余：`missing-scenarios` 均为 warning 型专项样本，暂不阻塞 TEST/mock 主流程收口，已转入 [19-TEST-mock-warning专项样本清单](/D:/Projects/SAAS/docs/19-TEST-mock-warning专项样本清单.md) 跟踪。

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
- TEST/mock 数据覆盖硬缺口已在 2026-05-16 清零：`overallPass=true / coverage=84.58% / api-errors=[]`；该结论只代表 TEST/mock 主流程覆盖收口，不代表所有业务增强场景或真实三方联调完成
- P3 real-pre 业务闭环 smoke 已于 2026-05-17 首轮通过；该结论代表单样本链路可执行，不等同于真实订单归因样本、Dashboard 数据库对账和异常分支全部收口
- P3-5 全角色账号业务流程验收已于 2026-05-17 通过；该结论代表六类角色账号、菜单权限、数据范围、核心业务动作、越权拦截与寄样闭环状态推进均已拿到 real-pre 证据
- P3 浏览器可视化全业务剧本回归已于 2026-05-17 通过；该结论代表真实浏览器中按账号顺序完成“管理员 -> 招商组长 -> 招商 -> 渠道 -> 招商复审 -> 运营 -> 渠道组长 -> 管理员复核”的主业务流转
- P3-6 真实订单归因样本首轮证据已于 2026-05-17 落盘；当前结论是 `SYNC_OK_NO_SAMPLE`，表示真实订单同步链路成功，但上游当前窗口和本地落库样本里都还没有带 `pick_source` 的真实订单
- P3-7 Dashboard 数据库口径对账已于 2026-05-17 通过；`/api/dashboard/summary` 与 PostgreSQL 聚合值 `10` 个核心指标逐项一致
- 当前可统一表述为：`real-pre 准入通过 -> P3 单样本业务闭环通过 -> P3-5 全角色账号业务流程通过 -> P3-6 SYNC_OK_NO_SAMPLE -> P3-7 Dashboard 与 PostgreSQL 口径对账 PASS -> P3-8 异常分支 PARTIAL_PASS -> 浏览器可视化全业务剧本回归 PASS -> 当前约 94%+`
- 当前发布判断：系统已经具备 real-pre 业务可用性；但尚不能宣称可生产发布，下一阶段只补真实订单归因样本与异常分支证据，并保留 Dashboard 数据库口径只读复跑
- P3-6 当前已完成 `P3-6-A` 真实订单同步窗口扫描、`P3-6-B` 无 `pick_source` 情况验证、`P3-6-C` 无样本不伪造通过、`P3-6-D` Dashboard 未归因口径真实数据支撑；`P3-6-E` 仍待真实样本补证
- 后续若需要继续补“多结算非空样本”证据，只能基于真实上游返回追加新目录，不能改写现有证据结论

## 2026-05-17 real-pre 准入基线

结论：real-pre 准入已通过，可以进入下一阶段：真实链路业务闭环验证。

证据目录：

- [qa-real-pre-preflight-20260517-090308-762](/D:/Projects/SAAS/runtime/qa/out/qa-real-pre-preflight-20260517-090308-762)
- [real-pre-e2e-20260517-090457-069](/D:/Projects/SAAS/runtime/qa/out/real-pre-e2e-20260517-090457-069)

通过项：

- `qa-real-pre-preflight.ps1`：`PASS`，blocking checks 为空
- `npm run e2e:real-pre`：`PASS`，`1/1`
- `RealDouyinTokenGatewayTest`：`PASS`，`5/5`
- token 状态：`hasAccessToken=true`，`hasRefreshToken=true`，`reauthorizeRequired=false`
- `saas-backend / saas-frontend / saas-postgres / saas-redis` 均为 `healthy`

安全说明：

- 授权码、真实 token、JWT、client secret 均未写入文档或 Git。

下一阶段：

- 进入 P3 real-pre 真实业务闭环验证，主线限定为：活动商品同步 -> 商品审核/上架 -> 一键转链 -> `pick_source_mapping` 落库 -> 订单同步 -> Dashboard 对账。
- 不扩到大范围批量同步、生产写操作、批量导出、批量发货、达人爬虫大规模采集。

## 2026-05-17 P3 real-pre 业务闭环 smoke

结论：P3 real-pre 业务闭环 smoke 首轮通过；继续补强真实订单归因样本、Dashboard 数据库对账和异常分支证据。

证据目录：

- [real-pre-business-e2e-20260517-091828](/D:/Projects/SAAS/runtime/qa/out/real-pre-business-e2e-20260517-091828)

通过项：

- `npm run e2e:real-pre:business`：`PASS`，`1/1`
- token 状态：`hasAccessToken=true`，`hasRefreshToken=true`，`reauthorizeRequired=false`
- 活动商品：指定活动 `3916506`，raw 商品 `20` 条，业务商品 `20` 条
- SKU 探针：样本商品 `3810562766247428542` 返回 `skuCount=1`
- 商品状态：样本商品本地状态 `LINKED`，`selectedToLibrary=true`
- 转链：返回 `pickSource=v.MxZLIw`，推广链接包含 `pick_source`
- 映射：`pick_source_mapping` 命中 `mappingCount=1`
- 订单同步：`totalFetched=100 / created=4 / updated=96 / failed=0`
- Dashboard：核心指标接口成功返回，`todayOrderCount=223 / todayGmv=4284.97`
- 页面：`/system/douyin`、`/dashboard`、`/product`、`/orders`、`/data` 均可打开

边界说明：

- 本轮未清库，未把真实 token / JWT / client secret 写入文档或 Git。
- 本轮只覆盖单样本真实业务闭环 smoke；真实订单归因样本、Dashboard 数据库口径一致性、无订单 / 有归因订单 / 无归因订单展示和异常分支仍需继续补证。

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

## P3-5 全角色账号业务流程验收

证据目录：

- [real-pre-role-business-e2e-20260517-101101](/D:/Projects/SAAS/runtime/qa/out/real-pre-role-business-e2e-20260517-101101)

执行命令：

- `npm run e2e:real-pre:roles`

覆盖账号：

- `admin`
- `biz_leader`
- `merchant`（账号：`biz_staff`）
- `channel_leader`
- `channel`（账号：`channel_staff`）
- `operator`（账号：`ops_staff`）

结论：

- `PASS`，Playwright `2/2` 通过，业务验收步骤 `11/11` 通过。
- 辅助回归：`backend mvn "-Dtest=SampleControllerTest" test` 为 `29/29 PASS`；`git diff --check` 无空白错误。
- 环境守卫：`real-pre` / `REAL-PRE`，`APP_TEST_ENABLED=false`，`DOUYIN_TEST_ENABLED=false`，后端健康状态 `UP`。
- 全角色账号：`admin`、`biz_leader`、`biz_staff`、`channel_staff`、`ops_staff`、`channel_leader` 均登录成功，并返回预期角色与数据范围。
- 闭环证据：商品 `3817426948628545700` 审核上架，拒绝样本商品 `3690698265050873924`；渠道转链返回 `pickSource=v.MxZLIw`，`pick_source_mapping` 命中 `mappingCount=1`。
- 寄样证据：`sampleRequestNo=SM20260517661BF393`，招商审核后进入 `PENDING_SHIP`，运营录入 `trackingNo=SF1778984000177` 后推进至 `PENDING_TASK`。
- 订单与看板：订单同步 `totalFetched=100 / created=7 / updated=93 / failed=0`，管理员复核操作日志、寄样日志、订单同步和 Dashboard 均通过。
- 权限结论：各角色菜单权限与越权页面拦截符合预期；六个账号退出登录后再次访问受保护接口均返回 `401`。

下一阶段：

- `P3-6`：补到至少 `1` 条真实订单归因样本，证明 `pick_source -> mapping -> attribution_status -> Dashboard` 可追溯
- `P3-7`：已通过，保留 `scripts/qa/run-real-pre-dashboard-reconcile.ps1` 作为只读复跑入口
- `P3-8`：首轮只读审计已拿到 `PARTIAL_PASS`，剩余补齐“live unsafe 样本、重复转链幂等、SKU 异常不中断主流程”等证据；统一观察入口已固定为 `scripts/qa/watch-real-pre-pending-evidence.ps1`

## 2026-05-17 real-pre 浏览器可视化全业务剧本回归

状态：`PASS`。

证据目录：

- [real-pre-full-business-journey-20260517-151152](/D:/Projects/SAAS/runtime/qa/out/real-pre-full-business-journey-20260517-151152)

执行命令：

- `npm run e2e:real-pre:journey:visual`

结果：

- Playwright：`1 passed (2.8m)`
- Journey：`overallPass=true`
- 步骤：`9/9 PASS`
- failures：`0`

覆盖账号：

- `admin`
- `biz_leader`
- `biz_staff`
- `channel_staff`
- `ops_staff`
- `channel_leader`

覆盖链路：

- 管理员配置与抖店联调刷新
- 招商组长同步活动商品并分配审核人
- 招商审核商品并补齐业务资料
- 渠道创建达人、真实转链、生成 `pick_source_mapping`、提交寄样
- 招商复审寄样进入发货队列
- 运营录入物流并签收推进到待交作业
- 渠道组长验证组内可见性与边界
- 管理员复核 Dashboard、订单、操作日志并清理 QA 配置

关键事实：

- `runId=QA20260517_151156`
- `selectedProductId=3816480086656418238`
- `pickSource=v.MxZLIw`
- `mappingCount=1`
- `sampleRequestNo=SM202605175EAB9051`
- `sampleStatus=PENDING_TASK`
- `trackingNo=SF20260517151156`

失败归类说明：

- 首次失败原因为 `http://localhost:3000/login` 无前端监听。
- 后端 `8080` 正常，real-pre env guard 已通过。
- 启动 frontend 到 `3000` 后复跑通过，因此首次失败归类为“环境启动前置条件缺失”，不归类为业务流程失败、权限失败、登录失败、接口失败或 real-pre 配置失败。
- 该结论不自动补齐 `P3-8` 剩余项；它证明当前系统主业务流程已经可以被真实浏览器按账号顺序跑通。

## 2026-05-17 P3-6 真实订单归因样本首轮证据

状态：`部分收口，等待真实 pick_source 订单样本`

结论：`reportStatus=SYNC_OK_NO_SAMPLE`。P3-6 真实订单归因样本验证当前只能得出“同步链路通过，但上游当前无可归因 `pick_source` 样本”；这比伪造一个“真实归因样本通过”更可信。

证据目录：

- [real-pre-attribution-evidence-20260517-104656](/D:/Projects/SAAS/runtime/qa/out/real-pre-attribution-evidence-20260517-104656)

核心文件：

- [summary.json](/D:/Projects/SAAS/runtime/qa/out/real-pre-attribution-evidence-20260517-104656/summary.json)
- [report.md](/D:/Projects/SAAS/runtime/qa/out/real-pre-attribution-evidence-20260517-104656/report.md)
- [orders-sample.json](/D:/Projects/SAAS/runtime/qa/out/real-pre-attribution-evidence-20260517-104656/orders-sample.json)
- [mapping-check.json](/D:/Projects/SAAS/runtime/qa/out/real-pre-attribution-evidence-20260517-104656/mapping-check.json)
- [dashboard-check.json](/D:/Projects/SAAS/runtime/qa/out/real-pre-attribution-evidence-20260517-104656/dashboard-check.json)

关键事实：

- 环境守卫：`activeProfiles=["real-pre"]`、`environmentLabel=REAL-PRE`、`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`healthStatus=UP`
- 订单同步：`totalFetched=100 / created=2 / updated=98 / attributed=0 / unattributed=100 / failed=0`
- raw probe：最近 `3` 天抽样 `20` 条真实订单，`rawOrdersWithPickSource=0`
- 本地近 `30` 天订单：`totalOrders=721 / ordersWithPickSource=0 / attributedOrders=0 / unattributedOrders=721`
- Dashboard 总览：`orderCount=721 / attributedOrderCount=0 / unattributedOrderCount=721 / upstreamProductUncoveredCount=718 / cannotAutoAttributionCount=3`
- `mapping-check.json` 结论：`Neither the raw probe nor the local persisted orders contain a real order with pick_source, so mapping-hit verification cannot start.`

本轮可确认：

- 订单同步接口正常
- 真实订单能拉回
- 写库 / 更新正常
- `failed=0`
- Dashboard 能反映真实订单池
- 当前订单没有 `pick_source`，所以无法产生真实归因样本

本轮完成：

- `P3-6-A`：真实订单同步窗口扫描完成
- `P3-6-B`：无 `pick_source` 情况验证完成
- `P3-6-C`：无可归因样本时未伪造通过
- `P3-6-D`：Dashboard 未归因口径有真实数据支撑

本轮未完成：

- `P3-6-E`：真实 `pick_source` 订单归因样本通过

边界说明：

- 本轮没有清库，没有写入真实 OAuth code、access token、refresh token、JWT 或 client secret。
- 该目录只能说明“同步成功但无样本”，不能替代 P3-6 的最终通过样本；后续需要继续等待下一真实订单窗口复跑同一入口。
- 后续统一观察入口已固定为 `scripts/qa/watch-real-pre-pending-evidence.ps1`；如只需专看 `pick_source` 样本，仍可使用 `scripts/qa/watch-real-pre-pick-source-orders.ps1`。一旦出现真实 `pick_source` 订单，可直接补 `P3-6-E` 归因样本证据。

## 2026-05-17 P3-7 Dashboard 数据库口径对账

结论：`status=PASS`。`GET /api/dashboard/summary` 与 PostgreSQL 聚合值在当前 real-pre 订单池上逐项一致，P3-7 可标记通过。

证据目录：

- [real-pre-dashboard-reconcile-20260517-122911](/D:/Projects/SAAS/runtime/qa/out/real-pre-dashboard-reconcile-20260517-122911)

核心文件：

- [summary.json](/D:/Projects/SAAS/runtime/qa/out/real-pre-dashboard-reconcile-20260517-122911/summary.json)
- [report.md](/D:/Projects/SAAS/runtime/qa/out/real-pre-dashboard-reconcile-20260517-122911/report.md)
- [api-dashboard-summary.json](/D:/Projects/SAAS/runtime/qa/out/real-pre-dashboard-reconcile-20260517-122911/api-dashboard-summary.json)
- [db-dashboard-summary.json](/D:/Projects/SAAS/runtime/qa/out/real-pre-dashboard-reconcile-20260517-122911/db-dashboard-summary.json)
- [diff.json](/D:/Projects/SAAS/runtime/qa/out/real-pre-dashboard-reconcile-20260517-122911/diff.json)
- [orders-breakdown.json](/D:/Projects/SAAS/runtime/qa/out/real-pre-dashboard-reconcile-20260517-122911/orders-breakdown.json)

关键事实：

- 环境守卫：`activeProfiles=["real-pre"]`、`environmentLabel=REAL-PRE`、`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`healthStatus=UP`
- `orderCount=808`
- `orderAmount=1548347`
- `serviceFee=22935`
- `attributedOrderCount=0`
- `unattributedOrderCount=808`
- `unsafeBecauseCreatedAfterOrderCount=0`
- `upstreamProductUncoveredCount=802`
- `cannotAutoAttributionCount=6`
- `nativeKeyMismatchCount=0`
- `ambiguousMappingCount=0`
- `diff.json` 为空，`candidateReasons=[]`，说明本轮没有差异字段

边界说明：

- 本轮是只读对账，不清库，不写真实 OAuth code、access token、refresh token、JWT 或 client secret。
- 后续如订单池变化，可直接复跑 `scripts/qa/run-real-pre-dashboard-reconcile.ps1`，持续验证 Dashboard Summary 与 PostgreSQL 口径一致。

## 2026-05-17 P3-8 异常分支首轮只读审计

结论：`status=PARTIAL_PASS`，部分可冻结。当前 real-pre 已能给出三类可信异常证据：`无订单同步成功`、`无 pick_source 订单保留`、`无映射订单保留且 dry-run 不写回`；但 `映射创建时间晚于订单时间` 当前没有 live 样本，`重复转链幂等` 与 `SKU 异常不中断主流程` 仍需受控 real-pre 动作或专门样本补证。

证据目录：

- [real-pre-exception-audit-20260517-124432](/D:/Projects/SAAS/runtime/qa/out/real-pre-exception-audit-20260517-124432)

核心文件：

- [summary.json](/D:/Projects/SAAS/runtime/qa/out/real-pre-exception-audit-20260517-124432/summary.json)
- [report.md](/D:/Projects/SAAS/runtime/qa/out/real-pre-exception-audit-20260517-124432/report.md)

关键事实：

- 环境守卫：`activeProfiles=["real-pre"]`、`environmentLabel=REAL-PRE`、`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`healthStatus=UP`
- 当前订单池快照：`orderCount=847 / unattributedOrderCount=847 / unsafeBecauseCreatedAfterOrderCount=0 / upstreamProductUncoveredCount=841 / cannotAutoAttributionCount=6`
- `EXC-001` 无订单同步成功：未来窗口 `2026-05-18 12:44:33 -> 12:54:33` 复跑 `POST /api/orders/sync`，结果 `totalFetched=0 / created=0 / updated=0 / failed=0`
- `EXC-002` 无 `pick_source` 订单保留：DB `totalOrders=847 / ordersWithPickSource=0 / unattributedOrders=847`，与 Dashboard `orderCount=847 / unattributedOrderCount=847` 一致
- `EXC-003` 无映射订单保留且 dry-run 不写回：DB `mappingNotFoundOrders=847`；`GET /api/orders/stats?unattributedReason=COLONEL_MAPPING_NOT_FOUND`=`847`；`POST /api/orders/replay-attribution` `dryRun=true` 结果 `scanned=20 / attributed=0 / unattributed=20`
- `EXC-004` 映射创建时间晚于订单时间：`NO_LIVE_SAMPLE`，当前 Dashboard / stats 口径均为 `0`
- `EXC-005` 重复转链幂等：`DEFERRED_NO_SAFE_READ_ONLY_PATH`
- `EXC-006` SKU 为空或异常但主流程不中断：`DEFERRED_NO_SAFE_READ_ONLY_PATH`

边界说明：

- 本轮严格保持只读 / dry-run；没有额外触发 real-pre 业务写操作。
- `EXC-005 / EXC-006` 当前只能沿用 TEST/mock 既有 warning 风险专项证据，不能被写成 real-pre 已完成。

## 2026-05-17 P3 Pending Evidence Watch

结论：`status=WATCH_COMPLETED`。这不是新的通过结论，而是把 `P3-6-E`、`EXC-004`、`EXC-005`、`EXC-006` 四个待补证项收成一个统一观察入口，避免后续反复手工判断。

证据目录：

- [real-pre-pending-evidence-watch-20260517-125831](/D:/Projects/SAAS/runtime/qa/out/real-pre-pending-evidence-watch-20260517-125831)

核心文件：

- [summary.json](/D:/Projects/SAAS/runtime/qa/out/real-pre-pending-evidence-watch-20260517-125831/summary.json)
- [report.md](/D:/Projects/SAAS/runtime/qa/out/real-pre-pending-evidence-watch-20260517-125831/report.md)

观察结果：

- `P3-6`：`SYNC_OK_NO_SAMPLE`，最近 `30` 分钟同步 `totalFetched=70 / created=18 / updated=52 / failed=0`，最近 `3` 天 raw probe `20` 条订单仍无 `pick_source`
- `EXC-004`：`NO_LIVE_SAMPLE`，`dashboardUnsafeCount=0`、`statsTotalOrders=0`
- `EXC-005`：`READ_ONLY_CANDIDATE_FOUND`，当前 real-pre 已存在 `1` 组重复转链历史样本：同一 `activityId=3916506 / productId=3810562766247428542 / channelUserId=4f0e4fbf-97a3-451e-aef6-ad88457fbe97 / pickExtra=channel_channelstaff` 下 `promotionLinkCount=2`、`mappingCount=1`，最新备注为 `已转链商品重新生成推广链接`
- `EXC-006`：`NO_HISTORICAL_SAMPLE`，当前 `product_operation_log / operation_log` 未发现 SKU 异常历史样本

用途边界：

- 该目录只能说明“下一轮证据该盯什么、现在有没有可用候选”，不能替代 `P3-6-E` 或 `P3-8` 最终通过证据。
- `EXC-005` 当前只能说“存在可只读分析的历史候选”，还不能写成 real-pre 幂等验证已通过。
