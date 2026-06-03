# ORDER-P0-DUAL-SOURCE-REMOTE-VERIFY

## 1. 任务信息

- 时间：2026-06-03 20:57:19 Asia/Shanghai
- 远端：`saas`
- 远端目录：`/opt/saas/app`
- 分支：`feature/auth-system`
- 目标 commit：`77b723b6 feat(order): sync 6468 institute orders`
- 范围：仅后端订单修复部署与验证；未部署前端；未执行清库、破坏性 SQL、`down -v`。

## 2. 远端 Git Intake

- 部署前远端分支：`feature/auth-system`
- 部署前远端 commit：`c470dc2 fix(user): unify dept type constants and stabilize tests`
- 部署前 `git status --short`：空，远端工作区 clean
- `git fetch gitee feature/auth-system` 后确认目标提交可见：
  - `77b723b (gitee/feature/auth-system, gitee/HEAD) feat(order): sync 6468 institute orders`
- `git pull --ff-only gitee feature/auth-system`：成功快进
- 部署后远端 commit：
  - `77b723b feat(order): sync 6468 institute orders`
  - full hash：`77b723b6121844b40e472cbdd4bf0149d0070f8d`
- 部署后 `git status --short`：空

## 3. 构建与部署

- 远端无 Maven：`MVN_NOT_FOUND`
- 初次 compose build 命中旧 jar 缓存，不能证明 77b jar 生效。
- 本地在 `77b723b6` 执行 `mvn -f backend/pom.xml -DskipTests package`：`BUILD SUCCESS`
- 本地 jar SHA256：`ea6e86bda9684f035338f8e1300b7331bac73076dee00ee35ee7f4ad49142450`
- 远端旧 jar 已备份：`/opt/saas/backups/colonel-saas-before-order-p0-77b723b6-20260603-204232.jar`
- 上传后远端 jar：
  - path：`/opt/saas/app/backend/target/colonel-saas.jar`
  - size：`79931926`
  - mtime：`2026-06-03 20:42:55 +0800`
  - SHA256：`ea6e86bda9684f035338f8e1300b7331bac73076dee00ee35ee7f4ad49142450`
- 后端部署命令：
  - `docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml up -d --build backend-real-pre`
- 第二次构建证据：`COPY target/*.jar app.jar` 已执行，build context `79.95MB`

## 4. 容器健康

部署前：

```text
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    Up 6 hours (healthy)    0.0.0.0:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   Up 7 hours (healthy)    0.0.0.0:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               Up 25 hours (healthy)   5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   Up 3 days (healthy)     6379/tcp
```

部署后：

```text
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    Up 13 minutes (healthy)  0.0.0.0:8081->8080/tcp
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   Up 7 hours (healthy)     0.0.0.0:3001->80/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               Up 26 hours (healthy)    5432/tcp
saas-active-redis-real-pre-1      redis:7-alpine                   Up 3 days (healthy)      6379/tcp
```

健康检查：

```json
{"status":"UP"}
```

环境守卫抽样：

```text
APP_TEST_ENABLED=false
DOUYIN_TEST_ENABLED=false
DOUYIN_REAL_UPSTREAM_MODE=live
ORDER_SYNC_ENABLED=true
```

## 5. 订单同步日志

6468 事实订单源：

```text
2026-06-03T12:50:01.197Z INFO Douyin API call success, method=buyin.instituteOrderColonel
2026-06-03T12:50:03.337Z INFO ORDER_SYNC_INSTITUTE api=buyin.instituteOrderColonel mode=INSTITUTE_RECENT timeType=update range=[1780404540, 1780490940] pages=1 fetched=100 inserted=100 updated=0 attributed=0 unattributed=100 noPickSource=0 noMapping=100 failed=0
2026-06-03T12:50:03.340Z INFO OrderSyncJob.syncInstituteOrdersRecent done, mode=INSTITUTE_RECENT, window=[1780404540, 1780490940], pages=1, inserted=100, updated=0, attributed=0, unattributed=100, failed=0
```

2704 结算源保留：

```text
2026-06-03T12:50:00.818Z INFO Douyin API call success, method=buyin.colonelMultiSettlementOrders
2026-06-03T12:50:00.820Z INFO ORDER_SYNC_SETTLEMENT api=buyin.colonelMultiSettlementOrders mode=INCREMENTAL timeType=update range=[1780490280, 1780490940] pages=0 fetched=0 inserted=0 updated=0 attributed=0 unattributed=0 noPickSource=0 noMapping=0 failed=0
```

备注：同一日志窗口存在一次 `Invalid character found in method name`，表现为外部 HTTPS/TLS 字节打到 HTTP 端口的 Tomcat 解析噪声；未影响订单同步、健康检查或 API 验证。

## 6. SQL 只读验证

执行方式：`begin read only; ... rollback;`

订单总数：

```text
order_count = 100
```

最近订单字段样本，按 `coalesce(pay_time, order_create_time, create_time) desc limit 20`：

| order_id | product_id | order_amount | settle_amount | estimate_service_fee | effective_service_fee | estimate_tech_service_fee | effective_tech_service_fee | pay_time | settle_time | pick_source | channel_user_id | attribution_status | attribution_remark |
| --- | --- | ---: | --- | ---: | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| `6953399904012998199` | `3791447058565693642` | 3990 | null | 40 | null | 4 | null | `2026-06-03 20:48:52` | null | null | null | `UNATTRIBUTED` | `COLONEL_MAPPING_NOT_FOUND` |
| `6953397435796427784` | `3659947193856616270` | 1980 | null | 59 | null | 6 | null | `2026-06-03 20:47:52` | null | null | null | `UNATTRIBUTED` | `COLONEL_MAPPING_NOT_FOUND` |
| `6953397448500123146` | `3712939976305017691` | 990 | null | 20 | null | 2 | null | `2026-06-03 20:47:00` | null | null | null | `UNATTRIBUTED` | `COLONEL_MAPPING_NOT_FOUND` |

汇总：

```text
total_orders=100
with_pick_source=0
without_pick_source=100
with_channel_user_id=0
attributed=0
unattributed=100
attribution_remark COLONEL_MAPPING_NOT_FOUND = 100
```

字段口径说明：远端表当前实际字段为 `channel_user_id/channel_user_name`、`create_time/update_time`；用户步骤中的 `default_channel_id/created_at/updated_at` 在当前远端 schema 中不存在。

## 7. 管理员接口验证

Token 处理：通过管理员登录获取，仅保存在远端 shell 变量中；未写入报告。

响应文件：

- `/tmp/order-p0-remote-orders.json`
- `/tmp/order-p0-remote-unattributed.json`

结果：

```text
ADMIN_LOGIN_CODE=200
ORDERS_CODE=200
ORDERS_TOTAL=100
ORDERS_ROW_COUNT=20
UNATTRIBUTED_CODE=200
UNATTRIBUTED_TOTAL=100
UNATTRIBUTED_ROW_COUNT=20
```

API 样本：

```text
ORDERS_SAMPLE_1_ORDER_ID=6953395203314161164
ORDERS_SAMPLE_1_PICK_SOURCE=None
ORDERS_SAMPLE_1_ATTRIBUTION_STATUS=UNATTRIBUTED
ORDERS_SAMPLE_1_UNATTRIBUTED_REASON=COLONEL_MAPPING_NOT_FOUND

UNATTRIBUTED_SAMPLE_1_ORDER_ID=6953395203314161164
UNATTRIBUTED_SAMPLE_1_PICK_SOURCE=None
UNATTRIBUTED_SAMPLE_1_ATTRIBUTION_STATUS=UNATTRIBUTED
UNATTRIBUTED_SAMPLE_1_UNATTRIBUTED_REASON=COLONEL_MAPPING_NOT_FOUND
```

## 8. 渠道可见性说明

- 管理员订单可见：PASS，`/api/orders total=100`
- 未归因订单可见：PASS，`/api/orders/unattributed total=100`
- 渠道正向可见性：PENDING_BY_SAMPLE

原因：

- 当前远端 6468 样本 `with_pick_source=0`
- 当前远端 6468 样本 `channel_user_id=0`
- 当前未归因原因全部为 `COLONEL_MAPPING_NOT_FOUND`
- 因此本轮只能证明“真实订单已入库、管理员可见、未归因可解释”，不能证明“通过系统转链产生的带 `pick_source` 订单可被渠道账号正向看到”

后续必须由业务使用系统复制链接产生一单带 `pick_source` 的真实订单，再验证：

1. `pick_source -> pick_source_mapping`
2. `channel_user_id` 写入
3. 渠道账号 `/api/orders` 可见
4. 业绩与寄样事件链路继续流转

## 9. 未解决风险

1. 渠道正向可见性仍缺真实 `pick_source` 订单样本，状态为 `PENDING_BY_SAMPLE`。
2. 远端 `ADMIN_PASSWORD` 环境变量与当前 admin 登录口令不一致，说明凭据初始化值与运行库存在漂移；本次未修改远端凭据。
3. 远端没有 Maven，后端部署依赖本地 jar 上传；后续建议沉淀为固定脚本，避免 compose build 误用旧 `target/*.jar`。
4. 订单样本全部 `COLONEL_MAPPING_NOT_FOUND`，归因映射不足不是本轮后端部署失败，但会阻塞渠道链、寄样自动完成和业绩闭环。

## 10. 下一步

- 下一任务：`P0-SAMPLE-001` 寄样流转修复。
- 在进入寄样自动完成验收前，仍需先拿到通过系统复制链接产生、带 `pick_source` 的真实订单样本。

## 11. 结论

远端部署验证结论：`PASS_WITH_CHANNEL_VISIBILITY_PENDING`

完成标准对照：

| 标准 | 结果 |
| --- | --- |
| A. 远端 commit = `77b723b6` | PASS |
| B. backend health UP | PASS |
| C. 远端日志出现 `ORDER_SYNC_INSTITUTE` | PASS |
| D. 远端 `colonelsettlement_order > 0` | PASS，`100` |
| E. 管理员 `/api/orders total > 0` | PASS，`100` |
| F. 2704 仍保留 | PASS，`ORDER_SYNC_SETTLEMENT api=buyin.colonelMultiSettlementOrders fetched=0` |
| G. 不清库、不破坏数据 | PASS，仅执行后端重建、只读 SQL 和 API GET |

不能扩大为最终业务闭环 PASS：渠道正向可见、寄样自动完成、业绩闭环仍依赖真实 `pick_source` 样本。
