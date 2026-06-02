# real-pre 部署后验收门禁

## 适用场景

本文用于服务器 real-pre 部署完成后的验收。验收目标是确认环境、权限、真实 API、订单同步、归因、寄样和业绩链路是否具备继续观察真实样本的条件。

## 当前仓库实际情况

根目录 `package.json` 已提供以下命令：

```bash
npm run e2e:real-pre:p0:preflight
npm run e2e:real-pre:roles
npm run e2e:real-pre:p0
```

real-pre P0 允许出现 `PENDING`，原因通常是真实订单、真实成交、真实 `pick_source` 或业绩样本不足。`PENDING` 不是 PASS，也不等同代码硬失败。

## 前置条件

- Docker 四个服务已启动。
- 后端和前端健康检查通过。
- real-pre 环境变量守卫通过。
- 已配置真实抖音应用凭据。
- 已运行 `scripts/real-pre-startup-check.sh`，基线开关全部 PASS（参考 [08-real-pre参数开关契约.md](08-real-pre参数开关契约.md)）。
- 若验证 OAuth，已经完成百应授权或至少具备可授权入口。

## 执行步骤

### 1. 健康检查

服务器上执行：

```bash
curl -fsS http://127.0.0.1:8081/api/system/health
curl -fsS http://127.0.0.1:3001/healthz
```

域名模式：

```bash
curl -fsS https://real-pre.xxx.com/api/system/health
curl -fsS https://real-pre.xxx.com/healthz
```

### 2. 端口和容器检查

```bash
cd /opt/saas/app
docker compose \
  --env-file /opt/saas/env/.env.real-pre \
  --project-name saas-active \
  -f docker-compose.real-pre.yml \
  ps

docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

### 3. 真实 API 和 Token 检查

```bash
docker compose \
  --env-file /opt/saas/env/.env.real-pre \
  --project-name saas-active \
  -f docker-compose.real-pre.yml \
  logs --tail=300 backend-real-pre | grep -E "real-pre|DOUYIN|RealDouyin|Token|order sync|订单"
```

不要在日志中打印真实 access_token 或 refresh_token。

### 4. 本地或服务器执行 E2E 门禁

IP 端口模式：

```powershell
$env:E2E_BASE_URL="http://服务器IP:3001"
$env:E2E_BACKEND_URL="http://服务器IP:8081"
npm run e2e:real-pre:p0:preflight
npm run e2e:real-pre:roles
npm run e2e:real-pre:p0
```

域名模式：

```powershell
$env:E2E_BASE_URL="https://real-pre.xxx.com"
$env:E2E_BACKEND_URL="https://real-pre.xxx.com"
npm run e2e:real-pre:p0:preflight
npm run e2e:real-pre:roles
npm run e2e:real-pre:p0
```

Linux shell：

```bash
E2E_BASE_URL=https://real-pre.xxx.com E2E_BACKEND_URL=https://real-pre.xxx.com npm run e2e:real-pre:p0:preflight
E2E_BASE_URL=https://real-pre.xxx.com E2E_BACKEND_URL=https://real-pre.xxx.com npm run e2e:real-pre:roles
E2E_BASE_URL=https://real-pre.xxx.com E2E_BACKEND_URL=https://real-pre.xxx.com npm run e2e:real-pre:p0
```

## 重点验证项

| 验证项 | 证据 |
| --- | --- |
| 订单同步 | `colonelsettlement_order` 是否有真实订单 |
| `pick_source` 归因 | 订单 `pick_source` 是否能匹配 `pick_source_mapping` |
| 原生 `colonel_buyin_id` 归因兜底 | 订单缺 `pick_source` 时是否能走原生团长字段 |
| 寄样自动完成 | 订单同步事件是否能命中渠道 + 达人 + 商品并完成寄样 |
| 业绩双轨金额 | 预估轨和结算轨金额是否计算 |
| 看板公式 | Dashboard / performance API 是否与数据库事实一致 |
| 角色数据范围 | admin、招商、渠道、运营等角色权限是否正确 |

数据库观察示例：

```bash
docker exec -it saas-active-postgres-real-pre-1 psql -U saas -d saas_real_pre
```

```sql
select count(*) from colonelsettlement_order;

select order_id, product_id, colonel_activity_id, pick_source, channel_user_id, talent_id, create_time, settle_time
from colonelsettlement_order
where deleted = 0
order by create_time desc
limit 20;

select id, pick_source, product_id, activity_id, user_id, colonel_buyin_id, create_time
from pick_source_mapping
where deleted = 0
order by create_time desc
limit 20;

select id, status, channel_user_id, talent_uid, product_id, complete_time
from sample_request
where deleted = 0
order by create_time desc
limit 20;

select order_id, product_id, final_channel_user_id, estimate_channel_commission, effective_channel_commission, estimate_gross_profit, effective_gross_profit
from performance_records
order by calculated_at desc nulls last, created_at desc
limit 20;
```

如果列名与实际 schema 不一致，先执行 `\d 表名`，以当前迁移后的数据库结构为准。

## 结果判定

| 结果 | 判定 |
| --- | --- |
| `preflight FAIL` | 配置或部署失败，必须阻断 |
| `roles FAIL` | 权限链路失败，必须阻断 |
| `p0 FAIL` | 核心链路硬失败，必须阻断 |
| `p0 PENDING` | 真实订单样本不足，不等于代码硬失败，不得写 PASS |
| `p0 PASS` | 具备进一步真实验收条件 |

## 验收标准

最终报告只允许使用以下口径：

```text
服务器 real-pre 受控部署完成。
环境健康检查通过。
real-pre 测试开关关闭：`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`，表示关闭应用侧和抖音侧 mock/test。
真实 upstream 模式开启：`DOUYIN_REAL_UPSTREAM_MODE=live`；订单联调开启：`ORDER_SYNC_ENABLED=true`；活动商品同步开启：`PRODUCT_ACTIVITY_SYNC_ENABLED=true`。
真实上游读、同步、刷新、回调和写入类开关默认开启；真实推广写双开关保持 `DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true` 且 `ALLOW_REAL_PROMOTION_WRITE=true`。
E2E preflight / roles / p0 已执行。
若仍有 PENDING，原因归类为真实订单样本不足，不定义为代码硬失败。
```

## 商品库复制简介 / 转链取证

默认受控部署下，`DOUYIN_REAL_PROMOTION_WRITE_ENABLED=true` 且 `ALLOW_REAL_PROMOTION_WRITE=true`。此时商品库“复制简介”应验证真实转链写入：

1. 使用渠道角色进入商品库，点击目标商品“复制简介”。
2. 接口返回 `promotionLinkGenerated=true`、`promotionLink` 非空、`pickSource` 非空。
3. 上游调用命中 `buyin.instPickSourceConvert` 且返回成功；若上游失败，保留请求时间、错误码、错误信息和 trace。
4. 查询 `pick_source_mapping`，确认目标 `product_id`、`activity_id`、`pick_source`、`promotion_link_id`、`converted_url` 写入成功。
5. 后端日志出现 `promotion_convert_result=success`，并包含 `product_id`、`channel_id`、`pick_source`、`result=success`。
6. 前端复制文案包含 `【链接】` 推广链接，并提示“复制成功，已生成推广链接”。

若因风控、上游冻结或只读排障临时关闭真实推广写双开关，则必须明确记录降级原因，并设置：

```dotenv
DOUYIN_REAL_PROMOTION_WRITE_ENABLED=false
ALLOW_REAL_PROMOTION_WRITE=false
```

降级验证步骤：

1. 使用渠道角色进入商品库，点击目标商品“复制简介”。
2. 接口返回 `promotionLinkGenerated=false`、`promotionLink=null`、`pickSource=null`、`fallbackReason=REAL_PROMOTION_WRITE_DISABLED`。
3. 前端提示基础简介复制成功，但真实推广链接因开关关闭未生成。
4. 该项按“复制基础简介 PASS”记录；真实推广链接、`pick_source` 归因和真实成交回流记录为 `BLOCKED_BY_PROMOTION_WRITE_DISABLED`，不得写成代码失败。
5. 数据库不应新增本次商品对应的 `pick_source_mapping`。

不允许写：

```text
正式生产全量上线成功。
real-pre P0 完全通过。
PENDING 已通过。
```

## 常见问题

| 问题 | 判断方法 | 处理 |
| --- | --- | --- |
| preflight FAIL | 查看 `runtime/qa/out/real-pre-*` | 先修环境变量、端口、Token 或 schema |
| roles FAIL | 查看角色 E2E report | 不能给业务使用，先修权限 |
| p0 FAIL | 查看失败步骤 | 按失败域修复后重跑 |
| p0 PENDING | 报告显示缺真实样本 | 继续观察真实订单，不写 PASS |
| 订单无回流 | 后端日志和订单表为空 | 检查 Token、权限包、订单同步任务和真实订单窗口 |
| 看板为空 | 订单和业绩表无样本 | 先确认订单同步和业绩计算是否有输入 |
