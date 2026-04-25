package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.TalentFollowRecord;
import com.colonel.saas.mapper.TalentFollowRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TalentFollowService {

    private final TalentFollowRecordMapper talentFollowRecordMapper;

    public TalentFollowService(TalentFollowRecordMapper talentFollowRecordMapper) {
        this.talentFollowRecordMapper = talentFollowRecordMapper;
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
        TalentFollowRecord record = new TalentFollowRecord();
        record.setActivityId(activityId);
        record.setProductId(productId);
        record.setTalentId(talentId);
        record.setTalentName(talentName);
        record.setFollowStatus(followStatus);
        record.setContent(content);
        record.setNextFollowTime(nextFollowTime);
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        talentFollowRecordMapper.insert(record);
        return record;
    }

    public List<TalentFollowRecord> listByProduct(String activityId, String productId) {
        return talentFollowRecordMapper.selectList(new LambdaQueryWrapper<TalentFollowRecord>()
                .eq(TalentFollowRecord::getActivityId, activityId)
                .eq(TalentFollowRecord::getProductId, productId)
                .orderByDesc(TalentFollowRecord::getCreateTime));
    }
}
