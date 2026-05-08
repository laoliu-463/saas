# real-pre 首单回流复核清单

更新时间：2026-05-08
适用环境：`real-pre`
目标：验证“真实转链 -> 新真实订单回流 -> 订单携带 pick_source -> 归因成功”是否真正成立

---

## 1. 本轮验证基线

本轮已完成真实转链的商品：

1. `activityId=3223881` / `productId=3811489772686409810`
2. `activityId=3859426` / `productId=3793873371549270033`

转链完成时间基线：

- `2026-05-08 13:41:29`（Asia/Shanghai）

本轮验证只看该时间之后进入系统的新订单，不把历史旧订单混入闭环判断。

---

## 2. 首单回流通过标准

同时满足以下 4 条，才算真实闭环成立：

1. 新订单 `create_time > '2026-05-08 13:41:29'`
2. 新订单 `product_id` 属于本轮投放商品
3. 新订单带回非空 `pick_source` 或 `pick_extra`
4. 新订单最终归因到正确渠道人员，且未发生串商品 / 串映射覆盖

只满足“有新订单”但没有 `pick_source`，不算闭环成立。
只满足“有 pick_source”但归因错商品，也不算闭环成立。

---

## 3. 值班复核顺序

### 步骤 1：先看是否有新订单

```sql
select
  order_id,
  product_id,
  shop_id,
  create_time,
  attribution_status,
  pick_source,
  pick_extra,
  channel_user_name
from colonelsettlement_order
where product_id in ('3811489772686409810', '3793873371549270033')
  and create_time > timestamp '2026-05-08 13:41:29'
order by create_time desc;
```

判定：

- 查不到记录：说明还没有形成“新链接 -> 新订单”
- 查到记录：进入步骤 2

### 步骤 2：确认是否带回推广来源

```sql
select
  order_id,
  product_id,
  create_time,
  pick_source,
  pick_extra,
  attribution_status,
  attribution_remark,
  extra_data->>'author_buyin_id' as author_buyin_id,
  extra_data->>'author_account' as author_account,
  extra_data#>>'{colonel_order_info,activity_id}' as upstream_activity_id
from colonelsettlement_order
where product_id in ('3811489772686409810', '3793873371549270033')
  and create_time > timestamp '2026-05-08 13:41:29'
order by create_time desc;
```

判定：

- `pick_source` / `pick_extra` 仍为空：说明下单链路没有带来源，闭环未成立
- 任一字段非空：进入步骤 3

### 步骤 3：核对订单命中的映射是否正确

```sql
select
  id,
  pick_source,
  pick_extra,
  product_id,
  activity_id,
  promotion_link_id,
  channel_user_name,
  valid_from,
  valid_until,
  create_time,
  update_time
from pick_source_mapping
where pick_source in (
    select distinct pick_source
    from colonelsettlement_order
    where product_id in ('3811489772686409810', '3793873371549270033')
      and create_time > timestamp '2026-05-08 13:41:29'
      and coalesce(pick_source, '') <> ''
)
   or product_id in ('3811489772686409810', '3793873371549270033')
order by update_time desc, create_time desc;
```

判定：

1. 映射 `product_id` 与订单 `product_id` 一致：进入步骤 4
2. 映射 `product_id` 与订单 `product_id` 不一致：判定为“映射覆盖 / 串商品风险命中”

### 步骤 4：确认最终归因结果是否正确

```sql
select
  order_id,
  product_id,
  create_time,
  pick_source,
  pick_extra,
  attribution_status,
  attribution_remark,
  user_id,
  dept_id,
  channel_user_id,
  channel_user_name,
  promotion_link_id
from colonelsettlement_order
where product_id in ('3811489772686409810', '3793873371549270033')
  and create_time > timestamp '2026-05-08 13:41:29'
order by create_time desc;
```

判定：

- `attribution_status='ATTRIBUTED'` 且渠道人员正确：闭环成立
- `UNATTRIBUTED / PARTIAL`：继续按 `attribution_remark` 排查

---

## 4. 风险专项：pick_source 复用覆盖

当前已知风险：

- 多个真实商品转链返回了同一个 `pick_source=v.MxZLIw`
- `pick_source_mapping` 对 `pick_source` 是唯一约束
- 本地逻辑会按 `pick_source` 覆盖更新映射

专项排查 SQL：

```sql
select
  pl.product_id,
  pl.activity_id,
  pl.pick_source,
  pl.pick_extra,
  pl.created_at as promotion_link_created_at,
  psm.product_id as mapping_product_id,
  psm.activity_id as mapping_activity_id,
  psm.update_time as mapping_update_time
from promotion_link pl
left join pick_source_mapping psm on psm.pick_source = pl.pick_source
where pl.pick_source = 'v.MxZLIw'
order by pl.created_at asc;
```

如果出现：

- `promotion_link.product_id` 有多条不同商品
- 但 `pick_source_mapping` 只保留最后一条商品

则后续新单即便带回 `pick_source`，也要高度怀疑发生归因覆盖。

---

## 5. 浏览器快查口径

### 商品库

- 账号：`channel_staff / admin123`
- 页面：`/product`
- 应看见：
  - 商品状态 `已转链`
  - 文案 `推广链接已就绪`
  - 详情页推广链接页签可见 `复制推广链接`

### 订单工作台

- 账号：`admin / admin123`
- 页面：
  - `/orders?productId=3811489772686409810`
  - `/orders?productId=3793873371549270033`
- 当前基线现状应为：
  - `3811489772686409810`：历史待排查 `9` 单
  - `3793873371549270033`：历史待排查 `5` 单
  - 排查摘要为 `订单未携带 pick_source`

只有当基线数量之后再新增新订单，且新订单显示出 `pick_source` 相关变化，才说明进入下一阶段。

---

## 6. 一句话结论模板

可直接在群里同步：

### 6.1 未形成闭环

`截至当前，real-pre 已完成真实转链，但转链后尚未出现携带 pick_source 的新真实订单，真实订单归因闭环仍未成立。`

### 6.2 已形成闭环

`截至当前，real-pre 在 2026-05-08 13:41:29 之后已出现通过新推广链接产生的新真实订单，订单已携带 pick_source 并成功归因到正确渠道人员，真实订单归因闭环成立。`

### 6.3 命中覆盖风险

`截至当前，新真实订单已带回 pick_source，但由于多个商品复用了同一 pick_source，本地映射出现覆盖，需先处理归因模型后再确认商品级闭环。`
