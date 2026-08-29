# Harness Changelog 1

Source: ../harness-changelog-full.md (split archive index)

# Harness Changelog

## v0.7.0

- 服务费收入双轨公式后端代码对齐（2026-06-06）。
- **决策来源**：用户明确给出：预估服务费收入 = 预估订单额 × 服务费率（未扣除技术服务费）；结算服务费收入 = 结算订单额 × 服务费率 - 技术服务费。
- **行为变化**：
  - `OrderDualTrackAmountResolver` 在上游未直接返回服务费金额但存在订单额 + 服务费率时，按预估 / 结算收入公式补算 `estimate_service_fee` / `effective_service_fee`。
  - `PerformanceCalculationService` 视 `effective_service_fee` 为已扣技术服务费后的结算服务费收入，结算轨不再重复扣 `effective_tech_service_fee`。
  - 数据页订单明细、dashboard metrics、订单汇总和业绩汇总 DTO 的服务费支出公式按预估 / 结算分轨。
- **验证**：后端定向单测 `OrderDualTrackAmountResolverTest`、`PerformanceCalculationServiceTest`、`DataControllerTest`、`DataApplicationServiceOrderSummaryCacheTest`、`PerformanceSummaryServiceTest`、`PerformanceMetricsQueryServiceTest`、`CommissionServiceTest` 已通过；构建、容器重启、健康检查和 real-pre 业务验证待 evidence gate 继续执行。

## v0.6.9

- 服务费收益双轨公式口径更新（2026-06-06）。
- **决策来源**：用户明确给出：预估服务费收益 = 预估服务费收入 - 预估服务费支出 - 技术服务费；结算服务费收益 = 结算服务费收入 - 结算服务费支出。
- **修改文件**：
  - `docs/领域/业绩域.md`、`docs/领域/分析模块.md`、`docs/流程/业绩计算链路.md`：补充服务费收益双轨公式和验收要求。
  - `docs/05-API契约总表.md`、`docs/09-测试验收总览.md`、`docs/04-上线验收清单.md`：更新经营指标 API / 验收口径。
  - `docs/01-V1领域裁剪表.md`、`docs/00-V1范围冻结说明.md`、`docs/V1领域对齐总表.md`、`docs/决策/ADR-002-V1范围优先级.md`：同步 V1 裁剪、范围冻结、对齐表和 ADR 冲突记录。
  - `harness/CURRENT_STATE.md`、`harness/HARNESS_CHANGELOG.md`、`harness/instructions/document-priority.md`、`harness/instructions/performance-domain.md`、`harness/instructions/analytics-module.md`、`harness/skills/performance-dashboard.skill.md`、`harness/state/DECISIONS.md`、`harness/state/DOMAIN_STATUS.md`、`harness/state/current-business-state.md`、`harness/QUALITY_LEDGER.md`：同步 Harness 执行口径。
- **行为变化**：
  - 旧“服务费收益 = 服务费收入 - 技术服务费”不得继续作为双轨统一公式。
  - 结算服务费收益不扣减技术服务费；技术服务费仍作为经营指标展示和预估服务费收益输入。
  - 本次为 docs / harness 口径更新，不宣称后端、前端、SQL 或 real-pre 运行态已完成一致性修复。
- **证据**：`harness/reports/2026-06-12/evidence-20260606-180600.md`、`harness/reports/retro-20260606-180615.md`。
- **范围**：Scope=docs；未修改业务代码、SQL、Docker、env，未重启容器，未部署远端。

## v0.6.8

- 渠道提成文案收口（媒介 → 渠道，2026-06-06）。
- **任务 ID**：DASH-CHANNEL-COMMISSION-LABEL-001。
- **证据**：`harness/reports/2026-06-12/evidence-20260606-123500-channel-commission-label-rename-001.md`。
- **修改文件**：
  - `frontend/src/views/data/index.vue`：业务指标矩阵 label `媒介提成` → `渠道提成`。
  - `frontend/src/views/data/index.test.ts`：断言同步覆盖 `渠道提成`。
- **行为变化**：仅文案；`channelCommission` 字段口径、API 返回、双轨金额、范围过滤均未变化。
- **验证**：`npx vitest run` 622 tests passed；`vue-tsc --noEmit` 0 错误。
- **范围**：Scope=frontend label；未修改后端业务代码、领域合同、ADR。

## v0.6.7

- 毛利纳入 V1 交付范围决策变更（2026-06-05）。
- **决策来源**：用户明确要求毛利要做，撤销原“不做毛利”限制。
- **修改文件**：
  - `AGENTS.md`：撤销“不做毛利口径扩展”。
  - `harness/FORBIDDEN_SCOPE.md`：撤销“不做毛利”和“禁止把毛利作为 P0 验收指标”。
  - `harness/CURRENT_STATE.md`：P0-004 降级为前端补齐任务；毛利字段策略更新。
  - `harness/state/p0-p1-register.md`：RISK-011 P0→P2，状态 REVOKED。
  - `harness/state/KNOWN_ISSUES.md`：DASH-MONEY-P0-004 状态 revoked。
  - `harness/doc/01-instructions/02-V1交付合同.md`：撤销“不做毛利口径扩展”。
  - `harness/instructions/document-priority.md`：毛利字段策略更新。
  - `harness/QUALITY_LEDGER.md`：P0-004 标记已撤销。
  - `docs/02-V1不做清单.md`：Y-04 毛利撤销。
  - `docs/01-V1领域裁剪表.md`：Y-04 毛利改为“做”，补充毛利公式。
  - `docs/00-V1范围冻结说明.md`：毛利撤销。
  - `docs/03-项目剩余事项与任务看板.md`：Y-04 毛利撤销。
  - `docs/V1对齐-业绩域.md`：Y-04 改为“做”。
  - `docs/V1领域对齐总表.md`：毛利裁剪和 Y-04 改为“做”。
- **行为变化**：
  - 毛利 = 服务费收益 - 招商提成 - 渠道提成，纳入 V1 交付与验收。
  - 后端已计算并返回 grossProfit，无需后端改动。
  - 前端毛利展示已补齐（GROSS-PROFIT-DISPLAY-001，2026-06-05 数据看板经营指标矩阵已补齐）。
  - 原 DASH-MONEY-P0-004 降级为 P2 前端展示补齐任务。
  - 全部 9 类指标（× 2 轨 = 18 个指标）确认纳入 V1。
  - 数据看板“媒介提成”统一为“渠道提成”（2026-06-05 用户确认媒介=渠道），`index.vue` + `index.test.ts` 已修正。
- **代码修改**：
  - `frontend/src/views/data/index.vue`：新增经营指标矩阵，补齐“渠道提成”和“毛利”等指标展示；“媒介提成”→“渠道提成”。
  - `frontend/src/views/data/index.test.ts`：测试断言同步覆盖 9 类经营指标；“媒介提成”→“渠道提成”。
- **范围**：Scope=frontend；未修改后端业务代码。

## v0.6.6

- 远端部署 JAR 刷新门禁加固（2026-06-04 20:55）。
- **问题证据**：`agent-do.ps1 -DeployRemote true` 首次完成后，远端源码已到 `20500b2`，但运行容器 `/app/app.jar` 仍是 6 月 3 日产物，且不含 `OrderDetailVO.class`；手动执行远端 Maven 构建后，新 `backend/target/colonel-saas.jar` 才包含该 class。
- **修改文件**：
  - `harness/commands/deploy-remote.ps1`。
- **行为变化**：
  - 远端 Maven 构建从 `mvn -DskipTests package` 改为 `mvn -DskipTests clean package`，避免旧 `target/` 产物残留。
  - 远端构建后输出 `backend/target/colonel-saas.jar` 时间和大小。
  - compose 重建后比较 host JAR 与容器 `/app/app.jar` size，不一致则部署失败。
- **运行态修正**：已手动重新执行远端 Maven build + `docker compose up -d --build backend-real-pre frontend-real-pre`，容器内已确认存在 `OrderDetailVO.class`，backend/frontend health 通过。

## v0.6.5

- Git 推送 helper 与当前 Git 门禁对齐（2026-06-04 20:44）。
- **修改文件**：
  - `harness/commands/git-push-safe.ps1`。
- **行为变化**：
  - 删除 `git add -A`，改为对 `git status` 识别出的变更逐文件执行 `git add -- <file>`。
  - 提交前新增 `git diff --cached --check`。
  - 推送从单一默认上游改为按 `gitee` -> `origin` 顺序双远端推送当前分支。
- **证据**：
  - `harness/reports/2026-06-12/evidence-20260604-204437.md`。
  - `harness/reports/retro-20260604-204454.md`。
- **范围**：Harness 工具链最小修正；未修改后端、前端、SQL、Docker 或 env；未重启容器；未部署远端。

## v0.6.4

- 订单明细表字段对齐 real-pre 验证收口完成（2026-06-04 19:17）。
- **代码提交**：`abf3f9eb fix: align order detail table fields`，已推送 `feature/auth-system`。
- **后端补齐**：
  - `/data/orders/detail` 与 `/orders/exports/detail` 增加 `activityName`、`partnerId`、`partnerName`、`recruiterName` 兼容参数。
  - 导出字段更新为 16 列订单明细口径。
  - **历史记录，已被 v0.6.9 supersede**：未结算 effective/settle 字段不回退 estimate/pay；当时服务费收益记录为“服务费收入 - 技术服务费”。当前服务费收益公式已更新为预估 / 结算两轨不同口径。
- **前端补齐**：
  - 数据平台保留汇总模块，订单明细 Tab 展示订单级 16 列。
  - 商品信息展示商品图、商品名和商品ID；保留自定义表头和导出。
  - V1 订单明细页默认不展示毛利；数据看板经营指标矩阵展示毛利；“媒介提成”在看板文案中映射现有 `channelCommission` 字段。
- **验证**：
  - 后端 targeted 99 tests PASS。
  - 前端 targeted 43 tests PASS。
  - `mvn -DskipTests package` PASS。
  - `npm run build` PASS。
  - `agent-do.ps1 -Env real-pre -Scope full` PASS，evidence `harness/reports/2026-06-12/evidence-20260604-191102.md`。
  - Playwright 页面 smoke PASS：`runtime/qa/out/order-detail-page-smoke-20260604-191711/result.json`，截图 `runtime/qa/out/order-detail-page-smoke-20260604-191711/order-detail-table.png`。
- **非阻塞噪声**：Google Fonts 被 CSP 拦截，未影响订单明细关键业务请求。
- **终态**：DONE_CLEAN（本地 real-pre 已验证；远端部署未请求）。

## v0.6.3

- 订单明细表复刻与前后端字段对齐完成（2026-06-04 18:15）。
- **新增文件**：
  - `backend/.../vo/data/OrderDetailVO.java`（16列订单明细 VO）
  - `frontend/src/views/data/OrderDetailTab.vue`（明细Tab子组件）
  - `harness/reports/2026-06-12/evidence-20260604-181500-order-detail-tab.md`（执行证据）
- **后端变更**：
  - `PerformanceRecordMapper` 新增 `findByOrderIds` 批量查询
  - `DataApplicationService` 新增 `getOrderDetailPage` + `exportOrderDetail` + 辅助方法
  - `DataController` 新增 `/data/orders/detail` + `/orders/exports/detail` 端点
  - `DataControllerTest` 新增 5 个测试（40/40 PASS）
- **前端变更**：
  - `api/data.ts` 新增 `getOrderDetailPage` + `exportOrderDetail`
  - `order-list-query.ts` 新增 `buildOrderDetailPageParams`
  - `OrderList.vue` 新增Tab切换 + 明细Tab导出调度
  - `OrderList.test.ts` 新增 4 个测试（8/8 PASS）
- **构建验证**：mvn compile/test/package PASS, vitest PASS, vite build PASS, docker real-pre restart healthy
- **终态**：PARTIAL（smoke 受沙箱网络限制未验证）

## v0.6.2

- DASHBOARD-MONEY-AUDIT-001 数据看板资金口径只读审查完成（2026-06-04 13:19）。
- **新增文件**：
  - `harness/reports/dashboard-money-audit-001-20260604-131908.md`（主审计报告，451 行，12 节完整审查）。
  - `harness/reports/2026-06-12/evidence-20260604-131908-dashboard-money-audit-001.md`（执行证据，160 行）。
  - `harness/reports/retro-20260604-131908-dashboard-money-audit-001.md`（复盘，100 行）。
- **修改文件**：
  - `harness/CURRENT_STATE.md`：版本 v0.6.0 → v0.6.2；新增 DASHBOARD-MONEY-AUDIT-001 完成段；当前 P0/P1 新增 4 条。
  - `harness/state/KNOWN_ISSUES.md`：新增 3 条 P0 问题卡片（DASH-MONEY-P0-001/002/004）。
  - `harness/state/p0-p1-register.md`：新增 RISK-009/010/011/012。
  - `harness/QUALITY_LEDGER.md`：业绩域 B- → C、分析模块 C → D。
- **结论**：FAIL（4 P0 + 4 P1 + 2 P2）。
- **未做**：
  - 未修改业务代码 / SQL / Docker / env。
  - 未重启容器 / 未部署远端。
  - 未执行数据库写操作。

