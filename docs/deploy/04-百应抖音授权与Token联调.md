# 百应抖音授权与 Token 联调

## 适用场景

本文用于 real-pre 部署后验证抖音 / 抖店 / 百应授权链路，完成 OAuth code 回调、token.create、Token 保存和真实 API 联调。

## 当前仓库实际情况

- 前端授权入口：`/system/douyin`。
- 授权 URL 接口：`GET /api/douyin/oauth/authorize-url`，需要管理员登录态。
- OAuth callback：`GET /api/douyin/oauth/callback`，公开路径。
- OAuth 服务会校验 Redis 中的 `state`，再调用 `DouyinTokenService.exchangeCodeAndBootstrap(...)`。
- Token 缓存在 Redis，真实网关使用 `douyin:token:`、`douyin:refresh:`、`douyin:token:expire_at:` 等前缀。
- 当前仓库实际成功日志包含 `Douyin OAuth callback handled successfully`；Token 创建链路可检索 `TokenCreateResponse received` 或 `RealDouyinTokenGateway`。

## 前置条件

- real-pre 后端、前端、PostgreSQL、Redis 已启动。
- `.env.real-pre` 中以下配置已替换真实值：

```dotenv
DOUYIN_BASE_URL=https://openapi-fxg.jinritemai.com
DOUYIN_APP_ID=真实值
DOUYIN_CLIENT_KEY=真实值
DOUYIN_CLIENT_SECRET=真实值
DOUYIN_OAUTH_REDIRECT_URI=https://real-pre.xxx.com/api/douyin/oauth/callback
DOUYIN_OAUTH_FRONTEND_SUCCESS_URL=https://real-pre.xxx.com/system/douyin?oauth=success
DOUYIN_OAUTH_FRONTEND_FAILURE_URL=https://real-pre.xxx.com/system/douyin?oauth=failed
APP_TEST_ENABLED=false
DOUYIN_TEST_ENABLED=false
DOUYIN_REAL_UPSTREAM_MODE=live
```

- 如果使用 IP 端口调试，上述 URL 可以临时写成 `http://服务器IP:8081` 和 `http://服务器IP:3001`，但完整授权体验建议使用 HTTPS 域名。

## 两种 Token 模式

### 模式一：后端主动使用授权信息获取 Token

适用于已有授权码、shop_id 或自用型授权参数的后端联调。当前仓库 Token 创建能力在后端封装，最终仍会走真实抖店 Token 网关。

注意：授权 code 往往短时有效且可能一次性使用。不要把旧 code 当作服务器长期凭据。

### 模式二：百应后台授权跳转后回调换 Token

标准链路：

```text
前端 /system/douyin
-> 后端 /api/douyin/oauth/authorize-url
-> 百应 / 抖店授权页
-> 后端 /api/douyin/oauth/callback?code=...&state=...
-> 后端 token.create
-> 保存 access_token / refresh_token / expire_time / shop_id
-> 跳转前端 /system/douyin?oauth=success
```

这是完整联调推荐路径。

## 执行步骤

### 1. 配置百应后台

推荐地址：

```text
去使用地址：https://real-pre.xxx.com/system/douyin
OAuth 回调地址：https://real-pre.xxx.com/api/douyin/oauth/callback
```

如果需要消息回调，按实际业务回调配置。OAuth callback 和 Webhook 不是同一个地址；当前 Webhook 入口应按代码和对接文档另行核对，不要混填到 OAuth 回调。

### 2. 登录 SaaS 管理后台

```text
https://real-pre.xxx.com/system/douyin
```

管理员点击授权入口。若未登录，先登录后再回到该页面。

### 3. 触发授权

前端调用：

```text
GET /api/douyin/oauth/authorize-url
```

接口返回授权页地址和 state，浏览器跳转到百应 / 抖店授权页。

### 4. 完成回调

授权成功后，百应回调：

```text
GET https://real-pre.xxx.com/api/douyin/oauth/callback?code=...&state=...
```

后端处理成功后跳转：

```text
https://real-pre.xxx.com/system/douyin?oauth=success
```

失败时跳转：

```text
https://real-pre.xxx.com/system/douyin?oauth=failed
```

### 5. 查看日志

```bash
cd /opt/saas/app
docker compose \
  --env-file /opt/saas/env/.env.real-pre \
  --project-name saas-active \
  -f docker-compose.real-pre.yml \
  logs --tail=300 backend-real-pre | grep -E "Douyin OAuth callback|TokenCreateResponse|RealDouyinTokenGateway"
```

当前仓库实际日志以如下关键字为准：

```text
Douyin OAuth callback handled successfully
TokenCreateResponse received
RealDouyinTokenGateway
```

如果团队要求字面日志为 `oauth callback received` 或 `token.create success`，需要先改后端日志实现后再按该字面验收。

### 6. 查看 Redis Token 记录

不要打印真实 Token 值，只检查 key 是否存在：

```bash
docker compose \
  --env-file /opt/saas/env/.env.real-pre \
  --project-name saas-active \
  -f docker-compose.real-pre.yml \
  exec -T redis-real-pre sh -lc 'redis-cli ${REDIS_PASSWORD:+-a "$REDIS_PASSWORD"} --scan --pattern "douyin:*" | head -20'
```

## 验收标准

- 浏览器最终跳到 `/system/douyin?oauth=success`。
- 后端日志出现 `Douyin OAuth callback handled successfully`。
- 后端日志出现 `TokenCreateResponse received` 或真实 Token 网关调用成功日志。
- Redis 能查到 `douyin:token:*`、`douyin:refresh:*` 或 `douyin:token:expire_at:*` 相关 key。
- 后续 `npm run e2e:real-pre:p0:preflight` 的 token readiness 不再 BLOCKED。

## 常见问题

| 问题 | 判断方法 | 处理 |
| --- | --- | --- |
| callback 地址与后台配置不一致 | 浏览器回调 URL 和 `.env.real-pre` 不一致 | 统一改成 HTTPS 域名 |
| code 过期 | 后端 callback 失败，重新授权仍可恢复 | 重新从授权页发起 |
| `client_key` / `client_secret` 错误 | token.create 返回鉴权失败 | 核对抖店开放平台应用配置 |
| 应用权限不足 | 真实 API 返回无权限 | 到百应 / 抖店后台开通权限包 |
| 店铺未授权 | Token 生成或后续接口失败 | 使用正确主体重新授权 |
| 需要招商团长角色授权 | 订单 / 团长接口返回权限不足 | 核对授权主体角色 |
| 服务器时间不准 | state / 签名 / token 过期异常 | 校准 NTP 时间 |
| Nginx `/api/` 未转发 | callback 404 或 502 | 检查宝塔反代路径 |
| CORS 不包含当前域名 | 浏览器 Console 跨域错误 | 修改 `CORS_ALLOWED_ORIGIN_PATTERNS` |
