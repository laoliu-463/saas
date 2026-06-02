# Eval: product-library

## 验收目标

验证推广中商品入库、非推广中商品隐藏、历史 backfill / repair 和活动筛选 URL 一致性。

## 前置条件

- 存在活动商品同步样本。
- real-pre 场景优先使用真实上游活动商品。

## 执行步骤

1. 查询上游 `status=1/推广中` 商品。
2. 验证 `selected_to_library=true`。
3. 验证 `audit_status`、`display_status`、`manual_disabled`。
4. 验证非推广中商品不进入商品库展示。
5. 对历史未重算数据执行 repair dry-run。
6. 确认后执行 backfill / repair 写入。
7. 验证活动列表点击商品与商品列表活动筛选 URL 一致。

## 通过标准

- 推广中商品进入商品库并可操作。
- 非推广中商品不展示。
- repair 有 dry-run 证据。
- URL 和筛选参数一致。

## 失败含义

- 推广中商品只剩“查看详情”：可能是商品库漂移。
- repair 无 dry-run 证据：不能直接写入 real-pre。

## 证据要求

- 活动商品 API。
- 商品库 API。
- `product_operation_state` SQL。
- repair 日志。
- 前端截图 / E2E。

