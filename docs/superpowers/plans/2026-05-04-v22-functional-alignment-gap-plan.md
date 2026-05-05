# V2.2 功能对齐补全计划 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不推翻当前 Spring Boot / Vue 主链路的前提下，把现有系统功能口径尽量对齐到《抖音团长 SaaS 系统 - 完整需求方案文档 V2.2》。

**Architecture:** 以当前稳定主链路为底座，优先补齐“部分实现”与“明确未实现”的功能，而不是按旧文档重做现有模块。先统一商品中心、达人 CRM、寄样规则、独家机制、数据平台和系统规则中心的业务口径，再按测试环境、浏览器回归和文档同步收口。

**Tech Stack:** Vue 3、Vite、Naive UI、Spring Boot、MyBatis-Plus、PostgreSQL、Redis、JUnit 5、MockMvc、Docker Compose、本地可见浏览器回归。

---

## File Map

### Product Center

- Modify: `frontend/src/views/product/index.vue`
- Modify: `frontend/src/views/product/ProductDetail.vue`
- Modify: `frontend/src/views/product/components/ProductCard.vue`
- Modify: `frontend/src/views/product/components/ProductFilters.vue`
- Modify: `frontend/src/api/activityProduct.ts`
- Modify: `frontend/src/api/product.ts`
- Modify: `backend/src/main/java/com/colonel/saas/service/ProductService.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/ColonelActivityProductController.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/ProductController.java`
- Modify: `backend/src/test/java/com/colonel/saas/service/ProductServiceTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/controller/ColonelActivityProductControllerTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/controller/ProductControllerTest.java`

### Talent CRM

- Modify: `frontend/src/views/talent/index.vue`
- Modify: `frontend/src/views/talent/components/TalentDetailModal.vue`
- Modify: `frontend/src/api/talent.ts`
- Modify: `backend/src/main/java/com/colonel/saas/controller/TalentController.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/TalentService.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/TalentQueryService.java`
- Modify: `backend/src/main/java/com/colonel/saas/entity/Talent.java`
- Modify: `backend/src/test/java/com/colonel/saas/controller/TalentControllerTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/service/TalentServiceTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java`

### Sample Desk

- Modify: `frontend/src/views/sample/index.vue`
- Modify: `frontend/src/views/sample/Apply.vue`
- Modify: `frontend/src/api/sample.ts`
- Modify: `backend/src/main/java/com/colonel/saas/service/SampleRequestService.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/SampleController.java`
- Modify: `backend/src/test/java/com/colonel/saas/service/SampleRequestServiceTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/controller/SampleControllerTest.java`

### Exclusive Rules / Data Platform

- Modify: `backend/src/main/java/com/colonel/saas/service/DashboardService.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/OrderAttributionService.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/CommissionService.java`
- Modify: `backend/src/main/java/com/colonel/saas/entity/Order.java`
- Create: `backend/src/main/java/com/colonel/saas/entity/ExclusiveTalentRule.java`
- Create: `backend/src/main/java/com/colonel/saas/entity/ExclusiveMerchantRule.java`
- Create: `backend/src/main/java/com/colonel/saas/service/ExclusiveTalentService.java`
- Create: `backend/src/main/java/com/colonel/saas/service/ExclusiveMerchantService.java`
- Modify: `backend/src/test/java/com/colonel/saas/service/DashboardServiceTest.java`
- Create: `backend/src/test/java/com/colonel/saas/service/ExclusiveTalentServiceTest.java`
- Create: `backend/src/test/java/com/colonel/saas/service/ExclusiveMerchantServiceTest.java`

### System Config / Logs / Docs

- Modify: `frontend/src/views/system/RoleList.vue`
- Modify: `frontend/src/views/system/UserList.vue`
- Create: `frontend/src/views/system/RuleConfig.vue`
- Create: `frontend/src/api/systemConfig.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/views/layout/Sider.vue`
- Modify: `backend/src/main/java/com/colonel/saas/controller/SystemConfigController.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/SystemConfigService.java`
- Modify: `backend/src/main/resources/db/init-db.sql`
- Add: `backend/src/main/resources/db/alter-test-existing-volumes-20260504.sql`
- Modify: `scripts/start-test-all.ps1`
- Modify: `scripts/apply-test-db-patches.ps1`
- Modify: `docs/01-业务闭环.md`
- Modify: `docs/04-开发进度.md`
- Modify: `docs/05-接口与数据模型.md`
- Modify: `docs/10-V2.2场景覆盖矩阵.md`

## Task 1: 锁定商品中心最终业务口径

**Files:**
- Modify: `docs/01-业务闭环.md`
- Modify: `docs/10-V2.2场景覆盖矩阵.md`
- Modify: `frontend/src/views/product/index.vue`
- Modify: `backend/src/main/java/com/colonel/saas/service/ProductService.java`

- [ ] **Step 1: 明确“审核通过”和“加入商品库”的关系**

结论必须二选一并写入文档：

- 方案 A：审核通过即自动进入商品库
- 方案 B：审核通过后仍在选品库，需人工“加入商品库”才对全员可见

当前代码已实现方案 B，若保持现状，需同步修正文档，不再保留“审核通过即上架”歧义。

- [ ] **Step 2: 运行定向后端测试**

Run:

```bash
cd backend
mvn "-Dtest=ProductServiceTest,ProductControllerTest,ColonelActivityProductControllerTest" test
```

Expected: PASS

- [ ] **Step 3: 运行商品中心浏览器回归**

Run:

```bash
node .codex-run/product-browser-check-4/result.json
```

Expected: 结果文件中 `activity-entry-action`、`detail-drawer`、`library-search` 均为 `true`

## Task 2: 补齐商品审核补充信息字段

**Files:**
- Modify: `frontend/src/views/product/ProductDetail.vue`
- Modify: `frontend/src/views/product/components/ProductAuditDialog.vue`
- Modify: `backend/src/main/java/com/colonel/saas/service/ProductService.java`
- Modify: `backend/src/main/resources/db/init-db.sql`
- Modify: `backend/src/test/java/com/colonel/saas/service/ProductServiceTest.java`

- [ ] **Step 1: 梳理文档要求字段与现有字段差异**

必须逐项核对以下字段是否具备存储和展示：

- 专属价说明
- 发货信息
- 商品卖点
- 推广话术
- 是否支持投流
- 奖励说明
- 参与要求
- 活动时间
- 手卡素材

- [ ] **Step 2: 为缺失字段补后端存储**

优先复用现有 `JSONB / remark / payload` 承载方式，避免立即拆出过多新表。

- [ ] **Step 3: 为缺失字段补前端审核录入与详情展示**

要求：

- 招商审核页能录入
- 商品详情页能查看
- 空值有降级展示，不白屏

- [ ] **Step 4: 运行商品服务测试**

Run:

```bash
cd backend
mvn "-Dtest=ProductServiceTest" test
```

Expected: PASS

## Task 3: 补齐达人 CRM 深规则

**Files:**
- Modify: `frontend/src/views/talent/index.vue`
- Modify: `frontend/src/api/talent.ts`
- Modify: `backend/src/main/java/com/colonel/saas/service/TalentService.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/TalentQueryService.java`
- Modify: `backend/src/test/java/com/colonel/saas/service/TalentServiceTest.java`
- Modify: `backend/src/test/java/com/colonel/saas/service/TalentQueryServiceTest.java`

- [ ] **Step 1: 固化“保护期无产出自动释放”规则**

至少明确：

- 无产出口径按订单判断，还是订单 + 寄样 + 合作综合判断
- 定时释放任务何时跑
- 释放后公海可见性如何变化

- [ ] **Step 2: 明确是否支持多人同时认领**

如果按文档执行，则要补：

- 同一达人多认领记录
- 联系方式可见范围
- 私海可见规则

如果不按文档执行，则必须回写文档和矩阵。

- [ ] **Step 3: 补手动刷新和周更任务**

要求：

- 前端有刷新入口
- 后端有刷新接口或任务入口
- 刷新失败有可见提示

- [ ] **Step 4: 运行达人模块测试**

Run:

```bash
cd backend
mvn "-Dtest=TalentControllerTest,TalentServiceTest,TalentQueryServiceTest" test
```

Expected: PASS

## Task 4: 补齐寄样限制规则

**Files:**
- Modify: `frontend/src/views/sample/Apply.vue`
- Modify: `backend/src/main/java/com/colonel/saas/service/SampleRequestService.java`
- Modify: `backend/src/main/java/com/colonel/saas/controller/SampleController.java`
- Modify: `backend/src/test/java/com/colonel/saas/service/SampleRequestServiceTest.java`

- [ ] **Step 1: 把“7 天限制”写成系统可配置规则**

至少包括：

- 限制开关
- 限制天数
- 组长 / 管理员豁免
- 拒绝后不受限

- [ ] **Step 2: 申请前校验并返回业务友好提示**

提示文案至少要说明：

- 哪个达人
- 哪个商品
- 剩余限制时间

- [ ] **Step 3: 运行寄样测试**

Run:

```bash
cd backend
mvn "-Dtest=SampleRequestServiceTest,SampleControllerTest" test
```

Expected: PASS

## Task 5: 落地独家达人机制

**Files:**
- Create: `backend/src/main/java/com/colonel/saas/service/ExclusiveTalentService.java`
- Create: `backend/src/test/java/com/colonel/saas/service/ExclusiveTalentServiceTest.java`
- Modify: `backend/src/main/resources/db/init-db.sql`
- Modify: `docs/10-V2.2场景覆盖矩阵.md`

- [ ] **Step 1: 先把规则计算放在服务层，不急着做复杂前端**

判定条件：

- 服务费占比 >= 70%
- 月寄样数量 >= 10

- [ ] **Step 2: 输出“本月判定 / 下月生效”结果**

至少要有：

- 达人 ID
- 渠道 ID
- 生效月份
- 判定来源统计值

- [ ] **Step 3: 用测试锁住规则**

Run:

```bash
cd backend
mvn "-Dtest=ExclusiveTalentServiceTest" test
```

Expected: PASS

## Task 6: 落地独家商家机制

**Files:**
- Create: `backend/src/main/java/com/colonel/saas/service/ExclusiveMerchantService.java`
- Create: `backend/src/test/java/com/colonel/saas/service/ExclusiveMerchantServiceTest.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/DashboardService.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/OrderAttributionService.java`

- [ ] **Step 1: 按文档实现独家商家优先级**

优先级必须为：

- 独家商家
- 默认 商品 -> 活动 -> 招商负责人

- [ ] **Step 2: 对订单归属链路补测试**

要求覆盖：

- 有独家商家时归独家招商
- 无独家商家时走默认规则

- [ ] **Step 3: 运行定向测试**

Run:

```bash
cd backend
mvn "-Dtest=ExclusiveMerchantServiceTest,DashboardServiceTest" test
```

Expected: PASS

## Task 7: 补数据平台高级归属与提成

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/service/DashboardService.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/CommissionService.java`
- Modify: `frontend/src/views/dashboard/index.vue`
- Modify: `frontend/src/views/data/index.vue`

- [ ] **Step 1: 明确看板口径**

需要区分：

- 预估：按订单创建时间
- 结算：按结算时间

- [ ] **Step 2: 提成先做全局规则，预留差异化扩展**

当前不要求一步到位做活动 / 商品级差异化 UI，但服务和表结构要预留。

- [ ] **Step 3: 运行数据平台相关测试**

Run:

```bash
cd backend
mvn "-Dtest=DashboardServiceTest" test
```

Expected: PASS

## Task 8: 补系统规则配置中心

**Files:**
- Create: `frontend/src/views/system/RuleConfig.vue`
- Create: `frontend/src/api/systemConfig.ts`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/views/layout/Sider.vue`
- Modify: `backend/src/main/java/com/colonel/saas/controller/SystemConfigController.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/SystemConfigService.java`

- [ ] **Step 1: 先把文档里的规则项入库**

至少包括：

- 寄样限制天数
- 寄样限制开关
- 独家达人服务费占比阈值
- 独家达人月寄样数量阈值
- 独家商家服务费占比阈值
- 达人保护期

- [ ] **Step 2: 做最小可用配置页面**

要求：

- 管理员可查看
- 管理员可修改
- 有默认值
- 改后即时生效或明确提示重载规则

- [ ] **Step 3: 运行配置接口测试**

Run:

```bash
cd backend
mvn "-Dtest=SystemConfigControllerTest,SystemConfigServiceTest" test
```

Expected: PASS

## Task 9: 补完整操作日志中心与文档收口

**Files:**
- Modify: `backend/src/main/java/com/colonel/saas/service/ProductService.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/TalentService.java`
- Modify: `backend/src/main/java/com/colonel/saas/service/SampleRequestService.java`
- Modify: `docs/04-开发进度.md`
- Modify: `docs/05-接口与数据模型.md`
- Modify: `docs/10-V2.2场景覆盖矩阵.md`

- [ ] **Step 1: 统一哪些动作必须记日志**

至少包括：

- 商品审核 / 上架 / 转链
- 达人认领 / 释放 / 拉黑
- 寄样申请 / 审核 / 发货 / 签收 / 完成 / 关闭
- 配置规则修改

- [ ] **Step 2: 把矩阵状态重新标色**

分类只保留：

- 已覆盖
- 部分覆盖
- 未进入本阶段
- 待澄清

- [ ] **Step 3: 运行最终回归**

Run:

```bash
cd frontend
npm run build

cd ../backend
mvn test
```

Expected:

- 前端构建通过
- 后端全量测试通过

## Self-Review

- V2.2 文档主差异已覆盖到商品中心、达人 CRM、寄样规则、独家机制、数据平台、系统规则中心
- 当前计划没有推翻现有主链路，符合 AGENTS.md “按当前代码推进任务”的要求
- 当前计划把“口径变更”与“真实缺口”分开，避免误把已演进模块当缺失重做

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-04-v22-functional-alignment-gap-plan.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
