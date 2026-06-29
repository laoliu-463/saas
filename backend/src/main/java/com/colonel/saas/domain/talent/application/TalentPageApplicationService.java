package com.colonel.saas.domain.talent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 达人分页查询应用层 (DDD-TALENT-04 Slice 10).
 *
 * <p>承接 Controller 的达人列表分页查询入口，自包含业务编排 + 数据范围 Policy 双委派。
 * 1:1 等价 TalentService.page(...) 35 行业务 + 4 个 applyPageDataScope helper。
 * Legacy {@code TalentService} 保留为薄壳委派壳。</p>
 *
 * <p><b>数据范围策略：</b>
 * <ul>
 *   <li>旧模式 (DddRefactorProperties.DataScopePolicy.Enabled=false): 按 DataScope + userId/deptId 决定查 active claims</li>
 *   <li>新模式 (Enabled=true): 通过 DataScopePolicy.decide() 决策</li>
 * </ul>
 *
 * <p><b>业务域：</b>达人域 — 分页查询</p>
 */
@Service
public class TalentPageApplicationService {

    private final TalentMapper talentMapper;
    private final TalentClaimMapper talentClaimMapper;
    private final DataScopePolicy dataScopePolicy;
    private final DddRefactorProperties dddRefactorProperties;

    public TalentPageApplicationService(
            TalentMapper talentMapper,
            TalentClaimMapper talentClaimMapper,
            DataScopePolicy dataScopePolicy,
            DddRefactorProperties dddRefactorProperties) {
        this.talentMapper = talentMapper;
        this.talentClaimMapper = talentClaimMapper;
        this.dataScopePolicy = dataScopePolicy;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    /**
     * 分页查询达人列表。
     * 1:1 等价 TalentService.page(long, long, String, String, Long, Long, DataScope, UUID, UUID) 35 行业务。
     */
    public IPage<Talent> page(long page,
                               long size,
                               String keyword,
                               String region,
                               Long minFans,
                               Long maxFans,
                               DataScope dataScope,
                               UUID userId,
                               UUID deptId) {
        LambdaQueryWrapper<Talent> wrapper = new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDeleted, 0)
                .orderByDesc(Talent::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Talent::getNickname, keyword)
                    .or().like(Talent::getDouyinUid, keyword)
                    .or().like(Talent::getDouyinNo, keyword)
                    .or().like(Talent::getUid, keyword)
                    .or().like(Talent::getSecUid, keyword));
        }
        if (StringUtils.hasText(region)) {
            wrapper.like(Talent::getIpLocation, region);
        }
        if (minFans != null) {
            wrapper.ge(Talent::getFans, minFans);
        }
        if (maxFans != null) {
            wrapper.le(Talent::getFans, maxFans);
        }

        boolean hasScopedClaims = applyPageDataScope(wrapper, dataScope, userId, deptId);
        if (!hasScopedClaims) {
            return new Page<>(page, size, 0L);
        }
        return talentMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 应用分页数据范围。
     * 1:1 等价 TalentService.applyPageDataScope(...) 6 行 helper。
     */
    private boolean applyPageDataScope(
            LambdaQueryWrapper<Talent> wrapper,
            DataScope dataScope,
            UUID userId,
            UUID deptId) {
        if (!dddRefactorProperties.getDataScopePolicy().isEnabled()) {
            return applyPageDataScopeLegacy(wrapper, dataScope, userId, deptId);
        }
        return applyPageDataScopeWithPolicy(wrapper, dataScope, userId, deptId);
    }

    /**
     * 旧版数据范围（硬编码 PERSONAL/DEPT 逻辑）。
     * 1:1 等价 TalentService.applyPageDataScopeLegacy(...) 11 行 helper。
     */
    private boolean applyPageDataScopeLegacy(
            LambdaQueryWrapper<Talent> wrapper,
            DataScope dataScope,
            UUID userId,
            UUID deptId) {
        if (dataScope == DataScope.PERSONAL && userId != null) {
            return applyClaimedTalentFilter(
                    wrapper,
                    talentClaimMapper.findActiveByUserId(userId));
        }
        if (dataScope == DataScope.DEPT && deptId != null) {
            return applyClaimedTalentFilter(
                    wrapper,
                    talentClaimMapper.findActiveByDeptId(deptId));
        }
        return true;
    }

    /**
     * 新版数据范围（DataScopePolicy 决策）。
     * 1:1 等价 TalentService.applyPageDataScopeWithPolicy(...) 17 行 helper。
     */
    private boolean applyPageDataScopeWithPolicy(
            LambdaQueryWrapper<Talent> wrapper,
            DataScope dataScope,
            UUID userId,
            UUID deptId) {
        DataScopePolicy.ContextRequirement requirement =
                dataScopePolicy.contextRequirement(userId, deptId, dataScope);
        if (requirement != DataScopePolicy.ContextRequirement.SATISFIED) {
            return true;
        }
        DataScopePolicy.Decision decision = dataScopePolicy.decide(userId, deptId, dataScope);
        if (decision == DataScopePolicy.Decision.FILTER_USER) {
            return applyClaimedTalentFilter(
                    wrapper,
                    talentClaimMapper.findActiveByUserId(userId));
        }
        if (decision == DataScopePolicy.Decision.FILTER_DEPT) {
            return applyClaimedTalentFilter(
                    wrapper,
                    talentClaimMapper.findActiveByDeptId(deptId));
        }
        return true;
    }

    /**
     * 应用已认领达人过滤器。
     * 1:1 等价 TalentService.applyClaimedTalentFilter(...) 10 行 helper。
     */
    private boolean applyClaimedTalentFilter(
            LambdaQueryWrapper<Talent> wrapper,
            List<TalentClaim> claims) {
        Set<UUID> ids = claims.stream()
                .map(TalentClaim::getTalentId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return false;
        }
        wrapper.in(Talent::getId, ids);
        return true;
    }
}