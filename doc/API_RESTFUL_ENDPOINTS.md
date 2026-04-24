# 后端 RESTful 接口总表

**更新时间**：2026-04-24  
**适用版本**：SaaS V2.2  
**说明**：本表为当前后端英文路径与 RESTful 风格接口的唯一整理版，适合导入 Apifox 后继续联调。

---

## 1. Apifox 导入方式

- OpenAPI 地址：`http://localhost:8080/api/v3/api-docs`
- Swagger UI：`http://localhost:8080/api/doc.html`
- 服务上下文前缀：`/api`

说明：
- 所有接口均已统一为英文路径
- 不建议再使用旧的中文路径或 `/page` 风格历史路径
- Apifox 中导入后，Base URL 使用：`http://localhost:8080/api`

---

## 2. 认证接口

| 模块 | 方法 | 路径 | 说明 |
|---|---|---|---|
| Auth | `POST` | `/auth/login` | 用户登录，返回 JWT |

---

## 3. 系统管理

### 用户

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/users` | 用户分页列表，使用 `page` `size` 等查询参数 |
| `GET` | `/users/{id}` | 用户详情 |
| `POST` | `/users` | 创建用户 |
| `PUT` | `/users/{id}` | 更新用户 |
| `DELETE` | `/users/{id}` | 删除用户 |
| `PUT` | `/users/{id}/password` | 重置密码 |
| `PUT` | `/users/{id}/roles` | 分配角色 |

### 角色

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/roles` | 角色分页列表 |
| `GET` | `/roles/enabled` | 所有启用角色 |
| `GET` | `/roles/{id}` | 角色详情 |
| `POST` | `/roles` | 创建角色 |
| `PUT` | `/roles/{id}` | 更新角色 |
| `DELETE` | `/roles/{id}` | 删除角色 |

---

## 4. 商品与活动

### 商品

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/products` | 商品分页列表 |
| `GET` | `/products/{id}` | 商品详情 |
| `PUT` | `/products/{id}/activity` | 绑定活动 |
| `PUT` | `/products/{id}/assignee` | 分配业务负责人 |
| `PUT` | `/products/{id}/audit-result` | 商品审核 |
| `POST` | `/products/{id}/promotion-links` | 生成推广链接 |

### 活动

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/activities` | 活动分页列表 |
| `GET` | `/activities/{activityId}/douyin-detail` | 抖音活动详情联调 |
| `GET` | `/activities/exports` | 导出活动 CSV |

---

## 5. 达人 CRM

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/talents` | 达人分页列表 |
| `GET` | `/talents/{id}` | 达人详情 |
| `POST` | `/talents` | 创建达人 |
| `PUT` | `/talents/{id}` | 更新达人 |
| `DELETE` | `/talents/{id}` | 删除达人 |
| `GET` | `/talents/pools/public` | 公海达人 |
| `GET` | `/talents/pools/private` | 私海达人 |
| `POST` | `/talents/{id}/claims` | 认领达人 |
| `POST` | `/talents/{id}/release` | 释放达人 |
| `GET` | `/talents/{id}/exclusive-status` | 独家判断 |

---

## 6. 寄样管理

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/samples` | 创建寄样申请 |
| `GET` | `/samples` | 寄样分页列表 |
| `GET` | `/samples/{id}` | 寄样详情 |
| `PUT` | `/samples/{id}/status` | 寄样状态流转 |
| `DELETE` | `/samples/{id}` | 删除寄样 |
| `GET` | `/samples/talent-candidates` | 寄样达人候选搜索 |

---

## 7. 数据看板与订单

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/dashboard/metrics` | 看板核心指标 |
| `GET` | `/orders` | 订单分页列表 |
| `POST` | `/orders/phone-decryptions` | 订单手机号解密 |
| `GET` | `/orders/exports` | 导出订单 CSV |
| `POST` | `/order-sync-jobs` | 手动触发订单同步 |
| `GET` | `/operations/exclusive-talents` | 独家达人状态监控 |
| `GET` | `/operations/exclusive-merchants` | 独家商家状态监控 |

---

## 8. 抖音联调接口

### Token

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/douyin/tokens` | 查询 token 状态，支持 `appId` |
| `POST` | `/douyin/tokens` | 创建 token |
| `POST` | `/douyin/token-refreshes` | 刷新 token |

### 活动与商品

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/douyin/activities` | 抖音活动列表联调 |
| `POST` | `/douyin/activities` | 创建抖音团长活动 |
| `PUT` | `/douyin/activities/{activityId}` | 更新抖音团长活动 |
| `GET` | `/douyin/activities/{activityId}` | 抖音活动详情联调 |
| `GET` | `/douyin/activity-products` | 抖音活动商品联调列表 |
| `GET` | `/douyin/activities/{activityId}/products` | 按活动查询商品 |
| `POST` | `/douyin/product-material-status-checks` | 商品素材状态查询 |
| `POST` | `/douyin/activity-product-cancellations` | 取消活动商品合作 |
| `POST` | `/douyin/activity-product-cancellations/raw` | 原始取消请求透传 |
| `GET` | `/douyin/order-settlements` | 团长结算订单联调 |

### Webhook

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/douyin/webhooks/colonel-open-events` | 抖音 webhook 回调 |

---

## 9. Apifox 调试建议

- Token 创建使用：`POST /douyin/tokens`
- Token 状态查询使用：`GET /douyin/tokens?appId=xxx`
- 所有分页接口统一使用查询参数：`page`、`size`
- 不要再使用中文路由或旧历史路由导入 Apifox
- 如需鉴权，请在 Apifox 统一配置 `Authorization: Bearer <JWT>`
