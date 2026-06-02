# Runbook: sample auto complete check

## 适用场景

寄样待交作业未因订单同步自动完成。

## 检查顺序

1. 查询寄样申请是否处于待交作业状态。
2. 查询订单是否真实同步入库。
3. 查询订单是否有渠道、达人、商品和支付时间。
4. 查询订单归因是否成功。
5. 查询寄样状态日志。
6. 查询后端订单已同步事件消费日志。

## 关键字段

- `channel_id`：必须能从订单归因结果追到渠道。
- `talent_id`：订单与寄样申请达人一致。
- `product_id`：订单与寄样申请商品一致。
- `pay_time`：订单支付时间落在可匹配窗口内。
- `status`：待交作业 -> 已完成。
- `completed_at` / 交作业时间：状态完成后必须记录。

## SQL 模板

```sql
select id, channel_id, talent_id, product_id, status, completed_at, updated_at
from sample_requests
where status in ('PENDING_WORK', 'WAITING_SUBMISSION', 'TO_SUBMIT')
order by updated_at desc
limit 20;
```

```sql
select id, order_id, product_id, talent_id, default_channel_id, pay_time
from orders
where product_id = :product_id
  and talent_id = :talent_id
order by pay_time desc
limit 20;
```

## 关键判断

- 若订单没有归因，优先回到 `order-attribution-check.md`。
- 若订单已归因但寄样未完成，再查寄样状态机和事件消费。
- 若状态由人工改库，不算自动完成证据。

## 输出

```md
寄样样本：
订单样本：
归因结果：
事件日志：
状态变化：
结论：
```
