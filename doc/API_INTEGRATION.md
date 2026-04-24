# API 集成说明（当前执行版）

**版本**：V1.4
**更新日期**：2026-04-24
**状态**：执行依据

> 2026-04-24 起，后端统一英文 RESTful 路径请优先参考 `doc/API_RESTFUL_ENDPOINTS.md`。

---

## 1. 当前集成状态

- 内部 API（前后端）已打通
- 抖音 SDK 接口封装已完成
- Token 实现已切换抖店官方规范，控制器已统一为 RESTful 路径：`/douyin/tokens` + `/douyin/token-refreshes`
- **第三方真实环境联调未完成**

---

## 2. 后端核心接口分组

### 认证与系统
- `POST /api/auth/login`
- `GET /api/sys/users/page`
- `GET /api/sys/roles/page`

### 商品与活动
- `GET /api/products/page`
- `GET /api/activities/page`

### 达人与寄样
- `GET /api/talent/page`
- `GET /api/samples/page`
- `POST /api/samples`
- `PUT /api/samples/{id}/action`
- `GET /api/samples/talents`

### 数据与订单
- `GET /api/data/metrics`
- `GET /api/data/orders/page`
- `POST /api/order/sync/trigger`

### 抖音联调接口（管理员）
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
- 调试建议：HTTP 客户端优先使用 ASCII 路径；当前不再建议使用旧 `/api/douyin/...` 别名描述，避免和代码实际路由漂移

---

## 3. 抖音 SDK 接口（服务内调用）

- ActivityApi
- ProductApi
- OrderApi
- PromotionApi
- TalentApi

说明：以上为后端服务内部调用，不直接暴露给前端。

活动商品查询接口已按官方文档切换：
- `alliance.colonelActivityProduct`（当前使用）
- `buyin.colonel.product.list`（旧接口，真实环境已出现 `isp.api-service-off`）

---

## 4. Token 协议约定（抖店）

1. 生成 token
- 接口：`POST /douyin/tokens`
- 工具型应用：`grant_type=authorization_code` + `code`
- 自用型应用：`grant_type=authorization_self`（有授权 code 时可用 `authorization_code`）

2. 刷新 token
- 接口：`POST /douyin/token-refreshes`
- 参数：`grant_type=refresh_token` + `refresh_token`
- 刷新规则（官方）：
  - 过期前 1 小时外刷新可能返回原 token
  - 过期前 1 小时内刷新会返回新 token，旧 token 继续有效 1 小时
  - 过期后刷新会返回新 token，旧 token 失效

3. 平台返回码重点
- `10000`：成功
- `31007/31021`：授权码失效或过期
- `31006/31020/31005`：授权记录失效或不存在
- `31012`：并发刷新/生成冲突（需降低并发）
- `31009/31008`：refresh_token 不存在或过期
- `31011`：应用不存在/被删除
- `31003`：grant_type 参数错误

---

## 5. 联调优先级

1. Token 真实获取/刷新
2. 活动、商品、订单三接口真实返回
3. 订单入库 + 归因 + 寄样自动闭环

---

## 6. 联调完成定义

满足以下条件才算“SDK 联调完成”：
- 真实环境能稳定获取 token
- 真实接口返回字段映射通过
- 订单同步任务可在真实返回下稳定跑通
- 异常码（尤其 31012）处理符合预期

---

## 7. 自动刷新任务（已实现）

- 任务类：`backend/src/main/java/com/colonel/saas/job/DouyinTokenRefreshJob.java`
- 调度配置：
  - `DOUYIN_TOKEN_AUTO_REFRESH_ENABLED=true`
  - `DOUYIN_TOKEN_AUTO_REFRESH_CRON=0 */10 * * * ?`
- 行为：
  - 每轮先读取 `TokenStatus`
  - 仅在 token 缺失或即将过期时触发 `DouyinTokenService.refreshToken`
  - 存在 `reauthorizeRequired=true` 时跳过，等待人工重新授权

---

## 8. 活动接口错误透传（新增）

- `DouyinApiClient` 对业务错误优先读取 `sub_msg`
- 日志中输出 `sub_code` 与 `log_id`，用于定位 `40003` 等泛化错误

---

## 9. 活动商品查询参数约定（2026-04-22）

- 对应联调入口：`GET /douyin/activities/{activityId}/products`
- 后端调用 method：`alliance.colonelActivityProduct`
- 参数映射：
  - `activityId -> activity_id`（必填）
  - `count -> count`（最大 20）
  - `cursor -> cursor`（字符串，传入时自动切到 `retrieve_mode=1`）
  - 未传 `cursor` 时默认 `retrieve_mode=0`
  - 默认排序：`search_type=4`、`sort_type=1`

## 10. 团长活动列表参数规范对齐（2026-04-23）

- 对应 method：`alliance.instituteColonelActivityList`
- 对齐点：
  - `status` 仅允许：`0/1/2/3/4/5/7`
  - `search_type` 仅允许：`0/1/2`
  - `sort_type` 仅允许：`0/1`
  - `page` 必须 `>=1`
  - `page_size` 必须在 `1~20`
- 后端联调入口：
  - `GET /douyin/activities`（默认活动列表）
  - `GET /douyin/activity-products`（可传完整筛选参数）
- 典型错误码：
  - `40004 / isv.parameter-invalid:257`：参数校验失败
  - `50002 / isv.business-failed:4197`：需【招商团长】角色授权

## 11. 商品状态查询接口规范对齐（2026-04-23）

- 对应 method：`buyin.materialsProductStatus`
- 权限特征：官方标注“无需授权”，后端已按无 `access_token` 模式调用
- 后端联调入口：
  - `POST /douyin/product-material-status-checks`
- 请求体（JSON）：
  - `appId`：可选，默认走系统配置
  - `products`：必填，商品 URL 列表，`1~50`
- 参数校验：
  - `products` 不能为空
  - `products` 数量不能超过 `50`
  - 列表项 URL 不能为空白字符串
- 响应关注字段：
  - `product_url`
  - `status`（上下架状态）
  - `join_alliance`（是否加入精选联盟）
  - `promotion_status`（推广状态）
  - `can_share`（是否可分销）
- 典型错误码：
  - `40004 / isv.parameter-invalid:257`：参数校验失败
  - `20000 / isp.service-error:256`：服务异常，需重试

## 12. 团长活动详情接口规范对齐（2026-04-23）

- 对应 method：`buyin.colonelActivityDetail`
- 权限特征：需团长授权
- 后端联调入口：
  - `GET /douyin/activities/{activityId}`
- 请求参数：
  - `appId`：可选，默认走系统配置
  - `activityId`：路径参数，后端要求数值型
- 响应关注字段：
  - `activity_id`
  - `institution_id`
  - `activity_name`
  - `activity_desc`
  - `apply_start_time`
  - `apply_end_time`
  - `commission_rate`
  - `service_rate`
  - `cos_limit_type`
  - `ad_commission_rate`
  - `ad_service_rate`
- 典型错误码：
  - `50002 / isv.business-failed:500`：活动不存在
  - `40004 / isv.parameter-invalid:257`：参数校验失败
  - `20000 / isp.service-error:256`：服务异常，需重试

## 13. 团长分次结算订单接口规范对齐（2026-04-23）

- 对应 method：`buyin.colonelMultiSettlementOrders`
- 权限特征：需团长授权
- 后端联调入口：
  - `GET /douyin/order-settlements`
- 请求参数：
  - `appId`：可选，默认走系统配置
  - `size`：可选，默认 `50`，范围 `1~100`
  - `cursor`：可选，默认 `"0"`，必须为数值字符串
  - `timeType`：可选，默认 `update`，仅支持 `settle/update`
  - `startTime/endTime`：可选，但必须成对传入，格式 `yyyy-MM-dd HH:mm:ss`
  - `orderIds`：可选，逗号分隔，最多 `100` 个
- 参数规则：
  - `orderIds` 或 `startTime+endTime` 至少提供一种
  - `startTime <= endTime`
  - 时间跨度不能超过 `90` 天
- 响应关注字段：
  - `orders`
  - `cursor`
  - `order_id`
  - `settle_time`
  - `update_time`
  - `settle_colonel_commission`
  - `settle_second_colonel_commission`
- 典型错误码：
  - `40004 / isv.parameter-invalid:1031`：无效 `size`
  - `40004 / isv.parameter-invalid:282`：无效 `time_type`
  - `40004 / isv.parameter-invalid:1033`：请指定时间范围或订单号
  - `40004 / isv.parameter-invalid:1036`：无效时间区间
  - `40004 / isv.parameter-invalid:284`：无效 `cursor`
  - `20000 / isp.service-error:256`：服务异常，需重试
