# 活动与商品同步并发治理 - 仍待设计部分 (DESIGN)

**日期：** 2026-07-13
**状态：** 待设计 (DRAFT, not implemented)
**关联：** 配合 `2026-07-12-activity-product-sync-repair-design.md` (FACTS - 已实施)

## 范围

本文档记录仍待设计的并发治理部分. 2026-07-12 死锁事件已通过 owner-safe release 修复 (commit 387b3e10 + 5b3b74c2),
但以下设计未实施, 待后续治理.

## 待设计 1: 阶段 A heartbeat / 续租

**背景**: 当前 owner-safe release 依赖 Redis Lua 原子 owner 校验.
任务 TTL 过期后, 旧任务 release 不会误删新任务锁, 但旧任务仍可能在持锁状态.

**设计要点**:
- DistributedJobLockService 增加 `tryRenew(lockKey, owner, ttl)` 方法
- heartbeat 线程按 TTL/2 周期续租
- 续租失败时: 任务主动停止, 标记 PARTIAL, 释放锁
- 续租日志: 结构化 logging, 含 jobId + lockKey + owner + ageMinutes

**实施风险**:
- heartbeat 线程崩溃 vs 任务崩溃无法区分
- 续租频率过高会加重 Redis 负担
- 续租失败时如何安全回滚已写数据库

## 待设计 2: 阶段 C fencing token

**背景**: 即使有 owner-safe release + heartbeat, 极端场景下 (Redis 不可用 + 任务暂停) 仍可能出现
"假持锁". Fencing token 是 Martin Kleppmann 提出的方案, 每次锁获取时递增 token, 写操作必须带 token.

**设计要点**:
- Redis INCR 维护全局 token
- 锁值改为 token (不再是 owner 字符串)
- 写 mapper 必须带 token, token 不匹配拒绝写
- MyBatis 拦截器实现 token 校验

**实施风险**:
- 现有所有 mapper 都要改 (侵入性大)
- PostgreSQL 事务隔离级别 + token 一致性保证
- 与现有 @Transactional 重构 (P8.5 已完成) 协调

## 待设计 3: 并发治理监控

**背景**: 当前 ArchUnit 红测 (LockOwnerReleaseGuardTest) 是静态检查, 缺少运行时监控.

**设计要点**:
- Micrometer 指标: lock_acquired_total / lock_released_total / lock_renew_failed_total
- 死锁检测: PostgreSQL `pg_stat_activity` 轮询, 超时锁告警
- Grafana 面板: 按 lock key 维度看并发度

**实施风险**:
- 指标基数 (cardinality) 爆炸
- Prometheus 存储成本

## 验证标准

每项设计实施后必须满足:
1. ArchUnit 红测 + 单元测试 + 集成测试 三层验证
2. 真实生产数据采样 (不能仅 mock)
3. 远端 real-pre 部署后 health 持续监控 1 周

## 不在本文档范围

- 商品同步事务重构 (P8.5 已完成)
- 活动状态 API (P8.2 已完成)
- 死锁重试 (P8.4 已完成, 批次级 retry)
- 活动状态字段接入 (P8.1 已完成)
- 活动状态字段 API 路径修正 (P8.2 已完成)

## 关联 commit

- `387b3e10` fix(lock): P9.5 阶段 2 - 全局锁 owner-safe 治理
- `5b3b74c2` feat(activity-sync): P8.3+P8.4+P8.5 部署门禁补全
- `37ef8b89` refactor(product): P8.5 移除 ProductService 冗余 @Transactional 重载
- `676de811` docs: record successful real-pre deployment
