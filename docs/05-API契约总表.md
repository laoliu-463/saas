# API 契约总表

> 范围标记：`V1 必做`、`V1 简化`、`V1 不做`、`V2 预留`、`历史归档`。

## 口径说明

- [V1 必做] 前端只调用内部 API，不直接调用抖音 / 抖店开放接口。
- [V1 必做] 后端通过 Gateway / SDK 封装第三方接口、鉴权、参数转换、错误码适配和响应标准化。
- [V1 必做] 当前 OpenAPI 缓存来自“抖音团长 SaaS API”V2.2.0，下载时间为 2026-05-18；2026-05-22 之后补齐接口需以代码和验收证据复核。
- [V1 简化] 本表作为总入口，字段级详情以代码、OpenAPI spec 和专项对接文档为准。

## 内部 API 总表

| 领域 | API 分组 / 典型路径 | 用途 | 验收证据 | 范围 |
| --- | --- | --- | --- | --- |
| 认证 | `/api/auth/login`、`/api/auth/me`、`/api/auth/logout` | 登录、当前用户、退出 | E2E 登录、Network 响应 | V1 必做 |
| 用户 | `/api/users/**`、`/api/roles/**`、`/api/menus/**` | 用户、角色、菜单、数据范围 | 权限测试、API 响应 | V1 必做 |
| 配置 | `/api/configs/**`、`/api/commission-rules/**`、`/api/rule-center/**` | 配置、佣金规则、规则中心 | 配置变更日志、规则 API | V1 必做 |
| 商品 | `/api/products/**`、`/api/colonel/products/**` | 商品库、活动商品、筛选、转链 | 商品表、转链记录 | V1 必做 |
| 活动 | `/api/activities/**`、`/api/colonel/activities/**` | 活动商品同步、活动查询、活动招商组长分配 | 同步日志、API 响应、`ColonelActivityControllerTest` | V1 必做 |
| 达人 | `/api/talents/**`、`/api/colonel/talents/**` | 达人资料、标签、地址、跟进 | 达人表、操作日志 | V1 必做 |
| 寄样 | `/api/samples/**`、`/api/sample-applications/**` | 寄样申请、审批、发货、状态 | 寄样状态日志、E2E | V1 必做 |
| 订单 | `/api/orders/**`、`/api/order-sync/**` | 订单同步、订单事实、退款事实 | 订单表、同步日志 | V1 必做 |
| 业绩 | `/api/performance/**`、`/api/commission/**` | 归属、提成、冲正、汇总 | 业绩明细、汇总 API | V1 必做 |
| 分析 | `/api/dashboard/**`、`/api/analytics/**`、`/api/reports/**` | dashboard、只读汇总、导出 | 看板 API、导出文件 | V1 必做 |
| 运维 | `/api/operations/**`、`/actuator/**` | 操作日志、健康检查 | 健康检查、操作审计 | V1 简化 |
| 抖音授权 | `/api/douyin/auth/**`、`/api/douyin/token/**` | 授权、Token、刷新 | real-pre Token 证据 | V1 必做 |
| 抖音物流 | `/api/douyin/logistics/**` | 物流接口适配 | real-pre 响应或阻塞证据 | V1 简化 |
| 主数据 | `/api/master-data/**`、`/api/current-user/**` | 前端下拉、当前用户上下文 | Network 响应 | V1 必做 |

## 活动 API 补充事实（2026-05-29）

- [V1 必做] `PUT /api/colonel/activities/{activityId}/assignee`：仅 `admin` 可将活动分配给招商用户（`biz_leader` / `biz_staff`），持久化 `colonel_activity.recruiter_*` 并级联 `product_operation_state.assignee_id`。
- [V1 必做] `GET /api/colonel/activities` 列表行兼容输出 `activityAssigneeId`、`activityAssigneeName`（及 `assigneeId`、`assigneeName`）。
- [V1 必做] `GET /api/colonel/activities`：`assignmentFilter` 为 `assigned/unassigned/mine` 时走本地 `colonel_activity` 分页；非 admin 招商角色强制 `mine`（`self` 数据范围）。
- [V1 必做] `GET /api/colonel/activities/{activityId}/products`：非 admin 仅可访问 `recruiter_user_id = 当前用户` 的活动。
- [V1 必做] 活动分配成功后驱逐 `activities:list:` 短缓存，避免前端刷新活动行时读取旧分配人。
- [V1 必做] 活动**已分配招商**且为抖店「推广中」（`activityStatus/status == 5` 或状态文案含「推广中」）时，活动下**已同步**商品自动全部入库并 `DISPLAYING`，且不参与同 `product_id` 去重隐藏；分配、活动落库、商品同步均会触发批量补齐。
- 字段级契约见 [接口/活动分配与推广入库API契约.md](接口/活动分配与推广入库API契约.md)。

## 商品 API 补充事实

- [V1 必做] `GET /api/products` 商品库分页返回的 `records[]` 需带出商品卡片展示输入：`shopName`、`detailUrl`、`promotionStartTime`、`promotionEndTime`。这些字段来源于 `product_snapshot`，用于前端商品库卡片展示店铺、商品链接和推广时间范围。

## 第三方 API 入口

| 对接项 | 内部文档 | 关键接口事实 | 范围 |
| --- | --- | --- | --- |
| 抖音授权与 Token | [对接/抖音授权与Token.md](对接/抖音授权与Token.md) | 授权码、Token 获取、刷新、过期处理 | V1 必做 |
| 活动商品同步 | [对接/活动商品同步.md](对接/活动商品同步.md) | 活动商品、商品库同步 | V1 必做 |
| 转链与归因 | [对接/转链与pick_source归因.md](对接/转链与pick_source归因.md) | `buyin.instPickSourceConvert`、`pick_source_mapping` | V1 必做 |
| 订单同步 | [对接/订单同步.md](对接/订单同步.md) | 抖店订单、退款、归因输入 | V1 必做 |
| 物流接口 | [对接/物流接口.md](对接/物流接口.md) | 发货、物流查询、阻塞证据 | V1 简化 |
| 达人信息获取 | [对接/达人信息获取.md](对接/达人信息获取.md) | 达人资料、权限包限制 | V1 简化 |

## 明确不做

- [V1 不做] 不让前端直接调用第三方开放接口。
- [V1 不做] 不把旧 OpenAPI 缓存当作 2026-05-22 之后所有接口的最终事实。
- [V1 不做] 不在 API 总表中虚构字段级契约；字段级契约需由代码、OpenAPI 或真实响应证明。
