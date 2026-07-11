# DDD Legacy 入口逐路径迁移设计

## 1. 背景与目标

当前仓库已建立多数领域的 Application、Port、Facade、Policy 与 Event，但部分 Controller、Job、Listener 和跨域调用方仍直接依赖 Legacy 大 Service。

本轮目标是逐入口把已经存在且有测试证据的 DDD 能力接入生产调用链，同时保留原大 Service 及其内部业务逻辑：

```text
Controller / Job / Listener
        -> DDD Application / Port / Facade
        -> Legacy Adapter
        -> 原大 Service
```

## 2. 已批准约束

- 不拆分或重写 `ProductService`、`TalentService`、`OrderSyncService`、旧 `service.sample.SampleApplicationService`、`DashboardService`、`DataApplicationService` 等大 Service。
- 不搬迁现有业务规则，不改变状态机、权限、数据范围、事务、幂等、异常和返回值语义。
- 保留旧 Service 作为 Legacy adapter 的最终执行端。
- `ddd.refactor.*` 开关默认值保持不变；本轮不通过改默认开关证明迁移成功。
- 保持 HTTP API、定时任务参数、Webhook 输入和数据库结构兼容。
- 不执行远端 real-pre 部署；远端部署必须由用户另行明确授权。

## 3. 非目标

- 不追求消除所有 Legacy 类或删除大 Service。
- 不在入口迁移时顺手重构业务算法、SQL、DTO、Mapper 或第三方 SDK。
- 不新增未经用户确认的商品、订单、业绩或数据范围规则。
- 不用 mock 结果替代 real-pre 业务证据。

## 4. 迁移单元与准入条件

最小迁移单元是一个 Controller 方法、一个 Job 入口、一个 Listener/Webhook 入口或一个明确的跨域调用方法。

每个入口先分类：

- `READY_TO_ROUTE`：已有 DDD 入口，参数、返回、异常和副作用可等价透传，可以直接迁移。
- `NEEDS_GLUE`：DDD 能力已存在，但缺薄的 inbound application、port 或 Legacy adapter；只允许补接线层，不搬业务逻辑。
- `DO_NOT_MIGRATE`：缺稳定业务合同或迁移会改变规则；保留原入口并登记原因，不由 AI 猜测。

## 5. 领域执行顺序

1. 寄样：核对命令入口已形成 `SampleController -> domain SampleApplicationService -> SampleCommandApplicationService -> SampleCommandService -> LegacySampleCommandService -> 旧 SampleApplicationService`；符合目标链路的只补证据。
2. 达人：迁移查询、认领、独家判断以及可等价的刷新/释放 Job 入口。
3. 订单：迁移 Controller、同步 Job 和 Webhook 入口到 `OrderSyncApplicationService` 等已存在应用入口。
4. 商品：迁移商品库、活动、转链、同步 Job 和快速寄样的可等价入口；Legacy product adapter 继续调用 `ProductService`。
5. 分析与事件：收口 Dashboard、Data、Analytics 和 Outbox 调用方向；只读分析不得重算订单归因或业绩归属。
6. 前端：恢复并执行前端业务边界合同，迁移可确认的 API/store 调用边界，不复制后端权限和状态机。
7. 最终清理：执行 Legacy 直连扫描、矩阵重算和状态文档收口；兼容 adapter 对大 Service 的依赖允许保留。

## 6. 测试与迁移流程

每个入口必须遵循：

1. 写行为刻画或架构失败测试，证明当前入口仍直连 Legacy 或缺少 DDD 路由。
2. 运行 RED，确认因目标行为缺失而失败。
3. 仅修改构造注入和调用目标，或补最薄的 Port/Legacy adapter。
4. 运行 GREEN，验证参数、权限上下文、异常和返回值完整透传。
5. 运行相邻 Controller/Job/Listener 测试及对应领域回归。
6. 运行 DDD redline 和宽口径架构测试。
7. 按项目 Harness 执行构建、容器重启、健康检查、业务验证和 evidence。

## 7. 多智能体并发模型

- 每个领域使用独立 worktree 和独立分支，写文件集合必须互斥。
- 探索与测试设计可并发执行；同一文件、同一 Spring composition root 或同一测试套件禁止并发修改。
- Maven、Docker、数据库和最终 Harness 门禁由主协调线程串行执行，避免共享缓存、端口和容器状态互相污染。
- 每个实现任务完成后依次进行规格审查和代码质量审查；Critical/Important 未关闭不得集成。
- 主分支按“寄样证据 -> 达人 -> 订单 -> 商品 -> 分析/事件 -> 前端 -> 最终清理”顺序逐批集成。

## 8. 回滚策略

- 每个入口独立提交；回滚只需恢复原构造依赖和调用目标。
- Legacy Service 与旧方法在运行态验证完成前不删除。
- 任一入口出现 API、权限、事务、状态机或数据差异，立即回退该入口，不扩大修复范围。
- 数据库无结构变更，因此本设计不包含数据迁移或清库操作。

## 9. 完成标准

- 所有 `READY_TO_ROUTE` 入口均经 DDD Application/Port/Facade 路由。
- 所有 `NEEDS_GLUE` 入口都有明确薄适配层和行为等价测试。
- `DO_NOT_MIGRATE` 项有业务合同缺口和后续决策记录。
- Controller/Job/Listener 不再在存在等价 DDD 入口时直接依赖 Legacy 大 Service。
- Legacy adapter 仍可依赖原大 Service，且大 Service 内部逻辑保持不变。
- DDD redline、领域回归、构建、Docker 健康、业务验证、evidence、retro、Git Exit Gate 全部有实际结果。
