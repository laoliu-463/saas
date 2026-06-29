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

    /** 按爬虫/外部达人信息查找或创建业务达人。 */
    TalentReadDTO findOrCreateSampleTalent(String douyinUid, String nickname, Long fansCount);

    /** 渠道用户是否已有效认领指定达人。 */
    boolean hasActiveClaim(UUID talentId, UUID userId);

    /** 订单归因保护：达人是否被其他用户有效认领。 */
    boolean hasActiveClaimOwnerConflict(UUID talentId, UUID userId);

    /** 寄样创建成功后回写收件地址到有效认领记录。 */
    void writeBackClaimAddress(UUID channelUserId, UUID talentId, String recipientName, String recipientPhone, String recipientAddress);

    /** 订单完成寄样时，根据有效认领关系修正样本负责人。 */
    UUID resolveSampleOwnerForOrderCompletion(UUID attributedOwner, UUID talentId);

    /**
     * 批量加载达人昵称，返回 talentId → nickname 映射。
     * 自动过滤 null 和重复 ID；缺失记录不包含在结果中。
     */
    Map<UUID, String> loadNicknamesByIds(Collection<UUID> ids);
}
