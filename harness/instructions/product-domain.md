# Product Domain DDD Instruction

## 领域职责

- 负责商品库、活动商品、商品展示输入、转链和 `pick_source_mapping`。
- 为订单归因提供映射事实。
- 负责商品同步、商品库查询、活动商品审核和转链证据。

## 领域不负责

- 不负责订单最终归属、提成、冲正。
- 不负责分析看板重算业绩。
- 不负责直接写寄样表。

## V1 规则

- 转链成功后必须落 `pick_source_mapping`。
- 活动商品以商品自身上游推广中状态作为自动入库和可操作主判断来源。
- 历史状态断链必须通过商品域 repair 入口修复，不允许裸 SQL 批量直改。

## 禁止越界

- 禁止商品域计算提成或最终归属。
- 禁止商品域绕过寄样域直接创建或改写寄样状态。
- 禁止前端绕过商品域审核补充信息。

## 允许调用的 Facade

- 优先使用商品域应用服务、Facade、商品查询 API、转链 API 或商品事件。
- 订单域消费映射事实，不反向修改商品事实。

## 必须执行的测试

- 商品同步和 repair 验证。
- 商品库查询测试，含 `productId` 精确查询。
- 转链后 `pick_source_mapping` 验证。
- real-pre 真实上游响应或 BLOCKED 证据。

## 完成后必须更新的 state

- `harness/state/DOMAIN_STATUS.md` 的商品域状态。
- 商品库、活动商品、转链和映射相关 evidence。

## 失败后必须写入 feedback

- 商品库漂移、转链失败、映射缺失或审核字段丢失写入 `harness/feedback/` 或 evidence report。
