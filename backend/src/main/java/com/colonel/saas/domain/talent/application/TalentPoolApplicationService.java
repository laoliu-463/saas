package com.colonel.saas.domain.talent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 达人公开池/私有池查询应用层 (DDD-TALENT-04 Slice 8).
 *
 * <p>承接 Controller 的公开池（未认领活跃达人）和私有池（用户已认领达人）
 * 查询入口，自包含业务编排，保留 1:1 行为等价。
 * Legacy {@code TalentService} 保留为薄壳委派壳。</p>
 *
 * <p><b>业务规则：</b>
 * <ul>
 *   <li>公开池：未删除 (deleted=0)、启用 (status=1)、未拉黑、按粉丝数降序、上限 500</li>
 *   <li>私有池：用户当前生效认领的达人列表</li>
 * </ul>
 *
 * <p><b>业务域：</b>达人域 — 池查询</p>
 */
@Service
public class TalentPoolApplicationService {

    /** 公开池最大返回数量。1:1 等价 TalentService.PUBLIC_POOL_LIMIT。 */
    public static final int PUBLIC_POOL_LIMIT = 500;

    private final TalentMapper talentMapper;
    private final TalentClaimMapper talentClaimMapper;

    public TalentPoolApplicationService(
            TalentMapper talentMapper,
            TalentClaimMapper talentClaimMapper) {
        this.talentMapper = talentMapper;
        this.talentClaimMapper = talentClaimMapper;
    }

    /**
     * 公开池达人列表。
     * 1:1 等价 TalentService.getPublicPool() 14 行业务。
     */
    public List<Talent> getPublicPool() {
        Set<UUID> claimedTalentIds = getClaimedTalentIds();
        return talentMapper.selectList(new LambdaQueryWrapper<Talent>()
                        .eq(Talent::getDeleted, 0)
                        .eq(Talent::getStatus, 1)
                        .ne(Talent::getBlacklisted, true)
                        .orderByDesc(Talent::getFans)
                        .last("limit " + PUBLIC_POOL_LIMIT))
                .stream()
                .filter(talent -> !claimedTalentIds.contains(talent.getId()))
                .sorted(Comparator.comparing(Talent::getFans, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(PUBLIC_POOL_LIMIT)
                .toList();
    }

    /**
     * 私有池达人列表（用户已认领）。
     * 1:1 等价 TalentService.getPrivatePool(UUID) 10 行业务。
     */
    public List<Talent> getPrivatePool(UUID userId) {
        List<TalentClaim> claims = talentClaimMapper.findActiveByUserId(userId);
        if (claims.isEmpty()) {
            return List.of();
        }
        Set<UUID> talentIds = claims.stream().map(TalentClaim::getTalentId).collect(Collectors.toSet());
        return talentMapper.selectBatchIds(talentIds).stream()
                .limit(PUBLIC_POOL_LIMIT)
                .toList();
    }

    /**
     * 获取所有生效中的认领 talent id 集合。
     * 1:1 等价 TalentService.getClaimedTalentIds() 12 行 helper。
     */
    private Set<UUID> getClaimedTalentIds() {
        List<TalentClaim> claims = talentClaimMapper.selectList(new LambdaQueryWrapper<TalentClaim>()
                .eq(TalentClaim::getStatus, 1) // CLAIM_STATUS_ACTIVE
                .eq(TalentClaim::getDeleted, 0));
        if (claims.isEmpty()) {
            return Collections.emptySet();
        }
        Set<UUID> ids = new HashSet<>();
        for (TalentClaim claim : claims) {
            if (claim.getTalentId() != null) {
                ids.add(claim.getTalentId());
            }
        }
        return ids;
    }
}