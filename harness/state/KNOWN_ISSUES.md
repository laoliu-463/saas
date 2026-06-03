# Known Issues

## 作用

本文件汇总当前仍影响 Agent 判断的 open / blocked / deferred 问题。细节主源仍在 `p0-p1-register.md`、`known-risks.md`、`real-pre-evidence-index.md` 和 `docs/验收/验收证据索引.md`。

| 问题 | 状态 | 证据 / 主源 | 下一步 |
| --- | --- | --- | --- |
| real-pre 渠道链真实订单归因样本不足 | blocked | `harness/CURRENT_STATE.md`、`p0-p1-register.md` | 等待真实通过系统转链产生的订单样本，不能写 PASS |
| 寄样自动完成依赖真实归因订单 | blocked | `known-risks.md`、`docs/验收/real-pre联调手册.md` | 有订单后验证 `channel_id + talent_id + product_id + pay_time` |
| 推广中商品历史入库可能漂移 | open | `known-risks.md`、商品域文档 | 优先走商品域同步/repair 入口，先 dry-run |
| `harness/doc/**` 与新 Harness 目录并存 | deferred | 当前仓库扫描 | 保留为历史聚合参考；通过 `harness/README.md` 和本文件明确主源 |
| `docs/归档/旧版V2.2完整方案/商品域未实现功能详细清单.md` 为空文件 | deferred | 当前仓库扫描 | 不直接删除；纳入旧内容维护候选 |
| `CODEX.md` 曾默认 `Env=test` | fixed | 本轮修改 | 已改为 `real-pre`，后续验证入口一致性 |
| U-2.5-B 后端全量测试失败 | fixed | `harness/reports/test-1-full-backend-failures-fix-20260603-104601.md` | TEST-1 已修复：全量 `mvn -f backend/pom.xml test` 通过 1671 tests / 0 failures / 0 errors |
| real-pre 前端 Google Fonts 被 CSP 阻止 | open | `harness/reports/func-001-product-card-hover-ui-20260603-111451.md`、`runtime/qa/out/func-001-product-card-hover-expanded.png` | 决定是否自托管字体或调整 CSP；本轮商品卡片功能验证仅记录为 `PASS_WITH_NON_TASK_WARNING` |
| P-FIX-001C 任务模板要求的 Harness 路径/Scope 与当前脚本不一致 | open | `harness/reports/p-fix-001c-product-library-pagination-20260603-113616.md`、`harness/reports/retro-20260603-113645.md` | `harness/skills/frontend-domain-change.md`、`post-task-gc.md` 不存在；`safety-check -Scope code` 无效，当前脚本仅支持 `backend/frontend/full/docs` |
| 远端同步任务禁用（`PRODUCT_ACTIVITY_SYNC_ENABLED` 未设置） | fixed | `harness/reports/p-fix-002a-product-sync-5min-config-20260603-120100.md`、`harness/reports/p-fix-002d-remote-deploy-verify-20260603-132805.md` | P-FIX-002A 已完成配置准备；P-FIX-002D-REMOTE 远端已补齐 env 并验证生效（`enabled=true, cron=0 */5 * * * ?`） |
| 唯一索引 `uk_pos_one_displaying_per_product` 冲突导致同步事务回滚 | fixed | `harness/reports/p-fix-002-product-sync-display-5min-20260603-121257.md`、`harness/reports/p-fix-002d-real-pre-runtime-verify-20260603-123411.md`、`harness/reports/p-fix-002d-remote-deploy-verify-20260603-132805.md` | P-FIX-002B 已修复 + P-FIX-002D 本地验证通过 + P-FIX-002D-REMOTE 远端验证通过：两个周期零冲突 |
| 推广中商品仍有未 DISPLAYING 记录 | open | `harness/reports/p-fix-002-product-sync-display-5min-20260603-121257.md`、`harness/reports/p-fix-002d-real-pre-runtime-verify-20260603-123411.md`、`harness/reports/p-fix-002d-remote-deploy-verify-20260603-132805.md` | 本地 PENDING=371，远端 PENDING=2128；仍需 P-FIX-002E repair（dry-run → 实际执行），区分过期活动、未选中和待重算来源 |
| Git 工作区 dirty 膨胀与批次提交门禁缺失 | fixed | `harness/reports/git-harness-001-worktree-governance-20260603-*.md` | GIT-HARNESS-001 已治理：新增 `harness/skills/git-change-control.md`（Git 全套 Gate）、`harness/skills/git-batch-submit.md`（批次提交流程）、`harness/skills/post-task-gc.md`（Git 清理流程）；在 `AGENT_CONTRACT.md` / `TASK_ROUTING.md` / `FORBIDDEN_SCOPE.md` / `COMPLETION_GATES.md` / `SESSION_EXIT_GATE.md` 落地 Git 强约束和 G0-G4 Git 子门禁。后续每个任务开始前必须执行 Git Intake Gate，结束前必须输出 `DONE_CLEAN` / `DONE_WITH_REGISTERED_DIRTY` / `PARTIAL_DIRTY_REMAINING` / `BLOCKED_DIRTY_UNKNOWN` 之一 |
| ORDER-P0 订单同步数据源不完整：仅接 2704 结算接口，未接入 6468 实际订单接口 | fixed-local, pending-remote | `harness/reports/order-api-729-verify-20260603-174500.md`、`harness/reports/order-api-729-server-verify-20260603-175500.md`、`harness/reports/order-api-6468-verify-20260603-180500.md`、`harness/reports/order-api-6468-raw-probe-20260603-181634.md`、`harness/reports/order-settlement-dual-track-verify-20260603-183157.md`、`harness/reports/evidence-20260603-202253.md`、`harness/reports/evidence-20260603-202320.md` | ORDER-P0-DUAL-SOURCE-SYNC 已在本地 real-pre 接入 6468：`ORDER_SYNC_INSTITUTE api=buyin.instituteOrderColonel fetched=100 inserted=100 updated=0`；订单表 `count(*)=100`，`order_amount/pay_time/estimate_*` 均有值，`settle_amount/effective_*` 未被 6468 写入；2704 继续执行 `ORDER_SYNC_SETTLEMENT ... fetched=0`。远端未部署/未验证；本轮 6468 样本 `pick_source=0`，渠道 mapping 命中正向样本仍为 PENDING。 |
| 真实付款订单 10 分钟 update 窗口可能丢单 | fixed-code, blocked-by-sample | `harness/reports/p0-order-001-real-order-visible-20260603-180450.md` | P0-ORDER-001 已增加 PAY_RECENT 6h 30min 回扫，使用独立锁 `ORDER_SYNC_PAY_RECENT` + 独立 Redis 水位 `order:sync:pay_recent_last_time`，不覆盖 10min 增量水位；增强同步日志含 mode/timeType/inserted/updated/attributed/unattributed/noPickSource/noMapping/failed；mvn test 1688/0/0、backend-real-pre 重启后双 scheduler 立即首次执行均正常；真实订单端到端业务验证需等待商务真实付款样本。注：本任务仅修复"同步窗口/水位/可观测性"层面；如订单数据源本身缺失（见上一条 ORDER-P0 2704/6468），仍需独立任务处理。 |

## 更新规则

- 每个问题必须有状态：`open`、`blocked`、`fixed`、`wontfix`、`deferred`。
- 没有证据的内容不能写成问题结论，只能写“待确认”。
- 修复后必须补 evidence report 路径。
