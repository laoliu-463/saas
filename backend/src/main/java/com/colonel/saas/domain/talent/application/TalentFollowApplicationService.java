package com.colonel.saas.domain.talent.application;

import com.colonel.saas.entity.TalentFollowRecord;
import com.colonel.saas.service.TalentFollowService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 达人跟进应用层。
 *
 * <p>商品域只通过本应用层创建和读取达人跟进记录；具体持久化仍由
 * Legacy {@link TalentFollowService} 承接，避免本切片改变现有表结构和行为。</p>
 */
@Service
public class TalentFollowApplicationService {

    private final TalentFollowService talentFollowService;

    public TalentFollowApplicationService(TalentFollowService talentFollowService) {
        this.talentFollowService = talentFollowService;
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
        return talentFollowService.createRecord(
                activityId,
                productId,
                talentId,
                talentName,
                followStatus,
                content,
                nextFollowTime,
                operatorId,
                operatorName);
    }

    public List<TalentFollowRecord> listByProduct(String activityId, String productId) {
        return talentFollowService.listByProduct(activityId, productId);
    }
}
