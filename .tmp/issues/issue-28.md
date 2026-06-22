LABEL=ready-for-agent
TITLE=[P1-URGENT] [PRODUCT-FIX-003] DB 快照 total 与抖音实时 total 偏差排查
---
## Parent

- PRD: `docs/决策/PRD-PRODUCT-MANAGE-FALLBACK-FIX.md`（参考"Further Notes: 范围外"）
- 关联诊断: e440f5ca 后端 `ProductService.buildActivityProductListViewFromDb` 路径

## What to build

**独立诊断 tracer bullet**：解决 PRD-PRODUCT-MANAGE-FALLBACK-FIX 中**显式标注**"不在本次范围"但同根的另一独立问题：

当 `/product/manage/products?activityId=X` 显式带 activityId 时，**即便 ID 正确**，DB 端的 items.length 仍可能 < 抖音实时 total。

可能原因：

1. `ProductService.refreshActivitySnapshots` 翻页上限 `productSyncActivityProductMaxRowsPerActivity: 50000` 截断
2. `reconcileActivitySnapshotsAfterCompleteRefresh` 软删除逻辑可能误删
3. `BizStatusFilter EMPTY` mode 触发 SQL `1=0` → 0 条
4. DB 快照时间戳过期，活动已下架

## Task

执行以下诊断并输出报告：

```bash
cd D:/Projects/SAAS/backend

# 1) 找一个测试活动 ID（与 #26 验证 case 一致，如 3916506）
# 2) 直接调 DB 看 product_snapshot 表
mysql -u root -p saas_db -e "SELECT activity_id, COUNT(*) AS cnt, MAX(sync_time) AS latest FROM product_snapshot WHERE activity_id='3916506' GROUP BY activity_id;"

# 3) 调后端 DB 路径（refresh=false）
curl -s "http://localhost:8080/colonel/activities/3916506/products?count=20&cursor=" | jq '.data.total, .data.items | length'

# 4) 调后端 refresh=true 路径触发同步（要 admin token）
# 5) 调抖音上游（需要 access_token，参考 DouyinProductGateway 测试）
```

## Acceptance

- [ ] 输出报告 `harness/reports/evidence-20260623-db-snapshot-vs-douyin-total.md`
- [ ] 报告包含：DB count、DB items、抖音实时 total 三方对比
- [ ] 明确给出：是翻页截断 / 软删除误判 / BizStatusFilter EMPTY / 其它
- [ ] 如果是翻页截断：建议提高 maxRowsPerActivity 默认值，并标 PR 候选
- [ ] 如果是 BizStatusFilter：建议在 Controller 层加 hintPayload（含 needSync=true）让前端感知

## On failure

1. 记录调用错误（HTTP 状态 / 异常堆栈）
2. 隔离：注释掉 `applyBizStatusFilter` 重跑，确认是否该过滤器导致
3. 不直接改代码，开 follow-up issue

## Blocked by

None — can start independently of #26

## Context (read first)

Per `ask-matt` context hygiene, this issue must be self-contained — the implementer is in a fresh session.

**Required reading order:**
1. `AGENTS.md`
2. `CONTEXT.md`
3. `docs/决策/PRD-PRODUCT-MANAGE-FALLBACK-FIX.md` (第 "Out of Scope" 节)
4. `harness/engineering/issues-index.md`
5. **必读后端文件**:
   - `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\service\ProductService.java:1700-2120` (refresh + DB listView)
   - `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\controller\ColonelActivityController.java:182-263` (Controller 三段分支)
   - `D:\Projects\SAAS\backend\src\main\java\com\colonel\saas\gateway\douyin\real\RealDouyinProductGateway.java` (抖音网关)

**Related issues:**
- #26 (PRODUCT-FIX-001, 同 PRD 独立分支)
- #27 (PRODUCT-FIX-002, 验证 #26)
