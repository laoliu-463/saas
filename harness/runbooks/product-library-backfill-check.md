# Runbook: product library backfill check

## 适用场景

推广中商品没有进入商品库、活动商品操作列只剩查看详情、历史数据需要 repair。

## 检查顺序

1. 查询活动商品上游状态是否为 `status=1/推广中`。
2. 查询 `product_operation_state.selected_to_library`。
3. 查询 `audit_status`、`display_status`、`manual_disabled`、`hidden_reason`。
4. 前端触发活动商品“一键同步商品”，等待后台任务完成。
5. 查看后端 `ProductActivityManualSync completed` 日志。
6. 如仍异常，执行商品库 repair dry-run。
7. 保存 dry-run 差异后，再决定是否 `dryRun=false`。

## 统计 SQL 模板

需按实际表名和字段确认后执行：

```sql
select count(*) as product_snapshot_count
from product_snapshot;
```

```sql
select count(*) as promoting_snapshot_count
from product_snapshot
where status = 1;
```

```sql
select ps.product_id, pos.selected_to_library, pos.audit_status, pos.display_status, pos.manual_disabled
from product_snapshot ps
left join product_operation_state pos on pos.product_id = ps.product_id
where ps.status = 1
  and coalesce(pos.selected_to_library, false) = false
limit 50;
```

```sql
select count(*) as visible_library_count
from product_operation_state
where selected_to_library = true
  and display_status in ('DISPLAYING', 'PENDING');
```

## backfill 判定

- 推广中商品未入库：需要同步或 repair。
- 非推广中商品未展示：预期。
- 本地暂停商品隐藏但可恢复：预期。
- repair 必须先 dry-run，real-pre 写入前必须人工确认窗口。

## 禁止事项

- 不裸 SQL 直改。
- real-pre 写入前必须保存 dry-run 证据。
- 非推广中商品不得强制入库。

## 输出

```md
活动 ID：
商品 ID：
上游状态：
本地状态：
同步 / repair 证据：
结论：
```
