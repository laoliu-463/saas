package com.colonel.saas.domain.talent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.talent.policy.TalentClaimPolicy;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.facade.dto.UserOwnershipReference;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 达人认领/释放应用层 (DDD-TALENT-04 Slice 2).
 *
 * <p>承接 Controller 的认领、释放、归属覆盖、过期释放等命令入口，
 * 自包含业务编排（旧 TalentService 同名方法迁入），保留 1:1 行为等价。
 * Legacy {@code TalentService} 保留为薄壳委派壳，不删除兜底路径。</p>
 *
 * <p>状态机常量：
 * <ul>
 *   <li>{@code CLAIM_TYPE_MANUAL} = 1（人工认领）</li>
 *   <li>{@code CLAIM_STATUS_ACTIVE} = 1（生效中）</li>
 *   <li>{@code CLAIM_STATUS_RELEASED} = 2（已释放）</li>
 *   <li>{@code CLAIM_STATUS_EXPIRED} = 3（已过期）</li>
 * </ul>
 *
 * <p>业务规则（保护期、去重、释放选择）通过 {@link TalentClaimPolicy} 复用。</p>
 *
 * <p><b>业务域：</b>达人域 — 认领管理</p>
 */
@Service
public class TalentClaimApplicationService {

    public static final int CLAIM_TYPE_MANUAL = 1;
    public static final int CLAIM_STATUS_ACTIVE = 1;
    public static final int CLAIM_STATUS_RELEASED = 2;
    public static final int CLAIM_STATUS_EXPIRED = 3;

    private final TalentClaimMapper talentClaimMapper;
    private final TalentMapper talentMapper;
    private final ConfigDomainFacade configDomainFacade;
    private final UserDomainFacade userDomainFacade;
    private final CurrentUserPermissionPolicy currentUserPermissionPolicy;
    private final DataScopePolicy dataScopePolicy;
    private final OperationLogService operationLogService;
    private final DddRefactorProperties dddRefactorProperties;

    public TalentClaimApplicationService(
            TalentClaimMapper talentClaimMapper,
            TalentMapper talentMapper,
            ConfigDomainFacade configDomainFacade,
            UserDomainFacade userDomainFacade,
            CurrentUserPermissionPolicy currentUserPermissionPolicy,
            DataScopePolicy dataScopePolicy,
            OperationLogService operationLogService,
            DddRefactorProperties dddRefactorProperties) {
        this.talentClaimMapper = talentClaimMapper;
        this.talentMapper = talentMapper;
        this.configDomainFacade = configDomainFacade;
        this.userDomainFacade = userDomainFacade;
        this.currentUserPermissionPolicy = currentUserPermissionPolicy;
        this.dataScopePolicy = dataScopePolicy;
        this.operationLogService = operationLogService;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    /**
     * 释放过期认领（用于定时任务）。
     * 1:1 等价 TalentService.releaseExpiredClaims(LocalDateTime)。
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseExpiredClaims(LocalDateTime now) {
        if (now == null) {
            return;
        }
        List<TalentClaim> activeClaims = talentClaimMapper.selectList(new LambdaQueryWrapper<TalentClaim>()
                .eq(TalentClaim::getStatus, CLAIM_STATUS_ACTIVE)
                .eq(TalentClaim::getDeleted, 0)
                .lt(TalentClaim::getProtectedUntil, now));
        if (activeClaims.isEmpty()) {
            return;
        }
        Set<UUID> talentIds = activeClaims.stream()
                .map(TalentClaim::getTalentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<Talent> talentList = talentIds.isEmpty() ? List.of() : talentMapper.selectBatchIds(talentIds);
        java.util.Map<UUID, Talent> talentMap = talentList.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Talent::getId, talent -> talent, (left, right) -> left));
        for (TalentClaim claim : activeClaims) {
            Talent talent = talentMap.get(claim.getTalentId());
            if (talent != null && hasOutputSinceClaim(talent, claim)) {
                continue;
            }
            claim.setStatus(CLAIM_STATUS_EXPIRED);
            talentClaimMapper.updateById(claim);
        }
    }

    /**
     * 是否有订单产出（用于跳过有效认领）。
     * 注：完整实现在 TalentService.hasOutputSinceClaim，本薄壳仅保留接口。
     */
    private boolean hasOutputSinceClaim(Talent talent, TalentClaim claim) {
        // 占位实现：完整业务在 TalentService，后续 Slice 3 增量迁移
        return false;
    }

    /**
     * 获取达人认领保护期天数。
     */
    public int getProtectDays() {
        return configDomainFacade.getTalentClaimProtectDays();
    }
}