# P0-SAMPLE-001 远端部署验证报告

## 结论

- 结论：`PARTIAL`
- 远端部署：已从 `77b723b feat(order): sync 6468 institute orders` 快进到 `ab03d72 docs: add sample apply verification report`，对应目标短提交 `ab03d729`。
- 核心修复链路：`PASS`。商品库快照 ID 快速寄样成功创建 `sample_request`，`status=1 / PENDING_AUDIT / 待审核`，商品主表已物化，未再出现外键失败；缺失 `crawler_talent_info` 时已按 `talent.douyin_uid` 兜底。
- 权限列表：`admin` 可见，`biz_leader` 可见，`biz_staff` 不可见。
- 失败原因：接口返回 `items[].message` 明细，前端 `QuickSampleModal` 可展示具体原因，不再只能展示“申请失败”。
- 未完全 PASS 原因：远端数据前置与本地验证不同，`channel_staff` 无有效私海达人，唯一有私海达人的等价渠道账号“玄同”无法用已知测试口径登录；且远端 `product_operation_state.assignee_id` 全为空，无法证明“该商品分配给 biz_leader”这一前提，只能证明 `biz_leader` 审核列表实际可见。

## 远端 Git Intake

- 远端目录：`/opt/saas/app`
- 分支：`feature/auth-system`
- 部署前 commit：`77b723b feat(order): sync 6468 institute orders`
- 部署前工作区：clean，无 dirty
- dirty 分类：无

## 拉取目标提交

- `git fetch gitee feature/auth-system` 后可见：
  - `ab03d72 docs: add sample apply verification report`
  - `b881a08 fix: quick sample manual talent fallback`
  - `0a9a6b3 fix: sample apply from product library`
- `git pull --ff-only gitee feature/auth-system` 成功。
- 部署后 commit：`ab03d72 docs: add sample apply verification report`
- 部署后工作区：clean

## 部署方式

本次没有使用项目远端部署脚本，因为现有远端部署脚本会执行数据库迁移流程；用户要求“所有 SQL 必须只读”。实际执行为：

- 使用 Maven Docker 镜像在远端构建后端 jar。
- 执行 `docker compose --env-file /opt/saas/env/.env.real-pre -f docker-compose.real-pre.yml up -d --build backend-real-pre frontend-real-pre`。
- 未使用 `-v`。
- 未执行 `docker compose down -v`。
- 未清库。
- 未重建 PostgreSQL / Redis volume。
- PostgreSQL / Redis 仅保持运行，未重建。

## 健康检查

部署后容器状态：

```text
saas-active-frontend-real-pre-1   colonel-saas/frontend:real-pre   Up 12 minutes (healthy)   0.0.0.0:3001->80/tcp
saas-active-backend-real-pre-1    colonel-saas/backend:real-pre    Up 12 minutes (healthy)   0.0.0.0:8081->8080/tcp
saas-active-postgres-real-pre-1   postgres:15-alpine               Up 27 hours (healthy)
saas-active-redis-real-pre-1      redis:7-alpine                   Up 3 days (healthy)
```

健康探针：

```text
GET http://127.0.0.1:8081/api/system/health -> {"status":"UP"}
GET http://127.0.0.1:3001/healthz -> ok
```

## 远端数据前置

只读 SQL 确认：

- `DISPLAYING + selected_to_library=true` 商品有 615 条。
- 615 条 `DISPLAYING` 商品全部 `assignee_id IS NULL`。
- `channel_staff` 账号存在且可登录，但没有有效私海达人。
- 等价渠道账号“玄同”为 `channel_leader:2`，有 1 个有效认领达人，达人 `douyin_uid=56723079343`，且没有 `crawler_talent_info`。
- “玄同”无法用已知测试口径登录，因此不能用该账号直接完成渠道入口成功申请。

## 快速寄样 API 结果

由于远端 `channel_staff` 无私海达人且等价渠道账号无法登录，本次成功链路使用 `admin` 代选等价渠道用户“玄同”发起快速寄样。

请求证据：

```text
POST /api/products/81a5d39b-e661-3b19-96d3-e55b145f15f1/quick-sample
relationId=81a5d39b-e661-3b19-96d3-e55b145f15f1
sourceProductId=3448534418308858561
talentUid=56723079343
proxyChannelUserId=1c34b680-30b2-41ec-bdc7-2dde1f37e786
marker=REMOTE_VERIFY_P0_SAMPLE_001_20260603220909
```

响应摘要：

```text
http=200
topCode=200
success=true
successCount=1
failureCount=0
externalEnabled=false
externalSupported=false
gatewayStatus=UNSUPPORTED_BY_SDK
fallbackType=LOCAL_FALLBACK
sampleRequestId=9c655738-76b1-4fd0-9676-8f307c694f3f
message=抖店外部寄样暂未接通，已创建系统内寄样申请
```

## sample_request SQL 证据

只读 SQL 结果：

```text
sample|9c655738-76b1-4fd0-9676-8f307c694f3f|QS2026060337AF9AAB|1|LOCAL_FALLBACK|81a5d39b-e661-3b19-96d3-e55b145f15f1|3448534418308858561|三利搓澡巾5A抗菌款强力搓泥神器男女专用儿童洗澡巾家用搓背手套|2d79278a-4a8d-4bff-bb5c-beeb358888ba|56723079343|小雪吃什么|admin|玄同|规格: remote verify spec；REMOTE_VERIFY_P0_SAMPLE_001_20260603220909|2026-06-03 14:09:11.058323
product_exists|81a5d39b-e661-3b19-96d3-e55b145f15f1|3448534418308858561|三利搓澡巾5A抗菌款强力搓泥神器男女专用儿童洗澡巾家用搓背手套|0|2026-06-03 14:09:11.043186
talent_fallback|56723079343|2d79278a-4a8d-4bff-bb5c-beeb358888ba|56723079343|小雪吃什么|has_crawler_info=false
product_assignment|3448534418308858561|DISPLAYING|selected_to_library=true|assignee_id=NULL
```

确认项：

- `sample_request.id=9c655738-76b1-4fd0-9676-8f307c694f3f`
- `request_no=QS2026060337AF9AAB`
- `status=1`
- 状态语义：`PENDING_AUDIT / 待审核`
- `product` 主表已存在对应记录。
- `crawler_talent_info` 缺失时，已按 `talent.douyin_uid=56723079343` 兜底。
- 未出现外键失败。

## 审核列表验证

接口：

```text
GET /api/samples?page=1&size=20&status=PENDING_AUDIT&requestNo=QS2026060337AF9AAB
```

结果：

```text
admin      -> http=200 code=200 total=1 requestNo=QS2026060337AF9AAB status=PENDING_AUDIT
biz_leader -> http=200 code=200 total=1 requestNo=QS2026060337AF9AAB status=PENDING_AUDIT
biz_staff  -> http=200 code=200 total=0
```

额外观察：

- `channel_staff` 也能查到该待审核单：`total=1`。这不是本任务完成标准的一部分，但从权限边界角度建议另起 RBAC 专项复核。
- 因远端商品 `assignee_id=NULL`，本次不能证明“负责该商品的 biz_leader”这一分配前提，只能证明 `biz_leader` 审核列表实际可见。

## 失败原因验证

失败分支 1：`channel_staff` 对未在自己私海中的达人发起申请。

```text
POST /api/products/81a5d39b-e661-3b19-96d3-e55b145f15f1/quick-sample
http=200
success=false
successCount=0
failureCount=1
items[0].message=该达人未在你的私海中，请先认领后再申请寄样
```

失败分支 2：不存在达人。

```text
POST /api/products/81a5d39b-e661-3b19-96d3-e55b145f15f1/quick-sample
http=200
success=false
successCount=0
failureCount=1
items[0].message=达人不存在
```

前端展示链路：

- `QuickSampleModal.vue` 会读取失败项 `items[].message` 并拼接为具体失败明细。
- 因接口已返回明确 `items[].message`，前端不会只剩“申请失败”。

## 日志证据

执行：

```bash
docker logs saas-active-backend-real-pre-1 --since 30m 2>&1 \
  | grep -Eina "QuickSample|ProductQuickSampleService|sample_request|PENDING_AUDIT|达人不存在|外键|constraint|ERROR|Exception" \
  | tail -n 300
```

关键日志：

```text
POST /api/products/81a5d39b-e661-3b19-96d3-e55b145f15f1/quick-sample status=200 durationMs=164 error=
GET /api/samples status=200
GET /api/samples status=200
GET /api/samples status=200
POST /api/products/81a5d39b-e661-3b19-96d3-e55b145f15f1/quick-sample status=200 durationMs=12 error=
POST /api/products/81a5d39b-e661-3b19-96d3-e55b145f15f1/quick-sample status=200 durationMs=12 error=
```

未发现：

- 外键失败
- `constraint` 异常
- `ERROR`
- `Exception`
- 不可解释异常

## PENDING_AUDIT / PENDING_REVIEW 命名

- 当前寄样域实际状态为 `status=1 -> PENDING_AUDIT -> 待审核`。
- `PENDING_REVIEW` 更多出现在商品官方/前端映射语义中，不是本次新建寄样单的实际 API 状态。
- 建议后续做一次命名口径清理：寄样域保留 `PENDING_AUDIT`，如产品文案需要“PENDING_REVIEW / 待审核”，需明确是否只是展示别名，避免状态机字段混用。

## 限制与风险

- 本次未用 `channel_staff` 成功创建寄样单，原因是远端 `channel_staff` 没有私海达人。
- 本次未用等价渠道账号“玄同”直接登录创建寄样单，原因是该账号无法用已知测试口径登录。
- 远端没有任何商品 `assignee_id`，不能验证“该商品分配给 biz_leader”的严格前提。
- `channel_staff` 可见该待审核单是额外权限观察，建议另起权限专项，不在本次远端部署验证内修改。

## 下一步建议

1. 单独准备远端真实渠道验收前置：给 `channel_staff` 认领一个有效达人，或提供可登录的等价渠道账号。
2. 单独准备远端招商分配前置：通过业务接口把一个 DISPLAYING 商品分配给 `biz_leader` 或 `biz_staff`，再重跑“负责该商品的审核人可见”。
3. 对 `GET /api/samples` 做 RBAC 专项复核，重点确认 `channel_staff` 是否应能看到非本人 `channel_user_id` 的待审核单。
4. 如业务要求样本状态展示为 `PENDING_REVIEW`，单独做状态命名 ADR；不要直接改寄样状态机。
