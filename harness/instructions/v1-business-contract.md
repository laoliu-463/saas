# V1 Business Contract

## 主源

- `docs/01-V1交付范围与边界.md`
- `docs/02-业务闭环总览.md`
- `harness/CURRENT_STATE.md`
- `harness/FORBIDDEN_SCOPE.md`

## V1 三条主链

### 渠道链

认领达人 -> 商品库选品 -> 复制讲解 / 转链 -> 寄样申请 -> 订单同步 -> 渠道业绩 -> 寄样自动完成。

### 招商链

同步活动 -> 活动商品入库 -> 商品上架 -> 审核寄样 -> 订单同步 -> 招商业绩。

### 管理链

用户角色 -> 数据范围 -> 规则配置 -> 各领域读取配置 -> 权限生效。

## 真实闭环判定

订单入库不等于业务闭环。真实闭环必须同时验证：

- `orders.pick_source` 不为空，或存在可解释的 NATIVE / `colonel_buyin_id` 映射证据。
- `orders.default_channel_id` 不为空。
- `orders.default_recruiter_id` 不为空。
- `sample_requests` 能从待交作业变为已完成。
- `performance_records` 正确生成。
- 看板 / 业绩归属正确。

## V1 不做

V1 不做项以 `harness/FORBIDDEN_SCOPE.md` 为准。旧 V2.2 完整方案只能作为背景，不能直接升级为 V1 P0。

