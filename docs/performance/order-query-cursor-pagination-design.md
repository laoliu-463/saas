# 订单游标分页设计

日期：2026-06-21
状态：设计文档，不实现代码，不替换旧接口。

## 目标

为深分页准备新查询接口，避免现有 `GET /orders?page=&size=` 在大 offset 下触发高成本 count 与跳页扫描。旧分页接口继续保留，API 响应结构不变。

## 新接口建议

`GET /orders/scroll`

参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `lastUpdateTime` | 否 | 上一页最后一条记录的 `update_time` |
| `lastCreateTime` | 否 | 上一页最后一条记录的 `create_time` |
| `lastId` | 否 | 上一页最后一条记录的 `id` |
| `size` | 否 | 默认 20，最大 200 |

排序固定为：

```sql
ORDER BY update_time DESC, create_time DESC, id DESC
```

## SQL 条件示例

第一页不带 cursor：

```sql
SELECT ...
FROM colonelsettlement_order
WHERE deleted = 0
ORDER BY update_time DESC, create_time DESC, id DESC
LIMIT :size;
```

下一页：

```sql
SELECT ...
FROM colonelsettlement_order
WHERE deleted = 0
  AND (
    update_time < :lastUpdateTime
    OR (update_time = :lastUpdateTime AND create_time < :lastCreateTime)
    OR (update_time = :lastUpdateTime AND create_time = :lastCreateTime AND id < :lastId)
  )
ORDER BY update_time DESC, create_time DESC, id DESC
LIMIT :size;
```

## 配套索引

```sql
CREATE INDEX IF NOT EXISTS idx_cso_active_scroll
ON colonelsettlement_order (update_time DESC, create_time DESC, id DESC)
WHERE deleted = 0;
```

如果按数据范围过滤，建议结合实际 `EXPLAIN` 评估：

- `(user_id, update_time DESC, create_time DESC, id DESC) WHERE deleted = 0`
- `(dept_id, update_time DESC, create_time DESC, id DESC) WHERE deleted = 0`
- `(channel_dept_id, update_time DESC, create_time DESC, id DESC) WHERE deleted = 0`

## 前端迁移方式

- 保留当前分页表格，默认仍调用 `GET /orders`。
- 新增“连续加载/滚动加载”模式时调用 `/orders/scroll`。
- cursor 由后端返回最后一条记录的 `updateTime/createTime/id` 或由前端从最后一行读取。
- 不支持跳转到任意页；需要跳页的后台审计场景继续使用旧分页。

## 共存策略

- `/orders`：兼容现有分页、导出、审计和跳页。
- `/orders/scroll`：仅面向高频浏览和深分页性能优化。
- 两者共享筛选条件、数据范围和返回行 DTO，避免业务口径分裂。
- 上线前必须用同一组筛选条件对比两接口第一页结果，确认排序和字段一致。

## 暂不实现原因

游标分页会改变前端交互语义，尤其是不再天然支持总页数和跳页。本阶段只提交设计，不替换旧接口，避免影响现有页面和用户使用习惯。
