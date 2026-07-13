# User Domain DDD Instruction

## 领域职责

- 负责登录用户上下文、用户、角色、菜单、组织和 `self/group/all` 数据范围。
- 对业务域输出身份、角色、组织和数据范围结果。
- 支撑权限 E2E、越权负例和操作审计。

## 领域不负责

- 不负责商品、订单、寄样、业绩的业务状态机。
- 不负责订单最终归属、提成、独家覆盖或业务结论。

## V1 规则

- 数据范围统一抽象为 `self/group/all`。
- 业务域只消费用户域输出的数据范围，不复制权限规则。
- 权限规则冲突先回到 `docs/07-权限与数据范围.md` 和 `docs/领域/用户域.md`。

## 禁止越界

- 禁止用户域计算订单、寄样、商品或业绩归属。
- 禁止业务域绕过用户域自行实现一套数据范围判断。

## 允许调用的 Facade

- 优先使用用户域应用服务、Facade、查询 API 或权限上下文。
- 如果当前代码没有统一 Facade，本阶段只能记录缺口，不得凭空创建业务规则。

## 必须执行的测试

- 用户域单测。
- admin/group/self 数据范围 API 或 E2E 对比。
- 权限越权负例。

## 完成后必须更新的 state

- `harness/rules/state/snapshots/DOMAIN_STATUS.md` 的用户域状态。
- 相关 evidence report 和 `harness/rules/state/snapshots/KNOWN_ISSUES.md`。

## 失败后必须写入 feedback

- 权限规则冲突、数据范围缺口、越权风险写入 `harness/feedback/` 或 evidence report。
