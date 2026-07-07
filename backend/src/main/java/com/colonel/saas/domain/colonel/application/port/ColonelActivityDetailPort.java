package com.colonel.saas.domain.colonel.application.port;

import java.util.Map;

/**
 * 团长活动详情端口。
 */
public interface ColonelActivityDetailPort {

    Map<String, Object> getDouyinDetail(String appId, String activityId);
}
