/**
 * 订单域 — DDD 目标包根（DDD-BASE-004）。
 * <p>职责：同步落库、双轨金额、默认归因、订单已同步事件。</p>
 * <p>不负责：提成、独家覆盖、寄样完成写库。</p>
 * <p>迁移：DDD-ORDER-001~005、DDD-CLEAN-001</p>
 */
package com.colonel.saas.domain.order;
