package com.colonel.saas.domain.talent.facade;

import com.colonel.saas.domain.talent.facade.dto.TalentReadDTO;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 达人域只读门面（DDD-TALENT-001）。
 * <p>
 * 寄样域、订单归因、业绩域等应通过本接口读取达人主数据，
 * 禁止新增跨域 {@code TalentMapper} / {@code TalentClaimMapper} 注入。
 * 第一版内部委派既有 Mapper，不改变线上行为。
 * </p>
 */
public interface TalentDomainFacade {

    /** 按内部主键查询达人，不存在时返回 null。 */
    TalentReadDTO findTalentById(UUID talentId);

    /** 按抖音 UID 查询有效达人，不存在时返回 null。 */
    TalentReadDTO findByDouyinUid(String douyinUid);

    /** 达人是否存在（按内部主键）。 */
    boolean existsById(UUID talentId);

    /**
     * 批量加载达人昵称，返回 talentId → nickname 映射。
     * 自动过滤 null 和重复 ID；缺失记录不包含在结果中。
     */
    Map<UUID, String> loadNicknamesByIds(Collection<UUID> ids);
}
