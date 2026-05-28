package com.colonel.saas.event;

import java.util.Set;

/**
 * 系统配置变更事件。
 * <p>
 * 当 {@code SystemConfig} 表中的配置项发生变更，且数据库事务提交成功后，
 * 通过 Spring 的 {@code ApplicationEventPublisher} 发布此事件。监听方收到事件后
 * 应立即失效本地缓存和 Redis 缓存，确保后续请求读取到最新配置值。
 * </p>
 * <p>
 * 该事件是跨域配置同步的核心机制：商品展示规则、业绩费率、权限缓存等均依赖
 * 此事件感知配置变更。
 * </p>
 *
 * @param changedKeys 发生变更的配置键集合，监听方可据此做精确缓存失效，避免全量刷新
 *
 * @see com.colonel.saas.listener.ConfigChangedCacheInvalidationListener
 */
public record ConfigChangedApplicationEvent(
        /** 发生变更的配置键集合，用于精确缓存失效 */
        Set<String> changedKeys) {
}
