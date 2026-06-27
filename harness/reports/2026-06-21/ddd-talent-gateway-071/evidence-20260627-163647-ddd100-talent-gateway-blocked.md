# DDD100-TALENT-GATEWAY #71 Evidence

- 时间：2026-06-27 16:36 Asia/Shanghai
- 环境：local real-pre
- 分支：feature/ddd/DDD-VERIFY-001
- Issue：#71 `[DDD100-TALENT-GATEWAY] 第三方达人接口真实响应或 BLOCKED 证据`
- 结论：`BLOCKED`

## 目标

验证第三方达人资料接口是否能在 real-pre 产生真实响应；若缺少真实 endpoint、Token、权限或样本，则按仓库规则记录 `BLOCKED` 证据。

## 代码通道证据

- 图谱检查：`code-review-graph find_large_functions(file_path_pattern="talent\\profile")` 定位关键链路：
  - `TalentProfileSyncService.resolveProfile/syncWithProviders`
  - `DouyinApiTalentProfileProvider.fetch/logAndFail`
  - `ConfigurableHttpTalentProvider.fetch/mapResponse`
  - `PublicWebTalentProvider.fetch/parseHtml`
- 代码事实：
  - `DouyinApiTalentProfileProvider` 当前仅校验抖店 Token 状态；Token 缺失返回 `NOT_CONFIGURED`，Token 正常但 SDK 无达人主页资料接口时返回 `UNSUPPORTED`。
  - `ConfigurableHttpTalentProvider.supports` 需要 `talent.profile.http.enabled=true`、API 采集允许、非 mock、endpoint 非空且请求非手动填写。
  - `PublicWebTalentProvider.supports` 需要 `talent.profile.public-web.enabled=true`、crawler 允许、非 mock 且请求非手动填写。

## 自动化测试

命令：

```powershell
mvn -q -f backend/pom.xml "-Dtest=TalentDataProviderTest,TalentProfileProviderTest,DouyinApiTalentProfileProviderTest,TalentProfileControllerTest" test
```

结果：`PASS`。

关键日志：

```text
Talent API collect skipped ... errorCode=NOT_CONFIGURED
Talent API collect skipped ... errorCode=UNSUPPORTED
```

解释：代码层能返回明确失败原因并保持 provider 链路可测试；该结果不代表 real-pre 已拿到第三方真实响应。

## real-pre 配置证据

`.env.real-pre`：

```text
TALENT_PROFILE_PUBLIC_WEB_ENABLED=false
TALENT_PROFILE_HTTP_ENABLED=false
```

容器内只读探针：

```powershell
docker exec saas-active-backend-real-pre-1 sh -lc 'printf "...redacted presence..."'
```

结果：

```text
PUBLIC_WEB_ENABLED=false
HTTP_ENABLED=false
HTTP_ENDPOINT_PRESENT=missing
HTTP_TOKEN_PRESENT=missing
HTTP_AUTH_PRESENT=missing
```

安全检查：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\safety-check.ps1 -Env real-pre
```

结果：

```text
TALENT_PROFILE_HTTP_TOKEN: missing
TALENT_PROFILE_HTTP_AUTHORIZATION: missing
Safety check passed.
```

## 运行环境证据

Docker 状态：

```text
saas-active-backend-real-pre-1    Up (healthy) 127.0.0.1:8081->8080/tcp
saas-active-frontend-real-pre-1   Up (healthy) 127.0.0.1:3001->80/tcp
saas-active-postgres-real-pre-1   Up (healthy)
saas-active-redis-real-pre-1      Up (healthy)
```

后端启动日志：

```text
activeProfiles=real-pre
app.test.enabled=false
douyin.test.enabled=false
db.name=saas_real_pre
Talent profile sync schema ensured
```

HTTP 健康路径说明：

- `GET http://127.0.0.1:8081/actuator/health` 返回 `404`。
- `GET http://127.0.0.1:8081/api/actuator/health` 返回 `401`，说明该路径当前受鉴权保护；本报告不把该手工请求记为健康失败。

## 数据库只读证据

命令：

```sql
SELECT to_regclass('public.talent_profile_sync_log') AS sync_log_table;
SELECT provider_code, sync_status, count(*) AS rows, max(started_at) AS latest_started
FROM talent_profile_sync_log
GROUP BY provider_code, sync_status
ORDER BY latest_started DESC NULLS LAST
LIMIT 20;
```

结果：

```text
sync_log_table = talent_profile_sync_log
provider_code | sync_status | rows | latest_started
(0 rows)
```

解释：real-pre 当前没有任何达人第三方资料同步记录，无法提供真实第三方响应样本。

## 现象、证据、推论、结论

- 现象：#71 要求补齐第三方达人接口真实响应或 `BLOCKED` 证据。
- 证据：real-pre 中 public-web 与 HTTP provider 均关闭，HTTP endpoint/token/authorization 均缺失；数据库无同步记录；provider/controller 测试通过。
- 推论：当前代码通道可测试，但 real-pre 缺少真实第三方 endpoint/认证配置，无法发起可审计的真实第三方达人资料请求。
- 阶段性结论：#71 满足 `BLOCKED` 关闭条件；不能写成真实第三方响应 `PASS`。

## 后续解除阻塞条件

1. 提供合法的第三方达人资料 endpoint 和认证方式，配置 `TALENT_PROFILE_HTTP_ENABLED=true`。
2. 或提供抖店开放平台达人资料官方接口权限，并扩展 `DouyinApiTalentProfileProvider` 的真实请求实现。
3. 重启 real-pre 后端后，使用可脱敏样本执行 `/talents/resolve-profile` 或同步入口。
4. 在不泄露密钥和个人敏感信息的前提下，记录请求时间、provider_code、状态码、响应字段摘要和 `talent_profile_sync_log` 记录。

## 本轮未执行项

- 未执行真实第三方请求：缺少 endpoint/token/authorization，且 provider 开关关闭。
- 未修改业务代码、DB schema 或 `.env.real-pre`。
- 未远端部署。
