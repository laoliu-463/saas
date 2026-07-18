# 后端数据与边界

## 数据

- 保留迁移 `V20260716_001__cooperation_workbench_actions.sql`，只创建 `sample_private_note`。
- `(sample_request_id, user_id)` 在 `deleted = 0` 时唯一。
- 同步维护 `init-db.sql` 和 `migrate-all.sql`。

## 达人域门面

- 提供 `findActiveClaimAddress(talentId, ownerUserId)`。
- 提供受控的 `updateActiveClaimAddress(...)`，失效或并发更新必须报冲突。
- `TalentReadDTO.windowSales30d` 只解析 `windowSales30d/window_sales_30d/showcaseSales30d` 的非负精确整数。
- 不读取金额字段冒充销量。

## 验证

- 迁移入口内容一致且幂等。
- 私有备注真实 PostgreSQL upsert、软删除和并发语义通过。
- 地址查询与更新始终使用合作单申请人 ID。
