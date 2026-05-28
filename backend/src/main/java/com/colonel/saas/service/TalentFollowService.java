package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.TalentFollowRecord;
import com.colonel.saas.mapper.TalentFollowRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 达人跟单记录服务。
 * <p>
 * 提供达人跟单记录的创建与查询能力，用于记录运营人员对达人的跟进状态、跟进内容及下次跟单时间。
 * </p>
 *
 * <ul>
 *     <li>创建跟单记录（{@link #createRecord}）</li>
 *     <li>按商品维度查询跟单记录列表（{@link #listByProduct}）</li>
 * </ul>
 *
 * <p><b>业务域：</b>达人域 — 跟单管理</p>
 * <p><b>协作关系：</b></p>
 * <ul>
 *     <li>{@link TalentFollowRecordMapper} — 跟单记录持久化</li>
 * </ul>
 */
@Service
public class TalentFollowService {

    /** 跟单记录数据访问 Mapper */
    private final TalentFollowRecordMapper talentFollowRecordMapper;

    public TalentFollowService(TalentFollowRecordMapper talentFollowRecordMapper) {
        this.talentFollowRecordMapper = talentFollowRecordMapper;
    }

    /**
     * 创建达人跟单记录。
     * <p>处理流程：</p>
     * <ol>
     *     <li>构建 {@link TalentFollowRecord} 实体，填充活动、商品、达人及跟单信息</li>
     *     <li>持久化到数据库</li>
     *     <li>返回包含自增主键的记录实体</li>
     * </ol>
     *
     * @param activityId     活动 ID
     * @param productId      商品 ID
     * @param talentId       达人 ID
     * @param talentName     达人名称
     * @param followStatus   跟单状态
     * @param content        跟单内容描述
     * @param nextFollowTime 下次跟单时间（可为 null）
     * @param operatorId     操作人 ID
     * @param operatorName   操作人姓名
     * @return 持久化后的跟单记录（包含自增主键）
     */
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
        // 第一步：构建跟单记录实体
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
        // 第二步：持久化到数据库
        talentFollowRecordMapper.insert(record);
        return record;
    }

    /**
     * 按商品维度查询跟单记录列表。
     * <p>根据活动 ID 和商品 ID 精确匹配，按创建时间倒序返回。</p>
     *
     * @param activityId 活动 ID
     * @param productId  商品 ID
     * @return 跟单记录列表（按创建时间倒序）
     */
    public List<TalentFollowRecord> listByProduct(String activityId, String productId) {
        return talentFollowRecordMapper.selectList(new LambdaQueryWrapper<TalentFollowRecord>()
                .eq(TalentFollowRecord::getActivityId, activityId)
                .eq(TalentFollowRecord::getProductId, productId)
                .orderByDesc(TalentFollowRecord::getCreateTime));
    }
}
