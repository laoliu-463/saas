package com.colonel.saas.domain.shared.application;

import com.colonel.saas.domain.shared.application.dto.DouyinActivityListProbeQuery;
import com.colonel.saas.domain.shared.application.dto.DouyinActivityMutateProbeCommand;
import com.colonel.saas.domain.shared.application.port.DouyinActivityDiagnosticPort;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Douyin activity diagnostic application service.
 */
@Service
public class DouyinActivityDiagnosticService {

    private final DouyinActivityDiagnosticPort douyinActivityDiagnosticPort;

    public DouyinActivityDiagnosticService(DouyinActivityDiagnosticPort douyinActivityDiagnosticPort) {
        this.douyinActivityDiagnosticPort = douyinActivityDiagnosticPort;
    }

    public Map<String, Object> listActivities(DouyinActivityListProbeQuery query) {
        return douyinActivityDiagnosticPort.listActivities(query);
    }

    public Map<String, Object> activityDetail(String appId, String activityId) {
        return douyinActivityDiagnosticPort.activityDetail(appId, activityId);
    }

    public Map<String, Object> cancelActivityProduct(String appId, Map<String, Object> payload) {
        return douyinActivityDiagnosticPort.cancelActivityProduct(appId, payload);
    }

    public Map<String, Object> createOrUpdateActivity(DouyinActivityMutateProbeCommand command) {
        return douyinActivityDiagnosticPort.createOrUpdateActivity(command);
    }
}
