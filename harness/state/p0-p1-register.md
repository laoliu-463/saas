# P0/P1/P2 Register

> **三文件分工指针**：
> - 本文件（`p0-p1-register.md`）：业务 P0/P1/P2 风险表，编号 `RISK-NNN`，按级别排版。
> - `state/KNOWN_ISSUES.md`：业务问题卡片，含 open / blocked / fixed / wontfix / deferred 状态。
> - `state/known-risks.md`：风险分类视图（按业务域组织）。
> - `state/HARNESS_DEBT.md`：**Harness 自身**的工程债务（harness / docs / scripts / 临时文件）。
> 业务 DEBT 优先进入本文件或 `KNOWN_ISSUES.md`；harness DEBT 必须进 `HARNESS_DEBT.md`。

| 编号 | 领域 | 级别 | 现象 | 根因 | 当前状态 | 证据路径 | 下一步 | 是否阻塞 V1 闭环 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RISK-001 | 订单域 / 商品域 | P0 | real-pre 历史订单 / 6468 样本 `pick_source` 为空 | 历史订单未通过系统转链或当前样本不足；远端 6468 已入库但 100 单 `pick_source=0`、`channel_user_id=0` | PENDING_BY_SAMPLE | `harness/reports/order-p0-dual-source-remote-verify-20260603-205719.md` | 采集真实系统转链订单样本，并执行渠道正向可见性验证 | 是，阻塞渠道真实闭环 |
| RISK-002 | 寄样域 | P1 | 寄样自动完成无法用历史样本证明 | 依赖订单归因后的 channel/talent/product/pay_time | PENDING | `harness/evals/sample-auto-complete.evals.md` | 用归因订单样本复验 | 条件阻塞 |
| RISK-003 | 商品域 | P1 | 推广中历史商品可能未入库 | 历史 `selected_to_library` 未补齐 | PARTIAL | `harness/runbooks/product-library-backfill-check.md` | repair dry-run 后确认写入 | 条件阻塞 |
| RISK-004 | 权限 | P1 | 权限注解口径仍需审计 | `@RequiresRole` / `@DataScope` 覆盖不完整风险 | PENDING | `docs/07-权限与数据范围.md` | 运行 RBAC eval | 可能阻塞 |
| RISK-005 | 文档 | P2 | V2/V2.5 旧口径可能干扰 V1 | 旧文档范围大于 V1 | ACTIVE | `harness/instructions/document-priority.md` | 冲突追加 ADR-002 | 不直接阻塞 |
| RISK-006 | 寄样域 / 权限 | P1 | P0-SAMPLE-001 远端核心寄样链路已通过，但不能按指定 `channel_staff` 和“已分配商品 biz_leader”完成全量验收 | 远端 `channel_staff` 无私海达人；等价渠道账号“玄同”无法用已知测试口径登录；远端全部 `product_operation_state.assignee_id` 为空；另观察到 `channel_staff` 可查到该待审核单 | PARTIAL_CORE_PASS | `harness/reports/p0-sample-001-remote-verify-20260603-221004.md` | 单独准备渠道账号私海达人与商品 assignee 前置；另起 RBAC 专项复核 `/api/samples` 待审核单可见性 | 条件阻塞 |
| RISK-007 | 订单域 | P1 | 渠道归因真实闭环样本不足 | 系统侧转链 + pick_source_mapping 写入（`v.MxZLIw`）已验证；远端 real-pre 在验证窗口内 6468 / colonelMultiSettlementOrders 上游 `pages=0 fetched=0`，无法产生带 `pick_source` 的真实订单 | BLOCKED_BY_SAMPLE | `harness/reports/order-attribution-sample-20260603-222120.md` | 在外部用真实抖店账号点击 `v.MxZLIw` 推广链接完成至少 1 单购买；或等下一个同步周期后重新核查 `colonelsettlement_order` | 条件阻塞 |
| RISK-008 | Git 工作区 | P1 | Batch B 8 文件 dirty 未提交（writeBackClaimAddress + 测试 + 前端 modal + 样本归因报告） | `49aefbda` 之后会话未执行 commit；缺 writeBackClaimAddress 设计/evidence/retro 配套文档 | DIRTY_REGISTERED | `harness/reports/git-intake-001-dirty-classify-20260603-225000.md` | 补全文档后单独批次 Batch B 提交与远端部署对齐 | 不直接阻塞 |
| RISK-009 | 业绩域 / 分析模块 | P0 | performance_records.settle_amount 被回退逻辑污染，全部 404 条 settle_amount=pay_amount（应=0） | PerformanceCalculationService:113 回退逻辑 `settleAmount > 0 ? settleAmount : actualAmount` | OPEN | `harness/reports/dashboard-money-audit-001-20260604-131908.md` | DASHBOARD-MONEY-FIX-001 删除回退逻辑 + 历史数据清理 | 是，阻塞结算轨验收 |
| RISK-010 | 分析模块 | P0 | 旧版 /dashboard/summary 单轨接口，字段映射错误（settle_colonel_commission 当服务费） | DashboardService 早期设计，未随双轨需求升级 | OPEN | `harness/reports/dashboard-money-audit-001-20260604-131908.md` | DASHBOARD-MONEY-FIX-002 废弃或修复 | 条件阻塞（视 V1 验收引用） |
| RISK-011 | 分析模块 | P0 | V1 不做毛利但前端仍展示 grossProfit | MetricsVO + data/index.vue 未做 V1 裁剪 | OPEN | `harness/reports/dashboard-money-audit-001-20260604-131908.md` | DASHBOARD-MONEY-FIX-001 前端隐藏毛利 | 是，阻塞 V1 验收口径 |
| RISK-012 | 分析模块 | P1 | DataApplicationService.buildMetrics talentCommission 计算逻辑错误 | `talentCommission = svcFee - techFee - profit` 但 profit=svcFee-techFee 导致结果=0 | OPEN | `harness/reports/dashboard-money-audit-001-20260604-131908.md` | DASHBOARD-MONEY-FIX-001 修正为 recruiter+channel | 条件阻塞 |
