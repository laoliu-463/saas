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
| 业绩 | `/api/performance/**`、`/api/commission/**` | 归属、提成、冲正、经营毛利、汇总 | 业绩明细、汇总 API | V1 必做 |
| 分析 | `/api/dashboard/**`、`/api/analytics/**`、`/api/reports/**` | dashboard、经营指标矩阵、只读汇总、导出 | 看板 API、导出文件 | V1 必做 |
| 运维 | `/api/operations/**`、`/actuator/**` | 操作日志、健康检查 | 健康检查、操作审计 | V1 简化 |
| 抖音授权 | `/api/douyin/auth/**`、`/api/douyin/token/**` | 授权、Token、刷新 | real-pre Token 证据 | V1 必做 |
| 抖音物流 | `/api/douyin/logistics/**` | 物流接口适配 | real-pre 响应或阻塞证据 | V1 简化 |
| 主数据 | `/api/master-data/**`、`/api/current-user/**`、`/api/colonel-partners` | 前端下拉、当前用户上下文、团长主数据 | Network 响应、单测 | V1 必做 |

## 经营指标 API 补充事实（2026-06-05）

- [V1 必做] `GET /api/dashboard/metrics` 返回 `estimate` 与 `settle` 双轨对象，数据页经营指标矩阵必须展示：
  - 总订单数：`estimate.totalOrders/todayOrderCount` 展示为“成交”，`settle.totalOrders/todayOrderCount` 展示为“结算”。
  - 订单额：`estimate.totalAmount/todayGmv` 展示为“成交”，`settle.totalAmount/todayGmv` 展示为“结算”。
  - 服务费收入：`serviceFeeIncome`，展示“预估 / 结算”；预估服务费收入 = 预估订单额 × 服务费率（未扣除技术服务费），结算服务费收入以官方结算服务费字段为准，不用订单额重算且不扣技术服务费。
  - 技术服务费：`techServiceFee`，展示“预估 / 结算”。
  - 服务费支出：优先读取 `serviceFeeExpense`，缺失时按 `bizCommission + channelCommission` 或 `commission` 派生，展示“预估 / 结算”。
  - 服务费收益：`serviceFee` / `serviceFeeProfit`，展示“预估 / 结算”；业务公式必须区分两轨：预估服务费收益 = 预估服务费收入 - 预估服务费支出 - 技术服务费；结算服务费收益 = 结算服务费收入 - 结算服务费支出。
  - 招商提成：`bizCommission`，展示“预估 / 结算”。
  - 媒介提成：当前代码字段为 `channelCommission`，页面可按业务文案展示为“媒介提成”，展示“预估 / 结算”。
  - 毛利：`grossProfit`，公式为 `服务费收益 - 招商提成 - 媒介/渠道提成`，展示“预估 / 结算”。
- [V1 必做] `techServiceFee` 仍作为经营指标展示和预估服务费收益输入；结算轨只展示技术服务费，结算服务费收入和收益公式均不扣减技术服务费，后端 / 前端不得用同一“收入 - 技术服务费”公式套两轨。
- [V1 必做] 以上字段属于经营指标，不代表财务结算、商家结算或多账期治理；后者仍为 V2 预留。

## 活动 API 补充事实（2026-05-29 / 2026-06-01）

- [V1 必做] `PUT /api/colonel/activities/{activityId}/assignee`：仅 `admin` 可将活动分配给招商用户（`biz_leader` / `biz_staff`），持久化 `colonel_activity.recruiter_*` 并级联 `product_operation_state.assignee_id`。
- [V1 必做] `GET /api/colonel/activities` 列表行兼容输出 `activityAssigneeId`、`activityAssigneeName`（及 `assigneeId`、`assigneeName`）。
- [V1 必做] `GET /api/colonel/activities`：`assignmentFilter` 为 `assigned/unassigned/mine` 时走本地 `colonel_activity` 分页；非 admin 招商角色强制 `mine`（`self` 数据范围）。
- [V1 必做] `GET /api/colonel/activities/{activityId}/products`：非 admin 仅可访问 `recruiter_user_id = 当前用户` 的活动。
- [V1 必做] 活动分配成功后驱逐 `activities:list:` 短缓存，避免前端刷新活动行时读取旧分配人。
- [V1 必做] 活动状态只描述活动自身，用于活动列表展示、活动筛选和活动数据范围判断；不得驱动商品入库、审核状态或展示状态。
- [V1 必做] `POST /api/colonel/activities/{activityId}/products/sync` 用于前端手动触发活动商品后台同步；接口只提交后台任务并立即返回 `syncStatus=ACCEPTED/RUNNING`，前端提示“后台同步中”，不得阻塞列表查询等待上游完成。
- [V1 必做] `GET /api/colonel/activities/{activityId}/products` 活动商品列表行需透出 `relationId`（`product_snapshot.id`，UUID）以及规范状态字段：`officialStatus`、`reviewStatus`、`publishStatus`、`manualDisabled`、`selectedToLibrary`、`displayStatus`、`hiddenReason`。前端审核、暂停/恢复等商品关系写操作必须使用 `relationId`，不能用平台 `productId` 代替；前端筛选和操作按钮优先使用上游数字状态与这些规范字段，不依赖 `statusText` 文案漂移。
- [V1 必做] `GET /api/colonel/activities/{activityId}/products?refresh=true` 继续兼容返回 `syncStats`；`libraryEntryCount` 表示本次因上游商品自身 `status=1/推广中` 新进入商品库的数量，`autoLibraryEligible` 表示本次存在自动入库商品。商品是否入库和活动商品页是否可操作以商品自身上游状态为主：`status=1` 自动补齐 `selectedToLibrary=true`、`auditStatus=2` 和可操作业务状态，历史本地拒绝不得阻断；本地暂停仅表示发布控制，不使活动商品行退化为只读。
- 字段级契约见 [接口/活动分配与推广入库API契约.md](接口/活动分配与推广入库API契约.md)。

## 商品 API 补充事实

- [V1 必做] `GET /api/products` 商品库分页返回的 `records[]` 需带出商品卡片展示输入：`shopName`、`detailUrl`、`promotionStartTime`、`promotionEndTime`。这些字段来源于 `product_snapshot`，用于前端商品库卡片展示店铺、商品链接和推广时间范围。
- [V1 必做] `GET /api/products` 商品库分页支持 `productId` 查询参数，按商品外部 ID 精确匹配；该参数独立于 `keyword`，`keyword` 仍用于商品名称 / 商品 ID / 店铺关键字模糊匹配。前端商品库搜索框输入商品 ID 时走 `keyword` 模糊查询；内部单行刷新等精确定位场景才使用 `productId`。
- [V1 必做] `POST /api/products/manage/{relationId}/approve` 为旧商品管理审核通过兼容入口，仍必须遵守活动商品审核补充字段契约：审核通过前请求体需携带 `exclusivePriceRemark`、`shippingInfo`、`sellingPoints`、`promotionScript`、`supportsAds`、`rewardRemark`、`participationRequirements`、`campaignTimeRemark`、`materialFiles`；后端透传到商品域审核服务写入 `audit_payload`，缺失时返回业务错误 `code=461` 和“审核通过前请补充：...”提示。拒绝入口 `POST /api/products/manage/{relationId}/reject` 只要求拒绝原因。
- [V1 必做] `POST /api/products/{relationId}/pause`：招商角色可暂停当前商品关系发布，后端写入 `product_operation_state.manual_disabled=true`、`display_status=HIDDEN`、`hidden_reason=LOCAL_PAUSED`，保留 `selected_to_library` 入库事实；`GET /api/products` 商品库分页必须不再返回该关系。
- [V1 必做] `POST /api/products/{relationId}/resume`：招商角色可清除当前商品关系暂停标记，写入 `manual_disabled=false`、`display_status=PENDING` 并触发商品展示规则重新计算；是否重新出现在商品库由上游推广状态、推广期和去重规则共同决定。
- [V1 必做] `POST /api/colonel/activities/{activityId}/products/repair-library-state`：仅管理员可执行活动商品库展示状态修复；默认 `dryRun=true`，返回历史推广中但未入库/未展示的差异。repair 与活动商品同步使用同一套规则：`status=1` 自动补齐入库、审核通过和可操作状态；非推广中不自动入库；本地暂停保持 `publishStatus=PAUSED`、商品库隐藏但活动商品页可恢复。`dryRun=false` 只写入历史入库状态，不强制所有商品进入 `DISPLAYING`；活动商品刷新链路可在 repair 后按展示规则单独重算。real-pre 写入前必须先保存 dryRun 结果并人工确认窗口。
- [V1 必做] `GET /api/colonel/products/library/health`：仅管理员可读取商品库状态巡检指标，包括 `status=1` 未入库、未展示、展示但有隐藏原因、已入库但非推广中、本地拒绝/暂停和最近同步时间。

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

## 主数据与当前用户 API 补充事实（2026-06-02 / t0-master-data）

[V1 必做] `GET /users/master-data/channels`：返回 `channel_leader` 与 `channel_staff` 角色下的用户选项，关键词命中 username / realName 模糊匹配，按姓名/用户名升序裁剪到 limit（默认 50，最大 100），由 `UserMasterDataController.channels` 委托 `UserMasterDataService.listChannels` → `UserMasterDataService.listByRoleCodes(CHANNEL_ROLE_CODES, ...)` 实现，鉴权开放给已登录用户。

[V1 必做] `GET /users/master-data/recruiters`：返回 `biz_leader` 与 `biz_staff` 角色下的用户选项，行为同 channels，由 `UserMasterDataController.recruiters` 委托 `UserMasterDataService.listRecruiters` 实现。

[V1 必做] `GET /users/master-data/group-members`：返回当前用户部门下的活跃成员；仅 `admin` 可传入 `deptId` 查看指定部门，其他角色传 `deptId` 也会回退到当前部门（防越权），由 `UserMasterDataController.groupMembers` 委托 `UserMasterDataService.listGroupMembers` 实现，Controller 用 `@RequireRoles({ADMIN, BIZ_LEADER, CHANNEL_LEADER})` 限制访问。

[V1 必做] `GET /users/current`：返回 `CurrentUserResponse{userId, username, realName, deptId, dataScope, dataScopeName, roleCodes, permissions, status, forcePasswordChange}`；`dataScope` 数字编码与 `dataScopeName`（`self`/`group`/`all`）必须一致，由 `UserDomainService.getCurrentUser` 计算并返回。

[V1 必做] `GET /users/current/data-scope`：返回 `UserDataScopeResponse{scope, code, userIds}`；`scope` 文本（`self`/`group`/`all`）与 `code` 数字编码必须一致，`userIds` 仅在 `self`/`group` 时返回非空集合；`all` 范围时 `userIds` 为空。

[V1 必做] `PUT /users/current/password`、`POST /users/current/permissions/check`：当前用户自服务；不暴露他人数据。

[V1 必做] `GET /api/colonel-partners`：团长主数据分页查询，支持 `keyword`（团长名称 like）、`source`（来源 eq）、`hasContact`（联系方式可用性）、`page`、`size`，按 `last_sync_at DESC, colonel_name ASC` 排序；Controller 与 service 拆分：`ColonelPartnerMasterDataController.list` 委托 `ColonelPartnerMasterDataService.list` 实现，访问限定 `BIZ_LEADER` / `BIZ_STAFF` / `ADMIN`。
- ⚠ **当前未在前端接入**：前端 `frontend/src/api/product.ts:listPartners` 调用的是 `/colonel/partners`（由 `ColonelPartnerController` 暴露，返回 `PartnerVO` 视图），而不是本主数据接口（返回 `ColonelPartner` 实体）。本主数据接口的 consumer 暂缺，需要决定：(a) 在 V1 后续阶段接入团长下拉（招商人员维护/查看团长列表）；(b) 标记为 V2 预留。

[V1 必做] `GET /api/colonel-partners/{id}`：按 ID 查团长主数据详情；记录不存在时抛 `BusinessException.notFound`（code=404）。

[V1 必做] `GET /api/colonel-partners/sources`：列出已存在的团长来源（如 `BUYIN` / `MANUAL`），供下拉候选使用；由 `ColonelPartnerMasterDataService.listSources` 实现，distinct + 字典序排序。

[V1 必做] **主数据接口不得直连第三方**：所有主数据均来自本地 PostgreSQL 表（`sys_user`/`sys_role`/`sys_user_role`/`colonel_partner`），不通过网关调用抖音/抖店开放接口。`DouyinOAuthController`（`/douyin/oauth/authorize-url` 与 `/douyin/oauth/callback`）仅用于后台 OAuth 授权流程，不参与下拉候选。

[V1 必做] **缓存策略**：当前 `/users/master-data/**` 与 `/users/current/**` **未引入 Redis 缓存**（避免脏读，用户/角色变更时缓存失效复杂），单次响应在 50 ~ 100 条限制下数据库直查耗时可接受；如果未来出现 N+1 或并发压力，应优先在 service 层引入短 TTL 缓存并配合 `user/role update` 事件主动失效，**不**直接堆 Redis 注解。

## 佣金规则与规则中心 API 补充事实（2026-06-02 / t6-commission）

[V1 必做] `GET /commission-rules`（`CommissionRuleController.page`）：分页查询提成规则（V2 差异化），筛选参数彼此通过 AND 组合：
- `dimensionType`（可选，eq）：`global` / `activity` / `product` / `user`，非法值由 service 抛 `BusinessException.param`（code=460）；
- `commissionType`（可选，eq）：`recruiter` / `channel`，非法值由 service 抛 `BusinessException.param`；
- `status`（可选，eq）：`1` 启用 / `0` 禁用；非法值（既非 0 也非 1）按"不筛选"处理，**不**抛错（避免静默回错数据）；
- `effectiveStart`（可选，ISO 8601）：查询生效区间起点，与规则有效期做"区间重叠"判定（`rule.effective_end IS NULL OR rule.effective_end >= effectiveStart`）；
- `effectiveEnd`（可选，ISO 8601）：查询生效区间终点（`rule.effective_start IS NULL OR rule.effective_start <= effectiveEnd`）；
- 当 `effectiveStart > effectiveEnd` 时抛 `BusinessException.param("查询生效区间终点不能早于起点")`；
- `page` / `size` 默认 `1` / `20`，按 `update_time DESC, create_time DESC` 排序。
- 委派 `CommissionRuleService.findPage` 实现。

[V1 必做] `GET /rule-center/schema` / `GET /rule-center` / `POST /rule-center/validate` / `PUT /rule-center/groups/{groupCode}` / `PUT /rule-center/batch` / `GET /rule-center/change-logs` / `GET /rule-center/events`：规则中心 7 个端点，统一 `@RequireRoles({RoleCodes.ADMIN})`，全部委派 `RuleCenterService` 实现。**`dataScope` 在规则中心不参与**——理由：① 配置域是平台级事实（"配置域只提供配置事实，不执行具体业务规则"），不存在按部门/按用户的过滤语义；② 唯一访问角色 `admin` 的 `dataScope=3 (ALL)` 已经隐式覆盖"全部配置"；③ 变更日志和事件消费方都返回全量，便于事后审计与回溯。Controller 不消费 `@RequestAttribute("dataScope")`，不需要在 service 注入 `dataScope` 形参。
- 前端 `rule-center/index.vue` 抽屉变更历史按 `key` 过滤（每次只查一个 key），不引入额外全量模式；`loadEventStatus(eventId)` 在抽屉展开时按需触发。
- 配置项 `enabled=false`（V2 预留）在前端统一 `:disabled="!item.enabled"`，后端 validate 拒绝 `enabled=false` 项的修改（"该规则项当前未启用，不能修改"）。

[V1 必做] **T6 端到端测试入口**：`cd backend; mvn "-Dtest=CommissionRuleServiceTest,CommissionRuleControllerTest,RuleCenterServiceTest,RuleCenterControllerTest" test` 共 4 个测试类。
