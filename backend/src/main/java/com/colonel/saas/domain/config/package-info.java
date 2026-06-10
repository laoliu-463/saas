/**
 * 配置域 — DDD 目标包根（DDD-BASE-004）。
 * <p>职责：系统参数、提成/寄样规则、推广模板、配置变更事件。</p>
 * <p>不负责：执行具体业务规则；配置变更自动重算历史业绩。</p>
 * <p>Facade：{@link com.colonel.saas.domain.config.facade.ConfigDomainFacade}
 *   <ul>
 *     <li>DDD-CONFIG-002：寄样/达人核心阈值（限频天数、超时天数、保护期、独家判定阈值）</li>
 *     <li>DDD-CONFIG-001：通用只读入口（getConfig / getString / getInt / getDecimal / getBoolean / getJson）
 *         + 聚合 DTO（佣金比例 / 寄样规则 / 达人规则 / 推广模板 / 商家规则）</li>
 *   </ul>
 * </p>
 */
package com.colonel.saas.domain.config;
