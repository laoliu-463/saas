# real-pre 参数开关契约

本文固定 real-pre 受控部署与真实联调的环境变量口径。真实值只允许写入服务器 `/opt/saas/env/.env.real-pre`，不得提交到仓库。

## 基线开关

| 参数 | real-pre 口径 | 说明 |
| --- | --- | --- |
| `APP_TEST_ENABLED` | `false` | 关闭应用侧 mock/test 数据与测试模式 |
| `DOUYIN_TEST_ENABLED` | `false` | 关闭抖音 / 抖店 mock gateway |
| `DOUYIN_REAL_UPSTREAM_MODE` | `live` | 使用真实抖店 upstream，不使用 contract/mock |
| `ORDER_SYNC_ENABLED` | `true` | 开启真实订单同步，给订单回流、归因和业绩链路提供输入 |

以上四项是 real-pre 真实联调基线。任一项不满足时，不进入真实联调结论。

## 真实推广写双确认

| 参数 | 默认 | 作用 |
| --- | --- | --- |
| `DOUYIN_REAL_PROMOTION_WRITE_ENABLED` | `false` | 真实转链写操作主开关 |
| `ALLOW_REAL_PROMOTION_WRITE` | `false` | 真实转链写操作二次确认开关 |

只有当两项同时为 `true` 时，后端才允许调用真实 `buyin.instPickSourceConvert`，生成真实推广链接并写入 `pick_source_mapping`。

双开关任一为 `false` 时：

- `generateLink()` 返回降级结果（所有链接字段为 null），**不抛出异常**，使商品库”复制基础简介”仍可用。
- `rawUpstreamPost()` 对 `buyin.instPickSourceConvert` 方法抛出 `BusinessException`，阻止原始 API 透传。
- 不调用真实 `instPickSourceConvert`。
- 不写入 `pick_source_mapping`。
- 商品库”复制基础简介”应 PASS（简介文本正常复制，推广链接部分省略）。
- 接口返回 `pickSource=null`、`promoteLink=null`、`shortLink=null`。
- 真实推广链接、`pick_source` 归因和真实成交回流标记为 `BLOCKED_BY_PROMOTION_WRITE_DISABLED`，不是代码失败。

## 人工开启条件

只有验收目标明确包含以下任一项时，才允许人工开启真实推广写双开关：

- 商品库复制简介必须携带真实推广链接。
- 需要生成真实 `pick_source` 并落库取证。
- 需要验证真实成交通过推广链接回流并完成归因。

开启前必须确认 `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`、`ORDER_SYNC_ENABLED=true`，并留存人工批准和复原计划。

## 配置映射

| 环境变量 | application.yml 路径 | Java 注入 | 默认值 |
| --- | --- | --- | --- |
| `APP_TEST_ENABLED` | `app.test.enabled` | `@ConditionalOnProperty` on gateway | `false` |
| `DOUYIN_TEST_ENABLED` | `douyin.test.enabled` | `@ConditionalOnProperty` on gateway | `false` |
| `DOUYIN_REAL_UPSTREAM_MODE` | `douyin.real.upstream-mode` | `DouyinUpstreamModeSupport` | `live` |
| `DOUYIN_REAL_PROMOTION_WRITE_ENABLED` | `douyin.real.promotion-write-enabled` | `RealDouyinPromotionGateway.promotionWriteEnabled` | `false` |
| `ALLOW_REAL_PROMOTION_WRITE` | `douyin.real.allow-promotion-write` | `RealDouyinPromotionGateway.allowPromotionWrite` | `false` |
| `ORDER_SYNC_ENABLED` | `order.sync.enabled` | `@ConditionalOnProperty` on sync job | `true` |

## 结果判定

| 场景 | 判定 |
| --- | --- |
| 双开关关闭，基础简介复制成功 | `PASS` |
| 双开关关闭，真实推广链接未生成 | `BLOCKED_BY_PROMOTION_WRITE_DISABLED` |
| 双开关关闭，`pick_source` 归因无样本 | `BLOCKED_BY_PROMOTION_WRITE_DISABLED` |
| 双开关开启，接口返回推广链接并写入 mapping | `PASS` |
| 双开关开启，上游返回错误 | 按上游错误码和日志排查，不得归因为 mock/test 问题 |
