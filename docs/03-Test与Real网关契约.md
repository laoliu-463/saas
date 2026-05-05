# 03-Test 与 Real 网关契约

更新时间：2026-05-03

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

## 五、当前 real-pre 与真实 Gateway 口径

为避免把真实抖店联调和现有 `test` 基线混在一起，当前项目统一执行口径如下：

- `local-mock` 继续承担默认本地人工联调与调试台验证
- `test` 继续承担自动化测试与隔离测试栈
- `real-pre` 当前承担浏览器回归、权限验收、独立拓扑验证
- 未来真实第三方 SDK 联调，应在不污染上述基线的前提下继续演进 `real-pre` 或单独拉起更纯粹的 real Gateway 环境

当前仓库中的实际 `real-pre` 不是“真实 SDK 已接通环境”，而是“独立回归拓扑”。当前事实如下：

| 项目 | `test` | 当前 `real-pre` |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | `test` | `local-mock` |
| `DOUYIN_TEST_ENABLED` | `true` | `true` |
| `DB_NAME` | `colonel_saas_test` | `colonel_saas_real` |
| `REDIS_DATABASE` | `1` | `2` |
| 后端端口 | `8080` | `8081` |
| `/api/test/**` | 可用 | 当前仍可用 |
| 当前职责 | 自动化验证 | 浏览器回归 / 权限验收 / 部署形态验证 |

执行约束：

1. 不允许把真实 `access_token`、真实订单、真实回调写进 `test` 或 `local-mock` 基线
2. 不允许为了真实联调临时修改 `test`、`local-mock` 的 DTO、页面字段和调试台口径
3. 当前 `real-pre` 首轮任务仍以浏览器回归和接口事实核对为主，不强行宣称“已接真实 SDK”
4. 所有真实联调记录统一回写到 `docs/archive/records/14-抖店SDK全量梳理与逐接口联调规划.md`
5. 后续真实 Gateway 联调环境必须关闭 Spring Boot DEBUG、`RestTemplate` DEBUG 与抖店 SDK INFO 原始报文日志，避免 `access_token`、`refresh_token`、签名或 JWT 出现在日志中
6. 首轮 Token 联调期间建议设置 `ORDER_SYNC_ENABLED=false`，避免订单定时同步在 Token 未创建前反复触发 Real Gateway

当前已知代码缺口：

1. `RealDouyinAuthGateway.ensureToken` 已补为“从 Redis 读取已有 token / refresh_token / expire_at 的非阻塞兜底”，不再固定返回 `null`
2. `RealDouyinOrderGateway` 尚未完成订单字段映射
3. `RealDouyinProductGateway.queryProductDetail` 未实现
4. `RealDouyinProductGateway.queryProductSkus` 未实现
5. Webhook 当前只接收、验签、记日志，未接业务消费

上述缺口不阻塞认证、身份、活动、商品、转链等前置接口联调，但会阻塞订单主链路闭环验收。


