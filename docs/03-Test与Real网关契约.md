# 03-Test 与 Real 网关契约

更新时间：2026-04-27

## 一、目的

为了实现环境的无缝切换，本项目所有与第三方（抖音、物流等）交互的逻辑均通过 Gateway 接口定义。
- `Test` 实现：用于 `test` 环境，返回本地构造的拟真数据。
- `Real` 实现：用于后期真实第三方 SDK 联调。

核心目标只有一个：
> 切换 Test / Real 时，只替换 Gateway 实现，不改 Controller、不改前端、不改主业务 Service。

## 二、统一约束

### 1. 数据模型转换
- Service 只依赖 Gateway 接口。
- Gateway 负责吸收第三方接口差异。
- Repository / Entity 不直接感知第三方 SDK 结构。

### 2. 状态码与异常处理
- 统一使用 `ApiResult` 或业务异常抛出。
- 不允许在 Service 中写 `if test` / `if real` 业务分支。
- 不允许为了适配 Real 接口去污染前端字段结构。

### 3. 数据透传规则
- 必须通过 DTO 进行转换。
- 不直接把第三方 SDK 原始对象向上透传。

## 三、当前覆盖的网关 (Gateways)

| 接口名称 | 描述 | Test 状态 | Real 状态 |
| :--- | :--- | :--- | :--- |
| `DouyinAuthGateway` | 授权与 Token 管理 | [x] 已完成 | [x] 已完成 |
| `DouyinProductGateway` | 商品同步与详情查询 | [x] 已完成 | [x] 已完成 |
| `DouyinActivityGateway` | 团长活动列表同步 | [x] 已完成 | [x] 已完成 |
| `DouyinOrderGateway` | 订单回流 (增量/Webhook) | [x] 已完成 | [x] 已完成 |
| `DouyinPromotionGateway` | 自动化转链 (Pick Source) | [x] 已完成 | [x] 已完成 |
| `LogisticsGateway` | 物流轨迹查询与发货 | [x] 已完成 | [ ] 待对接 |

## 四、Test 拟真化要求

Test 实现不应只是简单的静态返回，应支持：
- **随机性**：模拟不同商品的佣金率、库存状态。
- **关联性**：模拟订单中的商品 ID 必须能在商品池中找到。
- **时效性**：模拟物流单号的状态应随时间推进而变化。


