# real-pre 参数开关契约

本文固定 real-pre 受控部署与真实联调的环境变量口径。真实值只允许写入服务器 `/opt/saas/env/.env.real-pre`，不得提交到仓库。

总原则：real-pre 是真实上游 / 生产形态环境。凡是涉及上游连接、读取、同步、刷新、回调验证和真实写入的开关，在 real-pre 默认开启；关闭只能作为临时冻结、风控降级或外部权限阻塞处理，并必须记录原因、影响范围和恢复计划。

## 基线开关

| 参数 | real-pre 口径 | 说明 |
| --- | --- | --- |
| `APP_TEST_ENABLED` | `false` | 关闭应用侧 mock/test 数据与测试模式 |
| `DOUYIN_TEST_ENABLED` | `false` | 关闭抖音 / 抖店 mock gateway |
| `DOUYIN_REAL_UPSTREAM_MODE` | `live` | 使用真实抖店 upstream，不使用 contract/mock |
| `DOUYIN_TOKEN_AUTO_REFRESH_ENABLED` | `true` | 开启真实 Token 自动刷新，避免上游链路因 Token 过期静默失效 |
| `DOUYIN_WEBHOOK_VERIFY_SIGN` | `true` | 开启真实 Webhook 签名校验 |
| `DOUYIN_REAL_PROMOTION_WRITE_ENABLED` | `true` | 开启真实转链写操作主开关 |
| `ALLOW_REAL_PROMOTION_WRITE` | `true` | 开启真实转链写操作二次确认开关 |
| `ORDER_SYNC_ENABLED` | `true` | 开启真实订单同步，给订单回流、归因和业绩链路提供输入 |
| `PRODUCT_ACTIVITY_SYNC_ENABLED` | `true` | 开启活动商品定时同步，持续刷新上游商品状态并驱动推广中商品自动入库 |
| `TALENT_COLLECT_MODE` | `api` | 达人数据采集使用 API 模式 |
| `TALENT_COLLECT_API_ENABLED` | `true` | 开启达人 API 采集 |
| `TALENT_REFRESH_ENABLED` | `true` | 开启达人刷新 |
| `LOGISTICS_PROVIDER` | `kuaidi100` | 物流上游使用快递100 |
| `LOGISTICS_KD100_ENABLED` | `true` | 开启快递100查询 |
| `LOGISTICS_KD100_SUBSCRIBE_ENABLED` | `true` | 开启快递100订阅 |
| `LOGISTICS_SYNC_ENABLED` | `true` | 开启物流同步 |

以上是 real-pre 真实联调基线。任一项不满足时，不进入真实联调结论；若因外部原因临时关闭，只能把相关验收项记为 `BLOCKED` / `PENDING`，不得写成通过。

## 真实推广写默认开启与降级关闭

| 参数 | 默认 | 作用 |
| --- | --- | --- |
| `DOUYIN_REAL_PROMOTION_WRITE_ENABLED` | `true` | 真实转链写操作主开关 |
| `ALLOW_REAL_PROMOTION_WRITE` | `true` | 真实转链写操作二次确认开关 |

real-pre 默认两项同时为 `true`，后端允许调用真实 `buyin.instPickSourceConvert`，生成真实推广链接并写入 `pick_source_mapping`。这符合 real-pre 作为真实上游 / 生产形态环境的默认口径。

双开关任一被临时置为 `false` 时，属于降级或冻结状态：

- `generateLink()` 返回降级结果（所有链接字段为 null），**不抛出异常**，使商品库”复制基础简介”仍可用。
- `rawUpstreamPost()` 对 `buyin.instPickSourceConvert` 方法抛出 `BusinessException`，阻止原始 API 透传。
- 不调用真实 `instPickSourceConvert`。
- 不写入 `pick_source_mapping`。
- 商品库”复制基础简介”应 PASS（简介文本正常复制，推广链接部分省略）。
- 接口返回 `pickSource=null`、`promoteLink=null`、`shortLink=null`。
- 真实推广链接、`pick_source` 归因和真实成交回流标记为 `BLOCKED_BY_PROMOTION_WRITE_DISABLED`，不是代码失败。

## 临时关闭 / 恢复条件

只有出现以下情况时，才允许临时关闭真实推广写双开关：

- 上游平台冻结、风控或明确要求暂停写入。
- 当前授权主体、Token 或权限包异常，继续写入会污染证据。
- 本轮只做只读排障，需要冻结所有真实写副作用。

关闭时必须记录关闭原因、影响的验收项、预计恢复时间和复原计划。恢复前必须确认 `APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`、`DOUYIN_REAL_UPSTREAM_MODE=live`、`ORDER_SYNC_ENABLED=true`、`PRODUCT_ACTIVITY_SYNC_ENABLED=true`，并留存恢复记录。

## 配置映射

| 环境变量 | application.yml 路径 | Java 注入 | 代码默认值（非 real-pre 口径） |
| --- | --- | --- | --- |
| `APP_TEST_ENABLED` | `app.test.enabled` | `@ConditionalOnProperty` on gateway | `false` |
| `DOUYIN_TEST_ENABLED` | `douyin.test.enabled` | `@ConditionalOnProperty` on gateway | `false` |
| `DOUYIN_REAL_UPSTREAM_MODE` | `douyin.real.upstream-mode` | `DouyinUpstreamModeSupport` | `live` |
| `DOUYIN_TOKEN_AUTO_REFRESH_ENABLED` | `douyin.token.auto-refresh.enabled` | `DouyinTokenRefreshJob.autoRefreshEnabled` | `true` |
| `DOUYIN_WEBHOOK_VERIFY_SIGN` | `douyin.webhook.verify-sign` | `DouyinWebhookController.verifySign` / `RealProdEnvironmentGuard` | `true` |
| `DOUYIN_REAL_PROMOTION_WRITE_ENABLED` | `douyin.real.promotion-write-enabled` | `RealDouyinPromotionGateway.promotionWriteEnabled` | `false` |
| `ALLOW_REAL_PROMOTION_WRITE` | `douyin.real.allow-promotion-write` | `RealDouyinPromotionGateway.allowPromotionWrite` | `false` |
| `ORDER_SYNC_ENABLED` | `order.sync.enabled` | `@ConditionalOnProperty` on sync job | `true` |
| `PRODUCT_ACTIVITY_SYNC_ENABLED` | `product.activity.sync.enabled` | `ProductActivitySyncJob.enabled` | `false` |
| `TALENT_COLLECT_MODE` | `talent.collect.mode` | Talent collect configuration | `api_then_crawler` |
| `TALENT_COLLECT_API_ENABLED` | `talent.collect.api.enabled` | Talent API collect configuration | `false` |
| `TALENT_REFRESH_ENABLED` | `talent.refresh.enabled` | `TalentWeeklyRefreshJob.refreshEnabled` | `false` |
| `LOGISTICS_PROVIDER` | `logistics.provider` | `LogisticsProperties.provider` | `mock` |
| `LOGISTICS_KD100_ENABLED` | `logistics.kd100.enabled` | `Kuaidi100LogisticsGateway` conditional | `false` |
| `LOGISTICS_KD100_SUBSCRIBE_ENABLED` | `logistics.kd100.subscribe-enabled` | `LogisticsProperties.Kd100.subscribeEnabled` | `false` |
| `LOGISTICS_SYNC_ENABLED` | `logistics.sync.enabled` | `SampleLogisticsSyncJob` | `false` |

## 结果判定

| 场景 | 判定 |
| --- | --- |
| 双开关开启，接口返回推广链接并写入 mapping | `PASS` |
| 双开关开启，上游返回错误 | 按上游错误码和日志排查，不得归因为 mock/test 问题 |
| 双开关关闭，基础简介复制成功 | `PASS` |
| 双开关关闭，真实推广链接未生成 | `BLOCKED_BY_PROMOTION_WRITE_DISABLED` |
| 双开关关闭，`pick_source` 归因无样本 | `BLOCKED_BY_PROMOTION_WRITE_DISABLED` |
