# Retro Summary: DDD-AUDIT-SAMPLE-001

## 1. 做对了什么
* 严格遵循“只读审查任务”的定义，没有为了修复问题而对 Java、Vue、SQL 做出任何修改，做到了 `DONE_CLEAN`。
* 细致地梳理了 `SampleApplicationService` 的 3696 行庞大业务逻辑，包括手动申请、商品物化兜底、快速寄样、7天去重限制、达人资质门槛、自动完成链路，并深入到了状态流转与角色限制细节。
* 识别到了寄样域跨域 Mapper 依赖的实质情况，为提取 Facade 奠定了依据。

## 2. 发现的寄样域核心 DDD 风险
1. **商品物化隐患**: `ProductQuickSampleService` 在申请寄样时如果检测到主商品表 `product` 记录缺失，会直接调用快照生成并插入一条主商品数据。这种通过寄样链路来“物化商品表”的做法，导致商品写入职责穿透。
2. **多表 Native SQL 耦合**: `SampleLifecycleService` 匹配订单并触发寄样的 SQL 关联了 `sample_request` 与 `product` 表，同时还要结合 `talent_claim` 表计算认领权，最后强行以 LIMIT 1 形式取最早一条。该匹配逻辑属于应用层/域策略，却用原生 SQL 的方式固化在数据库，难以维护和测试。

## 3. 下一步为什么进入订单同步防护测试
* 我们已经完成了对订单域、业绩域和寄样域三个核心闭环领域的 Phase 0 只读审查。
* 订单同步是这三个闭环链路的事实源头（触发业绩计算、触发寄样自动完成）。
* 在 Phase 1 我们必须通过订单同步的防护测试来锁定系统的输入输出行为，防止后续重构拆分导致闭环链路断裂。

## 4. 后续 Agent 规避项
* 绝对不可在重构前擅自改动寄样状态机的命名（例如将 PENDING_AUDIT 改为 PENDING_REVIEW），以防造成接口契约与前端展示错位。
* 重构时必须通过 `TalentDomainFacade` 等来做依赖屏蔽，切忌直接在业务 Policy 或 Service 中大范围注入其他域的 Mapper。
