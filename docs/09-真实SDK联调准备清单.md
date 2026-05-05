# 09-真实 SDK 联调准备清单

更新时间：2026-05-03

## 一、文档目标

当前项目已经完成本地 Mock 核心闭环。

下一步不是直接全量切真实接口，而是进入：

> 真实 SDK 联调准备阶段

本清单用于明确：

- 需要准备什么
- 应该先联调哪些 Gateway
- 哪些东西不能被真实联调反向破坏

## 当前准备进度（2026-05-03）

- [x] real-pre 后端 `8081`、PostgreSQL `5433`、Redis `6380` 已稳定可用
- [x] `GET /api/douyin/tokens`、Webhook 验签、商品素材状态检查已拿到 real-pre 侧验证结果
- [x] `backend mvn test` 当前为 `410 tests, 0 failures, 0 errors`
- [x] real-pre 浏览器全路径回归 `45/45` 通过
- [x] 已确认当前 `real-pre` 仍是回归环境：`SPRING_PROFILES_ACTIVE=local-mock`、`DOUYIN_TEST_ENABLED=true`
- [ ] Token 建立仍受平台口径阻塞，真实 `access_token / refresh_token` 尚未落地
- [ ] `RealDouyinOrderGateway` 真实订单映射仍待补全，订单同步闭环尚未打开

## 当前阻塞事实（2026-05-04）

1. 真实 `access_token / refresh_token` 仍未正式落地，因此真实 SDK 联调尚未进入可持续执行阶段
2. 当前 `3000/8080`、`3001/8081` 的结论仍然是 mock / test / regression 基线，不代表真实抖音链路已打通
3. 真实 Gateway 工作必须遵守“只替换 Gateway，不反向破坏现有 Controller / Service / 前端 / 浏览器回归”的约束
4. 当前首轮执行顺序已经明确为：
   - `AuthGateway`
   - `ActivityGateway / ProductGateway`
   - `PromotionGateway`
   - `OrderGateway`
5. 在真实 Token 未落地前，不应把数据看板真实化或部署验证误判为“真实环境已经就绪”

开始本清单前，建议先阅读：

- [archive/records/16-local-mock业务联调记录](./archive/records/16-local-mock业务联调记录.md)
- [archive/records/17-项目剩余事项看板](./archive/records/17-项目剩余事项看板.md)

## 二、联调总原则

### 1. 只替换 Gateway

真实联调时允许替换：

- Gateway 实现类
- SDK 配置
- 认证与 token 管理

真实联调时不允许顺手改坏：

- Controller
- 前端页面
- 主业务 Service
- 现有 Test 闭环

### 2. Test 仍然保留

真实联调不是用 Real 覆盖 Test，而是：

- `test` 环境继续作为演示与回归基线
- `real` 环境用于逐项联调

对于当前项目，更准确的执行口径是：

- 本地 Mock / `test` 基线继续作为演示与回归基线
- `real` 环境用于逐项联调

### 3. 先契约，后实现

每个 Gateway 在切真实前都要先对齐：

- 入参
- 出参
- 错误码
- 空数据分支
- 限流分支
- 状态枚举映射

## 三、联调前置准备

### 1. 环境准备

- [ ] 明确真实联调环境地址
- [ ] 明确回调地址 / Webhook 地址
- [ ] 明确网络访问策略与白名单
- [ ] 明确真实环境是否需要固定出口 IP

### 2. 应用与权限准备

- [ ] 申请 `AppKey / AppSecret`
- [ ] 获取真实测试店铺或测试主体
- [ ] 获取真实授权账号
- [ ] 明确活动、商品、订单、推广、达人、物流相关权限范围

### 3. 数据与配置准备

- [ ] 确认数据库结构已完成当前 SQL 升级
- [ ] 确认 `application-test.yml` 与真实联调配置隔离
- [ ] 确认 token、secret、回调配置不写死在代码中
- [ ] 确认抖店 SDK 依赖通过项目内本地 Maven 仓库加载，而不是回退到 `systemPath`

当前构建口径说明：

- 抖店 SDK Jar 保存在 `backend/lib/`
- Maven 通过 `backend/lib/maven-repo/` 作为项目内本地仓库解析 `com.doudian:open-sdk:1.1.0`
- 不允许再把 SDK 依赖改回 `scope=system + systemPath`

## 四、按 Gateway 拆分的联调清单

### 1. `DouyinAuthGateway`

目标：

- 获取 token
- 刷新 token
- 验证授权信息

检查项：

- [ ] token 获取成功
- [ ] token 刷新成功
- [ ] 过期后重试机制明确
- [ ] 无权限错误码有清楚处理

开始条件：

- [ ] 真实 `AppKey / AppSecret`
- [ ] 可用回调地址
- [ ] 真实授权主体
- [ ] 独立环境变量已配置完成

完成条件：

- [ ] 成功建立真实 token
- [ ] 刷新 token 成功
- [ ] 失败 / 过期 / 无权限分支有记录
- [ ] 未破坏当前 mock / test 浏览器回归基线

### 2. `DouyinColonelActivityGateway`

目标：

- 获取活动列表
- 获取活动详情
- 获取活动商品

检查项：

- [ ] 分页参数与当前 DTO 对齐
- [ ] 活动状态能映射到系统口径
- [ ] 空活动、过期活动场景能处理
- [ ] 限流时不会把页面直接打挂

完成条件：

- [ ] 至少拿到一组真实活动列表样本
- [ ] 活动详情与活动商品字段能回写到当前 DTO 口径
- [ ] 浏览器端活动页未出现回归

### 3. `DouyinProductGateway`

目标：

- 拉取商品基础信息
- 拉取商品详情
- 支撑商品主链路

检查项：

- [ ] 商品 ID、标题、店铺、价格字段对齐
- [ ] 图片缺失时前端可降级
- [ ] 类目、佣金、素材字段映射明确
- [ ] 与当前商品详情结构兼容

完成条件：

- [ ] 至少拿到一组真实商品详情样本
- [ ] 当前 `/product` 与 `/product/activity/:id` 不回退

### 4. `DouyinPromotionGateway`

目标：

- 生成真实推广链接
- 获取真实归因标识

检查项：

- [ ] 转链成功能返回可用链接
- [ ] `pick_source` 或等价归因参数口径明确
- [ ] 失败原因能回传
- [ ] `promotion_link` 与 `pick_source_mapping` 仍按当前系统逻辑落库

完成条件：

- [ ] 至少打通一次真实转链
- [ ] 成功写入本地映射关系
- [ ] 失败原因可见且不破坏当前页面流程

### 5. `DouyinOrderGateway`

目标：

- 拉取真实订单
- 支撑订单归因、看板与详情排查

检查项：

- [ ] 订单状态字段映射明确
- [ ] 金额单位与精度明确
- [ ] 创建时间 / 结算时间口径明确
- [ ] 能拿到真实归因字段
- [ ] 无归因参数时能走未归因分支

完成条件：

- [ ] 订单状态、金额、时间字段映射固定
- [ ] 至少一组真实订单样本完成入库
- [ ] `/orders`、`/dashboard`、`/data` 未出现回归

### 6. `TalentGateway`

目标：

- 获取达人信息
- 刷新达人数据

检查项：

- [ ] 抖音号 / UID / sec_uid 对齐
- [ ] 粉丝数、获赞数、作品数字段对齐
- [ ] 空达人资料可降级
- [ ] 刷新失败不影响现有达人 CRM 展示

### 7. `LogisticsGateway`

目标：

- 获取物流状态
- 推进寄样物流节点

检查项：

- [ ] 物流单号来源明确
- [ ] 签收状态同步口径明确
- [ ] 延迟、失败、查无结果分支明确

## 五、联调验证顺序建议

建议按这个顺序推进：

1. `AuthGateway`
2. `ActivityGateway`
3. `ProductGateway`
4. `PromotionGateway`
5. `OrderGateway`
6. `TalentGateway`
7. `LogisticsGateway`

当前执行口径补充：

- 第一批只要求打开真实认证、活动、商品、转链、订单主链路
- `TalentGateway` 与 `LogisticsGateway` 可以在上述链路稳定后再推进
- 每完成一个 Gateway，都要先守住当前浏览器回归绿灯，再进入下一项

原因：

- 认证最先打通
- 商品与活动先能拉下来
- 再打通转链
- 最后打通订单、达人和物流

## 六、联调期间必须保留的回归项

每切通一个 Gateway，都要回归：

- [ ] 商品库
- [ ] 订单工作台
- [ ] Dashboard
- [ ] 寄样台
- [ ] 达人 CRM
- [ ] `/dev/test` 调试链路

重点要求：

- Real 联调失败时，Test 基线不能被破坏
- 不允许为了联调临时改前端展示字段
- 不允许把真实联调逻辑直接硬编码到页面中

## 七、当前结论

真实 SDK 联调的正确姿势不是“把现有 Test 替换掉”，而是：

1. 固化本地 Mock 基线
2. 逐个 Gateway 对照契约联调
3. 每完成一个 Gateway 就做全链路回归

这样才能保证项目既能继续演示，也能稳步迈向真实环境。
