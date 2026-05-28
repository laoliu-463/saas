# 转链与 pick_source 归因

> 范围标记：`V1 必做`、`V1 简化`、`V1 不做`、`V2 预留`、`历史归档`。

## 对接目标

- [V1 必做] 商品域调用抖音转链接口，生成可推广链接并落 `pick_source_mapping`。
- [V1 必做] `pick_source_mapping` 是订单归因链路的事实输入。

## 当前事实

- [V1 必做] 转链能力属于商品域。
- [V1 必做] 订单域消费 `pick_source` 等输入，但不做最终提成。
- [V1 必做] 业绩域根据映射、订单事实和配置计算最终归属。
- [V1 必做] real-pre 真实转链写操作受双开关控制：`DOUYIN_REAL_PROMOTION_WRITE_ENABLED` 对应后端 `douyin.real.promotion-write-enabled`，`ALLOW_REAL_PROMOTION_WRITE` 对应后端 `douyin.real.allow-promotion-write`，默认均为 `false`。商品库复制简介若需要携带推广链接，必须在人工批准窗口同时设置为 `true`。
- [V1 简化] V1 优先保证转链、映射落库、订单可追溯。

## 数据流

1. [V1 必做] 用户在内部商品页面触发转链。
2. [V1 必做] 后端 Gateway 调用转链接口，例如旧文档记录的 `buyin.instPickSourceConvert` 能力。
3. [V1 必做] 商品域保存第三方响应摘要和 `pick_source_mapping`。
4. [V1 必做] 订单同步时订单域保存 `pick_source` 等归因输入。
5. [V1 必做] 业绩域完成最终归属和提成计算。

## 验收证据

- [V1 必做] 转链接口请求 / 响应摘要。
- [V1 必做] `pick_source_mapping` SQL/API 记录。
- [V1 必做] 至少一笔订单能从 `pick_source` 追溯到映射和业绩明细。
- [V1 必做] 后端日志需留存 `promotion_convert_result=success|failed`，包含 `product_id`、`channel_id`、`pick_source` 和调用结果。
- [V1 必做] 证据路径写入 [../验收/验收证据索引.md](../验收/验收证据索引.md)。

## 不做

- [V1 不做] 不在商品域计算提成。
- [V1 不做] 不在订单域应用独家覆盖。
- [V2 预留] 多渠道归因冲突、归因重放和复杂归因窗口后续设计。

## 来源

- [历史归档] `docs/归档/旧版领域文档/02-商品域.md`
- [历史归档] `docs/archive/runbooks/20-real-pre商品订单归因逻辑排查.md`
