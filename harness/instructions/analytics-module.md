# Analytics Module DDD Instruction

## 领域职责

- 负责 dashboard、报表、趋势、排行、对账、导出和只读汇总展示。
- 按用户域数据范围读取业绩和订单汇总。

## 领域不负责

- 不负责订单同步、归因、提成、冲正。
- 不负责修改订单、寄样、业绩或配置事实。

## V1 规则

- 分析模块只读汇总表、报表快照或事实查询。
- dashboard 指标必须能通过 API/SQL 对账。
- 不得重新扫描订单产生新的最终归属。

## 禁止越界

- 禁止分析模块重新计算业绩归因。
- 禁止分析模块写回订单、寄样或业绩结论。
- 禁止将展示口径反向固化为业务规则。

## 允许调用的 Facade

- 优先使用分析查询 API、业绩域汇总查询、订单事实只读查询或领域事件产生的汇总。
- 不得直接穿透业务域 Repository 做写操作。

## 必须执行的测试

- dashboard API 与 SQL 对账。
- admin/group/self 数据范围对比。
- 报表导出或页面 E2E。

## 完成后必须更新的 state

- `harness/state/DOMAIN_STATUS.md` 的分析模块状态。
- 看板、报表、对账和导出相关 evidence。

## 失败后必须写入 feedback

- 指标漂移、越权展示、重算归因或只读边界破坏写入 `harness/feedback/` 或 evidence report。
