# Codex 会话记忆迁移索引

> 更新时间：2026-06-20
> 来源：`C:\Users\caojianing\.codex\session_index.jsonl`、`.codex\sessions\`、`.codex\archived_sessions\`。

## 迁移结论

- 已读取 `session_index.jsonl` 的会话索引结构，并按标题检索 SAAS / 团长 / real-pre / 订单 / 归因相关会话。
- 已确认部分会话正文文件存在于 `.codex\archived_sessions\` 或 `.codex\sessions\`。
- 已读取 Codex App 本地状态库 `state_5.sqlite` 和 `.codex-global-state.json` 的项目级索引字段。
- 本文只迁移会话 / 状态索引、脱敏摘要和导入边界；原始会话全文、工具输出和本地状态 JSON 未直接迁入 docs，避免把未经验证的日志、临时判断或敏感内容写成项目事实。

## 高相关会话索引

| 日期 | 会话标题 | session id | 迁移状态 |
| --- | --- | --- | --- |
| 2026-05-03 | 执行抖音团长 SaaS E2E回归 | `019deb70-a5e2-7181-992d-c95a86dbb785` | 已抽取摘要 |
| 2026-05-08 | 梳理团长业务链路 | `019e074c-0378-7402-b4b6-0a0e3425fd81` | 已抽取摘要 |
| 2026-05-09 | 对接团长分次结算订单API | `019e0baf-4b4c-7612-ab5d-a2536e06f1ae` | 待复核正文 |
| 2026-05-15 | 统一 SAAS 环境启动 | `019e297e-7aa4-7e12-a265-d9972198f3eb` | 已抽取摘要 |
| 2026-05-19 | 替换团长订单接口 | `019e3f3e-db48-7752-bbd0-5b9c50ba5533` | 已抽取摘要 |
| 2026-05-21 | 生成团长SaaS审查提示词 | `019e49dc-eb78-76a1-a68f-b29a4194b21c` | 已抽取边界 |
| 2026-05-21 | 审查抖音团长 SaaS | `019e49de-48e0-74c2-90b2-01356109a63c` | 待复核正文 |
| 2026-05-21 | 审计抖音团长SaaS | `019e4a05-25a8-7520-85ae-a10466a25b39` | 已抽取摘要 |

## 已抽取会话摘要

- `019deb70...`：2026-05-03 多角色 E2E 回归显示 `/dashboard`、`/data`、`/orders` 与角色权限范围对齐；当时样本下 `biz_leader` 看到 0 单被解释为权限与造数归属共同作用，不应直接当作缺陷。
- `019e074c...`：2026-05-08 业务链路梳理时，real-pre 已有转链和 `pick_source_mapping` 样本，且订单表出现 `pick_source` 非空 / 已归因记录；阶段性判断从“接口 10 未开始”修正为“需要复验样本链路和多数订单未归因原因”。
- `019e0baf...`：2026-05-09 分次结算订单接口会话确认 `buyin.colonelMultiSettlementOrders` 更适合作为需团长授权的结算补充取样入口，不应与主订单同步入口混用；同会话后段转向商品管理筛选改造，未抽取为最终事实。
- `019e297e...`：2026-05-15 环境治理会话形成单活环境口径：test / real-pre 共用固定 `saas-active` compose project 与 `saas-*` 容器名；会话中验证 test 与 real-pre 均健康、profile 与前端环境徽标正确，但用户中断前曾计划切回 test，最终机器状态需以当前 Docker 事实为准。
- `019e3f3e...`：2026-05-19 订单接口替换会话明确主同步和按订单号补拉走 `buyin.instituteOrderColonel`；`buyin.colonelMultiSettlementOrders` 只保留为结算补充取样入口，并已做过 real-pre 验证。
- `019e49dc...`：2026-05-21 审查提示词会话沉淀审查边界：当前项目口径为 Spring Boot + Vue 3；不使用 Celery；real-pre 默认不主动写真实上游，审查需优先读 docs / gateway 契约 / harness evidence。
- `019e49de...`：2026-05-21 全方位审查会话只抽到阶段性探索目标：权限注解、DataScope、真实 Gateway、订单归因、寄样状态机、配置缓存是高风险审查对象；未在本轮迁移中写成审查结论。
- `019e4a05...`：2026-05-21 死代码审查会话记录：当时 tracked 源码内可确认死代码已清理，保留了由 Spring 扫描的 Controller / Gateway / 配置类 / profile bean；验证包含前端 import graph、前端 build/test 和后端测试，但当前仓库后续已变更，需重新执行审查才能作为今天的结论。

## 其他相关会话主题

- 三方接口联调：启动、继续、覆盖检查、受阻状态。
- real-pre：真实链路验证、准入预检、可视化回归、上线前审查。
- 订单链路：真实订单归因失败、分次结算订单 API、6468 订单源、订单 P0 远端部署、订单明细字段对齐、业绩事件时序。
- V2.2 / 业务缺陷：需求方案细化、关键业务缺陷修复、业务链路梳理。

## Codex App 本地状态索引

- `C:\Users\caojianing\.codex\state_5.sqlite`：`threads` 表中命中 `D:\Projects\SAAS` / SAAS / 团长关键词的线程共 339 条；本轮只抽取标题、首条用户消息摘要、分支和 thread id，不迁移正文。
- 最新命中主题包括：项目记忆迁移、DDD 到 100%、CI/CD、分支安全合并、数据库对账、backfill 异步化、结算看板对账、订单金额口径、统一结算主接口、真实订单问题排查。
- `.codex-global-state.json`：确认 active workspace root 为 `D:\Projects\SAAS`，远端连接别名存在 `saas`；未迁移鉴权、窗口状态、线程权限和本地 UI 状态。
- `.codex\memories_1.sqlite`：`jobs` 与 `stage1_outputs` 均为 0 条，未发现可抽取的 Codex 结构化记忆。
- `.codex\memories\extensions\ad_hoc\instructions.md` 仅为 ad-hoc note 处理规则，不含 SAAS 业务事实。
- `.codex\logs_2.sqlite`：`logs` 表有 56195 条运行日志，其中 SAAS / 团长 / 项目路径命中 15670 条；仅登记为可回溯证据源，不迁移原始日志。

## Codex 附件 / 粘贴文本索引

- `C:\Users\caojianing\.codex\attachments\`：发现 77 个 `pasted-text.txt`，其中 76 个命中 SAAS / 团长 / DDD / harness / real-pre 等关键词。
- `pasted-text-attachments.json` 记录 77 个附件路径和 excerpt 映射；本轮只抽取数量、主题和风险边界，不迁移正文。
- 主题分布：商品 / backfill / 转链 72、AGENTS / Harness 规则 69、real-pre / 部署 / 环境 69、DDD / 领域重构 65、订单 / 归因 / 结算 57、寄样 / 达人 / 物流 49、项目记忆 / 文档 47。
- 发现 1 个附件命中敏感值模式；附件正文不得直接进入 docs，后续如需抽取必须先逐条脱敏。
- 这些附件多数是用户粘贴的任务提示词、阶段边界和执行约束；只能作为“历史指令/计划来源”，不能直接证明任务已完成。

## Codex Session Visibility Backup 索引

- `C:\Users\caojianing\.codex\backup-*-session-visibility-repair\`：共 7 个备份目录，166 个文件，其中 JSONL 165 个。
- 备份 JSONL 去重后 159 个文件名；与当前 `.codex\sessions\` / `.codex\archived_sessions\` 的 407 个唯一 JSONL 文件名重叠 159 个。
- 结论：这些备份是会话可见性修复前后的重复快照，不是新的独立记忆源；仅作为原会话丢失时的恢复入口。
- 未迁移备份正文；如当前 session 缺失或损坏，再按文件名回退到备份验证。

## 线程主题聚类摘要

> 口径：基于 `state_5.sqlite.threads` 的标题、首条用户消息和分支名做关键词多标签聚类；同一线程可命中多个主题，计数不能相加为总数。

| 主题 | 命中线程数 | 典型用途 |
| --- | ---: | --- |
| Git / 分支 / 合并 | 187 | 多分支安全合并、commit / push、工作区清理边界 |
| 订单 / 归因 / 结算 | 162 | 订单归因、金额双轨、结算看板、官方口径对账 |
| 商品 / backfill / 转链 | 156 | 商品回补、dry-run、转链、`pick_source` 映射 |
| real-pre / 真实联调 | 148 | 真实上游、SDK、授权、生产形态验证 |
| 寄样 / 达人 / 物流 | 148 | 寄样生命周期、达人、物流补证 |
| Harness / evidence / 报告治理 | 126 | evidence、retro、agent-do、质量门禁 |
| 文档 / 记忆 / 迁移 | 121 | 文档重构、项目记忆迁移、索引治理 |
| 环境 / Docker / 部署 | 117 | Docker Compose、单活环境、部署、CI/CD |
| 审查 / 死代码 / 安全 | 106 | 全面审查、死代码、安全风险 |
| DDD / 领域重构 | 96 | 用户域、配置域、领域边界、策略 / facade 收口 |
| 其他未聚类 | 64 | 空标题、短指令或需回读正文的线程 |

## 最新线程线索

| thread id | 主题线索 | 分支 |
| --- | --- | --- |
| `019ee373...` | 当前项目记忆迁移目标 | `feature/ddd/DDD-VERIFY-001` |
| `019edf0c...` | Matt Pocock skills 与 DDD 计划 | `feature/ddd/DDD-VERIFY-001` |
| `019ede73...` | DDD 小步重构到 100% | `feature/ddd/DDD-VERIFY-001` |
| `019ed91b...` | CI/CD 流水线目标 | `feature/ddd/DDD-VERIFY-001` |
| `019ed848...` | 多分支安全合并与回归验证 | `feature/ddd/DDD-VERIFY-001` |
| `019ed441...` | 文档与数据库对账整理 | `feature/ddd/DDD-VERIFY-001` |
| `019ece99...` | backfill / dry-run 异步化方向 | `feature/ddd/DDD-VERIFY-001` |
| `019ec3d1...` | 6 月 12 日结算看板对账问题 | 待回读正文 |

## Ambient Suggestions 摘要

- 本地 `ambient-suggestions` 命中 3 条 SAAS / 团长相关建议，主题均围绕 `buyin.colonelMultiSettlementOrders`。
- 这些建议只作为“曾被系统建议的下一步”线索，不是已执行事实；其中反复出现的建议是把团长分次结算订单接口做成可落库、可分页、可回归的真实链路。
- 当前事实仍以代码、docs、evidence 和 real-pre 验证为准；ambient suggestion 不得直接升级为业务结论。

## 导入边界

- 会话正文只作为证据线索，不能直接替代当前代码、ADR、docs、harness evidence。
- 导入前必须做敏感信息扫描，尤其是 Token、密码、OAuth code、服务器地址、订单原始响应。
- 每个会话只抽取仍然有效的结论、命令、证据路径和待办；临时猜测、失败尝试和过期状态不进入事实文档。
- 与当前阶段冲突的历史结论必须标记为历史，不得覆盖 ADR-010 的 V2 口径。

## 推荐抽取模板

| 会话 | 可迁移事实 | 证据路径 | 当前验证 | 去向 |
| --- | --- | --- | --- | --- |
| 待补 | 待抽取 | 原会话文件 / 命令 / 报告 | PASS / PARTIAL / BLOCKED | 本文 / 领域文档 / 验收文档 |

## 当前状态

- 已迁移会话索引、Codex App 本地状态索引，并完成 8 个高相关会话的首轮脱敏摘要抽取。
- 未迁移原始会话全文；工具 stdout、数据库返回、上游响应和可能包含敏感值的片段均未写入 docs。
- 下一步若要继续，应按主题回读正文和当前代码 / docs / evidence 交叉验证，再决定是否进入领域文档或验收文档。
