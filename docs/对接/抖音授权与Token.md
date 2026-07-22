# 抖音授权与 Token

> 范围标记：`V1 必做`、`V1 简化`、`V1 不做`、`V2 预留`、`历史归档`。

## 对接目标

- [V1 必做] 支撑 real-pre 环境获取、刷新和校验抖音 / 抖店访问 Token。
- [V1 必做] 为活动商品、转链、订单、物流、达人等接口提供统一鉴权输入。

## 当前事实

- [V1 必做] Token 由后端 Gateway / SDK 管理，前端不得直接持有第三方密钥。
- [V1 必做] real-pre 必须关闭 mock：`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`。
- [V1 必做] Token 获取失败时必须记录请求时间、环境、错误码和响应摘要。
- [V1 简化] V1 不要求完整多租户 Token 池，但必须能支撑当前联调账号。

## Token 持久化与刷新不变量

- [V1 必做] 授权码换 Token 成功后，必须把 `access_token`、`refresh_token` 和 `expires_in` 对应的过期时间写入 Redis；后续业务只从同一 `appId/client-key` 命名空间读取。
- [V1 必做] refresh 成功后，新的 `access_token` 必须写入 Redis；上游未返回轮换后的 `refresh_token` 时，保留本次请求使用的旧值，不能因字段缺失丢弃新 access token。
- [V1 必做] 自动刷新任务只在 Redis 中同时存在可用 `refresh_token` 且 access token 临近过期时执行；刷新成功后下一次刷新必须使用 Redis 中最新的 refresh token。
- [V1 必做] `DOUYIN_APP_ID` / `DOUYIN_CLIENT_KEY` / `DOUYIN_CLIENT_SECRET` 不得保留 `MUST_CHANGE_*` 占位值，也必须与 Redis 中 token key 的授权主体一致；real-pre safety-check / preflight 发现占位配置时必须标记 `BLOCKED`。

## API / 配置

| 项 | 证据 | 范围 |
| --- | --- | --- |
| 授权码换 Token | real-pre 请求 / 响应、后端日志 | V1 必做 |
| Token 刷新 | 后端日志、配置或 Token 状态 | V1 必做 |
| 权限包校验 | 第三方错误码、接口响应 | V1 必做 |
| Token 过期处理 | 错误日志、重试或刷新记录 | V1 简化 |

## 验收证据

- [V1 必做] `npm run e2e:real-pre:p0:preflight` 能证明 real-pre 环境和 Token 基础配置。
- [V1 必做] Token 相关证据写入 [../验收/验收证据索引.md](../验收/验收证据索引.md)。
- [V1 必做] 缺权限或授权码过期时标记 BLOCKED，不写成业务通过。

## 不做

- [V1 不做] 不在前端或文档中暴露真实 secret。
- [V1 不做] 不用 mock Token 冒充 real-pre。
- [V2 预留] 多店铺、多授权主体和 Token 池治理后续设计。

## 来源

- [历史归档] `Git 历史中的旧 V2.2 资料
- [历史归档] `docs/archive/records/20-2026-05-08-新授权码三方全流程联调报告.md`
