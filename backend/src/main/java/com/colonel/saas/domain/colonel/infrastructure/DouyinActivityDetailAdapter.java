package com.colonel.saas.domain.colonel.infrastructure;

import com.colonel.saas.domain.colonel.application.port.ColonelActivityDetailPort;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 团长活动详情端口的 Douyin Gateway 适配器。
 */
@Component
public class DouyinActivityDetailAdapter implements ColonelActivityDetailPort {

    private final DouyinActivityGateway douyinActivityGateway;

    public DouyinActivityDetailAdapter(DouyinActivityGateway douyinActivityGateway) {
        this.douyinActivityGateway = douyinActivityGateway;
    }

    @Override
    public Map<String, Object> getDouyinDetail(String appId, String activityId) {
        return douyinActivityGateway.activityDetail(appId, activityId);
    }
}
