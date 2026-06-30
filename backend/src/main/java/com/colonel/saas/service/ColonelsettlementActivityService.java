package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.domain.colonel.application.ColonelsettlementActivityApplicationService;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 活动列表服务（DDD 委派壳，DDD-COLONEL-002 Slice 2）。
 *
 * <p>本类为 1-line 委派壳，所有真实业务逻辑已搬至
 * {@link ColonelsettlementActivityApplicationService}。保留本壳以兼容遗留调用方。</p>
 */
@Service
public class ColonelsettlementActivityService {

    private final ColonelsettlementActivityApplicationService applicationService;

    public ColonelsettlementActivityService(@Lazy ColonelsettlementActivityApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public IPage<ColonelsettlementActivity> getPage(long page, long size, Integer status) {
        return applicationService.getPage(page, size, status);
    }

    public boolean syncActivitySummaryFromUpstream(String activityId, String appId) {
        return applicationService.syncActivitySummaryFromUpstream(activityId, appId);
    }

    public void syncFromGatewayItem(DouyinActivityGateway.ActivityItem item) {
        applicationService.syncFromGatewayItem(item);
    }

    public ColonelsettlementActivity findByActivityId(String activityId) {
        return applicationService.findByActivityId(activityId);
    }

    public Map<String, ColonelsettlementActivity> findAssignmentsByActivityIds(List<String> activityIds) {
        return applicationService.findAssignmentsByActivityIds(activityIds);
    }

    public boolean isPromotingActivity(String activityId) {
        return applicationService.isPromotingActivity(activityId);
    }

    public Map<String, Object> buildAssignmentListPage(
            long page,
            long pageSize,
            Integer activityStatusCode,
            String assignmentFilter,
            UUID recruiterUserId,
            String activityKeyword,
            Function<UUID, String> userNameResolver) {
        return applicationService.buildAssignmentListPage(
                page, pageSize, activityStatusCode, assignmentFilter, recruiterUserId, activityKeyword, userNameResolver);
    }
}