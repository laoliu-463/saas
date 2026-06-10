/**
 * 配置域 — DDD 目标包根（DDD-BASE-004）。
 * <p>职责：系统参数、提成/寄样规则、推广模板、配置变更事件。</p>
 * <p>不负责：执行具体业务规则；配置变更自动重算历史业绩。</p>
 * <p>Facade：{@link com.colonel.saas.domain.config.facade.ConfigDomainFacade}（DDD-CONFIG-002 寄样/达人配置已接入）</p>
 */
package com.colonel.saas.domain.config;
