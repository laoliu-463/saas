# Ubiquitous Language

## 用户与权限

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **用户域** | 负责系统用户、角色、组织单元和数据范围的业务边界。 | 权限模块, sys_user 模块 |
| **当前用户** | 已认证并触发本次系统操作的用户身份。 | 达人, 客户, 操作人文本 |
| **受管用户** | 被管理端查看、更新、禁用、删除或重置密码的系统用户身份。 | SysUser, 用户表记录, 被操作人 |
| **操作人账号** | 审计日志中用于标识操作者登录身份的账号文本。 | UserOptionResponse, 用户 DTO, 操作人对象, 真实姓名 |
| **负责人账号** | 业务记录展示中用于标识负责人的登录账号文本。 | UserOptionResponse, 用户 DTO, 负责人对象, 真实姓名 |
| **用户展示名称** | 业务记录中用于展示系统用户姓名的文本，真实姓名优先，用户名兜底。 | UserOptionResponse, 用户 DTO, 用户显示标签, 完整用户资料 |
| **用户显示标签** | 下拉或筛选项中用于展示系统用户的简短文本。 | UserOptionResponse, 用户 DTO, 用户对象, 展示用户资料 |
| **负责人归属组织单元** | 业务对象归属给某个系统用户时随负责人记录的主组织单元。 | deptId, 用户 DTO, 部门快照 |
| **渠道编码** | 系统为渠道用户生成并用于推广链接归因参数的短码。 | username, 用户 DTO, 渠道名称 |
| **组织单元** | 用户域中表示部门、招商组、渠道组或运营组的组织节点。 | sys_dept, 部门表, 业务组表 |
| **角色编码** | 用于后端鉴权和数据范围解析的稳定角色标识。 | 角色名称, 菜单权限 |
| **角色编码集合** | 当前用户拥有的一组稳定角色标识。 | 逗号字符串, 前端角色文本, 本地角色解析规则 |
| **数据范围** | 用户域输出的 `self/group/all` 可见性范围。 | 业务归属, 前端按钮权限 |
| **可分配负责人** | 当前用户在角色和组织单元约束下可以选择为业务负责人的系统用户。 | 候选用户, 负责人候选人, sys_user |

## 业务链路

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **团长活动** | 精选联盟中的招商活动，是活动商品进入系统流转的上游容器。 | 活动池, campaign |
| **活动商品** | 仍处于审核、分配或推进阶段的候选商品。 | 商品库商品, 共享商品 |
| **共享商品库商品** | 已审核入库、可被团队共享查看并继续推进的商品资产。 | 活动商品, 候选商品 |
| **推广链接** | 面向渠道或达人的可分发商品链接。 | 口令, 外链 |
| **归因映射** | 把 `pick_source` 还原到活动、商品、负责人等业务上下文的映射记录。 | 临时归属, 转链快照 |
| **联盟订单** | 从抖音联盟回流到系统、等待归因或已完成归因的订单记录。 | 店铺订单, 本地订单 |
| **寄样单** | 围绕某个达人与某个商品的一次履约申请。 | 样品表单, 快递单 |
| **寄样动作权限** | 寄样域中用于判断当前用户能否申请、审核、删除、推进物流、导出或享有重复申请豁免的动作约束。 | 用户域角色解析, 前端按钮权限, 数据范围 |
| **达人** | 可被认领、跟进、寄样并产生产出结果的合作对象。 | 用户, 客户 |
| **分析看板** | 面向管理或团队角色聚合展示订单、商品、业绩等指标的只读分析视图。 | 指标所有权, 归因规则, 业绩计算 |
| **数据页订单明细** | 面向分析页面按筛选条件展示订单事实和业绩补全字段的只读明细视图。 | 订单归因工作台, 业绩重算入口 |

## 业绩与访问控制

| Term | Definition | Aliases to avoid |
| --- | --- | --- |
| **业绩访问上下文** | 业绩域用于判断当前用户可查看、导出或重算哪些业绩数据的访问事实集合。 | PerformanceAccessContext, 权限参数, 查询上下文 |

## Relationships

- A **当前用户** may belong to one primary **组织单元**.
- A **当前用户** can maintain one or more **受管用户** when role permissions and **数据范围** allow it.
- An **操作人账号** is derived from a **当前用户** identity for audit records and does not expose the full user profile.
- A **负责人账号** is derived from a responsible **受管用户** identity for display and does not expose the full user profile.
- A **用户展示名称** is derived from one **受管用户** identity for business record display and does not expose roles, department, or channel code.
- A **用户显示标签** is derived from one **受管用户** identity for option labels and does not expose roles, department, or channel code.
- A **负责人归属组织单元** is derived from the assigned **受管用户** and supports group-level filtering.
- A **渠道编码** is derived from one **受管用户** and can be embedded in one or more **推广链接**.
- A **角色编码** contributes to resolving one **当前用户** to one **数据范围**.
- A **角色编码集合** belongs to one **当前用户** and should be interpreted by the user-domain permission policy, not by each business-domain service.
- A **数据范围** filters business-domain queries but does not decide order, sample, or performance ownership.
- A **当前用户** can select a **可分配负责人** only when role and **组织单元** constraints allow it.
- A **团长活动** contains many **活动商品**.
- An **活动商品** can become one **共享商品库商品**.
- A **共享商品库商品** can generate one or more **推广链接**.
- A **推广链接** can produce one or more **归因映射**.
- An **联盟订单** is interpreted through **归因映射** during order attribution.
- A **寄样单** links one **达人** and one **共享商品库商品**.
- A **寄样动作权限** consumes a **角色编码集合** to decide which actions may be attempted on a **寄样单**.
- A **业绩访问上下文** consumes **当前用户** identity, **角色编码**, **组织单元**, and **数据范围** but does not decide order attribution or commission ownership.
- An **分析看板** consumes **数据范围** to limit visible records but does not decide **归因映射**, commission ownership, or metric formulas.
- A **数据页订单明细** consumes **数据范围** to limit visible order records but does not create or change **归因映射**.

## Example dialogue

> **Dev:** "订单列表要按 **当前用户** 过滤，是订单域自己判断角色吗？"
> **Domain expert:** "不是。用户域先根据 **角色编码** 和 **组织单元** 给出 **数据范围**，订单域只消费这个结果。"
> **Dev:** "所以 `group` 范围只是查询可见性，不等于订单最终归属？"
> **Domain expert:** "对。订单归因和业绩归属由订单域、业绩域处理，**数据范围** 只回答当前用户能看哪些数据。"
> **Dev:** "那 **分析看板** 按团队过滤时，能不能顺便重算业绩归属？"
> **Domain expert:** "不能。看板只消费 **数据范围** 控制可见记录，归因和业绩公式仍归订单域、业绩域。"
> **Dev:** "**数据页订单明细** 呢？"
> **Domain expert:** "也是只读视图。它可以按 **数据范围** 过滤订单，但不能成为归因或重算入口。"

## Flagged ambiguities

- "部门"容易被理解为数据库表或组织节点；业务语言统一用 **组织单元**，表名 `sys_dept` 只在实现层使用。
- "权限"容易混用菜单、接口鉴权和数据可见性；查询过滤统一称为 **数据范围**。
- "用户"和 **达人** 是不同概念；用户是系统登录身份，达人是合作对象。
- "业务组"可能指招商组、渠道组或运营组；需要表达类型时使用 **组织单元** 加具体组别类型。
- "操作人"在审计日志中通常只需要 **操作人账号**；不应把它等同为完整用户资料、真实姓名或用户 DTO。
- "负责人名称"在业务记录展示中如果实际来源是登录账号，应统一称为 **负责人账号**，不应读取完整用户 DTO 来填充。
- "用户姓名"如果用于业务记录展示，应统一称为 **用户展示名称**；它不同于下拉或筛选项里的 **用户显示标签**。
- "用户选项"如果只用于下拉 label，应统一称为 **用户显示标签**，不应把完整用户 DTO 泄漏到业务域。
- "负责人部门"如果用于归属覆盖，应统一称为 **负责人归属组织单元**，不应读取完整用户 DTO 来获得。
- "渠道码"、"channelCode"和"推广码"如果用于 `pick_extra`，统一称为 **渠道编码**，不应读取完整用户 DTO 来获得。
- "roleCodes"如果表示当前用户拥有的多个角色，统一称为 **角色编码集合**；业务域不应各自维护字符串 / 集合解析规则。
- "寄样权限"如果指申请、审核、删除、物流、导出或重复申请豁免，统一称为 **寄样动作权限**；如果指列表可见性，应称为 **数据范围**。
- "看板权限"如果指记录可见性，应称为 **数据范围**；如果指指标公式或归因口径，不属于用户域数据范围策略。
- "数据页订单"如果指分析页列表，应称为 **数据页订单明细**；它不是订单归因主工作台，也不是业绩重算入口。
