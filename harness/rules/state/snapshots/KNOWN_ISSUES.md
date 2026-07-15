# Known Issues

> **三文件分工指针**：
> - 本文件（`KNOWN_ISSUES.md`）：业务问题卡片，含 open / blocked / fixed / wontfix / deferred 状态；每条问题有完整证据。
> - `03-P0-P1问题台账.md`：业务 P0/P1 风险摘要。
> - `04-real-pre证据索引.md`：real-pre 执行证据摘要。
> - `../debts/HARNESS_DEBT.md`：Harness 自身的工程债务。
> 互引规则：业务问题进入本文件或 P0/P1 台账；Harness 问题进入 `HARNESS_DEBT.md`。

## 作用

本文件汇总当前仍影响 Agent 判断的 open / blocked / deferred 问题。real-pre 验收主源是 `docs/验收/验收证据索引.md`。

| 问题 | 状态 | 证据 / 主源 | 下一步 |
| --- | --- | --- | --- |
| real-pre 渠道链真实订单归因样本不足 | blocked | `01-当前项目状态.md`、`03-P0-P1问题台账.md` | 等待真实通过系统转链产生的订单样本，不能写 PASS |
| 寄样自动完成依赖真实归因订单 | blocked | `03-P0-P1问题台账.md`、`docs/验收/real-pre联调手册.md` | 有订单后验证 `channel_id + talent_id + product_id + pay_time` |
| 推广中商品历史入库可能漂移 | open | `03-P0-P1问题台账.md`、商品域文档 | 优先走商品域同步/repair 入口，先 dry-run |
| `docs/归档/旧版V2.2完整方案/商品域未实现功能详细清单.md` 为空文件 | deferred | 当前仓库扫描 | 不直接删除；纳入旧内容维护候选 |
| `CODEX.md` 曾默认 `Env=test` | fixed | 本轮修改 | 已改为 `real-pre`，后续验证入口一致性 |
| U-2.5-B 后端全量测试失败 | fixed | `harness/reports/test-1-full-backend-failures-fix-20260603-104601.md` | TEST-1 已修复：全量 `mvn -f backend/pom.xml test` 通过 1671 tests / 0 failures / 0 errors |
| real-pre 前端 Google Fonts 被 CSP 阻止 | open | `harness/reports/func-001-product-card-hover-ui-20260603-111451.md`、`runtime/qa/out/func-001-product-card-hover-expanded.png` | 决定是否自托管字体或调整 CSP；本轮商品卡片功能验证仅记录为 `PASS_WITH_NON_TASK_WARNING` |
| P-FIX-001C 任务模板要求的 Harness 路径/Scope 与当前脚本不一致 | fixed | `harness/reports/p-fix-001c-product-library-pagination-20260603-113616.md`、当前路径检查 | 当前使用 `harness/rules/skills/workflow/post-task-gc.md`；脚本 Scope 以 `agent-do.ps1` 参数为准 |
| 远端同步任务禁用（`PRODUCT_ACTIVITY_SYNC_ENABLED` 未设置） | fixed | `harness/reports/p-fix-002a-product-sync-5min-config-20260603-120100.md`、`harness/reports/p-fix-002d-remote-deploy-verify-20260603-132805.md` | P-FIX-002A 已完成配置准备；P-FIX-002D-REMOTE 远端已补齐 env 并验证生效（`enabled=true, cron=0 */5 * * * ?`） |
| 唯一索引 `uk_pos_one_displaying_per_product` 冲突导致同步事务回滚 | fixed | `harness/reports/p-fix-002-product-sync-display-5min-20260603-121257.md`、`harness/reports/p-fix-002d-real-pre-runtime-verify-20260603-123411.md`、`harness/reports/p-fix-002d-remote-deploy-verify-20260603-132805.md` | P-FIX-002B 已修复 + P-FIX-002D 本地验证通过 + P-FIX-002D-REMOTE 远端验证通过：两个周期零冲突 |
| 推广中商品仍有未 DISPLAYING 记录 | open | `harness/reports/p-fix-002-product-sync-display-5min-20260603-121257.md`、`harness/reports/p-fix-002d-real-pre-runtime-verify-20260603-123411.md`、`harness/reports/p-fix-002d-remote-deploy-verify-20260603-132805.md` | 本地 PENDING=371，远端 PENDING=2128；仍需 P-FIX-002E repair（dry-run → 实际执行），区分过期活动、未选中和待重算来源 |
| Git 工作区 dirty 膨胀与批次提交门禁缺失 | open | `harness/rules/state/debts/HARNESS_DEBT.md` DEBT-026 | 规则已存在，但 `git-push-safe.ps1` 仍会暂存全部变更；修复前必须显式文件级提交，不能把脚本名当成安全证明 |
| ORDER-P0 订单同步数据源不完整：仅接 2704 结算接口，未接入 6468 实际订单接口 | fixed-local, pending-remote | `harness/reports/order-api-729-verify-20260603-174500.md`、`harness/reports/order-api-729-server-verify-20260603-175500.md`、`harness/reports/order-api-6468-verify-20260603-180500.md`、`harness/reports/order-api-6468-raw-probe-20260603-181634.md`、`harness/reports/order-settlement-dual-track-verify-20260603-183157.md`、`harness/reports/evidence-20260603-202253.md`、`harness/reports/evidence-20260603-202320.md` | ORDER-P0-DUAL-SOURCE-SYNC 已在本地 real-pre 接入 6468：`ORDER_SYNC_INSTITUTE api=buyin.instituteOrderColonel fetched=100 inserted=100 updated=0`；订单表 `count(*)=100`，`order_amount/pay_time/estimate_*` 均有值，`settle_amount/effective_*` 未被 6468 写入；2704 继续执行 `ORDER_SYNC_SETTLEMENT ... fetched=0`。远端未部署/未验证；本轮 6468 样本 `pick_source=0`，渠道 mapping 命中正向样本仍为 PENDING。 |
| 达人寄样地址不默认保存 | fixed | `harness/reports/talent-address-sample-default-20260603-224000.md` | TALENT-ADDRESS-SAMPLE-DEFAULT 已完成：后端回写 `talent_claim`、前端自动加载默认地址、历史快照不变、多渠道隔离；real-pre 验收 PASS |
| 商品库快速寄样把快照主键误按商品主键校验 | open | `harness/reports/current/latest-talent-address-product-sample-diagnosis.md` | `ProductController` 传入 `product_snapshot.id`，DDD 前置检查却调用按 `product.id` 查询的 `existsById`；改为快照语义校验，并补“快照存在、商品主表无同主键”回归测试 |
| 快速寄样失败时是否仍保存达人地址 | pending-business-decision | `harness/reports/current/latest-talent-address-product-sample-diagnosis.md` | 当前仅在寄样单创建成功后回写 `talent_claim`；需由业务确认失败提交是否也应独立保存地址，以及多选达人时地址归属 |
| 真实付款订单 10 分钟 update 窗口可能丢单 | fixed-code, blocked-by-sample | `harness/reports/p0-order-001-real-order-visible-20260603-180450.md` | P0-ORDER-001 已增加 PAY_RECENT 6h 30min 回扫，使用独立锁 `ORDER_SYNC_PAY_RECENT` + 独立 Redis 水位 `order:sync:pay_recent_last_time`，不覆盖 10min 增量水位；增强同步日志含 mode/timeType/inserted/updated/attributed/unattributed/noPickSource/noMapping/failed；mvn test 1688/0/0、backend-real-pre 重启后双 scheduler 立即首次执行均正常；真实订单端到端业务验证需等待商务真实付款样本。注：本任务仅修复"同步窗口/水位/可观测性"层面；如订单数据源本身缺失（见上一条 ORDER-P0 2704/6468），仍需独立任务处理。 |
| 订单同步事件早于事务提交导致业绩缺口 | fixed-code, pending-backfill | `harness/reports/order-performance-event-after-commit-fix-001-20260606-121000.md`、`harness/reports/evidence-20260606-120829.md` | ORDER-PERFORMANCE-EVENT-AFTER-COMMIT-FIX-001 已完成：`OrderSyncPersistenceService` 改为事务提交后发布 `OrderSyncedEvent`；新增 RED/GREEN 回归测试覆盖 after-commit 时序、Listener upsert、重复事件 upsert。当前本地 real-pre anti-join 仍为 `missing_performance=15`，下一步必须执行 `ORDER-PERFORMANCE-BACKFILL-001`，不得手动 insert 或把历史缺口写成已清零。 |
| DASH-MONEY-P0-001 settle_amount 回退逻辑污染业绩表 | open | `harness/reports/dashboard-money-audit-001-20260604-131908.md` | PerformanceCalculationService:113 回退 `settleAmount > 0 ? settleAmount : actualAmount` 导致全部 404 条 performance_records.settle_amount = pay_amount（应=0）；SQL 证据：订单表 settle_amount=0 vs 业绩表 settle_amount=771125。下一步：DASHBOARD-MONEY-FIX-001 |
| DASH-MONEY-P0-002 旧版 /dashboard/summary 是单轨接口 | open | `harness/reports/dashboard-money-audit-001-20260604-131908.md` | DashboardController.getSummary() 返回扁平 Summary DTO 无双轨结构。下一步：DASHBOARD-MONEY-FIX-002 评估废弃或修复 |
| ~~DASH-MONEY-P0-004 V1 不做毛利但前端仍展示~~ | **revoked** | `harness/reports/dashboard-money-audit-001-20260604-131908.md` | 2026-06-05 用户决策：毛利纳入 V1 交付与验收。原 P0 降级为前端展示补齐任务 GROSS-PROFIT-DISPLAY-001。后端已计算并返回 grossProfit，前端需补齐展示。 |
| Y-4 提成规则乐观锁冲突被误报成功 | fixed | `harness/reports/latest-evidence-20260710.md` | `4bb8ce1c` 补 version migration，`3ed74608` 对 update/delete 零行结果抛业务 409；47 tests、local real-pre schema/API/health 已验证 |
| 提成规则陈旧页面未透传原始版本 | open | `harness/reports/latest-evidence-20260710.md` | 当前守卫覆盖 Service 读写窗口内并发；由业务/API 合同确认是否要求跨页面陈旧提交检测，再补请求 DTO、前端透传和真实并发集成测试 |
| E-7 转链完成事件未透传请求幂等键 | open | `harness/reports/git-intake-20260710-125023.md` | 将调用方 `idempotencyKey` 传入事件与 outbox payload，补重复请求/事件行为测试 |
| Y-12/E-5 汇总刷新事件生产边界冲突 | open | `harness/reports/git-intake-20260710-125023.md` | 由业务/ADR 确认应由业绩明细变更还是 dashboard 增量路径发布；确认前不擅自改生产者 |

## 更新规则

- 每个问题必须有状态：`open`、`blocked`、`fixed`、`wontfix`、`deferred`。
- 没有证据的内容不能写成问题结论，只能写“待确认”。
- 修复后必须补 evidence report 路径。
