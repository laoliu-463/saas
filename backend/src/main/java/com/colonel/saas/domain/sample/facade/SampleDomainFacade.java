package com.colonel.saas.domain.sample.facade;

import com.colonel.saas.domain.sample.facade.dto.TalentRecentSampleDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 寄样域只读门面（DDD-SAMPLE-007 Batch3）。
 *
 * <p>其他域与 Controller 读路径应通过本接口校验寄样单存在性，
 * 禁止新增跨域 {@code SampleRequestMapper} 注入。</p>
 */
public interface SampleDomainFacade {

    /** 寄样申请是否存在（按主键）。 */
    boolean existsById(UUID sampleRequestId);

    /** 按达人 ID 批量统计寄样申请数。 */
    Map<UUID, Long> countSamplesByTalentIds(Set<UUID> talentIds);

    /** 统计指定达人在给定时间后的寄样申请数。 */
    long countSamplesByTalentIdSince(UUID talentId, LocalDateTime since);

    /** 查询指定达人最近的寄样申请记录。 */
    List<TalentRecentSampleDTO> listRecentSamplesByTalentId(UUID talentId, int limit);
}
