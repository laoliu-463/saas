package com.colonel.saas.domain.talent.domain;

import com.colonel.saas.entity.ExclusiveTalent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 独家达人持久化抽象（DDD-TALENT-004）。
 *
 * <p>领域应用层只通过本接口访问存储，避免直接注入
 * {@code ExclusiveTalentMapper}。当前实现委派给 legacy mapper。</p>
 */
public interface ExclusiveTalentRepository {

    Optional<ExclusiveTalent> findActiveByTalentUid(String talentUid, String effectiveMonth);

    Optional<ExclusiveTalent> findByTalentUidAndMonth(String talentUid, String effectiveMonth);

    void save(ExclusiveTalent record);

    void update(ExclusiveTalent record);

    /**
     * 查询某生效月已存在的全部独家记录；评估完成后用于与新评估结果对比，识别
     * 需要发布 {@code ExclusiveTalentExpiredEvent} 的记录。
     */
    List<ExclusiveTalent> listByEffectiveMonth(String effectiveMonth);

    /**
     * 评估上下文：渠道-达人单条聚合数据 + 阈值。
     */
    record ChannelTalentAggregate(
            String talentUid,
            UUID channelUserId,
            UUID deptId,
            long channelFee,
            long totalFee,
            int sampleCount,
            BigDecimal ratio,
            BigDecimal ratioThreshold,
            int sampleThreshold) {
    }
}