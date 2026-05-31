# 05-real-pre证据索引

更新时间：2026-05-19

> 本文回答两件事：**P0 证据在哪里**，以及**还缺什么证据**。  
> 验收标准看 [04-上线验收清单](./04-上线验收清单.md)，任务来源看 [03-项目剩余事项与任务看板](./03-项目剩余事项与任务看板.md)。

## 一、当前结论

- 当前 real-pre 证据已经能覆盖：
  - real-pre 准入
  - 单样本业务闭环 smoke
  - 全角色业务流程
  - 可视化全业务剧本
  - Dashboard Summary 与数据库对账
- 本轮已新增 E2E 计划底稿：
  - [09-07-MCP接口梳理总收口与E2E测试计划](./09-07-MCP接口梳理总收口与E2E测试计划.md)
  - [`runtime/qa/e2e/README.md`](../runtime/qa/e2e/README.md)
  - 这些材料是 **E2E 场景与脚本规划辅助材料**，不是 real-pre 主证据
- 本轮已新增 test 环境基础 API smoke 留档证据：
  - `runtime/qa/out/e2e-base-smoke-20260519-083947/`
  - 覆盖 `GET /api/actuator/health`（200/22ms）、`POST /api/auth/login`（200/252ms）、`GET /api/menus/tree`（200/21ms）、`GET /api/dashboard/summary`（200/63ms）
  - 结果：4/4 PASS
  - 该结果属于辅助材料，不计入 real-pre 已通过主证据
- 本轮已新增 test 环境商品主链 API 实跑证据（最新一轮）：
  - `runtime/qa/out/e2e-product-20260519-083634/`
  - 覆盖 `POST /api/auth/login`、`GET /api/colonel/activities`、`GET /api/colonel/activities/{activityId}/products`、`GET /api/colonel/activities/{activityId}/products/{productId}`、`POST /api/colonel/activities/{activityId}/products/{productId}/promotion-links`
  - 结果：5/5 PASS
  - 当前只能确认“转链前半段通过”，不计入 real-pre 已通过主证据，也不能替代 P0-4 真实归因样本
- 本轮已新增 test 环境订单 / 归因 / Dashboard API 实跑证据：
  - `runtime/qa/out/e2e-order-20260519-085255/`
  - 覆盖 `POST /api/auth/login`、`GET /api/orders`、`GET /api/orders/{orderId}`、`GET /api/orders/unattributed`、`GET /api/dashboard/summary`、`GET /api/dashboard/metrics?timeField=createTime`、`GET /api/dashboard/order-attribution-summary`
  - 结果：6 PASS / 1 SKIP（`WAITING_REAL_PICK_SOURCE_SAMPLE`），0 FAIL
  - 当前只能确认 test 环境订单查询、归因排查字段与 Dashboard 汇总接口基线可读，不计入 real-pre 已通过主证据，也不能替代 P0-4 真实归因样本
- 本轮已新增 test 环境配置规则 API 实跑证据：
  - `runtime/qa/out/e2e-config-20260519-090141/`
  - 覆盖 `POST /api/auth/login`、`GET /api/configs`、`GET /api/configs/grouped`、`GET /api/configs/{id}`、`PUT /api/configs/{id}`
  - 结果：9/9 PASS
  - 当前确认关键配置可读，且 `sample.restrict_days` 可通过 API 完成 `7 -> 1 -> 7` 安全改写与恢复；不计入 real-pre 已通过主证据，也不能替代 P0-8 寄样规则行为样本或 P0-7 完整闭证
- 本轮已新增 test 环境达人主链 API 实跑证据：
  - `runtime/qa/out/e2e-talent-20260519-091458/`
  - 覆盖 `POST /api/auth/login`、`GET /api/configs/grouped`、`GET /api/talents`、`POST /api/talents`、`GET /api/talents/{id}`、`PUT /api/talents/{id}`、`POST /api/talents/{id}/claims`、`POST /api/talents/{id}/release`、`DELETE /api/talents/{id}`
  - 结果：9 PASS / 1 SKIP（`TALENT_CLEANUP_NOT_SUPPORTED`），0 FAIL
  - 当前确认达人 CRUD、认领、保护期字段和释放 API 基线可用；物理删除清理返回 HTTP 200 / 业务码 500，不计入 real-pre 已通过主证据，也不能替代 P0-9 公私海多账号数据范围样本
- 本轮已新增 test 环境寄样主链 API 实跑证据：
  - `runtime/qa/out/e2e-sample-20260519-092948/`
  - 覆盖 `POST /api/auth/login`、`GET /api/configs/grouped`、`GET /api/samples/talent-candidates`、`POST /api/talents`、`POST /api/talents/{id}/claims`、`GET /api/samples/product-candidates`、`POST /api/samples/eligibility-check`、`POST /api/samples`、`GET /api/samples/{id}`、`PUT /api/samples/{id}/status`、`GET /api/samples`、`GET /api/samples/{id}/status-logs`
  - 结果：19 PASS / 1 SKIP（`WAITING_ORDER_SAMPLE_FOR_COMPLETED_BY_ORDER`），0 FAIL
  - 当前确认普通渠道寄样申请、7 天重复申请限制、管理员审核、手动发货、签收到 `PENDING_TASK`、列表回查和状态日志 API 基线可用；不计入 real-pre 已通过主证据，也不能替代 `completed_by_order` 真实或稳定订单样本、豁免样本和配置改前改后样本
- 本轮已新增 test 环境 RBAC / 菜单 / 数据范围 API 实跑证据：
  - `runtime/qa/out/e2e-rbac-20260519-133713/`
  - 覆盖 admin、bizStaff、channelStaff、ops、bizLeader、channelLeader 登录后分别请求 `GET /api/menus/tree`、`GET /api/talents`、`GET /api/samples`、`GET /api/orders`、`GET /api/dashboard/summary`
  - 结果：37 PASS / 0 FAIL / 2 SKIP
  - 当前确认登录回包中的 dataScope 基线为 admin=all、普通成员=self、组长=group；5 个原越权 FAIL 已改为 `DENY` 并记为 `PASS_DENIED_AS_EXPECTED`，`bizStaff /api/samples` 已修复为业务码 200；非管理员菜单树仍按 `OBSERVE` 记录且均为 0，`ops_staff` configured 新口径与 U-09 专用多角色样本仍需新 run 补证；不计入 real-pre 已通过主证据，也不能替代 P0-9 完整闭证
- 本轮已新增 `E2E-P0-REGRESSION` 总回归汇总：
  - `runtime/qa/out/e2e-p0-regression-20260519-134453/`
  - 汇总口径：只汇总最新单项 E2E 证据，不新增业务测试，不改业务代码
  - 汇总结果：`89 PASS / 0 FAIL / 5 SKIP`
  - 当前只能确认“单项 E2E 基线已统一收口”；`P0-4`、`P0-5`、`P0-6`、`P0-9` 仍不能写成完全通过，`P0-7` 已从“待手算”推进到“部分通过”
- 本轮已新增 `P0-7` 正式手算辅助证据：
  - `runtime/qa/out/p0-7-commission-manual-check-20260519-135831/`
  - 产物：`orders-source.json`、`config-source.json`、`dashboard-source.json`、`manual-check-table.md`、`summary.json`、`report.md`
  - 当前结论：`summary.json.overallPass=true`，单笔订单金额字段、活动 bucket 手算、Dashboard Summary（今日窗口）与 Dashboard Metrics（今日窗口）全部 PASS
  - 说明：单笔 `actualBusinessCommission / actualChannelCommission` 无接口与单笔落库字段，继续按汇总口径补证
- 本轮已新增 real-pre final regression（只读 / 低风险）证据：
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/`
  - 覆盖 `E2E-BASE-01`、`E2E-PRODUCT-01`、`E2E-ORDER-01` 与 `P0-7` 只读手算脚本重跑尝试
  - 当前结论：real-pre 环境可用；`E2E-BASE-01` 4/4 PASS；`E2E-ORDER-01` 6 PASS / 1 SKIP（`WAITING_REAL_PICK_SOURCE_SAMPLE`）；`E2E-PRODUCT-01` 当前样本 `promotion-links` 返回 `code=460 / 抖店 40003 授权主体不匹配`；`P0-7` 只读重跑因无 attributed order sample 未形成新单笔手算表
  - 该结果不把 `P0-4`、`P0-5`、`P0-6`、`P0-9` 写成通过；`P0-7` 继续保持“部分通过”
- 本轮已新增 `REALPRE-PROMOTION-LINK-TRIAGE-01` 专项证据：
  - `runtime/qa/out/real-pre-promotion-link-triage-20260519-152304/`
  - 覆盖活动列表、多个活动商品列表、5 个候选商品 `promotion-links` 抽样、real-pre token / 授权主体只读复核、商品字段与 DB 只读对照
  - 当前结论：`MIXED_SUBJECT_SAMPLES`；目标失败样本 `3920684 / 3817382656845414423` 复现 `code=460 / upstreamCode=40003`，同活动另外 2 个样本失败原因为“未加入商品库”，历史活动 `3916506` 的 2 个已入库样本仍可转链成功
  - 该结果不把 `P0-4` 写成通过，也不把问题扩大写成 `REALPRE_PROMOTION_AUTH_GLOBAL_BLOCKER`
- 本轮已新增 `REALPRE-PROMOTION-LINK-BASELINE-01` 基准样本固定证据：
  - `runtime/qa/out/real-pre-promotion-link-baseline-20260519-154254/`
  - 覆盖 `3916506` 下 2 个历史成功样本的 fresh `promotion-links` 重试、详情复核与异常样本对照
  - 当前结论：`BASELINE_LOCKED_WITH_EXCEPTION_SAMPLE`；`3916506 / 3810562766247428542` 与 `3916506 / 3817426948628545700` 当前均可稳定转链，可作为 future real-pre `E2E-PRODUCT-01` 的 primary / backup 样本；`3920684 / 3817382656845414423` 继续作为异常样本保留
  - 该结果不把 `P0-4` 写成通过，也不把 `40003` 改写为全局授权阻塞
- 本轮已新增 `E2E-PRODUCT-01` real-pre baseline 重跑证据：
  - `runtime/qa/out/e2e-product-real-pre-baseline-20260519-155237/`
  - 覆盖固定 baseline `3916506 / 3810562766247428542` 下的 `login`、`activities`、`activity detail`、`activity product list/detail`、`promotion-links`
  - 当前结论：real-pre 商品主链固定 baseline 转链前半段通过；`P0-4` 真实订单归因仍待真实 `pick_source` 订单样本
  - 该结果不把 `P0-4` 写成通过，也不把 `3920684 / 3817382656845414423` 的 `40003` 改写为全局 blocker
- 本轮已新增寄样接口事实梳理：
  - [09-05-MCP寄样接口梳理](./09-05-MCP寄样接口梳理.md)
  - 该文档提供 P0-6 / P0-8 的接口与源码取证路径，不替代 real-pre 样本
- 本轮已新增达人接口事实梳理：
  - [09-06-MCP达人接口梳理](./09-06-MCP达人接口梳理.md)
  - 该文档提供 P0-1 / P0-3 / P0-6 / P0-9 的接口与源码取证路径，不替代 real-pre 样本
- 当前 real-pre 证据仍明显缺少：
  - 真实 `pick_source` 归因成功样本
  - 默认招商归因专项样本
  - `completed_by_order` 样本
  - 7 天限制 / 豁免 / 配置改前改后专项样本
  - 商品详情快速寄样入口（PRODUCT-CASE-08）的浏览器路径证据
  - 商品库类目 / 合作方 / 标签筛选、同 `product_id` 去重展示、合作方列表 API 的浏览器 / API 验收截图
  - 达人保护期改前改后、出单后保护期变化、达人地址带入专项样本
  - U-09 一人多角色专项样本

## 二、证据索引规则

1. 本文只索引 **real-pre 证据**。
2. 若后续使用 local-mock 作为辅助补证，必须显式标注“辅助证据”，不能和 real-pre 混写。
3. 每条证据都必须映射到：
   - 一个 P0 验收项
   - 一个或多个 [03-项目剩余事项与任务看板](./03-项目剩余事项与任务看板.md) 中的任务
4. 如果当前没有证据，不留空，直接写待补状态、缺口和后续动作。
5. 不补伪造样本，不把“无样本”改写成“通过”。

## 三、P0 证据总表

| 编号 | 验收项 | 当前证据状态 | 计划证据类型 | 主证据目录 | 主要缺口 |
|---|---|---|---|---|---|
| P0-1 | 渠道链端到端 | 部分完成；已补 test 达人 CRUD / 认领 / 释放与寄样申请 / 发货 API 辅助证据 | API E2E + 浏览器 E2E + DB补证 | `real-pre-business-e2e-20260517-091828`、`real-pre-full-business-journey-20260517-151152` | 受 P0-4 / P0-6 缺口影响，未最终闭证；达人地址自动带入未确认 |
| P0-2 | 招商链端到端 | 部分完成 | API E2E + 浏览器 E2E + DB补证 | `real-pre-role-business-e2e-20260517-101101`、`real-pre-full-business-journey-20260517-151152` | 默认招商归因专项证据缺失；显式 `POST sync` 接口仍待核对 |
| P0-3 | 管理链规则生效 | 已补 test 配置 API 与达人保护期认领辅助证据，real-pre / 业务规则前后对照待补 | API E2E + 浏览器抽查 + 日志补证 | 暂无单独主证据目录 | 缺配置改后业务规则生效前后对照；达人保护期改配置前后仍需专项样本 |
| P0-4 | `pick_source` 渠道归因正确 | 待真实样本 | API E2E + DB/日志 + 真实样本 | `real-pre-attribution-evidence-20260517-104656` | 缺真实带 `pick_source` 的成功归因订单 |
| P0-5 | 活动默认招商归因正确 | 待补 | API E2E + 浏览器 E2E + DB补证 | 暂无单独主证据目录 | 缺默认招商归因专项对表 |
| P0-6 | 寄样交作业自动完成 | 部分完成；已补 test 寄样推进到 `PENDING_TASK` API 辅助证据，代码已加 `pay_time != null` guard，`completed_by_order` 正负样本待补 | API E2E + 浏览器 E2E + DB/日志补证 | `real-pre-role-business-e2e-20260517-101101` | 缺 `pay_time=null` 负向样本与 `pay_time!=null` 完成样本 |
| P0-7 | 双轨金额与提成正确 | 部分完成；已补 Dashboard/DB 对账与正式手算辅助证据 | API E2E + 浏览器 E2E + DB对账 + 手算 | `real-pre-dashboard-reconcile-20260517-122911`、`p0-7-commission-manual-check-20260519-135831` | 单笔实际提成字段仍需按汇总口径补证；real-pre 仍缺真实订单级辅助样本 |
| P0-8 | 寄样限制与豁免正确 | 已补 test 配置改写 / 恢复与普通渠道 7 天限制行为辅助证据，豁免 / 改配置后业务生效待补 | API E2E + 浏览器抽查 + 日志补证 | 暂无单独主证据目录 | 缺管理员 / 渠道组长豁免、拒绝单不计限制、改配置后业务生效样本 |
| P0-9 | 数据范围正确 | 基础角色边界通过，专项待补；已补 test 达人字段观察与 RBAC/API 菜单基线，RBAC 真实 FAIL 已清零 | 浏览器 E2E + API E2E + DB补证 | `real-pre-role-business-e2e-20260517-101101`、`real-pre-full-business-journey-20260517-151152` | 缺 U-09 专项样本、非管理员菜单授权复核、业务接口数据范围对表；`ops_staff` configured 新口径待补回归证据 |

## 四、P0-1 渠道链端到端证据

### EVID-P0-1-01：单样本真实业务闭环 smoke

- 对应验收项：P0-1
- 来源任务：TASK-MUST-01、TASK-MUST-02、TASK-MUST-03
- 环境：real-pre
- 证据类型：报告 / 页面链路 / 接口返回
- 证据路径：
  - `runtime/qa/out/real-pre-business-e2e-20260517-091828`
- 当前结论：渠道链 smoke 已通过，覆盖商品读取、转链、mapping、订单同步、Dashboard 页面打开
- 是否已完成：部分完成
- 缺口：还不能替代真实归因成功样本和寄样自动完成样本
- 后续动作：等待 P0-4、P0-6 补齐后再合并判定

### EVID-P0-1-02：可视化全业务剧本

- 对应验收项：P0-1
- 来源任务：TASK-MUST-01、TASK-MUST-03
- 环境：real-pre
- 证据类型：浏览器剧本 / 页面截图 / 报告
- 证据路径：
  - `runtime/qa/out/real-pre-full-business-journey-20260517-151152`
- 当前结论：渠道角色在真实浏览器中完成达人、转链、寄样等主线动作
- 是否已完成：部分完成
- 缺口：终点停在 `PENDING_TASK`，不是 `completed_by_order`；达人地址自动带入寄样未确认
- 后续动作：补完成态样本；达人侧按 [09-06-MCP达人接口梳理](./09-06-MCP达人接口梳理.md) 补“认领 -> 保护期 -> 地址边界 -> 寄样”的专项记录

## 五、P0-2 招商链端到端证据

### EVID-P0-2-01：全角色账号业务流程验收

- 对应验收项：P0-2
- 来源任务：TASK-MUST-04
- 环境：real-pre
- 证据类型：报告 / 页面链路 / 角色流程
- 证据路径：
  - `runtime/qa/out/real-pre-role-business-e2e-20260517-101101`
- 当前结论：招商组长、招商、运营等角色的活动列表、活动商品刷新、商品审核、加入商品库、寄样复审流程已有证据
- 是否已完成：部分完成
- 缺口：默认招商归因专项证据还没独立闭证；前端 `POST /colonel/activities/{activityId}/sync` 与 `POST /colonel/activities/{activityId}/products/sync` 仍未在 OAS / controller 中确认
- 后续动作：补“活动负责人 -> 默认招商归因 -> 业绩展示”的专项样本，并继续按 `GET /colonel/activities`、`GET /colonel/activities/{activityId}/products?refresh=true` 固化当前同步口径

### EVID-P0-2-02：可视化全业务剧本中的招商链

- 对应验收项：P0-2
- 来源任务：TASK-MUST-04
- 环境：real-pre
- 证据类型：浏览器剧本 / 页面链路
- 证据路径：
  - `runtime/qa/out/real-pre-full-business-journey-20260517-151152`
- 当前结论：已覆盖活动列表读取、活动商品刷新、商品审核、寄样复审、运营推进等动作
- 是否已完成：部分完成
- 缺口：仍缺默认招商归因最终落账说明；当前剧本不能反证两个 `POST sync` 调用已真实存在
- 后续动作：补订单与业绩视角的专项对表，并把未确认同步调用继续标记为“前端调用待核对”

## 六、P0-3 管理链规则生效证据

### EVID-P0-3-01：管理员与多角色基础链路

- 对应验收项：P0-3
- 来源任务：TASK-CONFIRM-01、TASK-CONFIRM-04
- 环境：real-pre
- 证据类型：角色流程 / 页面链路
- 证据路径：
  - `runtime/qa/out/real-pre-role-business-e2e-20260517-101101`
- 当前结论：管理员入口、多角色基础能力已有 real-pre 证据
- 是否已完成：部分完成
- 缺口：缺配置改前改后、规则生效前后对照
- 后续动作：补“改配置 -> 规则变化”的专项证据

### EVID-P0-3-02：规则生效前后对照

- 对应验收项：P0-3
- 来源任务：TASK-CONFIRM-04
- 环境：real-pre（主证据待补）；test（已有配置 API 辅助证据）
- 证据类型：待补专项证据 + API E2E 辅助证据
- 证据路径：
  - 辅助证据：`runtime/qa/out/e2e-config-20260519-090141/`
  - 辅助证据：`runtime/qa/out/e2e-talent-20260519-091458/`
  - real-pre 主证据：待补
- 当前结论：已在 test 环境确认配置 API 可读、关键配置存在，且 `sample.restrict_days` 可安全改写与恢复；`E2E-TALENT-01` 进一步确认 `talent.protection_days=30` 可读，认领后 `protectedUntil` 与配置天数关系合理；不能据此关闭规则业务生效验收
- 是否已完成：部分完成
- 缺口：缺寄样限制、达人保护期、提成比例改前改后业务对照；配置接口事实已补到 [09-04-MCP配置规则接口梳理](./09-04-MCP配置规则接口梳理.md)，达人保护期消费点和本轮 API 基线已补到 [09-06-MCP达人接口梳理](./09-06-MCP达人接口梳理.md) / `runtime/qa/out/e2e-talent-20260519-091458/`
- 后续动作：在 real-pre 或可控辅助环境补“改配置 -> 业务规则变化”成对证据；优先覆盖 `sample.restrict_enabled`、达人保护期认领样本，并把 `sample.restrict_days` API 改写证据与寄样行为样本合并判断；P0-7 正式手算辅助证据已另见 `runtime/qa/out/p0-7-commission-manual-check-20260519-135831/`

## 七、P0-4 `pick_source` 渠道归因证据

### EVID-P0-4-01：真实订单归因样本首轮观测

- 对应验收项：P0-4
- 来源任务：TASK-MUST-01
- 环境：real-pre
- 证据类型：报告 / 订单抽样 / Dashboard 校验
- 证据路径：
  - `runtime/qa/out/real-pre-attribution-evidence-20260517-104656`
- 当前结论：`SYNC_OK_NO_SAMPLE`
- 是否已完成：否
- 缺口：缺真实带 `pick_source` 的订单
- 后续动作：继续等待或制造真实推广购买样本，再复跑同一入口

### EVID-P0-4-02：转链与 mapping 落库证据

- 对应验收项：P0-4
- 来源任务：TASK-MUST-02
- 环境：real-pre
- 证据类型：页面链路 / 报告
- 证据路径：
  - `runtime/qa/out/real-pre-business-e2e-20260517-091828`
  - `runtime/qa/out/real-pre-full-business-journey-20260517-151152`
- 当前结论：转链成功、`pick_source_mapping` 命中已有证据
- 是否已完成：部分完成
- 缺口：仍缺“mapping 命中后订单真实归因成功”闭证；当前没有独立 business OAS 读接口直接查询 `pick_source_mapping`
- 后续动作：与 EVID-P0-4-01 合并形成最终闭环证据，补证时继续依赖：
  - 转链回包
  - `GET /products/{productId}/promotion-links/history`
  - `GET /orders/unattributed`
  - `GET /orders/{orderId}`
  - `GET /dashboard/summary`

### EVID-P0-4-03：real-pre 转链 460 / 40003 专项复核

- 对应验收项：P0-4
- 来源任务：REALPRE-PROMOTION-LINK-TRIAGE-01
- 环境：real-pre
- 证据类型：报告 / API 抽样 / token 主体只读复核 / DB 只读对照
- 证据路径：
  - `runtime/qa/out/real-pre-promotion-link-triage-20260519-152304`
- 当前结论：`MIXED_SUBJECT_SAMPLES`
- 是否已完成：部分完成
- 关键结论：
  - 目标失败样本 `3920684 / 3817382656845414423` 已复现 `code=460 / upstreamCode=40003 / 授权主体不匹配`
  - 同活动 `3920684` 的另外 2 个对照样本未复现 `40003`，而是在“请先将商品加入商品库后再生成推广链接”这一前置条件处失败
  - 历史活动 `3916506` 的 2 个已入库样本本轮仍返回 `code=200`，说明 real-pre 转链能力并未整体失效
  - `/api/douyin/institution-info` 返回 `colonelBuyinId=7351155267604218149 / colonelName=星链达客`；`activity-3920684` 与 `activity-3916506` 的上游详情也都返回相同 `colonel_buyin_id=7351155267604218149`，因此当前 `40003` 不能简单归因为活动级 `colonel_buyin_id` 不一致
  - `.env.real-pre` 与 backend container env 均检测到 `DOUYIN_APP_ID`、`DOUYIN_CLIENT_KEY` 存在；Redis 只读检查未观察到 `douyin:token:*` 旧主体缓存键
- 缺口：
  - 仍缺“目标失败样本为何在同主体下返回 `40003`”的上游样本级解释
  - 仍缺真实订单带回 `pick_source` 后的最终归因闭环
- 后续动作：
  - 保留当前结论为 `MIXED_SUBJECT_SAMPLES`，不写成 `REALPRE_PROMOTION_AUTH_GLOBAL_BLOCKER`
  - 优先补“同主体、已入库、detailUrl 完整”的 real-pre 样本继续对照
  - 等真实订单样本到位后，与 EVID-P0-4-01 / EVID-P0-4-02 合并形成最终闭环证据
  - 数据库取证

### EVID-P0-4-04：real-pre promotion-link baseline 基准样本锁定

- 对应验收项：P0-4
- 来源任务：REALPRE-PROMOTION-LINK-BASELINE-01
- 环境：real-pre
- 证据类型：报告 / API 成功样本锁定 / 异常样本对照
- 证据路径：
  - `runtime/qa/out/real-pre-promotion-link-baseline-20260519-154254`
- 当前结论：`BASELINE_LOCKED_WITH_EXCEPTION_SAMPLE`
- 是否已完成：部分完成
- 关键结论：
  - `3916506 / 3810562766247428542`（primary）与 `3916506 / 3817426948628545700`（backup）在 `channel_leader` 口径下 fresh `POST /promotion-links` 均返回 `code=200`
  - 两个基准样本当前均为 `selectedToLibrary=true`、`promotionAvailable=true`，回包 `pickSource=v.MpdLbM`，且 `promoteLink` 存在，可用于 future real-pre `E2E-PRODUCT-01`
  - 当前 API 回包提供 `shortId`，但未返回显式 `shortLink` 字符串字段；因此本轮把 baseline 可用性判定为“`code=200 + pickSource + promoteLink + 已入库`”
  - `3920684 / 3817382656845414423` 继续表现为 `code=460 / upstreamCode=40003` 的异常样本，但本证据包仅将其记为 `EXCEPTION_SAMPLE_ONLY`，不扩大为全局 blocker
  - `institution-info`、`activity-3916506`、`activity-3920684` 的 raw response 仍共同指向 `colonel_buyin_id=7351155267604218149`
- 缺口：
  - 仍缺真实订单带回 `pick_source` 的最终归因闭环
  - 仍缺 `3920684 / 3817382656845414423` 在同主体下返回 `40003` 的上游样本级解释
- 后续动作：
  - future real-pre `E2E-PRODUCT-01` 优先使用已锁定的 primary / backup baseline samples
  - `3920684 / 3817382656845414423` 继续按异常样本跟踪，不改写为全局 auth blocker
  - 等真实订单样本到位后，与 EVID-P0-4-01 / EVID-P0-4-02 / EVID-P0-4-03 合并形成最终闭环证据

### EVID-P0-4-05：real-pre 商品主链 baseline 重跑

- 对应验收项：P0-4
- 来源任务：E2E-PRODUCT-01 real-pre baseline rerun
- 环境：real-pre
- 证据类型：API E2E / 固定 baseline 样本重跑
- 证据路径：
  - `runtime/qa/out/e2e-product-real-pre-baseline-20260519-155237`
- 当前结论：real-pre 商品主链固定 baseline 转链前半段通过
- 是否已完成：部分完成
- 关键结论：
  - 使用环境变量固定 `E2E_ACTIVITY_ID=3916506`、`E2E_PRODUCT_ID=3810562766247428542` 后，`E2E-PRODUCT-01` fresh 重跑结果为 6/6 PASS
  - `GET /api/colonel/activities?page=1&pageSize=20`、`GET /api/douyin/activities/3916506`、`GET /api/colonel/activities/3916506/products?count=20&productInfo=3810562766247428542`、`GET /api/colonel/activities/3916506/products/3810562766247428542` 均返回 `code=200`
  - `POST /api/colonel/activities/3916506/products/3810562766247428542/promotion-links` 返回 `code=200`，并返回 `pickSource=v.MpdLbM` 与 `promoteLink`
  - 该结果把 real-pre 商品主链从“随机样本混杂导致转链失败”收敛为“固定 baseline 转链前半段通过”
- 缺口：
  - 仍缺真实订单带回 `pick_source` 的最终归因闭环
  - 仍不能把该结果外推为 `P0-4` 真实订单归因通过
- 后续动作：
  - future real-pre 商品主链回归继续优先复用 fixed baseline sample
  - 等真实 `pick_source` 订单样本到位后，把订单详情、归因摘要、Dashboard 与 DB/日志证据补回 P0-4 主闭证

## 八、P0-5 活动默认招商归因证据

### EVID-P0-5-01：招商链业务动作证据

- 对应验收项：P0-5
- 来源任务：TASK-MUST-04
- 环境：real-pre
- 证据类型：角色流程 / 页面链路
- 证据路径：
  - `runtime/qa/out/real-pre-role-business-e2e-20260517-101101`
  - `runtime/qa/out/real-pre-full-business-journey-20260517-151152`
- 当前结论：活动、商品、审核、寄样链路已有证据
- 是否已完成：部分完成
- 缺口：还没有“默认招商归因 = 活动绑定招商”的单独闭证；当前业务接口只直接暴露最终落库 `colonelUserId`，没有 `default / final` 分拆读字段
- 后续动作：补订单、业绩、看板三视角专项对表，并在必要时追加数据库取证

### EVID-P0-5-02：默认招商归因专项对表

- 对应验收项：P0-5
- 来源任务：TASK-MUST-04
- 环境：real-pre
- 证据类型：待补专项证据
- 证据路径：
  - 待补
- 当前结论：待补
- 是否已完成：否
- 缺口：缺活动负责人、订单默认招商、业绩展示三者一致的专项样本
- 后续动作：补专项对表记录，最少覆盖：
  - 活动负责人 / 商品负责人事实
  - `GET /orders/{orderId}` 或 `GET /orders`
  - `GET /dashboard/summary`
  - 必要时补数据库专项对表

## 九、P0-6 寄样交作业完成证据

### EVID-P0-6-01：寄样推进到待交作业

- 对应验收项：P0-6
- 来源任务：TASK-MUST-03
- 环境：real-pre
- 证据类型：角色流程 / 页面链路
- 证据路径：
  - `runtime/qa/out/real-pre-role-business-e2e-20260517-101101`
  - `runtime/qa/out/real-pre-full-business-journey-20260517-151152`
- 当前结论：寄样申请、审核、发货、推进到 `PENDING_TASK` 已有证据
- 是否已完成：部分完成
- 缺口：还没有“命中订单后进入 COMPLETED”的样本
- 后续动作：补 `completed_by_order` 样本

### EVID-P0-6-03：`E2E-SAMPLE-01` 寄样申请到待交作业 API 基线

- 对应验收项：P0-6
- 来源任务：TASK-MUST-03、TASK-CONFIRM-02
- 环境：test
- 证据类型：API E2E 辅助证据
- 证据路径：
  - `runtime/qa/out/e2e-sample-20260519-092948/`
- 当前结论：普通渠道创建寄样后，管理员上下文完成审核通过、手动发货、签收到 `PENDING_TASK`，详情 / 列表 / 状态日志均已回查通过
- 是否已完成：部分完成
- 覆盖结果：
  - `POST /api/samples`：PASS，HTTP 200 / 业务码 200，初始 `status=PENDING_AUDIT`
  - `PUT /api/samples/{id}/status action=APPROVED`：PASS，HTTP 200 / 业务码 200，`status=PENDING_SHIP`
  - `PUT /api/samples/{id}/status action=SHIPPED`：PASS，HTTP 200 / 业务码 200，`status=SHIPPED` 且 `trackingNo` 回显
  - `PUT /api/samples/{id}/status action=SIGNED`：PASS，HTTP 200 / 业务码 200，`status=PENDING_TASK`
  - `GET /api/samples/{id}/status-logs`：PASS，状态日志包含 `PENDING_AUDIT / PENDING_SHIP / SHIPPED / PENDING_TASK`
- 缺口：本条只证明待交作业前半段，不证明订单命中后的 `FINISHED / completed_by_order`
- 后续动作：等待真实或稳定订单样本后，按 `POST /orders/sync` -> `GET /orders/{orderId}` -> `GET /samples/{id}` -> `GET /samples/{id}/status-logs` -> DB/日志 继续补证

### EVID-P0-6-02：`completed_by_order` 样本

- 对应验收项：P0-6
- 来源任务：TASK-MUST-03
- 环境：real-pre
- 证据类型：待补专项证据
- 证据路径：
  - 待补
- 当前结论：待补
- 是否已完成：否
- 缺口：缺寄样被真实 / 稳定已付款订单命中后自动完成的证据，以及匹配但 `pay_time=null` 时不完成的负向证据
- 当前接口事实：
  - 无独立 `completed_by_order` 接口
  - `POST /orders/sync` 入库/更新订单后触发 `SampleLifecycleService.completePendingHomeworkByOrder`
  - 匹配条件是 `channel_user_id + talent_uid/author_id + product_id`，且寄样单处于内部 `PENDING_HOMEWORK`
  - 自动完成必须要求订单 `pay_time != null`；未付款订单不触发 `completed_by_order`
- 达人关联观察：
  - 无独立“出单后重置保护期”接口
  - 当前源码确认到期释放时若保护期内有出单则不释放；是否立即延长 `protectedUntil` 需结合 `GET /talents/{id}` 与 `talent_claim` DB 补证
- 后续动作：按 `POST /orders/sync` -> `GET /orders/{orderId}` -> `GET /samples/{id}` -> `GET /samples/{id}/status-logs` -> DB/日志 的路径补证；先断言匹配但 `pay_time=null` 的订单不会完成寄样，再断言 `pay_time!=null` 的匹配订单使 `sample.completedByOrderRule=true`、寄样 `status=FINISHED`、日志含 `auto complete by order: {orderId}`

## 十、P0-7 双轨金额与提成证据

### EVID-P0-7-01：Dashboard Summary 与数据库对账

- 对应验收项：P0-7
- 来源任务：TASK-MUST-01
- 环境：real-pre
- 证据类型：报告 / 数据库对账
- 证据路径：
  - `runtime/qa/out/real-pre-dashboard-reconcile-20260517-122911`
- 当前结论：`PASS`
- 是否已完成：部分完成
- 缺口：这是汇总层对账，不等于单笔订单手算闭证；单笔实际提成字段当前仍无接口和无单笔落库字段
- 后续动作：继续保留该 real-pre 汇总层证据，与 `EVID-P0-7-02` 的正式手算辅助证据共同使用；当前接口观察口径以 `GET /orders/{orderId}`、`GET /dashboard/summary`、`GET /dashboard/metrics`、`GET /data/orders` 为准；提成比例配置键以 `commission.business_default_ratio`、`commission.channel_default_ratio` 为准

### EVID-P0-7-02：单笔订单双轨金额 / 提成手算表

- 对应验收项：P0-7
- 来源任务：P0-7 正式辅助补证
- 环境：test（辅助证据）
- 证据类型：已落档专项证据
- 证据路径：
  - `runtime/qa/out/p0-7-commission-manual-check-20260519-135831/`
  - `runtime/qa/out/p0-7-commission-manual-check-20260519-135831/orders-source.json`
  - `runtime/qa/out/p0-7-commission-manual-check-20260519-135831/config-source.json`
  - `runtime/qa/out/p0-7-commission-manual-check-20260519-135831/dashboard-source.json`
  - `runtime/qa/out/p0-7-commission-manual-check-20260519-135831/manual-check-table.md`
  - `runtime/qa/out/p0-7-commission-manual-check-20260519-135831/summary.json`
  - `runtime/qa/out/p0-7-commission-manual-check-20260519-135831/report.md`
- 当前结论：`PASS`
- 是否已完成：部分完成
- 覆盖结果：
  - 3 条样本订单已完成单笔金额字段、订单详情、`/data/orders` 与 DB 事实对表
  - 活动 bucket `TEST_ACTIVITY_A` 已完成 `serviceFeeNet -> business/channel commission` 手算，结果为 `570 / 570`
  - 今日窗口 Dashboard Summary 与 Dashboard Metrics 已对齐 `orderCount=1`、`orderAmount=19900`、`serviceFeeIncome=2600`、`talentCommission=2600`、`businessCommission=0`、`channelCommission=0`
  - 单笔 `actualBusinessCommission / actualChannelCommission` 当前无接口和无单笔落库字段，证据中已明确按汇总口径补证，未伪造单笔值
- 缺口：该证据为 test 辅助证据；real-pre 仍只有汇总层对账，且真实 `pick_source` 与 `completed_by_order` 样本缺口不受本条关闭
- 后续动作：若后续 real-pre 出现稳定真实订单样本，可按相同表头追加 real-pre 单样本手算附件；当前单笔字段口径优先看 `orderAmount / serviceFee / settleTime`

### EVID-P0-7-03：2704 金额单位 raw JSON 对表

- 对应验收项：P0-7
- 来源任务：TASK-MUST-07
- 环境：real-pre
- 证据类型：待补专项证据
- 证据路径：
  - 待补：真实 `buyin.colonelMultiSettlementOrders / 2704` raw JSON
  - 待补：同一订单后台金额截图或官方订单详情对表记录
- 当前结论：`BLOCKED`
- 是否已完成：否
- 缺口：当前公开文档抓取无法确认金额字段单位；必须用真实 raw JSON 对照后台金额确认 `settled_goods_amount`、`settled_service_fee`、`service_fee`、`pay_amount`、`order_amount`、`commission_amount` 等字段是分、元数字还是元字符串。
- 后续动作：先补真实样本并记录 `source_amount_unit`；在确认前，2704 结算事实记录保持 `UNVERIFIED`，Dashboard / 数据页结算事实聚合不得把这些金额作为上线可信展示。

## 十一、P0-8 寄样限制与豁免证据

### EVID-P0-8-01：寄样限制与豁免专项样本

- 对应验收项：P0-8
- 来源任务：待从 P0-8 派生
- 环境：real-pre
- 证据类型：待补专项证据
- 证据路径：
  - 辅助证据：`runtime/qa/out/e2e-config-20260519-090141/`
  - 业务行为主证据：待补
- 当前结论：已在 test 环境确认 `sample.restrict_days` 和 `sample.restrict_enabled` 配置键可读，且 `sample.restrict_days` 可通过 API 安全改写并恢复；寄样规则行为仍待补
- 是否已完成：部分完成
- 缺口：缺 7 天限制命中、管理员 / 渠道组长豁免、配置改后业务生效样本；当前代码已确认 `sample.restrict_days`、`sample.restrict_enabled` 生效点，豁免角色为 `ADMIN` 与 `CHANNEL_LEADER`，`BIZ_LEADER` 不豁免
- 当前接口事实：
  - `POST /samples` 消费 `sample.restrict_enabled` 与 `sample.restrict_days`
  - 普通渠道按同渠道 + 同达人 + 同商品 + 限制天数内重复申请拦截
  - `REJECTED` 不计入重复限制
  - 当前准确豁免角色是 `admin` 与 `channel_leader`
- 后续动作：补普通渠道限制命中、管理员/渠道组长豁免、招商组长不豁免、拒绝单不计入限制、改配置前后对照样本

### EVID-P0-8-02：`E2E-SAMPLE-01` 普通渠道 7 天重复限制 API 基线

- 对应验收项：P0-8
- 来源任务：TASK-CONFIRM-04、TASK-CONFIRM-06
- 环境：test
- 证据类型：API E2E 辅助证据
- 证据路径：
  - `runtime/qa/out/e2e-sample-20260519-092948/`
- 当前结论：已在 test 环境读取 `sample.restrict_enabled=true`、`sample.restrict_days=7`，并确认普通渠道同渠道 + 同达人 + 同商品重复申请被业务拦截
- 是否已完成：部分完成
- 覆盖结果：
  - 首次 `POST /api/samples`：PASS，HTTP 200 / 业务码 200，创建 `PENDING_AUDIT` 寄样单
  - 第二次相同 payload `POST /api/samples`：PASS（负向规则断言），HTTP 200 / 业务码 460，`msg=Duplicate sample request is blocked within 7 days`
  - 本条明确没有把 HTTP 200 / 业务码 460 写成普通接口成功，而是作为“重复限制命中”的负向断言通过
- 缺口：管理员 / 渠道组长豁免、拒绝单不计入限制、关闭限制或修改天数后的业务前后对照仍待补
- 后续动作：在可控 test 或 real-pre 辅助环境补豁免、拒绝单和配置改前改后的组合样本

## 十二、P0-9 数据范围证据

### EVID-P0-9-01：全角色账号数据范围基础证据

- 对应验收项：P0-9
- 来源任务：TASK-CONFIRM-01
- 环境：real-pre
- 证据类型：角色流程 / 越权拦截
- 证据路径：
  - `runtime/qa/out/real-pre-role-business-e2e-20260517-101101`
- 当前结论：六类账号菜单权限、越权拦截、基础数据范围已有阶段性 real-pre 证据；接口事实已补充到 [09-03-MCP用户权限与数据范围接口梳理](./09-03-MCP用户权限与数据范围接口梳理.md)，配置域复核补充到 [09-04-MCP配置规则接口梳理](./09-04-MCP配置规则接口梳理.md)，达人公海/私海与数据范围事实补充到 [09-06-MCP达人接口梳理](./09-06-MCP达人接口梳理.md)；`E2E-TALENT-01` 已在单账号 test 环境观察 `poolStatus/ownerId/activeClaimCount`，`E2E-RBAC-01` 已在 test 环境补多账号登录 / 菜单 / 业务列表 / Dashboard API 基线，但不计为 P0-9 通过
- 是否已完成：部分完成
- 缺口：U-09 特殊口径还未闭证；达人侧还缺 channel_staff / channel_leader / admin 对公海、私海、详情的多账号样本；前端用户管理表单已支持多角色选择但缺专项截图 / 回读证据；角色管理表单缺 `group` 配置入口；`ops_staff` 已收口为登录保持 configured、寄样发货场景角色放行，但仍需补登录回包与业务接口数据范围对表；`E2E-RBAC-01` 进一步暴露非管理员菜单树为 0、bizStaff / ops / bizLeader 部分业务接口返回 HTTP 200 但业务码失败
- 后续动作：补一人多角色专项样本，并记录用户、角色、菜单、数据范围四视角证据；达人侧补 `GET /talents?view=TEAM_PUBLIC/MY_TALENTS/TEAM_PRIVATE` 与 `GET /talents/{id}` 的多账号断言；菜单授权 / 前端可见性和业务接口权限口径需单独复核

### EVID-P0-9-02：可视化全业务剧本中的角色边界

- 对应验收项：P0-9
- 来源任务：TASK-CONFIRM-01
- 环境：real-pre
- 证据类型：浏览器剧本 / 多角色串行流程
- 证据路径：
  - `runtime/qa/out/real-pre-full-business-journey-20260517-151152`
- 当前结论：多角色按顺序完成业务流转，基础边界已有证据
- 是否已完成：部分完成
- 缺口：还不是 U-09 一人多角色专项证据
- 后续动作：补 U-09 专项

### EVID-P0-9-03：U-09 一人多角色专项样本

- 对应验收项：P0-9
- 来源任务：TASK-CONFIRM-01
- 环境：real-pre
- 证据类型：待补专项证据
- 证据路径：
  - 待补
- 当前结论：待补
- 是否已完成：否
- 缺口：缺“权限并集、数据范围取最宽”的明确样本；缺前端多角色配置入口和 `group` / 运营口径确认记录
- 后续动作：补用户、角色、页面、接口四视角专项记录；必要时结合数据库记录 `sys_user_role`、`sys_role.data_scope`、订单 / Dashboard 数据范围对表

## 十三、缺口清单

1. 缺真实 `pick_source` 渠道归因成功样本
2. 缺默认招商归因专项对表
3. 缺 `completed_by_order` 样本
4. 缺寄样限制与豁免样本
5. 缺配置改前改后规则生效样本
6. 缺达人保护期改前改后、出单后保护期变化和地址带入专项样本
7. 缺 U-09 一人多角色专项样本

## 十四、补证顺序

1. **先补 P0-4**
   - 真实 `pick_source` 样本是当前最核心缺口
2. **再补 P0-5 / P0-6**
   - 默认招商归因
   - 寄样 `completed_by_order`
3. **再补 P0-8 / P0-3**
   - 规则限制、豁免、配置改前改后对照
4. **最后补 P0-9 专项**
   - 重点是 U-09 一人多角色

## 十五、辅助材料（非 real-pre 主证据）

### AUX-API-01：第一批 E2E 场景与脚本规划

- 关联验收项：
  - `P0-2` 活动 / 商品 / 审核 / 商品库接口
  - `P0-3` 登录 / 菜单 / 角色 / 负责人候选接口
  - `P0-4` 转链 / 订单同步 / 未归因排查接口
  - `P0-7` Dashboard / 数据页指标接口
  - `P0-8` 寄样限制 / 豁免 / 配置改前改后接口
  - `P0-9` 权限入口与数据范围相关接口
  - `09-03` 用户权限与数据范围接口事实表
  - `09-04` 配置规则接口事实表
  - `09-05` 寄样接口事实表与 `completed_by_order` 取证路径
  - `09-06` 达人接口事实表、保护期与地址边界、出单后保护期取证路径
- 材料路径：
  - `docs/09-07-MCP接口梳理总收口与E2E测试计划.md`
  - `docs/08-V1需求接口测试矩阵.md`
  - `runtime/qa/e2e/README.md`
- 当前结论：
  - 已形成基于 live OAS 与现有脚本资产的第一批 E2E 规划底稿
  - 配置规则接口事实已补充到 `09-04`，且已追加 `E2E-CONFIG-01` 的 `configs` 读写恢复脚本；`samples` / `talents` / `dashboard/metrics` 业务前后对照仍待补
  - 寄样接口事实已补充到 `09-05`，可追加 `samples` / `orders/{orderId}` / `status-logs` 脚本
  - 达人接口事实已补充到 `09-06`，可追加 `talents` / `talents/{id}/claims` / `talents/{id}/release` / `talents/pools/*` 脚本
  - 可直接作为后续 test / real-pre 补证剧本
  - 不计入 real-pre 已通过证据
- 当前缺口：
  - `POST /colonel/activities/{activityId}/sync`
  - `POST /colonel/activities/{activityId}/products/sync`
- 后续动作：
  - 按 `09-07` 规划把脚本逐步收口到 `runtime/qa/e2e/**`
  - 保持两个未确认 `sync` 调用待核
  - 在规划底稿基础上继续补更多实跑证据

### AUX-API-02：`E2E-BASE-01` 基础 API smoke 实跑

- 关联验收项：
  - `P0-3` 登录 / 菜单 / 权限脚本执行准入
  - `P0-7` Dashboard Summary 基础连通性
  - `P0-9` 管理员上下文菜单树读取链路
- 运行时间：
  - 2026-05-19
- 材料路径：
  - `runtime/qa/e2e/specs/smoke/base-smoke.spec.ts`
  - `runtime/qa/e2e/runners/run-base-smoke.ts`
  - `runtime/qa/out/e2e-base-smoke-20260519-083947/report.md`
  - `runtime/qa/out/e2e-base-smoke-20260519-083947/summary.json`
  - `runtime/qa/out/e2e-base-smoke-20260519-083947/api-responses/01-health-check.json`
  - `runtime/qa/out/e2e-base-smoke-20260519-083947/api-responses/02-admin-login.json`
  - `runtime/qa/out/e2e-base-smoke-20260519-083947/api-responses/03-menus-tree.json`
  - `runtime/qa/out/e2e-base-smoke-20260519-083947/api-responses/04-dashboard-summary.json`
- 当前结论：
  - `GET /api/actuator/health`：PASS，HTTP 200，`status=UP`
  - `POST /api/auth/login`：PASS，HTTP 200，已返回 `token / roleCodes / dataScope`
  - `GET /api/menus/tree`：PASS，HTTP 200，已返回管理员菜单树
  - `GET /api/dashboard/summary`：PASS，HTTP 200，已返回汇总对象
  - 当前可确认 test 环境下后端连通性、管理员登录、菜单上下文与 Dashboard summary 基础读取链路可用
  - 不计入 real-pre 已通过主证据，不能替代 P0-1 ~ P0-9 的专项闭证
- 后续动作：
  - 继续在同一套 `runtime/qa/e2e/**` 目录下补角色、多账号、配置规则与业务闭环专项脚本
  - 后续若补 real-pre 版本实跑，需单独入 P0 主证据目录，不与本条辅助材料混写

### AUX-API-03：`E2E-PRODUCT-01` 商品主链 API 实跑

- 关联验收项：
  - `P0-2` 商品主链读取口径辅助验证
  - `P0-4` `pick_source` 转链前半段辅助验证
- 运行时间：
  - 2026-05-19
- 材料路径：
  - `runtime/qa/e2e/specs/domains/product-main-chain.spec.ts`
  - `runtime/qa/e2e/runners/run-product-main-chain.ts`
  - `runtime/qa/out/e2e-product-20260519-083634/report.md`
  - `runtime/qa/out/e2e-product-20260519-083634/summary.json`
  - `runtime/qa/out/e2e-product-20260519-083634/api-responses/01-admin-login.json`
  - `runtime/qa/out/e2e-product-20260519-083634/api-responses/02-get-colonel-activities.json`
  - `runtime/qa/out/e2e-product-20260519-083634/api-responses/03-get-activity-products.json`
  - `runtime/qa/out/e2e-product-20260519-083634/api-responses/04-get-activity-product-detail.json`
  - `runtime/qa/out/e2e-product-20260519-083634/api-responses/05-create-promotion-link.json`
- 当前结论：
  - `POST /api/auth/login`：PASS，HTTP 200，管理员上下文可用于商品主链只读接口
  - `GET /api/colonel/activities`：PASS，HTTP 200，已返回可用 `activityId`
  - `GET /api/colonel/activities/{activityId}/products`：PASS，HTTP 200，已返回可转链商品样本
  - `GET /api/colonel/activities/{activityId}/products/{productId}`：PASS，HTTP 200，商品详情聚合读取正常
  - `POST /api/colonel/activities/{activityId}/products/{productId}/promotion-links`：PASS，HTTP 200，已返回 `pickSource / shortLink / promoteLink`
  - 当前只能确认 **test 环境商品主链 API 与转链前半段通过**，不能据此写成 `pick_source` 真实归因闭环已通过
  - 不计入 real-pre 已通过主证据
- 后续动作：
  - 继续在同一套 `runtime/qa/e2e/**` 目录下补商品审核、商品入库、订单归因与 Dashboard 对表脚本
  - 待真实订单样本到位后，把 `pick_source_mapping`、订单详情、Dashboard 与数据库证据合并进 P0-4 主闭证

### AUX-API-04：`E2E-ORDER-01` 订单 / 归因 / Dashboard API 实跑

- 关联验收项：
  - `P0-4` `pick_source` 渠道归因接口基线辅助验证
  - `P0-5` 默认招商归因订单 / Dashboard 读接口辅助验证
  - `P0-7` Dashboard summary / metrics 基础读取辅助验证
- 运行时间：
  - 2026-05-19
- 材料路径：
  - `runtime/qa/e2e/specs/domains/order-attribution-dashboard.spec.ts`
  - `runtime/qa/e2e/runners/run-order-attribution-dashboard.ts`
  - `runtime/qa/out/e2e-order-20260519-085255/report.md`
  - `runtime/qa/out/e2e-order-20260519-085255/summary.json`
  - `runtime/qa/out/e2e-order-20260519-085255/api-responses/01-admin-login.json`
  - `runtime/qa/out/e2e-order-20260519-085255/api-responses/02-orders-list.json`
  - `runtime/qa/out/e2e-order-20260519-085255/api-responses/03-order-detail.json`
  - `runtime/qa/out/e2e-order-20260519-085255/api-responses/04-orders-unattributed.json`
  - `runtime/qa/out/e2e-order-20260519-085255/api-responses/05-dashboard-summary.json`
  - `runtime/qa/out/e2e-order-20260519-085255/api-responses/06-dashboard-metrics.json`
  - `runtime/qa/out/e2e-order-20260519-085255/api-responses/07-dashboard-order-attribution-summary.json`
- 当前结论：
  - `POST /api/auth/login`：PASS，HTTP 200，管理员 token 可提取
  - `GET /api/orders`：PASS，HTTP 200，业务码 200，已返回订单列表样本
  - `GET /api/orders/{orderId}`：PASS，HTTP 200，业务码 200，订单详情与归因字段可读
  - `GET /api/orders/unattributed`：PASS，HTTP 200，业务码 200，未归因排查列表结构可读
  - `GET /api/dashboard/summary`：PASS，HTTP 200，业务码 200，汇总对象结构可读，本轮耗时 29ms
  - `GET /api/dashboard/metrics?timeField=createTime`：PASS，HTTP 200，业务码 200，metrics 对象结构可读
  - `GET /api/dashboard/order-attribution-summary`：接口 HTTP 200 / 业务码 200，但因本轮为 test 环境且无真实 `pick_source` 订单样本，标记 `WAITING_REAL_PICK_SOURCE_SAMPLE`
  - 当前只能确认 **test 环境订单 / 归因 / Dashboard API 基线可读**，不能据此写成 `pick_source` 真实归因闭环已通过
  - 不计入 real-pre 已通过主证据
- 后续动作：
  - 等真实 `pick_source` 订单样本到位后，复跑同一脚本或 real-pre 专项脚本，并把订单详情、归因摘要、Dashboard 与 DB/日志证据合并进 P0-4 主闭证
  - P0-7 正式手算辅助证据已补到 `runtime/qa/out/p0-7-commission-manual-check-20260519-135831/`；当前仍不能只凭 test 辅助证据关闭 real-pre 真实样本缺口

### AUX-API-05：`E2E-CONFIG-01` 配置规则 API 实跑

- 关联验收项：
  - `P0-3` 管理链规则配置读写辅助验证
  - `P0-7` 招商 / 渠道默认提成比例配置键读取辅助验证
  - `P0-8` 寄样限制配置改写 / 恢复辅助验证
- 运行时间：
  - 2026-05-19
- 材料路径：
  - `runtime/qa/e2e/specs/domains/config-rules.spec.ts`
  - `runtime/qa/e2e/runners/run-config-rules.ts`
  - `runtime/qa/out/e2e-config-20260519-090141/report.md`
  - `runtime/qa/out/e2e-config-20260519-090141/summary.json`
  - `runtime/qa/out/e2e-config-20260519-090141/api-responses/01-admin-login.json`
  - `runtime/qa/out/e2e-config-20260519-090141/api-responses/02-configs-list.json`
  - `runtime/qa/out/e2e-config-20260519-090141/api-responses/03-configs-grouped.json`
  - `runtime/qa/out/e2e-config-20260519-090141/api-responses/04-key-config-validation.json`
  - `runtime/qa/out/e2e-config-20260519-090141/api-responses/05-read-sample-restrict-days-original.json`
  - `runtime/qa/out/e2e-config-20260519-090141/api-responses/06-update-sample-restrict-days-temp.json`
  - `runtime/qa/out/e2e-config-20260519-090141/api-responses/07-read-sample-restrict-days-temp.json`
  - `runtime/qa/out/e2e-config-20260519-090141/api-responses/08-restore-sample-restrict-days-original.json`
  - `runtime/qa/out/e2e-config-20260519-090141/api-responses/09-read-sample-restrict-days-restored.json`
- 当前结论：
  - `POST /api/auth/login`：PASS，HTTP 200，业务码 200，管理员 token 可提取
  - `GET /api/configs`：PASS，HTTP 200，业务码 200，配置列表结构可读
  - `GET /api/configs/grouped`：PASS，HTTP 200，业务码 200，分组配置结构可读
  - 关键配置存在性：PASS，`sample.restrict_days`、`sample.restrict_enabled`、`talent.protection_days`、`commission.business_default_ratio`、`commission.channel_default_ratio` 均存在于列表与分组配置中
  - `GET /api/configs/{id}` / `PUT /api/configs/{id}`：PASS，HTTP 200，业务码 200，`sample.restrict_days` 已从 `7` 临时改为 `1`，再恢复为 `7`
  - 当前只能确认 **test 环境配置 API 可读写且可恢复**，不能据此单独写成 P0-8 业务规则生效、P0-7 完整闭证或 P0-3 管理链规则全闭证
  - 不计入 real-pre 已通过主证据
- 后续动作：
  - 在同一套 `runtime/qa/e2e/**` 下继续补寄样重复申请、管理员 / 渠道组长豁免和达人保护期认领专项脚本；P0-7 正式手算辅助证据已另行落档
  - 若后续 real-pre 环境允许安全配置改写，需单独生成 real-pre 主证据目录并记录恢复结果

### AUX-API-06：`E2E-TALENT-01` 达人主链 API 实跑

- 关联验收项：
  - `P0-1` 达人 CRUD / 认领 / 释放主链辅助验证
  - `P0-3` 达人保护期配置消费辅助验证
  - `P0-9` 达人公海 / 私海字段观察
- 运行时间：
  - 2026-05-19
- 材料路径：
  - `runtime/qa/e2e/specs/domains/talent-main-chain.spec.ts`
  - `runtime/qa/e2e/runners/run-talent-main-chain.ts`
  - `runtime/qa/out/e2e-talent-20260519-091458/report.md`
  - `runtime/qa/out/e2e-talent-20260519-091458/summary.json`
  - `runtime/qa/out/e2e-talent-20260519-091458/api-responses/01-admin-login.json`
  - `runtime/qa/out/e2e-talent-20260519-091458/api-responses/02-configs-grouped.json`
  - `runtime/qa/out/e2e-talent-20260519-091458/api-responses/03-talents-list.json`
  - `runtime/qa/out/e2e-talent-20260519-091458/api-responses/04-create-talent.json`
  - `runtime/qa/out/e2e-talent-20260519-091458/api-responses/05-talent-detail-created.json`
  - `runtime/qa/out/e2e-talent-20260519-091458/api-responses/06-update-talent.json`
  - `runtime/qa/out/e2e-talent-20260519-091458/api-responses/07-claim-talent.json`
  - `runtime/qa/out/e2e-talent-20260519-091458/api-responses/08-talent-detail-after-claim.json`
  - `runtime/qa/out/e2e-talent-20260519-091458/api-responses/09-release-talent.json`
  - `runtime/qa/out/e2e-talent-20260519-091458/api-responses/10-cleanup-delete-talent.json`
- 当前结论：
  - `POST /api/auth/login`：PASS，HTTP 200，业务码 200，管理员 token 可提取
  - `GET /api/configs/grouped`：PASS，HTTP 200，业务码 200，`talent.protection_days=30` 可读
  - `GET /api/talents`：PASS，HTTP 200，业务码 200，分页结构可读
  - `POST /api/talents` / `GET /api/talents/{id}` / `PUT /api/talents/{id}`：PASS，达人 ID 与 `douyinUid` 可读，基础信息可更新
  - `POST /api/talents/{id}/claims` 后再次详情：PASS，`protectedUntil`、`ownerId/activeClaimOwners`、`activeClaimCount` 可读，保护期与配置天数关系合理
  - `POST /api/talents/{id}/release`：PASS，认领可通过 API 释放
  - `DELETE /api/talents/{id}`：SKIP（`TALENT_CLEANUP_NOT_SUPPORTED`），HTTP 200 但业务码 500，未写成 PASS，也未绕过 API 直接改库
  - 当前只能确认 **test 环境达人 CRUD / 认领 / 保护期 / 释放 API 基线可用**，不能据此写成 P0-1 全链路、P0-3 规则改前改后或 P0-9 多账号公私海已通过
- 后续动作：
  - 补 channel_staff / channel_leader / admin 多账号公海、私海、详情权限专项
  - 补达人保护期改配置前后、出单后保护期变化和地址带入专项样本
  - 决定已认领测试达人是否需要完整物理删除 / 软删除清理接口，当前按 `TALENT_CLEANUP_NOT_SUPPORTED` 跟踪

### AUX-API-07：`E2E-SAMPLE-01` 寄样主链 API 实跑

- 关联验收项：
  - `P0-1` 渠道链寄样申请与发货前半段辅助验证
  - `P0-2` 寄样审核状态流转辅助验证
  - `P0-6` 寄样推进到待交作业辅助验证
  - `P0-8` 普通渠道 7 天重复申请限制辅助验证
- 运行时间：
  - 2026-05-19
- 材料路径：
  - `runtime/qa/e2e/specs/domains/sample-main-chain.spec.ts`
  - `runtime/qa/e2e/runners/run-sample-main-chain.ts`
  - `runtime/qa/out/e2e-sample-20260519-092948/report.md`
  - `runtime/qa/out/e2e-sample-20260519-092948/summary.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/01-admin-login.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/02-channel-staff-login.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/03-biz-staff-login.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/04-ops-login.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/05-configs-grouped.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/06-sample-talent-candidates.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/07-create-test-talent.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/08-claim-test-talent.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/09-sample-product-candidates.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/10-sample-eligibility-check.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/11-create-sample.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/12-sample-detail-pending-audit.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/13-duplicate-sample-restrict.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/14-approve-sample.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/15-ship-sample.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/16-sign-sample-pending-task.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/17-sample-detail-final.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/18-samples-list-by-request-no.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/19-sample-status-logs.json`
  - `runtime/qa/out/e2e-sample-20260519-092948/api-responses/20-completed-by-order-observation.json`
- 当前结论：
  - `POST /api/auth/login`：PASS，管理员、渠道专员、招商专员、运营账号均返回 HTTP 200 / 业务码 200 和 token
  - `GET /api/configs/grouped`：PASS，`sample.restrict_enabled=true`、`sample.restrict_days=7` 可读
  - `GET /api/samples/talent-candidates`：PASS，寄样达人候选接口可读；本轮为避免既有 7 天限制污染，随后创建唯一测试达人并认领
  - `POST /api/talents` / `POST /api/talents/{id}/claims`：PASS，测试达人创建与认领可用
  - `GET /api/samples/product-candidates`：PASS，返回可用于寄样的商品库商品主键
  - `POST /api/samples/eligibility-check`：PASS，资格预检 HTTP / 业务码通过
  - 首次 `POST /api/samples`：PASS，创建寄样申请并进入 `PENDING_AUDIT`
  - 第二次相同 `POST /api/samples`：PASS（负向规则断言），HTTP 200 / 业务码 460，确认 7 天重复限制命中
  - `PUT /api/samples/{id}/status`：PASS，管理员上下文完成 `APPROVED -> SHIPPED -> SIGNED`，状态依次为 `PENDING_SHIP / SHIPPED / PENDING_TASK`
  - `GET /api/samples/{id}` / `GET /api/samples` / `GET /api/samples/{id}/status-logs`：PASS，最终详情、列表和状态日志均确认停在 `PENDING_TASK`
  - `completed_by_order`：SKIP（`WAITING_ORDER_SAMPLE_FOR_COMPLETED_BY_ORDER`），未写成已通过
  - S-09 / S-10：本轮只验证手动物流主链，自动物流刷新和 Excel 批量导入不进入 P0 通过结论
- 后续动作：
  - 补真实或稳定订单样本触发 `completed_by_order`
  - 补管理员 / 渠道组长豁免、拒绝单不计限制、配置改前改后业务对照
  - 如需验证招商专员 / 运营专员跨数据范围动作，应走单独角色权限专项，不用本条 API 基线替代

### AUX-API-08：`E2E-RBAC-01` 角色权限与数据范围 API / 菜单基线实跑

- 关联验收项：
  - `P0-3` 管理链角色 / 菜单 / 权限基础验证
  - `P0-9` 数据范围与一人多角色专项辅助验证
  - `TASK-CONFIRM-01` U-09 一人多角色项目口径
  - `TASK-CONFIRM-09` RBAC/API 菜单基线缺口
  - `TASK-RBAC-01` bizStaff 查询寄样列表返回业务 500（本轮已修复）
- 运行时间：
  - 2026-05-19
- 材料路径：
  - `runtime/qa/e2e/specs/domains/rbac-scope.spec.ts`
  - `runtime/qa/e2e/runners/run-rbac-scope.ts`
  - `runtime/qa/out/e2e-rbac-20260519-133713/report.md`
  - `runtime/qa/out/e2e-rbac-20260519-133713/summary.json`
  - `runtime/qa/out/e2e-rbac-20260519-133713/api-responses/01-admin-login.json`
  - `runtime/qa/out/e2e-rbac-20260519-133713/api-responses/02-admin-menus-tree.json`
  - `runtime/qa/out/e2e-rbac-20260519-133713/api-responses/07-bizstaff-login.json`
  - `runtime/qa/out/e2e-rbac-20260519-133713/api-responses/09-bizstaff-talents-list.json`
  - `runtime/qa/out/e2e-rbac-20260519-133713/api-responses/10-bizstaff-samples-list.json`
  - `runtime/qa/out/e2e-rbac-20260519-133713/api-responses/19-ops-login.json`
  - `runtime/qa/out/e2e-rbac-20260519-133713/api-responses/21-ops-talents-list.json`
  - `runtime/qa/out/e2e-rbac-20260519-133713/api-responses/23-ops-orders-list.json`
  - `runtime/qa/out/e2e-rbac-20260519-133713/api-responses/24-ops-dashboard-summary.json`
  - `runtime/qa/out/e2e-rbac-20260519-133713/api-responses/25-bizleader-login.json`
  - `runtime/qa/out/e2e-rbac-20260519-133713/api-responses/27-bizleader-talents-list.json`
  - `runtime/qa/out/e2e-rbac-20260519-133713/api-responses/31-channelleader-login.json`
  - `runtime/qa/out/e2e-rbac-20260519-133713/api-responses/37-data-scope-baseline-assertion.json`
  - `runtime/qa/out/e2e-rbac-20260519-133713/api-responses/38-ops-staff-configured-conflict.json`
  - `runtime/qa/out/e2e-rbac-20260519-133713/api-responses/39-u09-multi-role-observation.json`
- 当前结论：
  - `POST /api/auth/login`：admin、bizStaff、channelStaff、ops、bizLeader、channelLeader 均返回 HTTP 200 / 业务码 200 和 token；登录回包记录 `userId / roleCodes / dataScope`
  - `GET /api/menus/tree`：所有账号 HTTP 200 / 业务码 200；admin 菜单数 12，bizStaff / channelStaff / ops / bizLeader / channelLeader 均为 0；本轮按 `OBSERVE` 记录，不能写成菜单权限已通过
  - dataScope：admin=all、bizStaff/channelStaff=self、bizLeader/channelLeader=group 的登录基线通过；但这只是登录回包证据，不替代业务数据范围对表
  - `GET /api/talents`：admin、channelStaff、channelLeader 通过；bizStaff、ops、bizLeader 的越权访问已按 `DENY` 口径记为 `PASS_DENIED_AS_EXPECTED`
  - `GET /api/samples`：admin、bizStaff、channelStaff、ops、bizLeader、channelLeader 全部通过；其中 bizStaff 已恢复为 HTTP 200 / 业务码 200，样本数 1
  - `GET /api/orders`：admin、bizStaff、channelStaff、bizLeader、channelLeader 通过；ops 的越权访问已按 `DENY` 口径记为 `PASS_DENIED_AS_EXPECTED`
  - `GET /api/dashboard/summary`：admin、bizStaff、channelStaff、bizLeader、channelLeader 通过；ops 的越权访问已按 `DENY` 口径记为 `PASS_DENIED_AS_EXPECTED`
  - `ops_staff`：历史 run 为 SKIP（`OPS_STAFF_CONFIGURED_ALL_CONFLICT`）；代码口径已调整为登录保持 configured，需用新 run 重新补证
  - U-09：SKIP（`RBAC_MULTI_ROLE_NEEDS_DEDICATED_SAMPLE`），本轮未安全操作专用测试用户，不能写成多角色已通过
  - 当前只能确认 **test 环境 RBAC/API 菜单和 dataScope 基线已实跑，且真实 FAIL 已清零**，不能据此写成 P0-9 通过
- 后续动作：
  - 补 U-09 专用多角色账号样本，确认“权限并集、数据范围取最宽”
  - 复核非管理员菜单授权与前端菜单可见性，避免只凭 `/menus/tree` HTTP 成功写通过
  - 保留 `TASK-RBAC-01` 修复结论，后续若 `bizStaff /api/samples` 再回归 500，优先复核联表查询条件别名
  - 补 `ops_staff` configured 新口径回归：登录 dataScope、寄样发货台全量能力、订单 / Dashboard / 达人越权拒绝

### AUX-API-09：`E2E-P0-REGRESSION` P0 总回归汇总

- 关联验收项：
  - `P0-1`
  - `P0-2`
  - `P0-3`
  - `P0-4`
  - `P0-5`
  - `P0-6`
  - `P0-7`
  - `P0-8`
  - `P0-9`
- 环境：
  - `test`（汇总已有单项 E2E 证据，不新增业务测试）
- 材料路径：
  - `runtime/qa/out/e2e-p0-regression-20260519-134453/report.md`
  - `runtime/qa/out/e2e-p0-regression-20260519-134453/summary.json`
  - `runtime/qa/out/e2e-p0-regression-20260519-134453/p0-result-matrix.md`
  - `runtime/qa/out/e2e-p0-regression-20260519-134453/evidence-index.md`
  - `runtime/qa/out/e2e-p0-regression-20260519-134453/gaps.md`
- 当前结论：
  - 7 条最新单项 run 已统一收口，合计 `89 PASS / 0 FAIL / 5 SKIP`
  - `TASK-RBAC-01` 已可按 `E2E-RBAC-01` 最新 run 标记为已修复
  - `P0-1`、`P0-2`、`P0-3`、`P0-8` 只能写“已有辅助证据”
  - `P0-4`、`P0-5`、`P0-6`、`P0-9` 仍必须写成“部分通过 / 待样本 / 待确认”；`P0-7` 已从“待手算”推进到“部分通过”
- 后续动作：
  - 统一以该目录作为当前 P0 总回归入口
  - 继续按 `gaps.md` 补真实 `pick_source`、`completed_by_order`、默认招商归因和 P0-9 专项样本；P0-7 正式手算辅助证据已落档

### AUX-REALPRE-01：real-pre final regression（只读 / 低风险）

- 关联验收项：
  - `P0-2` 商品主链读取 / 转链可用性复核
  - `P0-4` `pick_source` 渠道归因 real-pre 基线复核
  - `P0-5` 默认招商归因待补状态复核
  - `P0-7` 汇总对账与只读手算重跑可行性复核
- 运行时间：
  - 2026-05-19
- 材料路径：
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/report.md`
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/summary.json`
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/p0-real-pre-matrix.md`
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/evidence-index.md`
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/gaps.md`
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/environment-check.md`
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/e2e-base-01/report.md`
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/e2e-base-01/summary.json`
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/e2e-product-01/report.md`
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/e2e-product-01/summary.json`
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/e2e-product-01/api-responses/05-create-promotion-link.json`
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/e2e-order-01/report.md`
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/e2e-order-01/summary.json`
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/e2e-order-01/api-responses/07-dashboard-order-attribution-summary.json`
  - `runtime/qa/out/real-pre-final-regression-20260519-145309/p0-7-readonly-attempt.md`
- 当前结论：
  - real-pre 环境可用：`activeProfiles=real-pre`、`APP_TEST_ENABLED=false`、`DOUYIN_TEST_ENABLED=false`
  - `E2E-BASE-01`：4/4 PASS，登录 / 菜单 / Dashboard summary 基线可用
  - `E2E-PRODUCT-01`：4 PASS / 1 FAIL；活动列表、商品列表、商品详情可读，但当前样本 `POST /api/colonel/activities/3920684/products/3817382656845414423/promotion-links` 返回 `code=460` / 抖店 `40003 授权主体不匹配`，本轮不能写成 real-pre 转链可用通过
  - `E2E-ORDER-01`：6 PASS / 1 SKIP（`WAITING_REAL_PICK_SOURCE_SAMPLE`）；订单列表、订单详情、未归因排查、Dashboard summary / metrics 可读，`GET /api/dashboard/order-attribution-summary` 返回 `orderCount=0 / attributedOrderCount=0`
  - `P0-7` 只读重跑：`FAILED_NO_ATTRIBUTED_SAMPLE`；当前 real-pre 无可用 attributed order sample，继续沿用 `real-pre-dashboard-reconcile-20260517-122911` 与 `p0-7-commission-manual-check-20260519-135831`
  - 本轮不把 `P0-4`、`P0-5`、`P0-6`、`P0-9` 写成通过；`P0-7` 继续保持“部分通过”
- 专项补充：
  - `REALPRE-PROMOTION-LINK-TRIAGE-01` 已补到 `runtime/qa/out/real-pre-promotion-link-triage-20260519-152304/`
  - 该专项确认整体分类为 `MIXED_SUBJECT_SAMPLES`，不是 `REALPRE_PROMOTION_AUTH_GLOBAL_BLOCKER`
  - 目标失败样本复现 `40003`，但同活动对照样本失败于“未加入商品库”，历史 `3916506` 已入库样本仍可成功转链
  - `REALPRE-PROMOTION-LINK-BASELINE-01` 已补到 `runtime/qa/out/real-pre-promotion-link-baseline-20260519-154254/`
  - 该专项已固定 2 个 real-pre 可转链基准样本：`3916506 / 3810562766247428542`（primary）、`3916506 / 3817426948628545700`（backup）
  - `3920684 / 3817382656845414423` 继续只作为异常样本记录，不作为全局 blocker
  - `E2E-PRODUCT-01` baseline 重跑已补到 `runtime/qa/out/e2e-product-real-pre-baseline-20260519-155237/`
  - 该重跑确认 fixed baseline `3916506 / 3810562766247428542` 下商品主链 6/6 PASS，但结论仍只停留在“固定 baseline 转链前半段通过”
- 后续动作：
  - 对 `P0-4`：继续等待真实 `pick_source` 样本，不把当前 `WAITING_REAL_PICK_SOURCE_SAMPLE` 改写成通过
  - 对 `P0-5`：继续补默认招商归因专项对表
  - 对 `P0-6`：继续补 `completed_by_order` 样本
  - 对商品主链：继续围绕 `3920684 / 3817382656845414423` 做样本级主体 / 字段复核，并在不改业务代码的前提下补“同主体、已入库、detailUrl 完整”的可转链样本后重跑 `E2E-PRODUCT-01`
