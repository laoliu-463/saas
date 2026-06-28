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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
    private final RedisTemplate<String, Object> redisTemplate;

    public TalentClaimApplicationService(
            TalentClaimMapper talentClaimMapper,
            TalentMapper talentMapper,
            OrderReadFacade orderReadFacade,
            ConfigDomainFacade configDomainFacade,
            UserDomainFacade userDomainFacade,
            CurrentUserPermissionPolicy currentUserPermissionPolicy,
            DataScopePolicy dataScopePolicy,
            OperationLogService operationLogService,
            DddRefactorProperties dddRefactorProperties,
            RedisTemplate<String, Object> redisTemplate) {
        this.talentClaimMapper = talentClaimMapper;
        this.talentMapper = talentMapper;
        this.orderReadFacade = orderReadFacade;
        this.configDomainFacade = configDomainFacade;
        this.userDomainFacade = userDomainFacade;
        this.currentUserPermissionPolicy = currentUserPermissionPolicy;
        this.dataScopePolicy = dataScopePolicy;
        this.operationLogService = operationLogService;
        this.dddRefactorProperties = dddRefactorProperties;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 认领达人（含 Redis 分布式锁 + 保护期 + 状态机）。
     * 1:1 等价 TalentService.claim(UUID, UUID, UUID) 57 行业务。
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent claim(UUID talentId, UUID userId, UUID deptId) {
        TalentClaimPolicy.requireClaimUser(userId);
        String lockKey = "talent:claim:lock:" + talentId;
        String lockValue = userId.toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                Objects.requireNonNull(lockKey),
                Objects.requireNonNull(lockValue),
                10,
                TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            throw BusinessException.conflict("达人认领处理中，请稍后重试");
        }
        try {
            Talent talent = getById(talentId);
            int protectDays = getProtectDays();

            TalentClaimPolicy.assertNotDuplicateActiveClaim(
                    talentClaimMapper.findActiveByTalentAndUser(talentId, userId));

            LocalDateTime now = LocalDateTime.now();
            TalentClaim claim = findLatestClaimByTalentAndUser(talentId, userId);
            boolean newClaim = claim == null;
            if (newClaim) {
                claim = new TalentClaim();
                claim.setId(UUID.randomUUID());
                claim.setTalentId(talentId);
                claim.setTalentUid(talent.getDouyinUid());
                claim.setUserId(userId);
            }
            claim.setDeptId(deptId);
            claim.setClaimType(CLAIM_TYPE_MANUAL);
            claim.setClaimedAt(now);
            claim.setProtectedUntil(TalentClaimPolicy.protectedUntil(now, protectDays));
            claim.setStatus(CLAIM_STATUS_ACTIVE);
            if (newClaim) {
                talentClaimMapper.insert(claim);
            } else {
                persistTalentClaim(claim);
            }

            talent.setOwnerId(userId);
            talent.setClaimedAt(now);
            persistTalent(talent);
            operationLogService.recordSystemAction(
                    userId,
                    "达人管理",
                    "认领达人",
                    "POST",
                    "talent",
                    talentId.toString(),
                    talent.getNickname(),
                    String.format("认领达人: 负责人=%s", userId));
            return talent;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 释放达人认领（含权限校验 + owner snapshot）。
     * 1:1 等价 TalentService.release(UUID, UUID, UUID, Collection) 27 行业务。
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent release(UUID talentId, UUID userId, UUID deptId, Collection<?> roleCodes) {
        TalentClaimPolicy.requireClaimUser(userId);
        getById(talentId);

        List<TalentClaim> activeClaims = talentClaimMapper.findActiveByTalentId(talentId);
        boolean isAdmin = currentUserPermissionPolicy.hasAnyRole(roleCodes, RoleCodes.ADMIN);
        TalentClaim releaseTarget = TalentClaimPolicy.selectReleaseTarget(activeClaims, userId, isAdmin);

        releaseTarget.setStatus(CLAIM_STATUS_RELEASED);
        releaseTarget.setProtectedUntil(LocalDateTime.now());
        persistTalentClaim(releaseTarget);

        Talent talent = getById(talentId);
        List<TalentClaim> remainingActiveClaims = talentClaimMapper.findActiveByTalentId(talentId);
        applyReleaseOwnerSnapshot(talent, remainingActiveClaims);
        persistTalent(talent);
        operationLogService.recordSystemAction(
                userId,
                "达人管理",
                "释放达人",
                "POST",
                "talent",
                talentId.toString(),
                talent.getNickname(),
                String.format("释放达人: 操作人=%s, 释放认领=%s", userId, releaseTarget.getId()));
        return talent;
    }

    /**
     * 归属覆盖（管理员强制重新分配）。
     * 1:1 等价 TalentService.overrideTalentAssignment(UUID, UUID, String, UUID) 49 行业务。
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent overrideTalentAssignment(UUID talentId, UUID newUserId, String reason, UUID currentUserId) {
        if (newUserId == null) {
            throw BusinessException.param("新负责人ID不能为空");
        }
        UserOwnershipReference targetUser =
                userDomainFacade.loadUserOwnershipReferencesByIds(List.of(newUserId)).get(newUserId);
        if (targetUser == null) {
            throw BusinessException.notFound("目标负责人不存在");
        }
        Talent talent = getById(talentId);

        // Expire all active claims for this talent
        List<TalentClaim> activeClaims = talentClaimMapper.findActiveByTalentId(talentId);
        LocalDateTime now = LocalDateTime.now();
        for (TalentClaim claim : activeClaims) {
            claim.setStatus(CLAIM_STATUS_EXPIRED);
            claim.setProtectedUntil(now);
            persistTalentClaim(claim);
        }

        // Create a new manual claim for the new user
        TalentClaim newClaim = new TalentClaim();
        newClaim.setId(UUID.randomUUID());
        newClaim.setTalentId(talentId);
        newClaim.setTalentUid(talent.getDouyinUid());
        newClaim.setUserId(newUserId);
        newClaim.setDeptId(null);
        newClaim.setClaimType(CLAIM_TYPE_MANUAL);
        newClaim.setClaimedAt(now);
        newClaim.setProtectedUntil(now.plusDays(getProtectDays()));
        newClaim.setStatus(CLAIM_STATUS_ACTIVE);
        talentClaimMapper.insert(newClaim);

        talent.setOwnerId(newUserId);
        talent.setClaimedAt(now);
        persistTalent(talent);

        operationLogService.recordSystemAction(
                currentUserId,
                "达人管理",
                "归属覆盖",
                "POST",
                "talent",
                talentId.toString(),
                talent.getNickname(),
                String.format("归属覆盖: 新负责人=%s, 原因=%s", newUserId, reason));

        return talent;
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


    /**
     * 获取达人（含软删除校验）。
     * 1:1 等价 TalentService.getById(UUID)。
     */
    public Talent getById(UUID id) {
        Talent talent = talentMapper.selectById(id);
        if (talent == null || (talent.getDeleted() != null && talent.getDeleted() == 1)) {
            throw BusinessException.notFound("达人不存在");
        }
        return talent;
    }

    /**
     * 持久化达人 (含乐观锁)。
     * 1:1 等价 TalentService.persistTalent(Talent)。
     */
    private void persistTalent(Talent talent) {
        OptimisticLockSupport.requireUpdated(talentMapper.updateById(talent));
    }

    /**
     * 持久化达人认领 (含乐观锁)。
     * 1:1 等价 TalentService.persistTalentClaim(TalentClaim)。
     */
    private void persistTalentClaim(TalentClaim claim) {
        OptimisticLockSupport.requireUpdated(talentClaimMapper.updateById(claim));
    }

    /**
     * 查找达人最新认领记录。
     * 1:1 等价 TalentService.findLatestClaimByTalentAndUser(UUID, UUID)。
     */
    private TalentClaim findLatestClaimByTalentAndUser(UUID talentId, UUID userId) {
        return talentClaimMapper.selectOne(new LambdaQueryWrapper<TalentClaim>()
                .eq(TalentClaim::getTalentId, talentId)
                .eq(TalentClaim::getUserId, userId)
                .eq(TalentClaim::getDeleted, 0)
                .orderByDesc(TalentClaim::getClaimedAt)
                .last("limit 1"));
    }

    /**
     * 应用释放后的 owner snapshot。
     * 1:1 等价 TalentService.applyReleaseOwnerSnapshot(Talent, List)。
     */
    private void applyReleaseOwnerSnapshot(Talent talent, List<TalentClaim> activeClaims) {
        List<TalentClaim> remainingClaims = activeClaims == null
                ? List.of()
                : activeClaims.stream()
                        .filter(claim -> claim.getStatus() != null && claim.getStatus() == CLAIM_STATUS_ACTIVE)
                        .sorted(Comparator.<TalentClaim, LocalDateTime>comparing(
                                TalentClaim::getClaimedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList();
        talent.setActiveClaimCount(remainingClaims.size());
        if (remainingClaims.isEmpty()) {
            talent.setOwnerId(null);
            talent.setClaimedAt(null);
            talent.setProtectedUntil(null);
            return;
        }
        TalentClaim nextOwnerClaim = remainingClaims.get(0);
        talent.setOwnerId(nextOwnerClaim.getUserId());
        talent.setClaimedAt(nextOwnerClaim.getClaimedAt());
        talent.setProtectedUntil(remainingClaims.stream()
                .map(TalentClaim::getProtectedUntil)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(nextOwnerClaim.getProtectedUntil()));
    }
}
