# 域名 SSL 与宝塔 Nginx 反向代理

## 适用场景

本文用于在 Docker 端口部署成功后，接入域名、SSL 和宝塔 Nginx 反向代理。完整百应授权跳转、OAuth code 回调和 token 转换建议使用 HTTPS 域名。

## 当前仓库实际情况

- 后端宿主端口默认 `8081`，容器内端口 `8080`。
- 前端宿主端口默认 `3001`，容器内端口 `80`。
- 后端健康检查：`/api/system/health`。
- 前端健康检查：`/healthz`。
- 前端生产容器内部也有 `/api/` 到 `backend-real-pre:8080` 的 Nginx 代理；外层宝塔仍建议统一反代 `/api/` 到宿主 `127.0.0.1:8081/api/`。

## 前置条件

- 已完成 [real-pre 单机受控部署手册](01-xshell-manual-deploy.md)。
- 域名已备案或满足当前服务器供应商访问要求。
- 云服务器安全组开放 `80/443`。
- 宝塔已安装 Nginx。

## 执行步骤

### 1. 域名解析

在域名服务商添加 A 记录：

```text
类型：A
主机记录：real-pre
记录值：服务器公网 IP
```

验证：

```bash
nslookup real-pre.xxx.com
ping real-pre.xxx.com
```

### 2. 宝塔添加站点

宝塔面板：

```text
网站 -> 添加站点
```

建议配置：

| 配置 | 值 |
| --- | --- |
| 域名 | `real-pre.xxx.com` |
| 根目录 | `/www/wwwroot/real-pre` |
| PHP | 纯静态 / 不选 PHP |
| 数据库 | 不创建 |
| FTP | 不创建 |

这个站点只承载 Nginx、SSL 和反代，不放前端 build 文件。

### 3. 申请 Let's Encrypt SSL

宝塔站点设置：

```text
SSL -> Let's Encrypt -> 文件验证 -> 申请
```

前提：

- 域名已解析到服务器。
- `80` 端口开放。
- HTTP 能访问宝塔站点。

申请成功后开启强制 HTTPS。

### 4. 配置 Nginx 反向代理

目标结构：

```text
/api/ -> http://127.0.0.1:8081/api/
/     -> http://127.0.0.1:3001/
```

`proxy_pass` 末尾保留 `/api/`。

### 5. Nginx 示例配置

证书路径以宝塔实际生成路径为准。

```nginx
server {
    listen 80;
    server_name real-pre.xxx.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name real-pre.xxx.com;

    ssl_certificate     /www/server/panel/vhost/cert/real-pre.xxx.com/fullchain.pem;
    ssl_certificate_key /www/server/panel/vhost/cert/real-pre.xxx.com/privkey.pem;

    location /api/ {
        proxy_pass http://127.0.0.1:8081/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_connect_timeout 10s;
        proxy_read_timeout 60s;
        proxy_send_timeout 60s;
    }

    location / {
        proxy_pass http://127.0.0.1:3001/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

请求头说明：

| 头 | 用途 |
| --- | --- |
| `Host` | 保留原始访问域名 |
| `X-Real-IP` | 传递客户端真实 IP |
| `X-Forwarded-For` | 传递代理链路 IP |
| `X-Forwarded-Proto` | 告诉后端原始请求是 HTTPS |

### 6. 修改 real-pre OAuth 和 CORS

服务器 `/opt/saas/env/.env.real-pre`：

```dotenv
CORS_ALLOWED_ORIGIN_PATTERNS=https://real-pre.xxx.com
DOUYIN_OAUTH_REDIRECT_URI=https://real-pre.xxx.com/api/douyin/oauth/callback
DOUYIN_OAUTH_FRONTEND_SUCCESS_URL=https://real-pre.xxx.com/system/douyin?oauth=success
DOUYIN_OAUTH_FRONTEND_FAILURE_URL=https://real-pre.xxx.com/system/douyin?oauth=failed
```

如启用快递100订阅：

```dotenv
LOGISTICS_KD100_CALLBACK_URL=https://real-pre.xxx.com/api/public/logistics/kuaidi100/callback
```

修改后重启：

```bash
cd /opt/saas/app
ENV_FILE=/opt/saas/env/.env.real-pre ./scripts/deploy-real-pre.sh
```

## 验收标准

```bash
curl -I http://real-pre.xxx.com
curl -I https://real-pre.xxx.com
curl -fsS https://real-pre.xxx.com/api/system/health
curl -fsS https://real-pre.xxx.com/healthz
```

期望：

- HTTP 自动跳 HTTPS。
- HTTPS 前端返回 `200` 或进入登录页。
- `/api/system/health` 返回 `{"status":"UP"}`。
- `/healthz` 返回 `ok`。

反代成功后关闭公网 `8081/3001`，只保留 `80/443/22/宝塔面板受限端口`。

## 没有域名时的限制

没有域名只能先走 IP + 端口：

```text
http://服务器IP:3001
http://服务器IP:8081/api/system/health
```

这种模式不能完整验证百应授权跳转体验，尤其是 HTTPS callback、浏览器安全策略和后台回调地址一致性。

## 常见问题

| 问题 | 排查命令 | 判断方法 |
| --- | --- | --- |
| SSL 申请失败 | `curl -I http://real-pre.xxx.com` | 域名未解析、80 未开放或站点根目录验证失败 |
| HTTPS 502 | 宝塔 Nginx 错误日志 | 后端或前端端口不可达 |
| `/api/` 404 | `curl -v https://real-pre.xxx.com/api/system/health` | `proxy_pass` 路径写错 |
| 前端能开但接口跨域 | 浏览器 Console | `.env.real-pre` 的 CORS 未改为 HTTPS 域名 |
| OAuth 回调跳失败 | 后端日志和浏览器地址栏 | 百应后台 callback 和 `.env.real-pre` 不一致 |
