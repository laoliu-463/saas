# DDD 收口证据矩阵

更新时间：2026-06-29

## 结论口径

- 跨域 Mapper 治理：DONE / 100%。`backend/src/test/resources/ddd/cross-domain-mapper-legacy-whitelist.txt` 无有效 Mapper 白名单条目。
- DDD 架构守卫：基本 DONE。当前重跑 `DddCrossDomainMapperGuardTest,DddPackageStructureContractTest,DddPolicyLayerNoSpringDependencyTest,DddAnalyticsReadOnlyBoundaryTest,DddClean001OrderUserDependencyGuardTest,DddClean002SampleCrossDomainMapperGuardTest,DddClean003PerformanceCrossDomainMapperGuardTest` 通过。
- 项目整体 DDD 全量收口：PARTIAL。178 张任务卡尚未形成逐卡 DONE 证据，不能写 100%。
- 若必须估算：整体完成度不应高于 70%-75%。本矩阵中的 PARTIAL 表示代码或历史 evidence 已有进展，但缺少逐卡 DONE 证据。

## 证据规则

- DONE 必须同时有代码文件、测试文件、执行命令和 PASS 结果。
- PARTIAL 表示已有代码、报告或状态记录，但缺逐卡验收或仍有明确缺口。
- TODO 表示尚未看到可复用的卡级证据。
- BLOCKED 表示依赖真实样本、第三方响应、授权或业务数据。

## 全局基线

| 项 | 状态 | 证据 |
| --- | --- | --- |
| DDD-BASE-Mapper | DONE | `cross-domain-mapper-legacy-whitelist.txt` 无有效 Mapper 条目；当前 `DddCrossDomainMapperGuardTest` PASS |
| DDD-BASE-Guard | DONE | 当前 DDD guard subset PASS；架构测试位于 `backend/src/test/java/com/colonel/saas/architecture/` |
| DDD-BASE-Package | PARTIAL | `domain/*` 目录齐备，但 legacy service、QueryWrapper、Controller/Service 直连 Mapper 仍存在 |
| DDD-BASE-Progress | PARTIAL | `DDD_DOMAIN_TASK_MATRIX.md` 共 178 卡；无逐卡 DONE 台账 |

## 任务卡矩阵

| ID | 状态 | 优先级 | 证据 / 缺口 |
| --- | --- | --- | --- |
| U-1 | PARTIAL | P0 | 状态记录存在，但引用的 U-1 报告当前路径未找到，需补可复查报告 |
| U-2 | PARTIAL | P0 | 表结构与模型已有收口记录，但 U-2 报告当前路径未找到 |
| U-3 | PARTIAL | P0 | CurrentUser 出口存在，仍需全调用点证据 |
| U-4 | PARTIAL | P0 | PermissionContext / policy 有推进，缺全域调用清单 |
| U-5 | PARTIAL | P0 | DataScopePolicy 已被多域消费，DataScopeResolver 统一仍未完成 |
| U-6 | PARTIAL | P0 | Permission policy 有推进，PermissionChecker 统一仍未完成 |
| U-7 | PARTIAL | P0 | UserDomainFacade 多个标量出口已落地，但自身兼容 DTO 分层未完成 |
| U-8 | PARTIAL | P0 | 订单侧数据范围旁路已有证据，缺统一完成口径 |
| U-9 | PARTIAL | P0 | 寄样侧多入口已接 DataScopePolicy，缺全量负例 |
| U-10 | PARTIAL | P1 | 商品侧消费用户域出口已有证据，缺全调用面证明 |
| U-11 | PARTIAL | P1 | 达人侧消费用户域出口已有证据，缺权限负例 |
| U-12 | PARTIAL | P0 | 业绩侧 DataScopePolicy 旁路已有证据，缺完整权限回归 |
| U-13 | PARTIAL | P0 | 分析模块数据范围已有旁路，缺 dashboard 账号差异验收 |
| U-14 | DONE | P1 | `CurrentUserPasswordAuditIntegrationTest`；归档报告记录该测试、test-compile、package、backend health PASS |
| U-15 | PARTIAL | P0 | 用户域单测和部分权限测试存在，缺 admin/group/self 全链路 E2E |
| C-1 | TODO | P1 | DOMAIN_STATUS 下一步仍指向 C-1 盘点 |
| C-2 | PARTIAL | P1 | 领域合同列出配置来源，缺代码/表/测试全清单 |
| C-3 | PARTIAL | P0 | 合同明确只出参数，仍缺配置域全量越界扫描 |
| C-4 | PARTIAL | P1 | ConfigDomainFacade 存在，缺缓存策略验收 |
| C-5 | PARTIAL | P1 | 配置保存主链路存在，缺版本记录逐卡证据 |
| C-6 | PARTIAL | P1 | 审计主链路存在，缺 API/SQL 验证矩阵 |
| C-7 | DONE | P0 | `DddConfig003ConfigRoutingTest` 验证提成比例由业绩域消费 ConfigDomainFacade；当前命令 PASS |
| C-8 | DONE | P1 | `DddConfig003ConfigRoutingTest` 覆盖复制模板和 pick_extra 读取；当前命令 PASS |
| C-9 | TODO | P1 | 未形成业务域反向写配置事实扫描 |
| C-10 | PARTIAL | P1 | 测试覆盖配置变更不自动重算部分场景，非全量业务动作扫描 |
| C-11 | PARTIAL | P1 | 配置相关单测存在，缺 C-11 收口报告 |
| C-12 | PARTIAL | P1 | 缺配置审计 API/SQL 验证证据 |
| C-13 | TODO | P1 | 缺异常分支和权限验证证据 |
| C-14 | PARTIAL | P1 | DOMAIN_STATUS 有配置域状态，但未形成 C-14 evidence |
| O-1 | PARTIAL | P0 | 订单域状态记录丰富，缺 O-1 代码/接口/表/测试清单 |
| O-2 | PARTIAL | P0 | 1603/2704 口径已有文档和报告，缺错误码矩阵 |
| O-3 | PARTIAL | P0 | 订单事实和 raw_payload 有实现，缺字段级台账 |
| O-4 | PARTIAL | P0 | pick_source 保存存在，正向可见性样本仍 PENDING |
| O-5 | PARTIAL | P0 | 默认归因输入存在，缺独立卡级验证 |
| O-6 | PARTIAL | P0 | 领域合同禁止订单算提成，缺全代码扫描报告 |
| O-7 | PARTIAL | P0 | performance_records anti-join 曾为 0，缺守卫级禁止写入证据 |
| O-8 | PARTIAL | P0 | 订单提交后事件已修复，仍需证明不直接改寄样状态 |
| O-9 | PARTIAL | P0 | 订单列表数据范围已有旁路，缺 admin/group/self 验证 |
| O-10 | PARTIAL | P0 | 退款事实能力存在，缺冲正联动逐卡证据 |
| O-11 | PARTIAL | P0 | 同步日志维度已增强，缺失败证据矩阵 |
| O-12 | PARTIAL | P0 | 订单已同步事件存在，缺生产时机最终守卫 |
| O-13 | PARTIAL | P0 | 退款事实事件存在迹象，缺消费闭环验证 |
| O-14 | PARTIAL | P0 | 同步幂等有历史报告，缺当前卡级复跑 |
| O-15 | PARTIAL | P0 | real-pre 1603 有响应和入库证据，settle/effective/flow_point raw probe 仍缺 |
| O-16 | PARTIAL | P1 | 订单单测存在，缺 O-16 汇总报告 |
| O-17 | PARTIAL | P0 | 集成测试存在，缺当前 full evidence |
| O-18 | PARTIAL | P0 | 事件消费测试存在，缺消费者回归矩阵 |
| O-19 | PARTIAL | P0 | DOMAIN_STATUS 已更新，仍缺 O-19 独立 evidence |
| Y-1 | TODO | P0 | DOMAIN_STATUS 下一步仍指向 Y-1 盘点 |
| Y-2 | PARTIAL | P0 | performance_records 主链路存在，缺入口全清单 |
| Y-3 | PARTIAL | P0 | 输入来源有描述，缺追溯字段验证 |
| Y-4 | PARTIAL | P0 | 提成规则来源存在，缺规则版本证据 |
| Y-5 | PARTIAL | P0 | 冲正入口存在，缺退款冲正回归 |
| Y-6 | PARTIAL | P0 | 双轨金额公式已修，缺页面/API/SQL 三方对账 |
| Y-7 | PARTIAL | P0 | 领域合同禁止同步订单，缺代码扫描守卫 |
| Y-8 | PARTIAL | P0 | 领域合同禁止改订单事实，缺写操作守卫 |
| Y-9 | PARTIAL | P0 | 幂等能力存在，缺重复消费证据 |
| Y-10 | PARTIAL | P0 | 汇总刷新存在，缺刷新入口验收 |
| Y-11 | TODO | P1 | 未看到业绩归属已计算事件完整证据 |
| Y-12 | TODO | P1 | 未看到业绩汇总已刷新事件完整证据 |
| Y-13 | PARTIAL | P0 | 配置消费测试覆盖部分提成参数 |
| Y-14 | PARTIAL | P0 | 退款冲正和汇总变化证据不足 |
| Y-15 | PARTIAL | P1 | 单测存在，缺 Y-15 报告 |
| Y-16 | PARTIAL | P0 | 集成测试存在，缺当前卡级执行 |
| Y-17 | PARTIAL | P0 | 权限数据范围旁路存在，缺负例 |
| Y-18 | PARTIAL | P0 | 异常分支和重复消费证据不足 |
| Y-19 | PARTIAL | P0 | DOMAIN_STATUS 有状态，未形成 Y-19 evidence |
| A-1 | PARTIAL | P0 | 分析模块现状有状态记录，缺 A-1 清单 |
| A-2 | PARTIAL | P0 | dashboard 指标来源有文档，缺字段级证据 |
| A-3 | PARTIAL | P0 | 汇总/导出来源有实现，缺导出对账 |
| A-4 | DONE | P0 | `DddAnalyticsReadOnlyBoundaryTest` 禁止调用重算/命令服务；当前 guard subset PASS |
| A-5 | DONE | P0 | `DddAnalyticsReadOnlyBoundaryTest` 禁止 insert/update/delete 写事实；当前 guard subset PASS |
| A-6 | PARTIAL | P0 | dashboard 数据范围旁路存在，缺账号差异验证 |
| A-7 | PARTIAL | P0 | 趋势/排行查询存在，缺对账卡 |
| A-8 | PARTIAL | P0 | 历史有 Dashboard API/SQL 对账，当前仍需重跑 |
| A-9 | PARTIAL | P0 | admin/group/self 看板差异未形成证据 |
| A-10 | PARTIAL | P1 | 单测存在，缺 A-10 报告 |
| A-11 | TODO | P0 | dashboard E2E 仍是下一步 |
| A-12 | TODO | P0 | 导出验证仍是下一步 |
| A-13 | TODO | P1 | 异常分支验证缺失 |
| A-14 | PARTIAL | P1 | 只读边界已有架构测试，缺审查记录 |
| A-15 | PARTIAL | P0 | DOMAIN_STATUS 已更新，缺 A-15 evidence |
| P-1 | PARTIAL | P0 | 商品域状态丰富，缺 P-1 清单 |
| P-2 | PARTIAL | P0 | 表结构有文档和测试线索，缺逐字段清单 |
| P-3 | PARTIAL | P0 | 同步/backfill/repair 有报告，Phase 4-2 未完 |
| P-4 | PARTIAL | P0 | 展示规则 policy 有测试，缺全输入清单 |
| P-5 | PARTIAL | P0 | 转链接口存在，缺错误码矩阵 |
| P-6 | PARTIAL | P0 | pick_source_mapping 能力存在，缺字段和幂等键验收 |
| P-7 | PARTIAL | P0 | 合同禁止最终归属，缺守卫 |
| P-8 | PARTIAL | P0 | 合同禁止提成，缺守卫 |
| P-9 | PARTIAL | P0 | 商品到寄样边界有部分 facade，缺全守卫 |
| P-10 | PARTIAL | P0 | 商品库查询与数据范围存在，缺权限验证 |
| P-11 | PARTIAL | P1 | 审核补充信息有合同，缺验收 |
| P-12 | PARTIAL | P0 | 转链映射证据存在，缺当前复跑 |
| P-13 | TODO | P0 | 推广中商品自动入库仍是待优化 |
| P-14 | TODO | P0 | 历史状态断链 repair 仍是待优化 |
| P-15 | PARTIAL | P1 | productId 精确查询有合同，缺卡级测试结果 |
| P-16 | PARTIAL | P0 | 映射可追溯有历史证据，缺当前复跑 |
| P-17 | PARTIAL | P0 | real-pre 上游 backfill 有多份报告，仍非全量 |
| P-18 | PARTIAL | P1 | 商品单测存在，缺 P-18 汇总 |
| P-19 | PARTIAL | P0 | 同步集成测试存在，缺当前执行 |
| P-20 | PARTIAL | P0 | 转链测试存在，缺当前执行 |
| P-21 | TODO | P1 | 商品库 E2E 缺当前证据 |
| P-22 | PARTIAL | P0 | DOMAIN_STATUS 已更新，缺 P-22 evidence |
| T-1 | PARTIAL | P1 | 达人域状态存在，当前另有未提交达人切片 |
| T-2 | PARTIAL | P1 | 资料/标签/地址/跟进主链路存在，缺清单 |
| T-3 | PARTIAL | P1 | 认领和保护期规则存在，缺规则来源矩阵 |
| T-4 | PARTIAL | P1 | 关系事实存在，缺消费边界清单 |
| T-5 | PARTIAL | P1 | 合同禁止采集订单事实，缺守卫 |
| T-6 | PARTIAL | P1 | 订单读取已走 facade，但当前 dirty 正在迁移独家检查 |
| T-7 | PARTIAL | P1 | 合同禁止提成/冲正，缺守卫 |
| T-8 | PARTIAL | P1 | 列表/详情数据范围有旁路，缺负例 |
| T-9 | PARTIAL | P1 | 地址供寄样消费存在，缺边界验证 |
| T-10 | PARTIAL | P1 | 标签/跟进审计存在，缺验收 |
| T-11 | BLOCKED | P1 | 第三方达人补充真实响应或 BLOCKED 证据未补齐 |
| T-12 | DONE | P1 | `TalentQueryServiceTest#page_shouldRejectUnsupportedGenderFilter` 当前 PASS；文档记录 gender 缺口 |
| T-13 | PARTIAL | P1 | 达人单测存在，缺 T-13 汇总 |
| T-14 | PARTIAL | P1 | 列表/详情 E2E 缺当前证据 |
| T-15 | PARTIAL | P1 | 地址和寄样联动有主链路，缺当前验证 |
| T-16 | TODO | P1 | 权限越权负例缺失 |
| T-17 | TODO | P1 | 异常分支验证缺失 |
| T-18 | PARTIAL | P1 | DOMAIN_STATUS 已更新，缺 T-18 evidence |
| S-1 | PARTIAL | P0 | 寄样域状态存在，缺 S-1 清单 |
| S-2 | PARTIAL | P0 | 状态流转存在，缺全状态验收 |
| S-3 | PARTIAL | P0 | 状态机 policy 存在，缺非法流转矩阵 |
| S-4 | PARTIAL | P0 | 地址/商品/渠道输入存在，缺来源清单 |
| S-5 | PARTIAL | P0 | 合同禁止同步订单，缺守卫 |
| S-6 | PARTIAL | P0 | 合同禁止业绩归属，缺守卫 |
| S-7 | PARTIAL | P0 | 合同禁止提成/冲正，缺守卫 |
| S-8 | PARTIAL | P1 | 物流不阻塞主流程有文档，缺测试 |
| S-9 | PARTIAL | P0 | 列表/详情数据范围有旁路，缺负例 |
| S-10 | PARTIAL | P0 | 审核/发货权限有 policy，缺全路径验证 |
| S-11 | PARTIAL | P0 | 订单已同步事件消费存在，缺完整命中证据 |
| S-12 | BLOCKED | P0 | 需要真实 `channel_id+talent_id+product_id+pay_time` 样本 |
| S-13 | PARTIAL | P0 | 重复事件幂等迹象存在，缺当前验证 |
| S-14 | BLOCKED | P0 | 真实订单样本不足时只能 PENDING/BLOCKED |
| S-15 | PARTIAL | P1 | 寄样单测存在，缺 S-15 汇总 |
| S-16 | PARTIAL | P0 | 状态机测试存在，缺全状态当前执行 |
| S-17 | PARTIAL | P0 | 订单事件消费测试存在，缺当前执行 |
| S-18 | TODO | P0 | 权限越权负例缺失 |
| S-19 | TODO | P0 | 寄样 E2E 缺当前证据 |
| S-20 | TODO | P1 | 异常分支验证缺失 |
| S-21 | PARTIAL | P0 | real-pre 证据索引有历史，缺样本闭环 |
| S-22 | PARTIAL | P0 | DOMAIN_STATUS 已更新，缺 S-22 evidence |
| E-1 | PARTIAL | P1 | Outbox 表/服务/消费状态代码存在，缺盘点报告 |
| E-2 | PARTIAL | P0 | 订单已同步事件存在，缺最终事件契约证据 |
| E-3 | PARTIAL | P0 | 退款事件存在迹象，缺完整证据 |
| E-4 | TODO | P1 | 业绩归属已计算事件缺证据 |
| E-5 | TODO | P1 | 业绩汇总已刷新事件缺证据 |
| E-6 | PARTIAL | P1 | 寄样事件 publisher 存在，缺消费证据 |
| E-7 | PARTIAL | P1 | 商品事件 publisher 存在，缺转链完成事件证明 |
| E-8 | PARTIAL | P1 | 幂等键和 payload 有部分实现，缺统一规范 |
| E-9 | PARTIAL | P1 | 失败重试在 dispatcher 测试中有覆盖，缺运行证据 |
| E-10 | PARTIAL | P2 | V1 不强制 MQ 已写入 forbidden scope，缺卡级验证 |
| E-11 | PARTIAL | P1 | 事件生产单测存在，缺汇总执行 |
| E-12 | PARTIAL | P1 | 事件消费单测存在，缺汇总执行 |
| E-13 | PARTIAL | P1 | appendIfAbsent 相关测试存在，缺跨域重复消费验证 |
| E-14 | TODO | P1 | 事件证据索引未补齐 |
| E-15 | PARTIAL | P1 | 事件状态未形成 E-15 evidence |
| F-1 | PARTIAL | P1 | 前端页面/API/store 文件存在，缺领域化盘点报告 |
| F-2 | PARTIAL | P1 | 用户/权限页面存在，缺领域边界验收 |
| F-3 | PARTIAL | P1 | 规则中心页面存在，缺配置域收口证据 |
| F-4 | PARTIAL | P0 | 订单页面和测试存在，缺 E2E |
| F-5 | PARTIAL | P0 | 业绩页面/API 存在，缺双轨页面验收 |
| F-6 | PARTIAL | P0 | dashboard/data 页面存在，缺导出和对账 E2E |
| F-7 | PARTIAL | P1 | 商品页面/API 存在，缺商品库 E2E |
| F-8 | PARTIAL | P1 | 达人页面/API 存在，缺权限负例 |
| F-9 | PARTIAL | P0 | 寄样页面/API 存在，缺寄样 E2E |
| F-10 | TODO | P0 | 前端硬编码业务规则扫描未形成报告 |
| F-11 | PARTIAL | P0 | 前端 build 历史 PASS，缺领域化 Vitest/E2E/截图总证据 |
| G-1 | PARTIAL | P0 | 渠道链 P0 preflight 有历史，缺全链逐步证据 |
| G-2 | PARTIAL | P0 | 招商链有主链路，缺完整 E2E |
| G-3 | PARTIAL | P0 | 管理链有权限/配置能力，缺完整 E2E |
| G-4 | PARTIAL | P0 | admin/group/self 证据分散，缺统一回归 |
| G-5 | PARTIAL | P0 | 订单/寄样/业绩/dashboard 对账仍有缺口 |
| G-6 | PARTIAL | P0 | real-pre preflight 多次 PASS，但真实样本缺口仍需 BLOCKED/PENDING |
| G-7 | PARTIAL | P1 | 旧内容维护脚本和报告存在，旧债删除未完成 |
| G-8 | PARTIAL | P1 | evidence/retro 持续生成，Harness changelog 未逐卡闭环 |

## 下一轮优先级

| 优先级 | 收口目标 | 理由 |
| --- | --- | --- |
| P0 | A-8/A-11/A-12、Y-6/Y-14/Y-17、S-11/S-12/S-13/S-14、O-4/O-15、G-5/G-6 | 直接影响看板金额、业绩归属、寄样完成和 real-pre 主链路 |
| P1 | U-5/U-6/U-7、C-1/C-9/C-13、P-13/P-14/P-21、T-11/T-16/T-17、E-1/E-8/E-14、F-1/F-10/F-11 | 直接影响 DDD 收口质量和后续燃尽效率 |
| P2 | E-10、G-7、文档归档与旧债删除 | 治理项，不应阻塞 P0 主链路 |

## 当前验证记录

- PASS：当前 DDD guard subset。
- PASS：`mvn -q "-Dtest=DddConfig003ConfigRoutingTest" test`。
- PASS：`mvn -q "-Dtest=TalentQueryServiceTest#page_shouldRejectUnsupportedGenderFilter" test`。
- 注意：当前工作区存在非本轮后端 dirty：`TalentService.java`、`TalentServiceTest.java`、`ExclusiveTalentCheckApplicationService.java`。本审计不修改、不 stage、不提交这些文件。
