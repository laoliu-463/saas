/**
 * 用户域 — DDD 目标包根（DDD-BASE-004）。
 * <p>职责：身份、角色、组织架构、人员主数据、数据范围 self/group/all。</p>
 * <p>不负责：业务规则执行、其他域重复算数据范围。</p>
 * <p>对外 Facade：{@link com.colonel.saas.domain.user.facade.UserDomainFacade}</p>
 * <p>迁移：DDD-USER-001~004、DDD-CLEAN-001</p>
 */
package com.colonel.saas.domain.user;
