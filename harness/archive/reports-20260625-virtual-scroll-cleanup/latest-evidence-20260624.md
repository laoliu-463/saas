# evidence report：/alliance/colonelActivityProduct status=4 来源排查

## 基础信息
- 时间：2026-06-24 16:32:06+08:00
- 环境：real-pre
- 分支：`feature/product-manage-fallback-fix-20260623`
- Commit：`fbf1b728`
- 工作区状态：Dirty（有未提交修改）
- 容器状态（截面时刻）：
  - `saas-active-frontend-real-pre-1`：Up 6m (healthy)
  - `saas-active-backend-real-pre-1`：Up 6m (healthy)
  - `saas-active-postgres-real-pre-1`：Up 17h (healthy)
  - `saas-active-redis-real-pre-1`：Up 5d (healthy)
- 健康检查：未进行额外手工健康接口探活

## 结论目标
判定 `status=4` 是否上游传入，还是本地新增/转换产生。

## 证据一：数据库是否有 status=4

### 表内状态计数
| 表名 | total | status=4 条数 |
|---|---:|---:|
| `product` | 12 | 0 |
| `colonel_activity` | 24 | 0 |
| `colonel_activity_product` | 0 | 0 |
| `product_snapshot` | 70940 | 356 |

### 解释
当前数据库只有 `product_snapshot` 持有 `status=4`，其余主要商品主表/关联表不含 `4`。

## 证据二：`product_snapshot` status=4 的语义来源

### `status=4` 的 `status_text` 分布
- `未知状态`：330
- `合作前取消`：26

### raw_payload 透传核验
查询过滤口径：`status=4` 且 `raw_payload` 非空。

| 指标 | 数值 |
|---|---:|
| `total_status4` | 356 |
| `raw_null` | 0 |
| `raw_has_status` | 356 |
| `raw_status_4` | 356 |

### `raw_payload` 中的上游状态值（仅在可解析 JSON 时）
- `4`：356

### 解释
`product_snapshot.status=4` 的 356 条记录，全部能在 `raw_payload` 中看到 `"status":4`。

## 证据三：同步任务与 `status` 入参

| 指标 | 数值 |
|---|---:|
| 同步记录总数 | 146 |
| `request_params_json` 非空 | 146 |
| `request_params_json` 中存在 `status` 字段 | 0 |

### 解释
目前抓到的 `product_sync_job_log` 请求参数没有携带 `status` 字段，说明同步任务未显式下发 `status=4` 作为过滤条件。

## 证据四：status=4 与 activity 同步任务映射

按 `product_snapshot.status=4` 聚合后的 activity 与最近任务关联（`scope` 为 `ACTIVITY:<activityId>` 的任务）：

| activity_id | snapshot(status=4) | matched_job |
|---|---:|---|
| 3864871 | 150 | Y |
| 3419461 | 100 | Y |
| 3592624 | 18 | Y |
| 3916506 | 16 | Y |
| 3223881 | 15 | Y |
| 3176208 | 12 | Y |
| 3859423 | 11 | Y |
| 3859426 | 11 | Y |
| 3272470 | 7 | Y |
| 3558291 | 5 | Y |
| 3686015 | 5 | Y |
| 3676949 | 2 | Y |
| 3543332 | 2 | Y |
| 3667047 | 1 | Y |
| 3686016 | 1 | Y |

注：job 列在此报告中记录为存在性（均可匹配到 `scope`）并非完整时间序列。完整 `job_id` 列表已在日志中留存，可按需再导出到表格。

## 证据五：后端容器日志命中

在后端容器最近 24h（`docker logs --since 24h`）检索关键词：
- `status=4`
- `"status":4`
- `colonelActivityProduct`
- `activity-product-sync`
- `queryActivityProducts`

结果：**无命中记录**（日志仅见常规运行/探测心跳等内容，未见活动商品同步响应体明细）。

## 风险与说明
- 当前链路可证实：`status=4` 在数据库层面是“快照中的透传状态”，并非从 `product_sync_job_log` 入参层过滤生成。
- 未证实项：上游响应体在何处落盘/采样留档（若需 100% 锁定上游具体代码路径与时间点，可补一次“抓取并保留同步响应日志”的复测）。
- 先前已有表明：`product_snapshot` 与 `product_sync_job_log` 之间存在同步 activity 级关联，整体一致性指向“同步任务写入快照时直接保留了上游 status”。

## 结论（阶段性）
在现有证据下，`status=4` 更高概率为上游返回值透传到 `product_snapshot.raw_payload.status`，再持久化为 `product_snapshot.status=4`，不是在本地由 `status` 过滤参数显式注入或后续重写产生。
