package com.colonel.saas.domain.shared.application.port;

import com.colonel.saas.domain.shared.application.dto.DouyinActivityListProbeQuery;
import com.colonel.saas.domain.shared.application.dto.DouyinActivityMutateProbeCommand;

import java.util.Map;

/**
 * Port for Douyin activity diagnostic probes.
 */
public interface DouyinActivityDiagnosticPort {

    Map<String, Object> listActivities(DouyinActivityListProbeQuery query);

    Map<String, Object> activityDetail(String appId, String activityId);

    Map<String, Object> cancelActivityProduct(String appId, Map<String, Object> payload);

    Map<String, Object> createOrUpdateActivity(DouyinActivityMutateProbeCommand command);
}
