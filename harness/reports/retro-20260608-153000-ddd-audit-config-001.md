# Retro Report (DDD-AUDIT-CONFIG-001)

## 1. 任务回顾

本次任务是配置域 DDD 只读审查（Phase 0）。目标在于弄清配置域底层 CRUD 模型、缓存失效细节、配置 key 阶段分布以及非配置域越界直查配置表的情况，为后续的 Facade 重构提供真实的事实依据。

---

## 2. 核心经验与反思

* **表与服务隔离债：** 业绩计算 `CommissionService`、独家达人 `ExclusiveTalentService` 和独家商家 `ExclusiveMerchantService` 绕过业务规则缓存直接用 SQL 直查 `system_config` 表是重灾区。开发人员可能出于“月度定时任务”或“性能”考量而写了原生 SQL，但事实上导致了严重的隐式依赖。
* **事务级失效保障：** 配置域自身的 `ConfigChangedCacheInvalidationListener` 实现良好，采用 `AFTER_COMMIT` 避免了事务回滚导致的本地缓存误驱逐，可作为后续其他域设计缓存驱逐监听器的模板。

---

## 3. Harness 优化建议

* 当前配置变更虽然发布了 outbox 事件和 Spring 事件，但对于分布式环境下（如果以后转为多实例部署）的本地缓存在缺少 Redis Pub/Sub 或 MQ 机制时会有延迟不一致风险。在 V2 架构演进中可以考虑引入基于 Redis 的统一缓存驱逐，但在 V1 阶段本地 TTL 5m 能够保证最终一致性，可以暂维持现状。

---

## 4. 下一步任务建议

* 建议下一步执行 **DDD-AUDIT-TALENT-001**（达人域只读审查）。
* **原因：** 达人域在渠道链中扮演认领达人、独家保护期匹配等核心逻辑角色。在厘清配置域保护期等参数读取路径后，达人域只读审查可进一步明确渠道链前半段的归属规则以及与配置域 Facade 的调用契约。
