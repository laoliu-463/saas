# Retro: DDD-USER-DATASCOPE-DASHBOARD

## 本轮结论

- Dashboard 数据范围分支已从分析模块迁到用户域 `DataScopePolicy` 消费点。
- `DashboardService` 不再维护本地 `switch(dataScope)`。
- 缺失上下文时的受限范围 fail-closed 行为通过 `DataScopePolicy.requiresFilter` 明确表达并由测试覆盖。

## 证据

- RED boundary test 先失败，证明测试能识别 Dashboard 未消费用户域 policy。
- Focused 67 tests PASS。
- Expanded 117 tests PASS。
- 后端 package PASS。
- 本地 real-pre 后端 rebuild / restart / health PASS。
- code-review-graph CLI 已更新。

## 边界确认

- 用户域负责解释 `DataScope` 到过滤决策。
- 分析模块只消费过滤决策控制查询可见性。
- 本轮未改订单归因、业绩归属、指标公式、历史数据或权限业务规则。

## Harness 反馈

- `permission-next` 报告目录已满 10 个文件，本轮改用 `datascope-next`，无需升级 Harness 规则。
- 本轮无新增自动化脚本诉求，暂不升级 Harness。

## 下一步

- 继续小切片 `DDD-USER-DATASCOPE-DATA-APPLICATION`，处理 `DataApplicationService` 剩余 5 处 `switch(dataScope)`。
- 如果要声明仓库级完成，需要在干净提交边界内提交 / 推送，并补授权态业务 E2E 或明确豁免。
