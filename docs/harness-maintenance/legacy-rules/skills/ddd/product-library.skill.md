# Skill: product-library

## 使用场景

用于推广中商品自动入库、商品库漂移、活动筛选 URL 一致性、复制讲解、转链映射和历史数据 backfill。

## 必读文件

- `docs/领域/商品域.md`
- `docs/05-API契约总表.md`
- `docs/对接/活动商品同步.md`
- `docs/对接/转链与pick_source归因.md`
- `docs/决策/ADR-007-活动列表与商品库入口路由统一.md`
- `docs/决策/ADR-008-活动商品上游推广中状态优先.md`

## 禁止事项

- 禁止用活动状态驱动商品入库。
- 禁止裸 SQL 批量直改 `selected_to_library`。
- 禁止本地拒绝或本地暂停错误阻断上游 `status=1` 的可操作事实。
- 禁止用 mock 商品证明 real-pre 商品同步闭环。

## 标准流程

1. 查活动商品上游状态是否为 `status=1/推广中`。
2. 查 `product_operation_state.selected_to_library`。
3. 查 `audit_status`、`display_status`、`manual_disabled`、`hidden_reason`。
4. 若是活动粒度漂移，优先触发活动商品一键同步。
5. 若是历史数据，先执行 repair dry-run，保存差异证据，再决定是否写入。
6. 验证商品库展示规则和活动筛选 URL 一致。
7. 执行复制讲解 / 转链，检查 `pick_source_mapping`。
8. 记录同步日志、API 响应和 SQL 证据。

## 验证方式

- 活动商品 API。
- 商品库 API。
- `product_operation_state` SQL。
- `pick_source_mapping` SQL。
- 后端 `ProductActivityManualSync` 或 repair 日志。
- 前端页面截图 / E2E。

## 输出格式

```md
活动 / 商品样本：
上游状态：
本地状态：
同步 / repair 结果：
商品库展示：
转链映射：
结论：
```

