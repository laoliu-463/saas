# DDD 真实进度审计（2026-07-18）

## 基线与方法

- 分支：`codex/ddd-user-role-application`
- 审计源代码 HEAD：`1644ea55042874fecd88214230820eed29d9dd6e`
- 依据：Controller/Job/Event → Application → Domain Policy/Model → Port → Adapter/Repository 的实际引用；架构测试与 real-pre 运行证据。
- 目录/类数量只作为索引，不作为完成判定；旧链仍被最终调用时标记“过渡包装”。
- 报告生成时工作树存在 10 个未归属 performance/dashboard/order 修改，本审计只读取已提交 HEAD，不把 dirty 内容计入进度。
- 六态：`SHELL`（只有壳）、`SHADOW`（旁路未入主链）、`ROUTED`（部分流量进入）、`PRIMARY`（新链为主且可回退）、`VERIFIED`（real-pre 对账通过）、`RETIRED`（Legacy 删除）。

## 逐域结论

| 域 | 状态 | 真实依赖与证据 | 未达成原因 |
|---|---|---|---|
| user | ROUTED | user application/port/adapter 已被权限与用户流程使用 | Legacy auth/user service 仍存在；当前六角色回归受真实凭证阻塞 |
| product | ROUTED | product application、查询/策略和活动同步旁路存在；商品链 real-pre 31 通过 | ProductService/旧 Mapper 仍是部分最终路径，未证明新链为全量主路径 |
| order | ROUTED | order application/read facade 与订单事件存在；调度共享互斥已恢复运行稳定性 | 订单同步/归因最终仍委托 OrderSyncService 等旧实现；互斥锁是运维修复，不提高 DDD 等级 |
| sample | ROUTED | SampleApplicationService、状态事件和寄样接口形成部分应用链 | 33 寄样链因真实管理员/角色凭证失败，未取得 real-pre 对账证据；God Service 仍在主链 |
| performance | ROUTED | performance application/listener 与 dashboard 读取存在；34 看板只读通过 | 计算/汇总仍经过旧 Performance/Commission 服务，未证明新模型为唯一主路径 |
| talent | SHADOW | talent application/policy/facade 旁路存在 | TalentController/旧 TalentQueryService 仍承担大量最终调用，缺少完整角色闭环证据 |
| config | ROUTED | config port、policy、change-log 契约测试和配置 API 已接入 | 旧 SysConfig/BusinessRuleConfigService 仍保留，未完成主链切换与回退演练 |
| analytics | SHADOW | analytics event consumer/router 与 shadow listener 存在 | 本轮未证明其成为业绩汇总主路径；订单归因样本没有上游可对账结果 |
| colonel | SHADOW | colonel partner facade/policy/adapter 已存在 | 活动商品主查询仍需旧 Mapper/服务；未证明全量流量已切入新边界 |
| logistics | SHELL | 仅发现 2 个 logistics application service | 无可核验 Port/Adapter 主链；物流回归未形成 VERIFIED 证据 |

本次没有任何域可标记 `PRIMARY`、`VERIFIED` 或 `RETIRED`：本地 real-pre 商品链和 dashboard 通过不等于 DDD 新链完成真实对账；订单归因、寄样链和多角色权限仍为 `PENDING/BLOCKED_AUTH`。

## >1000 行 Service 红线

当前扫描得到 12 个（含 testsupport）超过 1000 行的 Service；与 `large-service-line-baseline.csv` 对比，本轮没有增长：

| 文件 | 行数 |
|---|---:|
| `ProductService.java` | 7239 |
| `SampleApplicationService.java` | 3613 |
| `TestDataService.java` | 2636 |
| `DataApplicationService.java` | 2582 |
| `ProductActivityBackfillService.java` | 1567 |
| `ProductDisplayRuleService.java` | 1515 |
| `OrderSyncService.java` | 1479 |
| `TalentQueryService.java` | 1443 |
| `OrderService.java` | 1181 |
| `DashboardService.java` | 1139 |
| `ProductActivityManualSyncService.java` | 1066 |
| `PickSourceMappingService.java` | 1051 |

红线：后续只能下降不能增长；禁止新增 Mapper 或跨域职责。`LargeServiceDebtRedlineTest` 已通过。本轮没有大规模拆解，只收敛 Schema 守卫、迁移入口和运行稳定性；低风险新切片仍为 `PENDING`。

## 综合真实完成度

按 `SHELL=0`、`SHADOW=0.25`、`ROUTED=0.5`、`PRIMARY=0.75`、`VERIFIED/RETIRED=1` 的保守权重，10 个业务域为约 **38%**。这是“真实主链迁移并有证据”的完成度，不是目录或类数量完成率；下一切片应在凭证/样本恢复后选择低风险、可回退的 config 或 analytics 旁路。
