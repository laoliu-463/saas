package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.dto.talent.TalentBatchImportResult;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.entity.TalentEnrichTask;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentEnrichTaskMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.talent.TalentEnrichOrchestrator;
import com.colonel.saas.service.talent.TalentInputParseResult;
import com.colonel.saas.service.talent.TalentInputParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class TalentService {

    private static final int PUBLIC_POOL_LIMIT = 500;
    private static final long ORDER_BATCH_SIZE = 2000L;
    private static final int CLAIM_STATUS_ACTIVE = 1;
    private static final int CLAIM_STATUS_EXPIRED = 2;
    private static final int CLAIM_STATUS_RELEASED = 3;
    private static final int CLAIM_TYPE_MANUAL = 1;

    private static final String ENRICH_TASK_STATUS_PENDING = "PENDING";
    private static final String ENRICH_TASK_STATUS_RUNNING = "RUNNING";
    private static final String ENRICH_TASK_STATUS_SUCCESS = "SUCCESS";
    private static final String ENRICH_TASK_STATUS_FAILED = "FAILED";
    private static final String ENRICH_TASK_STATUS_WAIT_MANUAL = "WAIT_MANUAL";
    private static final String ENRICH_SOURCE_SYSTEM = "SYSTEM";

    private final TalentMapper talentMapper;
    private final TalentClaimMapper talentClaimMapper;
    private final TalentEnrichTaskMapper talentEnrichTaskMapper;
    private final TalentEnrichOrchestrator talentEnrichOrchestrator;
    private final ColonelsettlementOrderMapper orderMapper;
    private final SampleRequestMapper sampleRequestMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CrawlerTalentInfoService crawlerTalentInfoService;
    private final boolean publicPageCrawlEnabled;
    private final BusinessRuleConfigService businessRuleConfigService;
    private final OperationLogService operationLogService;
    private final SysUserMapper sysUserMapper;

    public TalentService(
            TalentMapper talentMapper,
            TalentClaimMapper talentClaimMapper,
            TalentEnrichTaskMapper talentEnrichTaskMapper,
            TalentEnrichOrchestrator talentEnrichOrchestrator,
            ColonelsettlementOrderMapper orderMapper,
            SampleRequestMapper sampleRequestMapper,
            RedisTemplate<String, Object> redisTemplate,
            CrawlerTalentInfoService crawlerTalentInfoService,
            @Value("${talent.data.public-page-crawl-enabled:false}") boolean publicPageCrawlEnabled,
            BusinessRuleConfigService businessRuleConfigService,
            OperationLogService operationLogService,
            SysUserMapper sysUserMapper) {
        this.talentMapper = talentMapper;
        this.talentClaimMapper = talentClaimMapper;
        this.talentEnrichTaskMapper = talentEnrichTaskMapper;
        this.talentEnrichOrchestrator = talentEnrichOrchestrator;
        this.orderMapper = orderMapper;
        this.sampleRequestMapper = sampleRequestMapper;
        this.redisTemplate = redisTemplate;
        this.crawlerTalentInfoService = crawlerTalentInfoService;
        this.publicPageCrawlEnabled = publicPageCrawlEnabled;
        this.businessRuleConfigService = businessRuleConfigService;
        this.operationLogService = operationLogService;
        this.sysUserMapper = sysUserMapper;
    }

    private int getProtectDays() {
        return businessRuleConfigService.getTalentProtectionDays();
    }

    public List<Talent> getPublicPool() {
        Set<UUID> claimedTalentIds = getClaimedTalentIds();
        return talentMapper.selectList(new LambdaQueryWrapper<Talent>()
                        .eq(Talent::getDeleted, 0)
                        .eq(Talent::getStatus, 1)
                        .ne(Talent::getBlacklisted, true))
                .stream()
                .filter(talent -> !claimedTalentIds.contains(talent.getId()))
                .sorted(Comparator.comparing(Talent::getFans, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(PUBLIC_POOL_LIMIT)
                .toList();
    }

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

        if (dataScope == DataScope.PERSONAL && userId != null) {
            List<TalentClaim> claims = talentClaimMapper.findActiveByUserId(userId);
            Set<UUID> ids = claims.stream().map(TalentClaim::getTalentId).collect(Collectors.toSet());
            if (ids.isEmpty()) {
                return new Page<>(page, size, 0L);
            }
            wrapper.in(Talent::getId, ids);
        } else if (dataScope == DataScope.DEPT && deptId != null) {
            List<TalentClaim> claims = talentClaimMapper.findActiveByDeptId(deptId);
            Set<UUID> ids = claims.stream().map(TalentClaim::getTalentId).collect(Collectors.toSet());
            if (ids.isEmpty()) {
                return new Page<>(page, size, 0L);
            }
            wrapper.in(Talent::getId, ids);
        }
        return talentMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public Talent getById(UUID id) {
        Talent talent = talentMapper.selectById(id);
        if (talent == null || (talent.getDeleted() != null && talent.getDeleted() == 1)) {
            throw BusinessException.notFound("达人不存在");
        }
        return talent;
    }

    @Transactional(rollbackFor = Exception.class)
    public Talent create(Talent request) {
        if (!StringUtils.hasText(request.getDouyinUid())) {
            String fallbackInput = StringUtils.hasText(request.getProfileUrl())
                    ? request.getProfileUrl()
                    : (StringUtils.hasText(request.getDouyinNo()) ? request.getDouyinNo()
                    : (StringUtils.hasText(request.getUid()) ? request.getUid()
                    : request.getSecUid()));
            if (!StringUtils.hasText(fallbackInput)) {
                throw BusinessException.param("达人抖音号或链接不能为空");
            }
            TalentInputParseResult parsed = TalentInputParser.parse(fallbackInput);
            if (StringUtils.hasText(parsed.getDouyinUid())) {
                request.setDouyinUid(parsed.getDouyinUid());
            }
            if (!StringUtils.hasText(request.getDouyinNo()) && StringUtils.hasText(parsed.getDouyinNo())) {
                request.setDouyinNo(parsed.getDouyinNo());
            }
            if (!StringUtils.hasText(request.getUid()) && StringUtils.hasText(parsed.getUid())) {
                request.setUid(parsed.getUid());
            }
            if (!StringUtils.hasText(request.getSecUid()) && StringUtils.hasText(parsed.getSecUid())) {
                request.setSecUid(parsed.getSecUid());
            }
            if (!StringUtils.hasText(request.getProfileUrl()) && StringUtils.hasText(parsed.getProfileUrl())) {
                request.setProfileUrl(parsed.getProfileUrl());
            }
        }
        if (!StringUtils.hasText(request.getDouyinUid())) {
            throw BusinessException.param("douyinUid 不能为空");
        }
        Talent existing = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, request.getDouyinUid())
                .last("limit 1"));
        if (existing != null) {
            throw BusinessException.duplicate("达人 douyinUid 已存在");
        }
        request.setStatus(1);
        if (StringUtils.hasText(request.getNickname())) {
            request.setNickname(request.getNickname().trim());
        }
        if (StringUtils.hasText(request.getContactPhone())) {
            request.setContactPhone(request.getContactPhone().trim());
        }
        if (StringUtils.hasText(request.getContactWechat())) {
            request.setContactWechat(request.getContactWechat().trim());
        }
        if (StringUtils.hasText(request.getIntro())) {
            request.setIntro(request.getIntro().trim());
        }
        if (!StringUtils.hasText(request.getDouyinAccount()) && StringUtils.hasText(request.getDouyinNo())) {
            request.setDouyinAccount(request.getDouyinNo().trim());
        }
        if (!StringUtils.hasText(request.getTalentUid()) && StringUtils.hasText(request.getUid())) {
            request.setTalentUid(request.getUid().trim());
        }
        if (request.getUnsupportedFields() == null || request.getUnsupportedFields().isEmpty()) {
            request.setUnsupportedFields(List.of("talentLevel", "sales30d"));
        }
        request.setId(UUID.randomUUID());
        talentMapper.insert(request);
        boolean profilePrefilled = StringUtils.hasText(request.getDataSource()) && StringUtils.hasText(request.getSyncStatus());
        if (profilePrefilled) {
            request.setLastSyncTime(LocalDateTime.now());
            persistTalent(request);
            return request;
        }
        TalentEnrichTask task = createEnrichTask(request, ENRICH_TASK_STATUS_PENDING, null);
        markEnrichTask(task, ENRICH_TASK_STATUS_RUNNING, null);
        try {
            TalentEnrichOrchestrator.OrchestrateResult orchestrateResult = talentEnrichOrchestrator.enrich(request, false);
            enrichTalentInfo(request, false);
            persistTalent(request);
            if (orchestrateResult.updated()) {
                markEnrichTask(task, ENRICH_TASK_STATUS_SUCCESS, null);
            } else {
                request.setEnrichStatus(ENRICH_TASK_STATUS_WAIT_MANUAL);
                request.setLastEnrichTime(LocalDateTime.now());
                persistTalent(request);
                markEnrichTask(task, ENRICH_TASK_STATUS_WAIT_MANUAL, orchestrateResult.message());
            }
        } catch (RuntimeException ex) {
            request.setEnrichStatus(ENRICH_TASK_STATUS_FAILED);
            request.setLastEnrichTime(LocalDateTime.now());
            persistTalent(request);
            markEnrichTask(task, ENRICH_TASK_STATUS_FAILED, ex.getMessage());
        }
        return request;
    }

    @Transactional(rollbackFor = Exception.class)
    public Talent update(UUID id, Talent request) {
        Talent talent = getById(id);
        if (StringUtils.hasText(request.getNickname())) {
            talent.setNickname(request.getNickname());
        }
        if (request.getFans() != null) {
            talent.setFans(request.getFans());
        }
        if (StringUtils.hasText(request.getLevel())) {
            talent.setLevel(request.getLevel());
        }
        if (request.getStatus() != null) {
            talent.setStatus(request.getStatus());
        }
        if (StringUtils.hasText(request.getContactPhone())) {
            talent.setContactPhone(request.getContactPhone().trim());
        }
        if (StringUtils.hasText(request.getContactWechat())) {
            talent.setContactWechat(request.getContactWechat().trim());
        }
        if (StringUtils.hasText(request.getIntro())) {
            talent.setIntro(request.getIntro().trim());
        }
        persistTalent(talent);
        return talent;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<String> updateTags(UUID id, List<String> tags) {
        return updateTags(id, tags, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<String> updateTags(UUID id, List<String> tags, UUID operatorId) {
        Talent talent = getById(id);
        List<String> normalized = normalizeTalentTags(tags);
        talent.setTags(normalized);
        talent.setTagUpdatedBy(operatorId);
        persistTalent(talent);
        return normalized;
    }

    @Transactional(rollbackFor = Exception.class)
    public Talent updateShippingAddress(
            UUID id,
            String recipientName,
            String recipientPhone,
            String recipientAddress) {
        Talent talent = getById(id);
        talent.setShippingRecipientName(trimToNull(recipientName));
        talent.setShippingRecipientPhone(trimToNull(recipientPhone));
        talent.setShippingRecipientAddress(trimToNull(recipientAddress));
        persistTalent(talent);
        return talent;
    }

    @Transactional(rollbackFor = Exception.class)
    public Talent updateShippingAddress(
            UUID id,
            UUID userId,
            String recipientName,
            String recipientPhone,
            String recipientAddress) {
        if (userId == null) {
            return updateShippingAddress(id, recipientName, recipientPhone, recipientAddress);
        }
        Talent talent = getById(id);
        TalentClaim claim = talentClaimMapper.findActiveByTalentAndUser(id, userId);
        if (claim == null) {
            throw new ForbiddenException("仅当前认领人可以维护达人收货地址");
        }
        // T-04 fix: 地址仅存于 claim 层，不写入 talent 主表，避免非认领人通过达人详情查见
        String normalizedName = trimToNull(recipientName);
        String normalizedPhone = trimToNull(recipientPhone);
        String normalizedAddress = trimToNull(recipientAddress);
        claim.setRecipientName(normalizedName);
        claim.setRecipientPhone(normalizedPhone);
        claim.setRecipientAddress(normalizedAddress);
        persistTalentClaim(claim);
        return talent;
    }

    public Talent getShippingAddress(UUID id, UUID userId) {
        Talent talent = getById(id);
        if (userId == null) {
            return talent;
        }
        TalentClaim claim = talentClaimMapper.findActiveByTalentAndUser(id, userId);
        if (claim == null) {
            // T-04 fix: 无认领时返回空地址，不再泄露 talent 主表旧数据
            talent.setShippingRecipientName(null);
            talent.setShippingRecipientPhone(null);
            talent.setShippingRecipientAddress(null);
            return talent;
        }
        // T-04 fix: 仅返回答领人地址，不使用 talent 主表兜底
        talent.setShippingRecipientName(claim.getRecipientName());
        talent.setShippingRecipientPhone(claim.getRecipientPhone());
        talent.setShippingRecipientAddress(claim.getRecipientAddress());
        return talent;
    }

    @Transactional(rollbackFor = Exception.class)
    public TalentBatchImportResult batchImport(List<String> accounts, UUID operatorId) {
        if (accounts == null || accounts.isEmpty()) {
            return new TalentBatchImportResult(0, 0, 0, 0, List.of());
        }
        List<TalentBatchImportResult.TalentBatchImportItemResult> items = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        int failed = 0;
        for (String rawAccount : accounts) {
            String account = rawAccount == null ? null : rawAccount.trim();
            if (!StringUtils.hasText(account)) {
                failed++;
                items.add(new TalentBatchImportResult.TalentBatchImportItemResult(
                        rawAccount, "FAILED", null, "账号为空"));
                continue;
            }
            try {
                TalentInputParseResult parsed = TalentInputParser.parse(account);
                if (!StringUtils.hasText(parsed.getDouyinUid())) {
                    failed++;
                    items.add(new TalentBatchImportResult.TalentBatchImportItemResult(
                            account, "FAILED", null, "无法解析达人账号"));
                    continue;
                }
                Talent existing = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                        .eq(Talent::getDouyinUid, parsed.getDouyinUid())
                        .last("limit 1"));
                if (existing != null) {
                    skipped++;
                    items.add(new TalentBatchImportResult.TalentBatchImportItemResult(
                            account, "SKIPPED", existing.getId(), "达人已存在"));
                    continue;
                }
                Talent request = new Talent();
                request.setDouyinUid(parsed.getDouyinUid());
                request.setDouyinNo(parsed.getDouyinNo());
                request.setUid(parsed.getUid());
                request.setSecUid(parsed.getSecUid());
                request.setProfileUrl(parsed.getProfileUrl());
                Talent saved = create(request);
                created++;
                items.add(new TalentBatchImportResult.TalentBatchImportItemResult(
                        account, "CREATED", saved.getId(), null));
                operationLogService.recordSystemAction(
                        operatorId,
                        "达人批量导入",
                        "创建达人",
                        "POST",
                        "talent",
                        saved.getId() == null ? account : saved.getId().toString(),
                        saved.getNickname(),
                        "batch_import_talents");
            } catch (RuntimeException ex) {
                failed++;
                items.add(new TalentBatchImportResult.TalentBatchImportItemResult(
                        account, "FAILED", null, ex.getMessage()));
            }
        }
        return new TalentBatchImportResult(accounts.size(), created, skipped, failed, items);
    }

    public List<String> listPresetTags() {
        return businessRuleConfigService.getPresetTalentTags();
    }

    private List<String> normalizeTalentTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
        List<String> presets = businessRuleConfigService.getPresetTalentTags();
        for (String tag : tags) {
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            String normalized = tag.trim();
            if (!presets.isEmpty() && !presets.contains(normalized)) {
                throw com.colonel.saas.common.exception.BusinessException.param("标签必须从预设库选择: " + normalized);
            }
            unique.add(normalized);
            if (unique.size() >= 3) {
                break;
            }
        }
        return List.copyOf(unique);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id) {
        getById(id);
        talentMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Talent claim(UUID talentId, UUID userId, UUID deptId) {
        if (userId == null) {
            throw BusinessException.param("缺少登录用户");
        }
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

            TalentClaim selfActiveClaim = talentClaimMapper.findActiveByTalentAndUser(talentId, userId);
            if (selfActiveClaim != null) {
                throw BusinessException.duplicate("你已认领该达人，无需重复认领");
            }

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
            claim.setProtectedUntil(now.plusDays(protectDays));
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

    @Transactional(rollbackFor = Exception.class)
    public Talent release(UUID talentId, UUID userId, UUID deptId, Collection<?> roleCodes) {
        if (userId == null) {
            throw BusinessException.param("缺少登录用户");
        }
        getById(talentId);

        List<TalentClaim> activeClaims = talentClaimMapper.findActiveByTalentId(talentId);
        if (activeClaims.isEmpty()) {
            throw BusinessException.stateInvalid("达人当前无有效认领记录");
        }

        boolean isAdmin = hasRole(roleCodes, "admin");
        TalentClaim releaseTarget = activeClaims.stream()
                .sorted(Comparator.comparing((TalentClaim claim) -> !userId.equals(claim.getUserId()))
                        .thenComparing(TalentClaim::getClaimedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(claim -> canRelease(claim, userId, deptId, isAdmin))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("仅认领人或管理员可以释放达人"));

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

    @Transactional(rollbackFor = Exception.class)
    public Talent overrideTalentAssignment(UUID talentId, UUID newUserId, String reason, UUID currentUserId) {
        if (newUserId == null) {
            throw BusinessException.param("新负责人ID不能为空");
        }
        SysUser targetUser = sysUserMapper.selectById(newUserId);
        if (targetUser == null || targetUser.getDeleted() == 1) {
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

    @Transactional(rollbackFor = Exception.class)
    public Talent blacklist(UUID talentId, String reason) {
        return blacklist(talentId, reason, null, null, DataScope.ALL);
    }

    @Transactional(rollbackFor = Exception.class)
    public Talent blacklist(UUID talentId, String reason, UUID userId, UUID deptId, DataScope dataScope) {
        Talent talent = getById(talentId);
        assertCanOperateBlacklist(talentId, userId, deptId, dataScope);
        talent.setBlacklisted(true);
        talent.setBlacklistReason(StringUtils.hasText(reason) ? reason.trim() : "手动拉黑");
        persistTalent(talent);
        return talent;
    }

    @Transactional(rollbackFor = Exception.class)
    public Talent unblacklist(UUID talentId) {
        return unblacklist(talentId, null, null, DataScope.ALL);
    }

    @Transactional(rollbackFor = Exception.class)
    public Talent unblacklist(UUID talentId, UUID userId, UUID deptId, DataScope dataScope) {
        Talent talent = getById(talentId);
        assertCanOperateBlacklist(talentId, userId, deptId, dataScope);
        talent.setBlacklisted(false);
        talent.setBlacklistReason(null);
        persistTalent(talent);
        return talent;
    }

    @Transactional(rollbackFor = Exception.class)
    public Talent refresh(UUID talentId) {
        Talent talent = getById(talentId);
        TalentEnrichTask task = createEnrichTask(talent, ENRICH_TASK_STATUS_RUNNING, null);
        try {
            TalentEnrichOrchestrator.OrchestrateResult orchestrateResult = talentEnrichOrchestrator.enrich(talent, true);
            if (publicPageCrawlEnabled) {
                enrichTalentInfo(talent, true);
            }
            persistTalent(talent);
            if (orchestrateResult.updated()) {
                markEnrichTask(task, ENRICH_TASK_STATUS_SUCCESS, null);
            } else {
                talent.setEnrichStatus(ENRICH_TASK_STATUS_WAIT_MANUAL);
                talent.setLastEnrichTime(LocalDateTime.now());
                persistTalent(talent);
                markEnrichTask(task, ENRICH_TASK_STATUS_WAIT_MANUAL, orchestrateResult.message());
            }
            return talent;
        } catch (RuntimeException ex) {
            talent.setEnrichStatus(ENRICH_TASK_STATUS_FAILED);
            talent.setLastEnrichTime(LocalDateTime.now());
            persistTalent(talent);
            markEnrichTask(task, ENRICH_TASK_STATUS_FAILED, ex.getMessage());
            return talent;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Talent manualFill(UUID talentId, Talent request) {
        Talent talent = getById(talentId);
        if (StringUtils.hasText(request.getNickname())) {
            talent.setNickname(request.getNickname().trim());
        }
        if (StringUtils.hasText(request.getAvatarUrl())) {
            talent.setAvatarUrl(request.getAvatarUrl().trim());
        }
        if (request.getFans() != null) {
            talent.setFans(request.getFans());
        }
        if (request.getLikesCount() != null) {
            talent.setLikesCount(request.getLikesCount());
        }
        if (request.getFollowingCount() != null) {
            talent.setFollowingCount(request.getFollowingCount());
        }
        if (request.getWorksCount() != null) {
            talent.setWorksCount(request.getWorksCount());
        }
        if (StringUtils.hasText(request.getIpLocation())) {
            talent.setIpLocation(request.getIpLocation().trim());
        }
        if (StringUtils.hasText(request.getContactPhone())) {
            talent.setContactPhone(request.getContactPhone().trim());
        }
        if (StringUtils.hasText(request.getContactWechat())) {
            talent.setContactWechat(request.getContactWechat().trim());
        }
        if (StringUtils.hasText(request.getIntro())) {
            talent.setIntro(request.getIntro().trim());
        }
        talent.setDataSource("MANUAL");
        talent.setEnrichStatus(ENRICH_TASK_STATUS_SUCCESS);
        talent.setLastEnrichTime(LocalDateTime.now());
        persistTalent(talent);
        return talent;
    }

    public TalentEnrichTask getLatestEnrichTask(UUID talentId) {
        return talentEnrichTaskMapper.findLatestByTalentId(talentId);
    }

    public List<UUID> findActiveTalentIdsForRefresh() {
        return talentMapper.selectList(new LambdaQueryWrapper<Talent>()
                        .eq(Talent::getDeleted, 0)
                        .eq(Talent::getStatus, 1))
                .stream()
                .map(Talent::getId)
                .filter(Objects::nonNull)
                .toList();
    }

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
        Map<UUID, Talent> talentMap = talentMapper.selectBatchIds(talentIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Talent::getId, talent -> talent, (left, right) -> left));
        for (TalentClaim claim : activeClaims) {
            Talent talent = talentMap.get(claim.getTalentId());
            if (talent != null && hasOutputSinceClaim(talent, claim)) {
                continue;
            }
            claim.setStatus(CLAIM_STATUS_EXPIRED);
            persistTalentClaim(claim);
        }
    }

    private boolean hasOutputSinceClaim(Talent talent, TalentClaim claim) {
        if (talent == null || claim == null || !StringUtils.hasText(talent.getDouyinUid())) {
            return false;
        }
        LocalDateTime since = claim.getClaimedAt() == null ? LocalDateTime.now().minusDays(getProtectDays()) : claim.getClaimedAt();
        LambdaQueryWrapper<com.colonel.saas.entity.ColonelsettlementOrder> wrapper =
                new LambdaQueryWrapper<com.colonel.saas.entity.ColonelsettlementOrder>()
                        .ge(com.colonel.saas.entity.ColonelsettlementOrder::getCreateTime, since);
        return loadOrdersInBatches(wrapper).stream()
                .anyMatch(order -> matchesTalent(order, talent.getDouyinUid()));
    }

    public ExclusiveCheckResult evaluateExclusive(UUID talentId, DataScope dataScope, UUID userId, UUID deptId) {
        Talent talent = getById(talentId);
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        LambdaQueryWrapper<com.colonel.saas.entity.ColonelsettlementOrder> wrapper =
                new LambdaQueryWrapper<com.colonel.saas.entity.ColonelsettlementOrder>()
                        .ge(com.colonel.saas.entity.ColonelsettlementOrder::getSettleTime, start);
        if (dataScope == DataScope.PERSONAL && userId != null) {
            wrapper.eq(com.colonel.saas.entity.ColonelsettlementOrder::getUserId, userId);
        } else if (dataScope == DataScope.DEPT && deptId != null) {
            wrapper.eq(com.colonel.saas.entity.ColonelsettlementOrder::getDeptId, deptId);
        }
        List<com.colonel.saas.entity.ColonelsettlementOrder> monthOrders = loadOrdersInBatches(wrapper);

        long totalServiceFee = 0L;
        long talentServiceFee = 0L;
        for (com.colonel.saas.entity.ColonelsettlementOrder order : monthOrders) {
            long serviceFee = order.getSettleColonelCommission() == null ? 0L : order.getSettleColonelCommission();
            totalServiceFee += serviceFee;
            if (matchesTalent(order, talent.getDouyinUid())) {
                talentServiceFee += serviceFee;
            }
        }
        long serviceRatio = totalServiceFee == 0 ? 0 : (talentServiceFee * 100 / totalServiceFee);
        Long sampleCount = sampleRequestMapper.selectCount(new LambdaQueryWrapper<com.colonel.saas.entity.SampleRequest>()
                .eq(com.colonel.saas.entity.SampleRequest::getTalentId, talentId)
                .ge(com.colonel.saas.entity.SampleRequest::getCreateTime, start));
        long monthlySamples = sampleCount == null ? 0L : sampleCount;

        boolean eligible = serviceRatio >= businessRuleConfigService.getTalentExclusiveRatioThreshold().longValue()
                && monthlySamples >= businessRuleConfigService.getTalentExclusiveMonthlySamples();
        return new ExclusiveCheckResult(eligible, serviceRatio, monthlySamples);
    }

    private List<com.colonel.saas.entity.ColonelsettlementOrder> loadOrdersInBatches(
            LambdaQueryWrapper<com.colonel.saas.entity.ColonelsettlementOrder> wrapper) {
        List<com.colonel.saas.entity.ColonelsettlementOrder> result = new java.util.ArrayList<>();
        long current = 1L;
        while (true) {
            Page<com.colonel.saas.entity.ColonelsettlementOrder> page = new Page<>(current, ORDER_BATCH_SIZE);
            IPage<com.colonel.saas.entity.ColonelsettlementOrder> batch = orderMapper.selectPage(page, wrapper);
            List<com.colonel.saas.entity.ColonelsettlementOrder> records = batch.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            result.addAll(records);
            if (current >= batch.getPages()) {
                break;
            }
            current++;
        }
        return result;
    }

    private boolean matchesTalent(com.colonel.saas.entity.ColonelsettlementOrder order, String douyinUid) {
        if (!StringUtils.hasText(douyinUid)) {
            return false;
        }
        if (order.getExtraData() == null) {
            return false;
        }
        Object authorId = order.getExtraData().get("author_id");
        if (authorId != null && douyinUid.equals(String.valueOf(authorId))) {
            return true;
        }
        Object talentUid = order.getExtraData().get("talent_uid");
        return talentUid != null && douyinUid.equals(String.valueOf(talentUid));
    }

    private TalentClaim findLatestClaimByTalentAndUser(UUID talentId, UUID userId) {
        return talentClaimMapper.selectOne(new LambdaQueryWrapper<TalentClaim>()
                .eq(TalentClaim::getTalentId, talentId)
                .eq(TalentClaim::getUserId, userId)
                .eq(TalentClaim::getDeleted, 0)
                .orderByDesc(TalentClaim::getClaimedAt)
                .last("limit 1"));
    }

    private Set<UUID> getClaimedTalentIds() {
        List<TalentClaim> claims = talentClaimMapper.selectList(new LambdaQueryWrapper<TalentClaim>()
                .eq(TalentClaim::getStatus, CLAIM_STATUS_ACTIVE)
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

    private void enrichTalentInfo(Talent talent, boolean forceCrawl) {
        if (talent == null || !StringUtils.hasText(talent.getDouyinUid())) {
            return;
        }
        String talentUid = talent.getDouyinUid().trim();

        if (forceCrawl && publicPageCrawlEnabled) {
            int success = crawlerTalentInfoService.crawlAndSave(List.of(talentUid));
            if (success <= 0) {
                talent.setCrawlStatus(2);
                talent.setCrawlMessage("crawl failed");
            }
        }

        CrawlerTalentInfo info = crawlerTalentInfoService.findByTalentId(talentUid);
        if (info == null) {
            return;
        }

        if (StringUtils.hasText(info.getNickname())) {
            talent.setNickname(info.getNickname());
        }
        if (info.getFansCount() != null) {
            talent.setFans(info.getFansCount());
        }
        if (StringUtils.hasText(info.getAvatarUrl())) {
            talent.setAvatarUrl(info.getAvatarUrl());
        }
        if (StringUtils.hasText(info.getRegion())) {
            talent.setIpLocation(info.getRegion());
        }
        talent.setLastCrawlAt(info.getLastCrawlTime() == null ? LocalDateTime.now() : info.getLastCrawlTime());
        talent.setCrawlStatus(1);
        talent.setCrawlMessage(null);
    }

    private boolean canRelease(TalentClaim claim, UUID userId, UUID deptId, boolean isAdmin) {
        if (isAdmin) {
            return true;
        }
        if (userId.equals(claim.getUserId())) {
            return true;
        }
        return false;
    }

    private void applyReleaseOwnerSnapshot(Talent talent, List<TalentClaim> activeClaims) {
        List<TalentClaim> remainingClaims = activeClaims == null
                ? List.of()
                : activeClaims.stream()
                        .filter(claim -> claim.getStatus() != null && claim.getStatus() == CLAIM_STATUS_ACTIVE)
                        .sorted(Comparator.comparing(
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

    private void assertCanOperateBlacklist(UUID talentId, UUID userId, UUID deptId, DataScope dataScope) {
        if (dataScope == null || dataScope == DataScope.ALL) {
            return;
        }
        List<TalentClaim> activeClaims = talentClaimMapper.findActiveByTalentId(talentId);
        if (activeClaims.isEmpty()) {
            return;
        }
        if (dataScope == DataScope.PERSONAL) {
            boolean ownedByCurrentUser = userId != null && activeClaims.stream()
                    .anyMatch(claim -> userId.equals(claim.getUserId()));
            if (!ownedByCurrentUser) {
                throw new ForbiddenException("无权操作该达人");
            }
            return;
        }
        boolean ownedByCurrentDept = deptId != null && activeClaims.stream()
                .anyMatch(claim -> deptId.equals(claim.getDeptId()));
        if (!ownedByCurrentDept) {
            throw new ForbiddenException("无权操作该达人");
        }
    }

    private boolean hasRole(Collection<?> roleCodes, String role) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return false;
        }
        String target = role.toLowerCase(Locale.ROOT);
        return roleCodes.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(code -> code.toLowerCase(Locale.ROOT))
                .anyMatch(target::equals);
    }

    private TalentEnrichTask createEnrichTask(Talent talent, String status, String errorMsg) {
        if (talent == null || talent.getId() == null) {
            return null;
        }
        TalentEnrichTask task = new TalentEnrichTask();
        task.setTalentId(talent.getId());
        task.setInputValue(resolveInputValue(talent));
        task.setInputType(resolveInputType(talent));
        task.setSourceType(ENRICH_SOURCE_SYSTEM);
        task.setTaskStatus(status);
        task.setRetryCount(0);
        task.setErrorMsg(errorMsg);
        task.setId(UUID.randomUUID());
        talentEnrichTaskMapper.insert(task);
        return task;
    }

    private void markEnrichTask(TalentEnrichTask task, String status, String errorMsg) {
        if (task == null || task.getId() == null) {
            return;
        }
        TalentEnrichTask update = new TalentEnrichTask();
        update.setId(task.getId());
        update.setTaskStatus(status);
        update.setErrorMsg(errorMsg);
        update.setUpdateTime(LocalDateTime.now());
        talentEnrichTaskMapper.updateById(update);
    }

    private String resolveInputValue(Talent talent) {
        if (StringUtils.hasText(talent.getProfileUrl())) {
            return talent.getProfileUrl().trim();
        }
        if (StringUtils.hasText(talent.getDouyinNo())) {
            return talent.getDouyinNo().trim();
        }
        if (StringUtils.hasText(talent.getUid())) {
            return talent.getUid().trim();
        }
        if (StringUtils.hasText(talent.getSecUid())) {
            return talent.getSecUid().trim();
        }
        if (StringUtils.hasText(talent.getDouyinUid())) {
            return talent.getDouyinUid().trim();
        }
        return null;
    }

    private String resolveInputType(Talent talent) {
        if (StringUtils.hasText(talent.getProfileUrl())) {
            return "PROFILE_URL";
        }
        if (StringUtils.hasText(talent.getDouyinNo())) {
            return "DOUYIN_NO";
        }
        if (StringUtils.hasText(talent.getUid())) {
            return "UID";
        }
        if (StringUtils.hasText(talent.getSecUid())) {
            return "SEC_UID";
        }
        if (StringUtils.hasText(talent.getDouyinUid())) {
            return "DOUYIN_UID";
        }
        return "UNKNOWN";
    }

    private void persistTalent(Talent talent) {
        OptimisticLockSupport.requireUpdated(talentMapper.updateById(talent));
    }

    private void persistTalentClaim(TalentClaim claim) {
        OptimisticLockSupport.requireUpdated(talentClaimMapper.updateById(claim));
    }

    public record ExclusiveCheckResult(boolean eligible, long serviceFeeRatio, long monthlySamples) {
    }
}
