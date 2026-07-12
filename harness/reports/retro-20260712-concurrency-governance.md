# Retro Summary — 活动商品并发治理

## 本轮结果

- 本地 real-pre 已重建并重启，健康检查通过。
- 通过 TDD 增加 Redis owner 租约槽、手动同步槽不足回队列、stale 回收测试。
- 手动同步不再竞争 `PRODUCT_BACKFILL_GLOBAL`；完整 backfill 仍保持全局独占。
- 默认活动并发 2，单活动状态分片并发 2；上游首包预检仍可回退串行。

## 有效做法

- 先用 code-review-graph 定位全局锁调用方，再用失败测试证明“全局锁导致活动串行”。
- 用 Lua + ZSET owner lease 实现槽位回收，避免裸 `DECR/INCR` 重复归还。
- 重启前查询 RUNNING 任务，确认没有正在执行的 backfill/真实写入任务。
- 通过 SQL 和 Redis 双向核对任务终态、并发 lease 和活动锁清零。

## 暴露问题

- 本地 real-pre admin 凭据与旧探针默认值不一致，受控 HTTP 手动触发未执行；业务证据来自已完成任务 SQL 和容器日志。
- 上游订单定时任务出现 Douyin `20000 / isp.service-error:256`，与本轮活动商品改动无直接因果，但需要继续观察。
- 相关回归已扩大到 55 tests，全部通过；全量 `mvn clean test` 被 JaCoCo 0.8.11 的 `Truncated class file` 阻塞，不能把局部回归写成全量回归。
- 全量长跑曾出现增量 target / Spring Context 噪声，后续应单独修复 JaCoCo/Surefire 工具链并固定干净构建门禁。

## Harness 结论

- 本轮无需修改 Harness 脚本或规则。
- 未使用 `agent-do.ps1`，因为其会整体暂存并推送当前历史脏文件；仅 scoped 提交 `653eb41b` 已推送。
- 远端部署保持未执行，待用户另行确认。
