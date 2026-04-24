# 抖音 SDK 接入与联调指南（实况版）

**版本**：V1.5
**更新日期**：2026-04-24
**状态**：执行依据

---

## 1. 当前状态

- SDK 封装代码已完成
- 本地单测已通过
- Token 链路已升级为抖店官方 SDK `token.create/token.refresh` 协议
- **第三方真实环境联调尚未完成**（当前重点：真实授权码换 token 验收）

---

## 2. 代码路径

- 客户端：`backend/src/main/java/com/colonel/saas/douyin/DouyinApiClient.java`
- Token 服务：`backend/src/main/java/com/colonel/saas/douyin/DouyinTokenService.java`
- Token SDK 适配：`backend/src/main/java/com/colonel/saas/douyin/DoudianTokenGateway.java`
- API：
  - `douyin/api/ActivityApi.java`
  - `douyin/api/ProductApi.java`
  - `douyin/api/OrderApi.java`
  - `douyin/api/PromotionApi.java`
  - `douyin/api/TalentApi.java`

---

## 3. 已实现机制

1. Token 创建与刷新（抖店协议）
- 控制器接口：`POST /douyin/tokens`、`POST /douyin/token-refreshes`、`GET /douyin/tokens`
- SDK：`TokenCreateRequest`、`TokenRefreshRequest`
- Redis 锁：`douyin:token:lock:{appId}`
- 缓存：`douyin:token:{appId}` / `douyin:refresh:{appId}` / `douyin:token:expire_at:{appId}`
- 刷新 token TTL：14 天（与接口文档保持一致）
- 定时刷新任务：`backend/src/main/java/com/colonel/saas/job/DouyinTokenRefreshJob.java`

2. 联调接口（后端）
- `GET /douyin/activities`
- `POST /douyin/activities`
- `PUT /douyin/activities/{activityId}`
- `GET /douyin/activities/{activityId}`
- `GET /douyin/activity-products`
- `GET /douyin/activities/{activityId}/products`
- `POST /douyin/product-material-status-checks`
- `GET /douyin/order-settlements`
- `POST /douyin/activity-product-cancellations`
- `POST /douyin/activity-product-cancellations/raw`
- `GET /douyin/tokens`
- `POST /douyin/tokens`
- `POST /douyin/token-refreshes`

说明：
- HTTP 客户端联调时请优先使用英文 RESTful 路径，不要再使用旧 `/api/douyin/...` 描述或中文路径，避免和当前代码实际路由不一致

3. 订单同步
- 滑窗任务（每 10 分钟）
- 去重写库
- 归因优先级链路

4. 风险控制
- 异常分支统一抛业务异常
- 不持久化敏感明文

---

## 4. Token API 规范（当前实现）

1. `POST /douyin/tokens`（无需 access_token）
- 工具型应用：`grant_type=authorization_code` 且 `code` 必填
- 自用型应用：`grant_type=authorization_self`（有 code 时可使用 `authorization_code`）
- 支持参数：`code`、`grant_type`、`test_shop`、`shop_id`、`auth_id`、`auth_subject_type`

2. `POST /douyin/token-refreshes`（无需 access_token）
- 必填参数：`grant_type=refresh_token`、`refresh_token`
- 刷新行为对齐文档：
  - access_token 过期前 1 小时之外刷新：可能返回原 token，且有效期不变
  - access_token 过期前 1 小时内刷新：返回新 token，旧 token 继续有效 1 小时
  - access_token 过期后刷新：返回新 token，旧 token 立即失效

3. 关键业务码
- `31007/31021`：授权 code 过期或失效（code 有效期 10 分钟）
- `31006/31020/31005`：授权不存在或失效，需重新授权
- `31012`：并发创建/刷新冲突，需降低并发并重试
- `31009/31008`：refresh_token 不存在或过期，需重新走授权
- `31011`：应用不存在或被删除，需检查应用配置
- `31003`：grant_type 错误，刷新时必须为 `refresh_token`

---

## 5. 待联调清单（必须完成）

1. Token 真联调
- 验证 `token.create`：code 换 token（测试店铺可带 `test_shop=1`）
- 验证 `token.refresh`：刷新成功与并发 31012 分支

2. 三接口联调
- 活动列表
- 商品列表（已切换到 `alliance.colonelActivityProduct`，旧接口 `buyin.colonel.product.list` 已出现 `70000 isp.api-service-off`）
- 订单列表（时间窗口）
4. 活动管理联调
- 验证 `alliance.colonelActivityCreateOrUpdate`：创建/编辑活动
- 必填时间参数：`apply_start_time`、`apply_end_time`
- 验证新增入参：`ad_commission_rate`、`ad_service_rate`、`cos_limit_type`

3. 联调后回归
- 订单入库
- 归因优先级正确
- 寄样自动完成触发

---

## 6. 联调记录模板

每次联调请补充：
- 日期
- 环境（appId/shopId）
- 接口
- 请求参数
- 关键响应字段
- 结果（成功/失败）
- 问题与处理

---

## 7. 当前结论

SDK 开发状态：**开发完成（含 token.create/token.refresh 协议对齐），联调未完成**。  
下一步必须进入真实环境验证，不建议直接进入上线阶段。

---

## 8. 自动刷新配置（新增）

- `DOUYIN_TOKEN_REFRESH_THRESHOLD_SECONDS`：判定“即将过期”的阈值（默认 300 秒）
- `DOUYIN_TOKEN_AUTO_REFRESH_ENABLED`：是否启用定时自动刷新（默认 `true`）
- `DOUYIN_TOKEN_AUTO_REFRESH_CRON`：自动刷新任务 cron（默认 `0 */10 * * * ?`）

执行策略：
- 仅在“无 access_token”或“token 即将过期”时调用 `POST /douyin/token-refreshes`
- 有 `reauthorizeRequired=true` 标记时跳过自动刷新，避免无效重试
- 始终使用 Redis 分布式锁，避免并发刷新触发 `31012`

---

## 9. 团长活动接口排错补充（2026-04-22）

- 若出现 `40003 unknown error`，优先检查请求是否缺失 `apply_start_time`
- SDK 客户端已增强错误透传：优先读取 `sub_msg`，并在日志打印 `sub_code/log_id`
- 日志关键字：`Douyin API business error, method=alliance.colonelActivityCreateOrUpdate`

## 10. 活动商品接口切换说明（2026-04-22）

- 新接口：`alliance.colonelActivityProduct`
- 旧接口：`buyin.colonel.product.list`（真实环境已出现下线/不可用返回）
- 关键参数对齐：
  - 必填：`activity_id`、`count`（最大 20）
  - 推荐：`retrieve_mode=1`（游标模式）、`cursor`（字符串游标）
  - 排序建议：`search_type=4`（按更新时间）、`sort_type=1`（降序）
- 常见返回码：
  - `50002 / isv.business-failed:4197`：需【招商团长】角色授权后访问
  - `50002 / isv.business-failed:4097`：分页大小非法（每页最多 20）
  - `20000 / isp.api-service-off`：接口不存在或已下线（通常是调用了旧 method）

## 11. 团长招商能力追加（2026-04-22）

- 双佣金字段适配：`alliance.colonelActivityProduct`
  - 已在后端适配字段：`cos_type`、`ad_service_ratio`、`activity_ad_cos_ratio`
  - 新增派生字段：`dual_commission_enabled`
- 终止合作接口：`alliance.colonelActivityProductCancel`
  - 新增联调入口：`POST /douyin/activity-product-cancellations`
  - 请求体（强类型）：`appId`、`activityId`、`applyIds`、`productIds`、`reason`
  - 规则：`activityId/applyIds/productIds` 至少提供一个
- 开放消息回调：`doudian_alliance_colonelOpenEvent`
  - 新增接收端点：`POST /douyin/webhooks/colonel-open-events`
  - 默认不强制验签；可通过 `DOUYIN_WEBHOOK_VERIFY_SIGN=true` 开启验签

## 12. 团长活动列表接口规范（2026-04-23）

- method：`alliance.instituteColonelActivityList`
- 规范对齐：
  - `status`：`0/1/2/3/4/5/7`
  - `search_type`：`0/1/2`
  - `sort_type`：`0/1`
  - `page >= 1`
  - `page_size`：`1~20`（超限直接拦截）
- 联调入口：
  - `GET /douyin/activities`
  - `GET /douyin/activity-products`
- 典型业务码：
  - `isv.parameter-invalid:257`（参数错误）
  - `isv.business-failed:4197`（需招商团长角色授权）

## 13. 订单同步接口迁移（2026-04-22）

- 已将订单同步主链路 method 从旧版 `buyin.settlement.order.list` 切换为 `buyin.instituteOrderColonel`
- 参数兼容策略：
  - 首次请求使用分页参数：`start_time/end_time/page/count`
  - 若三方返回参数校验失败，自动回退到：`start_time/end_time/cursor/count`
- 返回兼容策略：
  - 订单列表字段兼容：`order_list/orders/list/datas`
  - 翻页字段兼容：`has_more/more/is_has_more/next_cursor/page`
- 目的：避免旧接口下线导致 `70000 isp.api-service-off` 使 `/api/order/sync/trigger` 直接失败

## 14. 商品状态查询接口（2026-04-23）

- method：`buyin.materialsProductStatus`
- 官方属性：无需授权、免费 API
- 后端实现：
  - SDK 封装：`ProductApi.materialsProductStatus`
  - 客户端通道：`DouyinApiClient.postWithoutAuth`（不拼接 `access_token`）
  - 联调入口：`POST /douyin/product-material-status-checks`
- 请求参数约束：
  - `products` 必填，且数量范围 `1~50`
  - 列表中每个 URL 不允许为空白
- 响应字段映射：
  - `product_url`、`status`、`join_alliance`、`promotion_status`、`can_share`
- 错误码关注：
  - `40004 / isv.parameter-invalid:257`
  - `20000 / isp.service-error:256`

## 15. 团长活动详情接口（2026-04-23）

- method：`buyin.colonelActivityDetail`
- 官方属性：免费 API，需团长授权
- 后端实现：
  - SDK 封装：`ActivityApi.detail`
  - 联调入口：`GET /douyin/activities/{activityId}`
- 请求参数约束：
  - `activityId` 必填，且必须为数值型字符串
  - `appId` 可选，不传默认使用系统配置
- 响应字段关注：
  - `activity_id`、`institution_id`
  - `activity_name`、`activity_desc`
  - `apply_start_time`、`apply_end_time`
  - `commission_rate`、`service_rate`
  - `cos_limit_type`、`ad_commission_rate`、`ad_service_rate`
- 错误码关注：
  - `50002 / isv.business-failed:500`
  - `40004 / isv.parameter-invalid:257`
  - `20000 / isp.service-error:256`

## 16. 团长分次结算订单接口（2026-04-23）

- method：`buyin.colonelMultiSettlementOrders`
- 官方属性：免费 API，需团长授权
- 后端实现：
  - SDK 封装：`OrderApi.listColonelMultiSettlementOrders`
  - 联调入口：`GET /douyin/order-settlements`
- 请求参数约束：
  - `appId`：可选，不传默认走系统配置
  - `size`：可选，默认 `50`，取值范围 `1~100`
  - `cursor`：可选，默认 `"0"`，要求数值型字符串
  - `timeType`：可选，默认 `update`，仅允许 `settle/update`
  - `startTime/endTime`：按 `yyyy-MM-dd HH:mm:ss` 传入，必须成对出现，时间跨度不能超过 `90` 天
  - `orderIds`：可选，逗号分隔，最多 `100` 个
  - 查询条件要求：必须提供 `orderIds` 或一组合法 `startTime/endTime`
- 响应字段关注：
  - `orders`
  - `cursor`
  - `settle_time`
  - `update_time`
  - `settle_colonel_commission`
  - `settle_second_colonel_commission`
- 错误码关注：
  - `40004 / isv.parameter-invalid:1031`：`size` 非法
  - `40004 / isv.parameter-invalid:282`：`time_type` 非法
  - `40004 / isv.parameter-invalid:1033`：未提供时间范围或订单号
  - `40004 / isv.parameter-invalid:1036`：时间区间非法
  - `40004 / isv.parameter-invalid:284`：`cursor` 非法
  - `20000 / isp.service-error:256`
