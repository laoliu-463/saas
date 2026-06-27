# Retro: DDD-USER-DATASCOPE-DATA-APPLICATION

## 本轮结论

- 数据页服务的数据范围判断已收口到用户域 `DataScopePolicy`。
- `DataApplicationService` 不再维护本地 `switch(dataScope)`。
- 缺少 user/dept 上下文时的原有 fail-fast 行为已保留，并由 `DataControllerTest` 覆盖。

## 证据

- RED boundary test 先失败，证明测试能识别数据页服务未消费用户域 policy。
- Focused 89 tests PASS。
- Expanded 130 tests PASS。
- 后端 package PASS。
- 本地 real-pre 后端 rebuild / restart / health PASS。
- code-review-graph CLI 已更新。

## 边界确认

- 用户域负责解释 `DataScope` 与上下文要求。
- 分析模块只消费过滤决策控制查询可见性。
- 本轮未改订单事实、归因、业绩公式、导出列、历史数据或前端展示口径。

## Harness 反馈

- `datascope-next` 目录当前未满 10 文件，本轮继续复用该目录。
- 本轮无新增自动化脚本诉求，暂不升级 Harness。

## 下一步

- 扫描剩余非 `switch(dataScope)` 的本地数据范围 if/else 或 `PermissionChecker` / `DataScopeResolver` 消费不统一点。
- 后续若要声明仓库级完成，需要补授权态 admin/group/self API 或 E2E 对比，并在干净提交边界内提交 / 推送。
