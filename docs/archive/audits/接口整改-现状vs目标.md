# 接口整改：现状 vs 目标

> 文档状态：执行前盘点  
> 创建时间：2026-04-28  
> 维护说明：本文档只记录当前代码、前端调用与主文档可验证事实，不补写未经确认的业务语义。

## 1. 盘点范围

本次盘点覆盖以下信息源：

- 后端 `backend/src/main/java/**` 下全部 Controller
- 前端 `frontend/src/api/*.ts`
- 现有主文档 `docs/05-接口与数据模型.md`
- 接口整改任务单 `docs/archive/audits/13-接口导入APIFOX整改任务单.md`

## 2. 当前可验证事实

### 2.1 当前 Controller 分组现状

| 模块 | 路径前缀 | 当前 Tag / 现状 | 备注 |
|---|---|---|---|
| AuthController | `/auth` | `认证中心` | 已有中文 Tag、summary、description |
| OrderController | `/orders` | `订单管理` | 存在 `page/pageSize` |
| OrderAttributionController | `/orders/*`、`/dashboard/*` | `订单回流与归因` | 与订单主分组、看板接口并存 |
| DashboardController | `/dashboard` | `数据看板` | 分组独立 |
| DataController | `/data`、`/dashboard`、`/orders/*` | `数据平台` | 与订单、看板存在视角重叠 |
| ColonelActivityController | `/colonel/activities` | `团长活动管理` | 参数较多，部分参数说明仍不完整 |
| ColonelActivityProductController | `/colonel/activities/{activityId}/products` | `活动商品主链路` | 主链路接口已独立 |
| SampleController | `/samples` | `寄样管理` | 使用 `page/size` |
| TalentController | `/talents` | `达人CRM` | 使用 `page/size`，路径含 `claims` / `release` |
| SysUserController | `/users` | `系统用户` | 使用分页查询 |
| SysRoleController | `/roles` | `系统角色` | 使用分页查询 |
| ProductController | `/products` | `商品管理（兼容接口，已废弃）` | 已 `@Deprecated`，需继续降噪 |
| TestController | `/test` | 无独立 Tag | 当前需补 `测试工具` 分组 |
| DouyinController | `/douyin` | Tag 使用不统一 | 需统一到 `抖音联调` |
| DouyinWebhookController | `/douyin/webhooks` | 无明确联调分组 | 需纳入联调分组策略 |

### 2.2 当前前端 API 文件现状

| 前端文件 | 对应业务域 | 主要路径前缀 |
|---|---|---|
| `frontend/src/api/auth.ts` | 认证 | `/auth` |
| `frontend/src/api/order.ts` | 订单 | `/orders` |
| `frontend/src/api/dashboard.ts` | 看板 | `/dashboard` |
| `frontend/src/api/data.ts` | 数据页 | `/data`、`/dashboard`、`/orders/*` |
| `frontend/src/api/activity.ts` | 团长活动 | `/activities`、`/colonel/activities` |
| `frontend/src/api/activityProduct.ts` | 商品主链路 | `/colonel/activities/*/products` |
| `frontend/src/api/product.ts` | 旧商品接口 | `/products` |
| `frontend/src/api/promotionLink.ts` | 转链相关 | 商品主链路相关 |
| `frontend/src/api/sample.ts` | 寄样 | `/samples` |
| `frontend/src/api/talent.ts` | 达人 | `/talents` |
| `frontend/src/api/sys.ts` | 系统管理 | `/users`、`/roles` |
| `frontend/src/api/douyin.ts` | 联调 | `/douyin` |
| `frontend/src/api/test.ts` | 测试工具 | `/test` |
| `frontend/src/api/operationLog.ts` | 操作日志 | 商品主链路相关 |

### 2.3 当前文档现状

| 文档 | 当前问题 |
|---|---|
| `docs/05-接口与数据模型.md` | 与代码不完全一致，存在路径、分组、接口覆盖范围遗漏 |
| `docs/archive/audits/12-文档编码乱码问题分析报告.md` | 已明确 UTF-8 约束，但终端环境仍可能出现显示乱码 |
| `docs/archive/audits/13-接口导入APIFOX整改任务单.md` | 已形成执行口径，可作为本次整改主任务单 |

## 3. 当前差异清单

### 3.1 直接可执行项

| 项目 | 当前现状 | 目标状态 | 处理方式 |
|---|---|---|---|
| `/test` 分组 | 无独立 Tag | 独立为 `测试工具` | 直接改 Controller 类级 Tag + summary 前缀 |
| `/douyin` 分组 | Tag 不统一 | 独立为 `抖音联调` | 直接改 Controller 类级 Tag + summary 前缀 |
| 废弃接口标识 | 已有 `@Deprecated` 但口径不完全统一 | `summary` 全部加 `[已废弃]` | 直接执行 |
| summary 中文化 | 部分接口不完整或风格不统一 | 全部中文、可直接识别业务意图 | 直接执行 |
| description 完整化 | 多处缺失或偏弱 | 写清业务场景、前置条件、注意事项 | 直接执行 |
| 参数中文说明 | 部分缺失 | 为公开参数补描述 | 直接执行 |
| 请求示例 | 不完整 | 补典型 JSON 示例 | 直接执行 |
| `docs/05` 回写 | 内容过时 | 与最终 OpenAPI 保持一致 | 直接执行 |

### 3.2 待决策项

| 项目 | 当前现状 | 待确认问题 | 决策前限制 |
|---|---|---|---|
| 分页参数命名 | 混用 `page/pageSize` 与 `page/size` | 仅改业务 API，还是扩大到联调接口 | 未确认前不批量改参数名 |
| 达人路径单复数 | `/{id}/claims` 与 `/{id}/release` 并存 | `release` 是否动作语义、是否改名 | 未确认前不改路径 |
| 业务分组合并 | `订单管理`、`订单回流与归因`、`数据平台`、`数据看板` 并存 | 是否合并为更少的统一分组 | 未确认前保持现有业务 Tag |
| `/douyin/webhooks` 暴露范围 | 当前默认会进入 OpenAPI | 是公开展示还是隐藏 | 未确认前先纳入联调分组说明 |
| 枚举值说明 | 部分参数无代码常量映射 | 是否已有产品侧统一定义 | 无代码依据时只能标注待确认 |

## 4. 推荐整改顺序

1. 先完成 `/test`、`/douyin`、废弃接口的分组和前缀统一
2. 再补齐 summary、description、参数说明、请求示例
3. 导出一次 OpenAPI，先看 APIFOX 导入效果
4. 再决定是否推进分页参数统一
5. 最后重写 `docs/05-接口与数据模型.md`

## 5. 本文档后续更新规则

- 若发现新的 Controller、前端 API 文件或未覆盖路径，先补到“当前可验证事实”
- 若团队确认了分页、达人路径、webhook 暴露策略，先更新“待决策项”，再落代码
- 若 `docs/05` 完成重写，本文档只保留盘点与决策追踪用途，不作为最终接口说明书
