package com.colonel.saas.service;

import com.colonel.saas.domain.talent.application.TalentFollowApplicationService;
import com.colonel.saas.entity.TalentFollowRecord;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 达人跟单记录服务（DDD 委派壳）。
 *
 * <p>委派到 {@link TalentFollowApplicationService}，保留旧签名以兼容 Controller 调用方。
 * 生产路径业务规则由 DDD Application 实现。</p>
 *
 * <p><b>业务域：</b>达人域 — 跟单管理</p>
 *
 * @deprecated 请直接注入 {@link TalentFollowApplicationService}
 */
@Service
@Deprecated
public class TalentFollowService {

    private final TalentFollowApplicationService applicationService;

    public TalentFollowService(TalentFollowApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public TalentFollowRecord createRecord(
            String activityId,
            String productId,
            UUID talentId,
            String talentName,
            String followStatus,
            String content,
            LocalDateTime nextFollowTime,
            UUID operatorId,
            String operatorName) {
        return applicationService.createRecord(
                activityId, productId, talentId, talentName, followStatus,
                content, nextFollowTime, operatorId, operatorName);
    }

    public List<TalentFollowRecord> listByProduct(String activityId, String productId) {
        return applicationService.listByProduct(activityId, productId);
    }
}