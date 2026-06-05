# Current Business State

## 用户域

- 已完成：登录、角色、菜单、组织和 `self/group/all` 数据范围主链路。
- 未完成：权限注解口径仍需持续审计。
- 当前风险：前端隐藏按钮不能替代后端权限校验。
- 验证方式：`rbac-scope.evals.md`、多账号 API / E2E。
- 关联 eval：`harness/evals/rbac-scope.evals.md`
- 关联 skill：`harness/skills/domain-alignment.skill.md`

## 配置域

- 已完成：配置读取、规则参数、变更审计主链路。
- 未完成：复杂规则治理和审计湖不属于 V1。
- 当前风险：配置域被误写成执行业务规则。
- 验证方式：配置 API、审计表、操作日志。
- 关联 eval：`harness/evals/v1-business-closure.evals.md`
- 关联 skill：`harness/skills/domain-alignment.skill.md`

## 商品域

- 已完成：商品库、活动商品同步、转链、`pick_source_mapping` 主链路能力。
- 未完成：历史推广中商品可能仍需 repair / backfill。
- 当前风险：`selected_to_library` 漂移导致商品库选品入口不完整。
- 验证方式：商品 API、`product_operation_state` SQL、转链映射 SQL。
- 关联 eval：`harness/evals/product-library.evals.md`
- 关联 skill：`harness/skills/product-library.skill.md`

## 达人域

- 已完成：达人资料、标签、地址、跟进和渠道关系主链路。
- 未完成：真实达人信息第三方补充依赖权限和样本。
- 当前风险：旧文档采集方案与当前 Gateway / 系统内资料口径混淆。
- 验证方式：达人 API、跟进记录、权限边界 E2E。
- 关联 eval：`harness/evals/v1-business-closure.evals.md`
- 关联 skill：`harness/skills/domain-alignment.skill.md`

## 寄样域

- 已完成：申请、审批、发货、待交作业、订单事件触发自动完成主链路设计。
- 未完成：real-pre 自动完成仍依赖真实归因订单样本。
- 当前风险：订单归因失败会表现为寄样自动完成失败。
- 验证方式：`sample_requests` SQL、订单事件日志、E2E。
- 关联 eval：`harness/evals/sample-auto-complete.evals.md`
- 关联 skill：`harness/skills/sample-lifecycle.skill.md`

## 订单域

- 已完成：订单事实、退款事实、同步日志和归因输入。
- 未完成：real-pre 历史订单 `pick_source` 大量为空，不能证明渠道归因闭环。
- 当前风险：把历史无 `pick_source` 样本误判为代码 bug。
- 验证方式：订单 SQL/API、mapping SQL、同步日志。
- 关联 eval：`harness/evals/order-attribution.evals.md`
- 关联 skill：`harness/skills/order-attribution.skill.md`

## 业绩域

- 已完成：最终归属、提成、冲正、双轨金额和汇总输出主链路。
- 未完成：复杂财务结算、多账期治理和财务毛利不属于 V1；经营毛利已按 2026-06-05 用户决策纳入 V1 指标。
- 当前风险：订单域或分析模块误算业绩归属。
- 验证方式：`performance_records` SQL、汇总表、计算日志。
- 关联 eval：`harness/evals/v1-business-closure.evals.md`
- 关联 skill：`harness/skills/performance-dashboard.skill.md`

## 分析模块

- 已完成：dashboard、报表、导出和只读汇总展示。
- 未完成：高级 BI 和整体数据平台导出不属于 V1。
- 当前风险：分析模块重算业绩归属。
- 验证方式：dashboard API、汇总 SQL、E2E。
- 关联 eval：`harness/evals/v1-business-closure.evals.md`
- 关联 skill：`harness/skills/performance-dashboard.skill.md`
