package com.colonel.saas.domain.colonel.application;

import com.colonel.saas.domain.colonel.application.port.ColonelActivityDetailPort;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 团长活动详情查询应用服务。
 */
@Service
public class ColonelActivityDetailQueryService {

    private final ColonelActivityDetailPort colonelActivityDetailPort;

    public ColonelActivityDetailQueryService(ColonelActivityDetailPort colonelActivityDetailPort) {
        this.colonelActivityDetailPort = colonelActivityDetailPort;
    }

    public Map<String, Object> getDouyinDetail(String appId, String activityId) {
        return colonelActivityDetailPort.getDouyinDetail(appId, activityId);
    }
}
