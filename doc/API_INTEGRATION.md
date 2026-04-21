# API 集成说明（当前执行版）

**更新日期**：2026-04-21

---

## 1. 当前集成状态

- 内部 API（前后端）已打通
- 抖音 SDK 接口封装已完成
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

---

## 3. 抖音 SDK 接口（服务内调用）

- ActivityApi
- ProductApi
- OrderApi
- PromotionApi
- TalentApi

说明：以上为后端服务内部调用，不直接暴露给前端。

---

## 4. 联调优先级

1. Token 真实获取/刷新
2. 活动、商品、订单三接口真实返回
3. 订单入库 + 归因 + 寄样自动闭环

---

## 5. 联调完成定义

满足以下条件才算“SDK 联调完成”：
- 真实环境能稳定获取 token
- 真实接口返回字段映射通过
- 订单同步任务可在真实返回下稳定跑通
- 异常码（尤其 31012）处理符合预期
