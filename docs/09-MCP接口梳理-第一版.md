# 09-MCP接口梳理-第一版

更新时间：2026-05-18

> 本文档是 **MCP / OpenAPI / 现有 V1 文档三方对齐后的接口梳理起点**。  
> 它不替代 [08-V1需求接口测试矩阵](./08-V1需求接口测试矩阵.md)，而是把“先看哪组接口、哪些算主链、哪些只算联调辅助”先定下来，方便后续持续补全。

## 一、梳理依据

本轮梳理统一基于以下 4 类事实源：

1. **MCP 已接通的离线 OAS**
   - `SAAS-business-offline` -> `D:\Projects\SAAS\docs\openapi\openapi-business.json`
   - `SAAS-sdk-debug-offline` -> `D:\Projects\SAAS\docs\openapi\openapi-sdk-debug.json`
2. [08-V1需求接口测试矩阵](./08-V1需求接口测试矩阵.md)
3. [V1领域对齐总表](./V1领域对齐总表.md)
4. `docs/` 下当前主文档口径

一句话约束：

> **MCP 负责提供当前可读的接口事实，V1 文档负责定义哪些接口应该进入主验收口径。**

## 二、当前结论

1. 当前已接通两组离线 MCP：
   - `SAAS-business-offline`
   - `SAAS-sdk-debug-offline`
2. 业务主链梳理只以 `business` OAS 为主，`sdk-debug` 只做抖音联调辅助。
3. 当前 `business` OAS 规模：
   - `90` 个 path
   - `106` 个 operation
4. 当前 `sdk-debug` OAS 规模：
   - `11` 个 path
   - `14` 个 operation
5. `/products/**` 仍存在，但已在 OAS 中以“商品管理（已废弃）”出现，V1 主梳理优先走 `/colonel/activities/**`。
6. 当前前端仍有两条待确认调用，先挂起，不纳入已确认主链：
   - `POST /colonel/activities/{activityId}/sync`
   - `POST /colonel/activities/{activityId}/products/sync`
7. 商品主链已拆分到：
   - [09-01-MCP商品主链接口梳理](./09-01-MCP商品主链接口梳理.md)
8. 订单归因链已拆分到：
   - [09-02-MCP订单归因接口梳理](./09-02-MCP订单归因接口梳理.md)
9. 用户权限与数据范围链已拆分到：
   - [09-03-MCP用户权限与数据范围接口梳理](./09-03-MCP用户权限与数据范围接口梳理.md)
10. 配置规则链已拆分到：
   - [09-04-MCP配置规则接口梳理](./09-04-MCP配置规则接口梳理.md)
11. 寄样链已拆分到：
   - [09-05-MCP寄样接口梳理](./09-05-MCP寄样接口梳理.md)
12. 达人链已拆分到：
   - [09-06-MCP达人接口梳理](./09-06-MCP达人接口梳理.md)
13. 总收口与 E2E 计划已拆分到：
   - [09-07-MCP接口梳理总收口与E2E测试计划](./09-07-MCP接口梳理总收口与E2E测试计划.md)

## 三、接口总览

### 1. business OAS 主分布

| 分组 | 当前规模 | 主路径前缀 / tag | 说明 |
|---|---:|---|---|
| 达人域 | 17 ops | `/talents/**` / `达人CRM` | 达人列表、详情、认领、释放、手工补录、黑名单等；详见 [09-06-MCP达人接口梳理](./09-06-MCP达人接口梳理.md) |
| 寄样域 | 15 ops | `/samples/**` / `寄样管理` | 寄样申请、审批、发货、物流刷新、批量动作；详见 [09-05-MCP寄样接口梳理](./09-05-MCP寄样接口梳理.md) |
| 商品活动主链 | 10 ops | `/colonel/activities/**` / `活动商品主链路` | 活动商品列表、审核、分配、转链、入库、关注 |
| 商品旧链路 | 9 ops | `/products/**` / `商品管理（已废弃）` | 兼容保留，不作为 V1 主梳理入口 |
| 订单域 | 7 ops | `/orders/**` / `订单管理` | 同步、列表、详情、统计、筛选项 |
| 用户权限 | 20+ ops | `/auth/**` `/menus/**` `/users/**` `/roles/**` | 登录、菜单、用户、角色、负责人候选 |
| 配置域 | 6 ops | `/configs/**` / `系统配置` | 配置列表、分组、增删改；详见 [09-04-MCP配置规则接口梳理](./09-04-MCP配置规则接口梳理.md) |
| 分析与数据 | 8 ops | `/dashboard/**` `/data/**` / `数据平台` `数据看板` | summary、metrics、activity-products、导出 |
| 其它辅助 | 少量 | `/system/env` `/ops/redis-probe` `/operation-logs` `/merchants` | 环境探针、日志、运营辅助 |

### 2. sdk-debug OAS 主分布

当前 `sdk-debug` 全部集中在 `抖音联调` 一组，主要是：

- `GET/POST /douyin/tokens`
- `POST /douyin/token-refreshes`
- `GET/POST /douyin/activities`
- `GET/PUT /douyin/activities/{activityId}`
- `GET /douyin/activity-products`
- `GET /douyin/activities/{activityId}/products`
- `GET /douyin/order-settlements`
- `POST /douyin/product-material-status-checks`
- `POST /douyin/activity-product-cancellations`
- `POST /douyin/webhooks/colonel-open-events`

使用边界：

> `sdk-debug` 是“查抖音侧状态、Token、Webhook、上游回流”的工具箱，不是 V1 页面主链的验收接口集合。

## 四、按领域的梳理入口

| 领域 | 先看接口 | 文档入口 | 当前判断 |
|---|---|---|---|
| 用户域 | `/auth/**` `/menus/**` `/users/**` `/roles/**` | [V1对齐-用户域](./V1对齐-用户域.md) | 已具备主梳理条件 |
| 配置域 | `/configs/**` | [V1对齐-配置域](./V1对齐-配置域.md)、[09-04-MCP配置规则接口梳理](./09-04-MCP配置规则接口梳理.md) | 已具备主梳理条件 |
| 商品域 | `/colonel/activities/**` | [V1对齐-商品域](./V1对齐-商品域.md) | 优先梳理，V1 主链核心 |
| 达人域 | `/talents/**` | [V1对齐-达人域](./V1对齐-达人域.md)、[09-06-MCP达人接口梳理](./09-06-MCP达人接口梳理.md) | 已具备主梳理条件，达人链已拆分 |
| 寄样域 | `/samples/**` | [V1对齐-寄样域](./V1对齐-寄样域.md)、[09-05-MCP寄样接口梳理](./09-05-MCP寄样接口梳理.md) | 已具备主梳理条件，寄样链已拆分 |
| 订单域 | `/orders/**` | [V1对齐-订单域](./V1对齐-订单域.md) | 优先梳理，V1 主链核心 |
| 业绩域 | `/orders/**` `/dashboard/**` | [V1对齐-业绩域](./V1对齐-业绩域.md) | 依赖订单样本补证 |
| 分析模块 | `/dashboard/**` `/data/**` | [V1对齐-分析模块](./V1对齐-分析模块.md) | 可与订单域一起梳理 |
| 跨域流程 | 商品 -> 达人 -> 寄样 -> 订单 -> 看板 | [V1对齐-跨域流程](./V1对齐-跨域流程.md) | 适合放在领域梳理后收口 |
| SDK 联调 | `/douyin/**` | [07-部署联调与三方对接](./07-部署联调与三方对接.md) | 只做辅助，不单独升格为主链 |

## 五、建议的梳理顺序

### 第一层：先把 V1 主链压实

1. **登录 / 当前用户 / 权限上下文**
   - `POST /auth/login`
   - `GET /menus/tree`
   - `GET /roles/enabled`
   - `GET /users/assignable`
   - 详见 [09-03-MCP用户权限与数据范围接口梳理](./09-03-MCP用户权限与数据范围接口梳理.md)
2. **系统配置 / 规则配置**
   - `GET /configs`
   - `GET /configs/grouped`
   - `GET /configs/{id}`
   - `POST /configs`
   - `PUT /configs/{id}`
   - `DELETE /configs/{id}`
   - `GET /operation-logs`
   - 详见 [09-04-MCP配置规则接口梳理](./09-04-MCP配置规则接口梳理.md)
3. **活动 / 商品主链**
   - `GET /colonel/activities`
   - `GET /colonel/activities/{activityId}/products`
   - `PUT /colonel/activities/{activityId}/products/{productId}/audit-result`
   - `POST /colonel/activities/{activityId}/products/{productId}/library-entry`
   - `POST /colonel/activities/{activityId}/products/{productId}/promotion-links`
4. **订单 / 归因 / 看板主链**
   - `POST /orders/sync`
   - `GET /orders`
   - `GET /orders/{orderId}`
   - `GET /orders/unattributed`
   - `GET /orders/stats`
   - `GET /dashboard/summary`
   - `GET /dashboard/activity-products`
   - `GET /dashboard/metrics`
   - `GET /data/orders`
5. **寄样申请 / 审核 / 发货 / 交作业**
   - `POST /samples`
   - `GET /samples`
   - `GET /samples/{id}`
   - `PUT /samples/{id}/status`
   - `GET /samples/{id}/status-logs`
   - `POST /samples/batch-approve`
   - `POST /samples/batch-reject`
   - `POST /samples/batch-ship`
   - `POST /samples/{id}/logistics/refresh`
   - 详见 [09-05-MCP寄样接口梳理](./09-05-MCP寄样接口梳理.md)

### 第二层：把主链上下游补完整

6. **达人域**
   - `GET /talents`
   - `GET /talents/{id}`
   - `POST /talents`
   - `PUT /talents/{id}`
   - `POST /talents/{id}/claims`
   - `POST /talents/{id}/release`
   - `GET /talents/pools/public`
   - `GET /talents/pools/private`
   - `PUT /talents/{id}/manual-fill`
   - 详见 [09-06-MCP达人接口梳理](./09-06-MCP达人接口梳理.md)
7. **运营辅助**
   - `GET /system/env`
   - `GET /ops/redis-probe`

### 第三层：只在需要时再打开 sdk-debug

8. **抖音联调辅助**
   - Token 管理
   - 活动 / 活动商品上游状态核查
   - order settlement 查询
   - webhook 回流排查

## 六、当前不该混进主梳理的内容

1. **Deprecated 商品旧链路**
   - `/products/**`
   - 用作兼容和回退参考，不再作为 V1 主路径
2. **纯联调辅助接口**
   - `/douyin/**`
   - 只在排查第三方问题时使用
3. **未确认映射的前端调用**
   - `POST /colonel/activities/{activityId}/sync`
   - `POST /colonel/activities/{activityId}/products/sync`
4. **旧 V2.2 增强项**
   - 独家达人
   - 独家商家
   - 高级分析
   - 自动物流增强

## 七、当前缺口

1. **真实 `pick_source -> 订单归因` 样本**
   - 当前接口链已在 OAS 中存在，但真实样本仍要补证
2. **test / real-pre 实跑证据**
   - 当前多数结论仍是“接口已对齐”，不是“实跑已通过”
3. **前端两条 sync 调用的真实归属**
   - 要么补进 live OAS
   - 要么确认是历史调用并删掉

## 八、下一步怎么继续

如果继续沿这份梳理往下做，前 6 份接口拆分已经形成主链底稿：

1. **商品主链接口梳理**
   - 已拆分文档：[09-01-MCP商品主链接口梳理](./09-01-MCP商品主链接口梳理.md)
   - 活动列表
   - 活动商品
   - 审核
   - 商品入库
   - 转链
2. **订单与看板接口梳理**
   - 已拆分文档：[09-02-MCP订单归因接口梳理](./09-02-MCP订单归因接口梳理.md)
   - 订单同步
   - 列表 / 详情 / 统计
   - 未归因排查
   - Dashboard / Data
3. **达人接口梳理**
   - 已拆分文档：[09-06-MCP达人接口梳理](./09-06-MCP达人接口梳理.md)
   - 达人新增 / 查询 / 编辑
   - 认领 / 释放 / 公海私海
   - 保护期与出单后取证
   - 地址与寄样衔接
4. **配置规则接口梳理**
   - 已拆分文档：[09-04-MCP配置规则接口梳理](./09-04-MCP配置规则接口梳理.md)
   - 系统配置
   - 寄样限制
   - 达人保护期
   - 提成比例
   - 角色数据范围 configured
5. **寄样接口梳理**
   - 已拆分文档：[09-05-MCP寄样接口梳理](./09-05-MCP寄样接口梳理.md)
   - 寄样申请
   - 7 天限制与豁免
   - 审核 / 发货 / 签收
   - `completed_by_order` 取证路径

一句话结论：

> 当前 `business` OAS 已经把登录、配置、商品、订单、看板、寄样、达人这些 V1 主链拆成 09-01~09-06；下一步应进入 [09-07-MCP接口梳理总收口与E2E测试计划](./09-07-MCP接口梳理总收口与E2E测试计划.md) 的脚本收口，而不是继续把 `sdk-debug` 或 V2 能力拉回主矩阵。
