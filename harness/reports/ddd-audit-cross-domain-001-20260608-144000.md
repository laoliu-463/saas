# Main Audit Report - DDD-AUDIT-CROSS-DOMAIN-001

## 1. 审计概述
本报告是抖音团长内部 SaaS V1 系统渐进式重构 Phase 0 的核心产出。本任务为只读审查（A类），旨在摸清当前系统的跨域耦合度，理清系统边界，为接下来的 Facade 隔离和测试防护奠定基础。

## 2. 领域划分分布情况
后端目前结构为扁平结构，未按照领域进行独立包划分。然而从业务层面上看，主要分为以下 9 个自治领域：
- **User (用户域)**: 处理后台账号、角色分配、组织架构（部门）、数据范围过滤等。
- **Talent (达人域)**: 达人 CRM，包含认领达人、独家达人设置、防骚扰保护期控制等。
- **Product (商品域)**: 招商与自有商品库，涉及佣金、讲解复制和商品转链。
- **Sample (寄样域)**: 处理从达人申请寄样、商家审核、顺丰物流追踪到最后签收的闭环逻辑。
- **Order (订单域)**: 接入抖音开放平台订单，并完成订单持久化。
- **Performance (业绩域)**: 包含提成计算、达人结算、渠道提成拆分等。
- **Config (配置域)**: 存储渠道防骚扰天数、系统佣金门槛等核心规则参数。
- **Merchant (招商域)**: 商家对接及招商活动提拔。
- **Analysis (分析域)**: 看板、大屏和业绩指标汇总。

## 3. 跨域调用图谱
各领域组件在逻辑上存在着大量双向甚至网状的依赖关系：
```
[User] <======== (SysUserMapper 被几乎所有领域注入)
   ^
   |
[Talent] -------> [Sample] (TalentService 注入了 SampleRequestMapper)
   |                 ^
   v                 |
[Order] <-------- [Product] (ProductService 注入了 ColonelsettlementOrderMapper)
   ^
   |
[Performance]
```

## 4. 跨域 Mapper 穿透清单
- **TalentService**: 注入 `ColonelsettlementOrderMapper` (订单域) / `SampleRequestMapper` (寄样域) / `SysUserMapper` (用户域)
- **TalentQueryService**: 注入 `SysUserMapper` (用户域) / `SampleRequestMapper` (寄样域)
- **ProductService**: 注入 `ColonelsettlementOrderMapper` (订单域) / `SysUserMapper` (用户域)
- **SampleApplicationService**: 注入 `ProductSnapshotMapper` (商品域) / `SysUserMapper` (用户域) / `TalentMapper` (达人域) / `TalentClaimMapper` (达人域)
- **SampleFilterOptionsService**: 注入 `ProductSnapshotMapper` (商品域) / `SysUserMapper` (用户域)
- **OrderService**: 注入 `ProductSnapshotMapper` (商品域)
- **OrderSyncPersistenceService**: 注入 `SysUserMapper` (用户域)
- **PerformanceQueryService**: 注入 `ColonelsettlementOrderMapper` (订单域)
- **DataApplicationService**: 注入 `ColonelsettlementOrderMapper` (订单域) / `PerformanceRecordMapper` (业绩域) / `SysUserMapper` (用户域) / `ExclusiveTalentMapper` (达人域) / `ExclusiveMerchantMapper` (招商域) / `ColonelsettlementActivityMapper` (招商域)
- **ProductQuickSampleService**: 注入 `SampleRequestMapper` (寄样域) / `TalentMapper` (达人域) / `TalentClaimMapper` (达人域)
- **SampleLifecycleService**: 注入 `TalentClaimMapper` (达人域)

## 5. 胖 Service (God Service) 排行榜及统计
- `ProductService` (246KB, ~2000行): 承担了商品转链、规则维护、达人独占、状态变更等多重职能。
- `SampleApplicationService` (191KB, ~1600行): 承担寄样创建、规则过滤、数据权限、以及归因重算触发等多重功能。
- `DataApplicationService` (110KB, ~2280行): 重大设计错位，该类放置在 `service` 目录下，但实际上标有 `@RestController` 注解并继承了 `BaseController`。此外，它还注入了大量的跨域 Mapper，并用裸 SQL `JdbcTemplate` 进行看板的深度查询和聚合。

## 6. 胖 Controller (Controller fat logic) 盘点
- `OrderController` (75KB) 与 `ColonelActivityProductController` (59KB) 存在编排、重试和权限控制逻辑。

## 7. 事务边界一致性风险
- 大量类在方法级直接标注了 `@Transactional`。如果在一个 Service 方法中同时调用了外部 API、向抖音发送转链请求，并修改了达人及订单的数据，只要外部网络或无关的状态机出错，整个事务都会回滚，且这会导致数据库锁被长期占用。

## 8. 事件发布一致性风险
- 订单同步使用 `TransactionSynchronization.afterCompletion` 实现了在事务提交后触发事件。
- 但在其他领域中（如配置更改、达人分配等），由于缺少本地消息表（Outbox Pattern），在抛出异常时有可能出现数据已提交但事件丢失的情形，存在一致性漏洞。
- 监听器内耦合性高。例如 `OrderSyncedEventListener` 虽然异步接收到了订单同步完成的消息，但由于监听器内部直接注入了 `TalentClaimMapper` 并重写了保护期天数，又再次造成了达人与订单的深度耦合。

## 9. DTO/Entity/VO 混乱依赖现状
- Controller 层大量直接暴露 Entity。
- 许多 DTO 被直接用作 Controller 的方法入参，然后层层向下穿透到 Service 乃至 Mapper 甚至持久层。
- 缺少防腐层 (ACL) 来屏蔽外界数据模型的侵蚀。

## 10. Facade 收敛优先级
1. **User (用户域)**: 建议首先创建 `UserFacade`，消除其他 Service 直接注入 `SysUserMapper` 的问题。
2. **Config (配置域)**: 统一收敛为 `ConfigFacade`，防止各处散落的 `SysConfig` 读写。
3. **Talent (达人域)**: 收敛为 `TalentFacade`，对外提供达人信息的查询和状态控制。
4. **Order (订单域)**: 收敛为 `OrderFacade`，隔离对订单表结构的越界访问。

## 11. 第一批 DDD 重构子任务推荐
- `DDD-FACADE-USER-001`: 实现 `UserFacade`
- `DDD-FACADE-CONFIG-001`: 实现 `ConfigFacade`
- `DDD-FACADE-ORDER-001`: 实现 `OrderFacade`

## 12. 绝对禁止做的事项
- 禁止物理包结构的大范围迁移。
- 禁止修改任何 API 契约和数据库字段。
- 禁止在只读任务中混入业务修复或顺便写代码。

## 13. 审计结论
当前 SaaS V1 系统的重构必须坚持 **“小步快跑，Facade 隔离先行，保护测试配合，再行逻辑收敛与包位置迁移”** 的核心战略。
