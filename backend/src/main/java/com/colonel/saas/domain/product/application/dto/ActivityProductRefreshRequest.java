package com.colonel.saas.domain.product.application.dto;

/**
 * 活动商品快照刷新请求。
 *
 * <p>用于隔离 Controller 与抖音 Gateway 内部请求类型，保持活动商品刷新入口的请求字段不变。</p>
 */
public record ActivityProductRefreshRequest(
        String appId,
        String activityId,
        Long searchType,
        Long sortType,
        Integer count,
        String cooperationInfo,
        Integer cooperationType,
        String productInfo,
        Integer status,
        Long retrieveMode,
        String cursor,
        Long page
) {
}
