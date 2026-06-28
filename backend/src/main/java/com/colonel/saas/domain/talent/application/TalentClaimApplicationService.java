package com.colonel.saas.domain.talent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.order.facade.OrderReadFacade;
import com.colonel.saas.domain.talent.policy.TalentClaimPolicy;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.facade.dto.UserOwnershipReference;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
 *   <li>{@code CLAIM_STATUS_EXPIRED} = 2（已过期）</li>
 *   <li>{@code CLAIM_STATUS_RELEASED} = 3（已释放）</li>
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
    public static final int CLAIM_STATUS_EXPIRED = 2;
    public static final int CLAIM_STATUS_RELEASED = 3;
    private static final long ORDER_BATCH_SIZE = 2000L;

    private final TalentClaimMapper talentClaimMapper;
    private final TalentMapper talentMapper;
    private final OrderReadFacade orderReadFacade;
    private final ConfigDomainFacade configDomainFacade;
    private final UserDomainFacade userDomainFacade;
    private final CurrentUserPermissionPolicy currentUserPermissionPolicy;
    private final DataScopePolicy dataScopePolicy;
    private final OperationLogService operationLogService;
    private final DddRefactorProperties dddRefactorProperties;

    public TalentClaimApplicationService(
            TalentClaimMapper talentClaimMapper,
            TalentMapper talentMapper,
            OrderReadFacade orderReadFacade,
            ConfigDomainFacade configDomainFacade,
            UserDomainFacade userDomainFacade,
            CurrentUserPermissionPolicy currentUserPermissionPolicy,
            DataScopePolicy dataScopePolicy,
            OperationLogService operationLogService,
            DddRefactorProperties dddRefactorProperties) {
        this.talentClaimMapper = talentClaimMapper;
        this.talentMapper = talentMapper;
        this.orderReadFacade = orderReadFacade;
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
            OptimisticLockSupport.requireUpdated(talentClaimMapper.updateById(claim));
        }
    }

    /**
     * 是否有订单产出（用于跳过有效认领）。
     * 1:1 等价 TalentService.hasOutputSinceClaim(Talent, TalentClaim)。
     */
    private boolean hasOutputSinceClaim(Talent talent, TalentClaim claim) {
        if (talent == null || claim == null || !StringUtils.hasText(talent.getDouyinUid())) {
            return false;
        }
        LocalDateTime since = claim.getClaimedAt() == null ? LocalDateTime.now().minusDays(getProtectDays()) : claim.getClaimedAt();
        return loadOrdersCreatedSinceInBatches(since).stream()
                .anyMatch(order -> matchesTalent(order, talent.getDouyinUid()));
    }

    /**
     * 分批加载订单。
     * 1:1 等价 TalentService.loadOrdersInBatches(LambdaQueryWrapper)。
     */
    private List<ColonelsettlementOrder> loadOrdersCreatedSinceInBatches(LocalDateTime since) {
        java.util.List<ColonelsettlementOrder> all = new java.util.ArrayList<>();
        long pageNo = 1L;
        boolean hasMore = true;
        while (hasMore) {
            OrderReadFacade.OrderPage result = orderReadFacade.findOrdersCreatedSince(since, pageNo, ORDER_BATCH_SIZE);
            if (result == null || result.records() == null || result.records().isEmpty()) {
                break;
            }
            all.addAll(result.records());
            hasMore = pageNo < result.pages();
            pageNo++;
        }
        return all;
    }

    /**
     * 匹配达人订单（按 douyinUid 或其他字段）。
     * 1:1 等价 TalentService.matchesTalent(ColonelsettlementOrder, String)。
     */
    private boolean matchesTalent(ColonelsettlementOrder order, String talentDouyinUid) {
        if (order == null || !StringUtils.hasText(talentDouyinUid)) {
            return false;
        }
        if (order.getExtraData() != null) {
            Object authorId = order.getExtraData().get("author_id");
            if (authorId != null && talentDouyinUid.equals(String.valueOf(authorId))) {
                return true;
            }
            Object talentUid = order.getExtraData().get("talent_uid");
            if (talentUid != null && talentDouyinUid.equals(String.valueOf(talentUid))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取达人认领保护期天数。
     */
    public int getProtectDays() {
        return configDomainFacade.getTalentClaimProtectDays();
    }

    /**
     * 拉黑达人。
     * 1:1 等价 TalentService.blacklist(UUID, String)。
     */
    public Talent blacklist(UUID talentId, String reason) {
        return blacklist(talentId, reason, null, null, DataScope.ALL);
    }

    /**
     * 拉黑达人（完整版本）。
     * 1:1 等价 TalentService.blacklist(UUID, String, UUID, UUID, DataScope)。
     */
    public Talent blacklist(UUID talentId, String reason, UUID userId, UUID deptId, DataScope dataScope) {
        Talent talent = talentMapper.selectById(talentId);
        if (talent == null) {
            throw BusinessException.notFound("达人不存在");
        }
        assertCanOperateBlacklist(talentId, userId, deptId, dataScope);
        talent.setBlacklisted(true);
        talent.setBlacklistReason(StringUtils.hasText(reason) ? reason.trim() : "手动拉黑");
        OptimisticLockSupport.requireUpdated(talentMapper.updateById(talent));
        return talent;
    }

    /**
     * 取消拉黑达人。
     * 1:1 等价 TalentService.unblacklist(UUID)。
     */
    public Talent unblacklist(UUID talentId) {
        return unblacklist(talentId, null, null, DataScope.ALL);
    }

    /**
     * 取消拉黑达人（完整版本）。
     * 1:1 等价 TalentService.unblacklist(UUID, UUID, UUID, DataScope)。
     */
    public Talent unblacklist(UUID talentId, UUID userId, UUID deptId, DataScope dataScope) {
        Talent talent = talentMapper.selectById(talentId);
        if (talent == null) {
            throw BusinessException.notFound("达人不存在");
        }
        assertCanOperateBlacklist(talentId, userId, deptId, dataScope);
        talent.setBlacklisted(false);
        talent.setBlacklistReason(null);
        OptimisticLockSupport.requireUpdated(talentMapper.updateById(talent));
        return talent;
    }

    private void assertCanOperateBlacklist(UUID talentId, UUID userId, UUID deptId, DataScope dataScope) {
        if (dataScope == null || dataScope == DataScope.ALL) {
            return;
        }
        List<TalentClaim> activeClaims = talentClaimMapper.findActiveByTalentId(talentId);
        if (activeClaims.isEmpty()) {
            return;
        }
        if (!dddRefactorProperties.getDataScopePolicy().isEnabled()) {
            assertCanOperateBlacklistLegacy(activeClaims, userId, deptId, dataScope);
            return;
        }
        assertCanOperateBlacklistWithPolicy(activeClaims, userId, deptId, dataScope);
    }

    private void assertCanOperateBlacklistLegacy(
            List<TalentClaim> activeClaims,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        if (dataScope == DataScope.PERSONAL) {
            assertCanOperateBlacklistAllowed(hasActiveClaimForUser(activeClaims, userId));
            return;
        }
        assertCanOperateBlacklistAllowed(hasActiveClaimForDept(activeClaims, deptId));
    }

    private void assertCanOperateBlacklistWithPolicy(
            List<TalentClaim> activeClaims,
            UUID userId,
            UUID deptId,
            DataScope dataScope) {
        DataScopePolicy.ContextRequirement requirement =
                dataScopePolicy.contextRequirement(userId, deptId, dataScope);
        if (requirement != DataScopePolicy.ContextRequirement.SATISFIED) {
            throw new ForbiddenException("无权操作该达人");
        }
        DataScopePolicy.Decision decision = dataScopePolicy.decide(userId, deptId, dataScope);
        if (decision == DataScopePolicy.Decision.FILTER_USER) {
            assertCanOperateBlacklistAllowed(hasActiveClaimForUser(activeClaims, userId));
            return;
        }
        if (decision == DataScopePolicy.Decision.FILTER_DEPT) {
            assertCanOperateBlacklistAllowed(hasActiveClaimForDept(activeClaims, deptId));
        }
    }

    private boolean hasActiveClaimForUser(List<TalentClaim> activeClaims, UUID userId) {
        return userId != null && activeClaims.stream()
                .anyMatch(claim -> userId.equals(claim.getUserId()));
    }

    private boolean hasActiveClaimForDept(List<TalentClaim> activeClaims, UUID deptId) {
        return deptId != null && activeClaims.stream()
                .anyMatch(claim -> deptId.equals(claim.getDeptId()));
    }

    private void assertCanOperateBlacklistAllowed(boolean allowed) {
        if (!allowed) {
            throw new ForbiddenException("无权操作该达人");
        }
    }
}
