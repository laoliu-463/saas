# P0/P1/P2 Register

| 编号 | 领域 | 级别 | 现象 | 根因 | 当前状态 | 证据路径 | 下一步 | 是否阻塞 V1 闭环 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RISK-001 | 订单域 / 商品域 | P0 | real-pre 历史订单 / 6468 样本 `pick_source` 为空 | 历史订单未通过系统转链或当前样本不足；远端 6468 已入库但 100 单 `pick_source=0`、`channel_user_id=0` | PENDING_BY_SAMPLE | `harness/reports/order-p0-dual-source-remote-verify-20260603-205719.md` | 采集真实系统转链订单样本，并执行渠道正向可见性验证 | 是，阻塞渠道真实闭环 |
| RISK-002 | 寄样域 | P1 | 寄样自动完成无法用历史样本证明 | 依赖订单归因后的 channel/talent/product/pay_time | PENDING | `harness/evals/sample-auto-complete.evals.md` | 用归因订单样本复验 | 条件阻塞 |
| RISK-003 | 商品域 | P1 | 推广中历史商品可能未入库 | 历史 `selected_to_library` 未补齐 | PARTIAL | `harness/runbooks/product-library-backfill-check.md` | repair dry-run 后确认写入 | 条件阻塞 |
| RISK-004 | 权限 | P1 | 权限注解口径仍需审计 | `@RequiresRole` / `@DataScope` 覆盖不完整风险 | PENDING | `docs/07-权限与数据范围.md` | 运行 RBAC eval | 可能阻塞 |
| RISK-005 | 文档 | P2 | V2/V2.5 旧口径可能干扰 V1 | 旧文档范围大于 V1 | ACTIVE | `harness/instructions/document-priority.md` | 冲突追加 ADR-002 | 不直接阻塞 |
