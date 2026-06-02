# 抖音团长 SaaS V1 Context

This glossary standardizes the current V1 business and environment language used in this repo. `AGENTS.md` and `docs/*.md` remain the source of truth for requirements, milestones, and execution rules. Historical V2.2 terms are reference only and must not override V1 scope.

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

**达人**:
可被认领、跟进、寄样并产生产出结果的合作对象。
_Avoid_: 用户, 客户

**公海**:
当前未归属于当前用户，或允许再次认领的达人池视图。
_Avoid_: 未分配列表

**私海**:
当前用户或其团队已认领并持续经营的达人池视图。
_Avoid_: 个人收藏

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
- A **推广链接** produces one or more **归因映射**
- An **联盟订单** is interpreted through **归因映射** during **订单归因**
- A **寄样单** links one **达人** and one **共享商品库商品**
- A **达人** can appear in **公海** or **私海**
- The **test 环境** and **real-pre 环境** share one **Gateway 契约**

## Example dialogue

> **开发**: 这个商品现在已经能给渠道发链接了，它还是**活动商品**吗？
> **业务**: 不是。只要审核通过并进入共享商品库，它就是**共享商品库商品**；后面的转链、寄样和订单归因都基于这个状态继续推进。

## Flagged ambiguities

- “商品”曾同时指**活动商品**和**共享商品库商品** — resolved: 未入库叫**活动商品**，已入库叫**共享商品库商品**。
- “Mock”曾同时指历史 `local-mock` 口径和当前默认测试基线 — resolved: 当前默认 Mock 基线叫 **test 环境**，`local-mock` 仅作历史资料，不作为运行入口。
- “real”与“real-pre”容易混用 — resolved: 当前真实上游与生产形态入口统一叫 **real-pre 环境**，不再保留独立 `real` / `prod` profile。
- “组长”可能同时指招商组长与渠道组长 — resolved: 讨论权限、分配或菜单时必须明确是 `biz_leader` 还是 `channel_leader`。
