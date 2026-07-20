# real-pre 稳定性与业务闭环 Evidence

## 元数据

- 时间：2026-07-19 17:20 +08:00
- 环境：本地与远端 `real-pre`
- 分支：`codex/ddd-user-role-application`
- 远端代码：`db930364f577f965f93601297e5e9854b4ff1813`
- 远端镜像：backend/frontend 均为 `db930364f577f965f93601297e5e9854b4ff1813`
- 远端部署：已执行，按用户要求跳过备份
- 任务范围：物流限频、Outbox、操作日志、快递100回调、壮云账号闭环核验

## 变更与构建

- `96b98eed`：物流 31 分钟门禁、Redis 限频、`QUERY_THROTTLED` 跳过语义、Outbox 与操作日志治理。
- `dbe8686b`：认证失败日志补充结构化错误字段。
- `914e355f`：Redis String 序列化类型修复。
- `7b58f309`：成功查询后显式清空数据库历史物流错误。
- `db930364`：商品展示规则遇到 409 乐观锁冲突时，重新读取最新状态并重算，最多重算 3 次；非 409 仍直接失败。
- 后端 Maven 构建：PASS。
- 前端构建：PASS。
- 物流、快递100网关、Outbox、操作日志相关定向测试：PASS。
- 商品展示规则、活动同步批次和商品服务定向测试：PASS；覆盖冲突后成功、活动批次不中断、重试耗尽三个分支。
- 本地对应容器重启与健康检查：PASS。
- Harness 安全检查：TASK_GATE PASS；历史报告数量/行数债务仍使 REPOSITORY_HEALTH 为 PARTIAL。

## 远端部署与健康

- `/opt/saas/app` HEAD：`db930364f577f965f93601297e5e9854b4ff1813`。
- `.env.real-pre` IMAGE_TAG：与 HEAD 完全一致。
- backend、frontend、PostgreSQL、Redis：全部 healthy。
- backend JAR 版本守卫与 Flyway：PASS。
- Redis 使用 AOF 与持久卷；物流限频键在 Redis 容器重建后仍保留。
- 远端临时增量 bundle 已从 `/tmp` 清理。

## 物流轮询与限频证据

- 数据库候选门禁改为至少 31 分钟，Redis key TTL 为 31 分钟。
- 2026-07-19 08:00 UTC 自然调度：`total=1 success=1 failed=0 skipped=0`。
- 2026-07-19 08:30 UTC 自然调度：`total=0 success=0 failed=0 skipped=0`；距上次仅 30 分钟时未进入查询。
- 2026-07-19 08:33 UTC，使用现有 `ops_staff` 调用真实同步接口，HTTP 200。
- 样品 `QS20260717371F7A25`：
  - 状态保持 `3`，物流状态保持 `UNKNOWN`；
  - `logistics_last_query_at` 更新为 `2026-07-19 08:33:11.851124`；
  - 历史 `logistics_last_error` 已清空；
  - 未伪造签收或完成状态。
- Redis 新限频键：数量 1、类型 string、采样 TTL 1814 秒。
- 限频键有效时调用同步接口：HTTP 200，但数据库状态、查询时间、错误字段均不变，证明限频分支不覆盖已有状态。
- 单元测试证明 `QUERY_THROTTLED` 统计为 `skipped=1`，不计 success/failed。

## Outbox 证据

- 初始：DEAD 159，PENDING 约 27.7 万。
- DEAD 构成：150 条 `OrderRefundFactSynced`、9 条 `OrderSynced`。
- 159 条事件按原 ID 重放一次，不删除事件；最终 159/159 PUBLISHED。
- 下游核对：150/150 退款事实、9/9 订单均被消费更新。
- 调整分发批次/间隔后积压已排空并保持稳定。
- 最终采样：仅 `PUBLISHED=1,039,613`，PENDING=0、DEAD=0。

## 操作日志证据

- `operation_log` 已增加 `error_code`、`error_message`、`trace_id`。
- 无效登录探针已记录 `AUTH_INVALID_CREDENTIALS`、错误消息和 trace_id。
- 订单批量同步改为批次汇总日志；实际采样包含 inserted/updated/failed 与 trace_id，不再逐订单制造噪声。
- 业务日志仅在实际状态变化时写入；无变化与限频跳过不写变更日志。
- 分区保留期清理使用严格分区边界执行；当前没有早于截止日的分区，实际 drop 数为 0，未删除事件或业务行。
- 历史 2026-06/07 分区较大，且缺少适合按用户/时间回查的索引；这是历史查询性能风险，本轮未在线加索引。

## 快递100回调证据

- 配置回调：`http://1.14.108.159/api/public/logistics/kuaidi100/callback`。
- 错误签名探针：HTTP 200 并返回签名错误，证明公网路由可达。
- 正确签名由后端容器内部生成：签名校验通过；虚拟快递单因公司/单号不匹配被拒绝，未修改业务数据。
- HTTP 公网可达：PASS。
- HTTPS：FAIL，`https://1.14.108.159/...` TLS 握手失败；当前没有可用域名证书/443 入口。

## 远端日志稳定性

- 当前部署后 backend/frontend/PostgreSQL/Redis 均持续 healthy；远端 HEAD、IMAGE_TAG 与两个业务镜像一致。
- 09:00 UTC 的 PAY_RECENT 自然轮次完成：65 页、6436 条、inserted=7、updated=6429、attributed=247、failed=0，证明 08:30 的单次上游 `isp.service-error:256` 已恢复。
- 部署后发现两个活动并行处理共享商品时可能触发 `ProductDisplayRuleService` 409 乐观锁冲突，旧实现会使整个活动失败；已通过 `db930364` 修复并部署。
- 使用活动负责人 `biz_leader` 通过正式异步接口复验：活动 `3920684` 同步 452 个商品、更新 254；活动 `3916506` 同步 1806 个商品、更新 1064；两个 job 均 SUCCESS、failed=0、complete=true。
- 上述两个活动涉及的已选品数据中，同一商品存在多条 `DISPLAYING` 的数量为 0；部署后 `optimistic conflict` 与 `activity sync failed` 日志计数均为 0。
- 状态分片预探测对上游不支持的 `status=4` 返回 `50002`，系统按既定合同降级为串行全量并最终成功；当前 SDK 仍将这一预期降级过程记录为 ERROR，属于日志噪声，不是同步失败。
- 09:13 UTC 后 backend ERROR 为 0；09:16 UTC 后 PostgreSQL ERROR/FATAL/PANIC 为 0；frontend、Redis 自部署以来相应错误计数均为 0。此前 PostgreSQL ERROR 均来自本次人工诊断中的错误 SQL，不是应用请求。
- 未再出现物流 Redis `ClassCastException`、Outbox DEAD/PENDING、容器重启循环或数据库连接故障。

## 壮云账号业务闭环

- 目标账号：玄同；用户要求暂不调整角色，本轮未修改角色或重置密码。
- 当前事实：达人认领 4、有效选品映射 2、寄样 3（待审核 2、寄出 1、完成 0）。
- 渠道订单 0、渠道业绩 0；招商订单/业绩 1。
- 目标订单渠道归因为 `UNATTRIBUTED`，原始订单缺少可确定映射的 pick_source/channel_user，不能安全强制归属。
- 寄出样品与目标订单在“达人相同/商品相同”两个条件上不能同时匹配，且没有签收事实，不能伪造自动完成。
- 结论：代码链路与接口可用，但该账号真实“渠道订单 -> 渠道业绩 -> 寄样完成”闭环尚不存在，属于业务数据前置条件不足，不是角色权限问题。

## 结论

`PARTIAL`

- 本轮要求的应用层修复、构建、测试、远端部署、物流真实轮询、Outbox 重放与清零、结构化日志均已通过。
- 商品活动同步的乐观锁并发故障已完成根因修复、测试、远端部署和正式异步任务复验。
- 这次优化属于应用层调度、Redis 并发限频、Outbox 消费与数据库日志治理，不是 JVM GC/堆参数优化。
- 不能判定“全部业务闭环且完全稳定”：快递100 HTTPS 尚不可用；壮云账号缺少真实渠道归因/签收数据。

## 剩余风险与后续动作

1. 为快递100回调配置域名、可信 TLS 证书和 443 反向代理，再从公网复验 HTTPS 回调与签名。
2. 等真实渠道订单携带可确定的 pick_source/达人映射，并在真实签收后验证壮云账号完整闭环；禁止手工伪造归属和签收。
3. 将上游不支持状态分片的 `50002` 在降级路径中收敛为结构化 WARN，避免预期回退污染 ERROR 告警。
4. 低峰期评估 operation_log 分区索引与历史分区归档，避免在线大表 DDL。
5. npm audit 仍有既有 6 项依赖风险（1 low、1 moderate、2 high、2 critical），本轮未执行破坏性自动升级。

## Retro

- 真实调度先后暴露了 Redis value 序列化类型与 ORM null 更新策略两个单元测试未覆盖的差异；现已分别补充类型断言与数据库显式清空测试。
- 真实并行活动同步暴露了共享商品状态的乐观锁竞争；代码审查据此增加批次不中断和重算耗尽测试，修复范围保持在展示规则服务内部。
- 部署脚本要求远端 checkout/IMAGE_TAG 预对齐；仅看到脚本 PASS 不能证明新镜像已上线，后续必须同时核对远端 HEAD、IMAGE_TAG、容器 image label 与健康状态。
