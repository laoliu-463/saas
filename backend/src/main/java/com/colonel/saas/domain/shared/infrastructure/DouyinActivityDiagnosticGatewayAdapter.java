package com.colonel.saas.domain.shared.infrastructure;

import com.colonel.saas.domain.shared.application.dto.DouyinActivityListProbeQuery;
import com.colonel.saas.domain.shared.application.dto.DouyinActivityMutateProbeCommand;
import com.colonel.saas.domain.shared.application.port.DouyinActivityDiagnosticPort;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Gateway adapter for Douyin activity diagnostic probes.
 */
@Component
public class DouyinActivityDiagnosticGatewayAdapter implements DouyinActivityDiagnosticPort {

    private final DouyinActivityGateway douyinActivityGateway;

    public DouyinActivityDiagnosticGatewayAdapter(DouyinActivityGateway douyinActivityGateway) {
        this.douyinActivityGateway = douyinActivityGateway;
    }

    @Override
    public Map<String, Object> listActivities(DouyinActivityListProbeQuery query) {
        return douyinActivityGateway.listActivities(new DouyinActivityGateway.ActivityListQuery(
                query.appId(),
                query.status(),
                query.searchType(),
                query.sortType(),
                query.page(),
                query.pageSize(),
                query.activityInfo()))
                .toMap();
    }

    @Override
    public Map<String, Object> activityDetail(String appId, String activityId) {
        return douyinActivityGateway.activityDetail(appId, activityId);
    }

    @Override
    public Map<String, Object> cancelActivityProduct(String appId, Map<String, Object> payload) {
        return douyinActivityGateway.cancelActivityProduct(appId, payload);
    }

    @Override
    public Map<String, Object> createOrUpdateActivity(DouyinActivityMutateProbeCommand command) {
        return douyinActivityGateway.createOrUpdateActivity(new DouyinActivityGateway.ActivityMutateCommand(
                command.appId(),
                command.activityId(),
                command.applicationLimited(),
                command.isNewShop(),
                command.shopType(),
                command.activityName(),
                command.activityDesc(),
                command.applyStartTime(),
                command.applyEndTime(),
                command.commissionRate(),
                command.serviceRate(),
                command.wechatId(),
                command.phoneNum(),
                command.estimatedSingleSale(),
                command.activityType(),
                command.specifiedShopIds(),
                command.online(),
                command.categories(),
                command.shopScore(),
                command.minPromotionDays(),
                command.thresholdCrossBorder(),
                command.minExclusionDuration(),
                command.adCommissionRate(),
                command.adServiceRate(),
                command.cosLimitType()));
    }
}
