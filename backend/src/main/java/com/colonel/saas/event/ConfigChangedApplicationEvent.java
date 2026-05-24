package com.colonel.saas.event;

import java.util.Set;

/**
 * 配置变更事务提交后发布，用于失效本地/Redis 缓存。
 */
public record ConfigChangedApplicationEvent(Set<String> changedKeys) {
}
