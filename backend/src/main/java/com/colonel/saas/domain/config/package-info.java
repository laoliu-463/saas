/**
 * 配置域 — DDD 目标包根（DDD-BASE-004）。
 * <p>职责：系统参数、提成/寄样规则、推广模板、配置变更事件。</p>
 * <p>不负责：执行具体业务规则；配置变更自动重算历史业绩。</p>
 * <p>规划 Facade：ConfigDomainFacade。迁移：DDD-CONFIG-001~002</p>
 */
package com.colonel.saas.domain.config;
