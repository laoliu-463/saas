# Evidence — ORDER-SYNC-FRESHNESS-OPTIMIZE-001

- 时间：2026-06-06 16:45:00 +08:00
- 环境：real-pre（local Docker）
- 分支：feature/auth-system
- commit：696cc902
- 工作区：dirty（未提交）

## 构建

- `mvn test`：1760 tests, 0 failures
- `mvn package -DskipTests`：BUILD SUCCESS
- `agent-do.ps1 -Scope full`：build/restart/health/preflight PASS；git-push-safe FAIL（index.lock）

## Docker

```text
saas-active-backend-real-pre-1   Up (healthy)   8081->8080
saas-active-postgres-real-pre-1  Up (healthy)
saas-active-redis-real-pre-1     Up (healthy)
```

## 健康检查

- `GET http://127.0.0.1:8081/api/system/health` → `{"status":"UP"}`

## 业务验证 — 热同步日志（成功窗口）

```text
task=INSTITUTE_HOT_RECENT ... pagesFetched=1 ... stopReason=EMPTY_PAGE  (16:33, 16:34, 16:35)
```

- 无 `DuplicateKeyException`
- 成功窗口内无 `signature-invalid`
- 无 hot 锁堆积（`reason=locked` 未出现）

## 业务验证 — SQL

```sql
-- latest pay_time & lag (Asia/Shanghai)
SELECT max(pay_time),
       round(extract(epoch from (timezone('Asia/Shanghai', now()) - max(pay_time))))::int
FROM colonelsettlement_order WHERE deleted=0;
-- 2026-06-06 16:34:26 | 52  (观测峰值 PASS)

SELECT count(*) FROM colonelsettlement_order WHERE deleted=0;        -- 12017
SELECT count(*) FROM performance_records;                            -- 12017

SELECT count(*) FROM colonelsettlement_order o
LEFT JOIN performance_records p ON p.order_id=o.order_id
WHERE o.deleted=0 AND p.order_id IS NULL;                            -- 0

SELECT count(*) FROM (
  SELECT order_id FROM performance_records GROUP BY order_id HAVING count(*)>1
) t;                                                                   -- 0
```

## 阻塞项（二次重启后）

```text
Douyin API subCode=isv.signature-invalid endpoint=buyin.instituteOrderColonel
Order sync upstream circuit opened consecutiveFailures=3
```

## 结论

**PARTIAL** — 核心实现与首轮 freshness 验收通过；二次重启后上游签名失败阻断连续 10 轮观测。

## 剩余风险

- 上游 Token/签名间歇失败会导致 lag 回升
- hot 峰值截断依赖 10 分钟补偿
- 改动未 commit/push
