# 抖音团长 SaaS Context

This glossary standardizes the current business and environment language used in this repo. `AGENTS.md` and `docs/*.md` remain the source of truth for requirements, milestones, and execution rules. Historical V2.2 terms are reference only and must not override the current SaaS scope.

## Language

### 业务闭环

**团长活动**:
精选联盟中的招商活动，是活动商品进入系统运营流转的上游容器。
_Avoid_: 活动池, campaign

**活动商品**:
仍处于审核、分配或推进阶段的候选商品，尚未沉淀为共享商品资产。
_Avoid_: 商品库商品, 共享商品

**共享商品库商品**:
已审核入库、可被团队共享查看并继续推进的商品资产。
_Avoid_: 活动商品, 候选商品

**推广链接**:
面向渠道或达人的可分发商品链接，是后续订单归因的入口线索。
_Avoid_: 口令, 外链

**渠道编码**:
系统为渠道用户生成的短码，用于推广链接和归因映射中识别渠道来源。
_Avoid_: 用户名, 渠道名称

**归因映射**:
把 `pick_source` 还原到活动、商品、负责人等业务上下文的映射记录。
_Avoid_: 临时归属, 转链快照

**联盟订单**:
从抖音联盟回流到系统、等待归因或已完成归因的订单记录。
_Avoid_: 店铺订单, 本地订单

**订单归因**:
根据推广链接与归因映射，将联盟订单判定为已归因或未归因并给出原因的过程。
_Avoid_: 对账, 结算

**寄样单**:
围绕某个达人与某个商品的一次履约申请，按审核、发货、签收、待交作业到完成流转。
_Avoid_: 样品表单, 快递单

**寄样动作权限**:
寄样域中用于判断当前用户能否申请、审核、删除、推进物流、导出或享有重复申请豁免的动作约束。
_Avoid_: 用户域角色解析, 前端按钮权限, 数据范围

### 订单金额事实

> 范围：仅指**联盟订单**同步链路中事实落库的金额字段，单位为**人民币分**（long，整数）。与 ADR-009（订单金额双轨结算口径冻结）保持一致：预估轨和结算轨是两套独立事实字段，禁止 estimate → effective 兜底。

**payAmount（实付金额）**:
联盟订单从上游回流的实付金额，是预估轨和结算轨的"基础事实"。
_Avoid_: 实付分, pay_goods_amount（任一 alias 不是规范名）

**settleAmount（已结金额）**:
已结订单的最终金额（结算轨）。待结算单保持 0/null。SETTLEMENT_STRICT 模式下不向 payAmount 兜底。
_Avoid_: settle_amount（应使用 settleAmount）

**estimateServiceFee（预估服务费）**:
预估轨服务费，来自上游"预估服务费"字段或按 serviceFeeRate × payAmount 推导。
_Avoid_: 佣金（commission 是另一回事）

**effectiveServiceFee（已结服务费）**:
结算轨服务费。**只能来自上游"已结服务费"字段**，不得用 estimateServiceFee、payAmount 或预估技术服务费兜底（ADR-009）。
_Avoid_: settle_colonel_commission 单独使用（见"历史兼容字段"）

**estimateTechServiceFee（预估技术服务费）** / **effectiveTechServiceFee（已结技术服务费）**:
同 estimateServiceFee / effectiveServiceFee 规则，分别属于预估轨 / 结算轨。
_Avoid_: tech_service_fee 单独使用（在 INSTITUTE 轨是模糊字段，需 alias 解析后落到 estimate 或 effective）

**serviceFeeRate（服务费率）** / **commissionRate（招商提成率）**:
费率字段（小数，如 0.05 = 5%）。**raw payload 未提供时保持 null，不得伪造或用其他字段推导**。

### 解析轨道

**INSTITUTE 轨**:
学院/招商来源的订单同步。允许 estimate 字段互相 fallback；**禁止 estimate → effective 兜底**（ADR-009）。

**SETTLEMENT_STRICT 轨**:
已结订单的严格结算轨。**禁止所有 estimate → effective 兜底**；raw settleAmount 缺失时保持 0；raw effectiveTechServiceFee 缺失时回退到 ambiguousTechRaw（INSTITUTE 轨之外的**唯一**允许的回退）。
_Avoid_: 结算兜底（"兜底"是 estimate→effective 的委婉说法，禁止）

### 别名解析

每个 OutputField 在 raw payload 中可能有多个上游字段名。**`payAmount` 一个字段就有 8 个 alias**（pay_goods_amount, payGoodsAmount, order_amount, orderAmount, total_pay_amount, totalPayAmount, pay_amount, payAmount）—— alias 字典是 `OrderPayAmountAliasPolicy` 的输入，不在 Policy 内重复硬编码。
_Avoid_: 硬编码 alias（任何新增 alias 必须在 AliasPolicy 注册，不允许在 Writer 里临时 add 一个）

### 兜底规则

5 个允许的兜底链（按 `OrderAmountFallbackPolicy` 强制执行）：

1. effectiveTechServiceFee 缺失时回退到 ambiguousTechRaw（仅 SETTLEMENT_STRICT 轨）
2. estimateTechServiceFee 缺失时回退到 ambiguousTechRaw（仅 INSTITUTE 轨）
3. estimateTechServiceFee ≤ 0 时回退到 effectiveTechServiceFee（任何轨）
4. estimateServiceFee ≤ 0 且 effectiveServiceFee > 0 时回退到 effectiveServiceFee
5. estimateServiceFee ≤ 0 且已有订单 estimateServiceFee > 0 时回退到已有值（**仅当 SyncTrack=INSTITUTE**）

**禁止的兜底**（ADR-009 复述）：estimate → effective 兜底（除第 1 条之外）、payAmount → settleAmount 兜底（SETTLEMENT_STRICT 模式下）、用 serviceFeeRate × payAmount 推 effectiveServiceFee。

### 历史兼容字段

**settleColonelCommission** / **settleColonelTechServiceFee**:
旧 6468 系统的命名。**仅用于兼容旧链路数据**，不得用预估轨冒充结算事实（ADR-009 line 31）。
_Avoid_: 在新代码中作为主字段名（保留为 alias，但 Writer 应当写到 effectiveServiceFee / effectiveTechServiceFee）

**达人**:
可被认领、跟进、寄样并产生产出结果的合作对象。
_Avoid_: 用户, 客户

**公海**:
当前未归属于当前用户，或允许再次认领的达人池视图。
_Avoid_: 未分配列表

**私海**:
当前用户或其团队已认领并持续经营的达人池视图。
_Avoid_: 个人收藏

### 用户与权限

**组织单元**:
用户域内承载部门、招商组、渠道组或运营组的组织节点，是数据范围和组织管理的基础事实。
_Avoid_: sys_dept, 部门表, 业务组表

**数据范围**:
用户域输出的 `self/group/all` 可见性范围，用于业务查询侧过滤当前用户可访问的数据。
_Avoid_: 权限规则, 前端菜单权限, 业务归属

**当前用户**:
已通过认证并触发本次操作的系统用户身份，是审计、权限和数据范围解析的输入。
_Avoid_: 达人, 客户, 操作人文本

**角色编码**:
系统用于鉴权和数据范围解析的稳定角色标识，如 `admin`、`biz_leader`、`channel_staff`。
_Avoid_: 角色名称, 菜单名称

**角色编码集合**:
当前用户拥有的一组稳定角色标识，是业务域判断入口权限时消费的用户域事实集合。
_Avoid_: 逗号字符串, 前端角色文本, 本地角色解析规则

**用户展示名称**:
业务记录中用于展示系统用户姓名的文本，真实姓名优先，用户名仅作兜底。
_Avoid_: UserOptionResponse, 用户 DTO, 用户显示标签

**负责人归属组织单元**:
业务对象归属给某个系统用户时随负责人记录的主组织单元，用于团队范围查询和后续归属过滤。
_Avoid_: deptId 字段, 用户 DTO, 部门快照

### 环境与契约

**test 环境**:
用于 Mock 闭环、权限测试和自动化验证的默认环境。
_Avoid_: local-mock, 真实联调环境

**real-pre 环境**:
用于真实 SDK、真实上游联调和当前生产形态部署的单活环境。
_Avoid_: 独立 prod 环境, Mock 环境

**Gateway 契约**:
Service 依赖的统一第三方接口抽象，切换 Test 与 Real 时只替换实现，不改 Controller、前端和主业务 Service。
_Avoid_: SDK 直连, 页面直连第三方

## Relationships

- A **团长活动** contains many **活动商品**
- An **活动商品** can become one **共享商品库商品**
- A **共享商品库商品** can generate one or more **推广链接**
- A **推广链接** can include a **渠道编码** and produces one or more **归因映射**
- An **联盟订单** is interpreted through **归因映射** during **订单归因**
- A **寄样单** links one **达人** and one **共享商品库商品**
- A **寄样动作权限** consumes a **角色编码集合** to decide which actions may be attempted on a **寄样单**
- A **达人** can appear in **公海** or **私海**
- A **当前用户** belongs to zero or one primary **组织单元**
- A **用户展示名称** is derived from one **当前用户** or managed user identity without exposing the full user profile
- A **负责人归属组织单元** is derived from the assigned system user and does not decide whether that user is a valid assignee
- A **角色编码** helps resolve one **当前用户** to one **数据范围**
- A **角色编码集合** belongs to one **当前用户** and should be interpreted by the user-domain permission policy
- A **数据范围** constrains business-domain queries but does not decide business ownership
- The **test 环境** and **real-pre 环境** share one **Gateway 契约**

## Example dialogue

> **开发**: 这个商品现在已经能给渠道发链接了，它还是**活动商品**吗？
> **业务**: 不是。只要审核通过并进入共享商品库，它就是**共享商品库商品**；后面的转链、寄样和订单归因都基于这个状态继续推进。

## Flagged ambiguities

- “商品”曾同时指**活动商品**和**共享商品库商品** — resolved: 未入库叫**活动商品**，已入库叫**共享商品库商品**。
- “Mock”曾同时指历史 `local-mock` 口径和当前默认测试基线 — resolved: 当前默认 Mock 基线叫 **test 环境**，`local-mock` 仅作历史资料，不作为运行入口。
- “real”与“real-pre”容易混用 — resolved: 当前真实上游与生产形态入口统一叫 **real-pre 环境**，不再保留独立 `real` / `prod` profile。
- “组长”可能同时指招商组长与渠道组长 — resolved: 讨论权限、分配或菜单时必须明确是 `biz_leader` 还是 `channel_leader`。
- “用户展示名称”和“用户显示标签”用途不同 — resolved: 业务记录用**用户展示名称**，下拉或筛选项用**用户显示标签**。
- “寄样权限”容易混用数据范围、前端按钮和后端动作校验 — resolved: 申请、审核、删除、物流、导出和重复申请豁免统一称为**寄样动作权限**；查询可见性仍称为**数据范围**。
