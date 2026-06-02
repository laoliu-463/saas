# Runbook: order attribution check

## 适用场景

订单入库后渠道 / 招商归因、业绩和寄样自动完成未闭环。

## SQL 检查模板

以下 SQL 需按实际表名和字段复核；如果字段不存在，先查实体类或 migration，不要猜。

### 检查订单 pick_source

```sql
select id, order_id, product_id, talent_id, pick_source, default_channel_id, default_recruiter_id, pay_time
from orders
order by pay_time desc
limit 20;
```

### 检查 mapping

```sql
select id, pick_source, product_id, channel_user_id, talent_id, activity_id, colonel_buyin_id, created_at
from pick_source_mapping
where pick_source = :pick_source
order by created_at desc;
```

### 检查 default_channel_id

```sql
select order_id, pick_source, default_channel_id, attribution_reason
from orders
where order_id = :order_id;
```

### 检查 default_recruiter_id

```sql
select order_id, activity_id, product_id, default_recruiter_id, attribution_reason
from orders
where order_id = :order_id;
```

### 检查 performance_records

```sql
select id, order_id, final_channel_id, final_recruiter_id, estimated_commission, settled_commission, created_at
from performance_records
where order_id = :order_id;
```

### 检查 sample_requests

```sql
select id, channel_id, talent_id, product_id, status, completed_at, updated_at
from sample_requests
where product_id = :product_id
  and talent_id = :talent_id
order by updated_at desc;
```

## 结论口径

- 有真实订单、有 mapping、有默认归属、有业绩、有寄样联动：可记 `PASS`。
- 历史订单 `pick_source` 为空：只能说明样本不足或未走系统转链，不能直接判定代码 bug。
- 有映射但未归因：进入代码 / 数据链路排查。

