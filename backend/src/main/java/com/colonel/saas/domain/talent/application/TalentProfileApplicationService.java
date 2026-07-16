package com.colonel.saas.domain.talent.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.domain.talent.policy.TalentTagPolicy;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.entity.TalentEnrichTask;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.domain.talent.policy.TalentAddressPolicy;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentEnrichTaskMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.colonel.saas.service.CrawlerTalentInfoService;
import com.colonel.saas.service.talent.TalentEnrichOrchestrator;
import com.colonel.saas.service.talent.TalentInputParseResult;
import com.colonel.saas.service.talent.TalentInputParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 达人资料写侧应用层 (DDD-TALENT-04 Slice 4).
 *
 * <p>本层承接 Controller 的资料、标签、手动补全和导入命令入口，
 * 自包含业务编排（旧 TalentService 同名方法迁入），保留 1:1 行为等价。
 * Legacy {@code TalentService} 保留为薄壳委派壳，不删除兜底路径。</p>
 *
 * <p>当前 Slice 4 范围：{@code create/manualFill/refresh(Talent)} + 6 个常量 + 4 个
 * enrich helper（{@code createEnrichTask / markEnrichTask / persistTalent /
 * enrichTalentInfo / resolveInputValue / resolveInputType}）。
 * 1:1 等价 TalentService 资料写侧编排。</p>
 *
 * <p><b>业务域：</b>达人域 — 资料管理</p>
 */
@Service
public class TalentProfileApplicationService {

    // enrich task status constants (1:1 from TalentService)
    static final String ENRICH_TASK_STATUS_PENDING = "PENDING";
    static final String ENRICH_TASK_STATUS_RUNNING = "RUNNING";
    static final String ENRICH_TASK_STATUS_SUCCESS = "SUCCESS";
    static final String ENRICH_TASK_STATUS_FAILED = "FAILED";
    static final String ENRICH_TASK_STATUS_WAIT_MANUAL = "WAIT_MANUAL";
    static final String ENRICH_SOURCE_SYSTEM = "SYSTEM";

    private final TalentMapper talentMapper;
    private final TalentClaimMapper talentClaimMapper;
    private final TalentEnrichTaskMapper talentEnrichTaskMapper;
    private final TalentEnrichOrchestrator talentEnrichOrchestrator;
    private final CrawlerTalentInfoService crawlerTalentInfoService;
    private final BusinessRuleConfigService businessRuleConfigService;
    private final boolean publicPageCrawlEnabled;

    public TalentProfileApplicationService(
            TalentMapper talentMapper,
            TalentClaimMapper talentClaimMapper,
            TalentEnrichTaskMapper talentEnrichTaskMapper,
            TalentEnrichOrchestrator talentEnrichOrchestrator,
            CrawlerTalentInfoService crawlerTalentInfoService,
            BusinessRuleConfigService businessRuleConfigService,
            @Value("${talent.data.public-page-crawl-enabled:false}") boolean publicPageCrawlEnabled) {
        this.talentMapper = talentMapper;
        this.talentClaimMapper = talentClaimMapper;
        this.talentEnrichTaskMapper = talentEnrichTaskMapper;
        this.talentEnrichOrchestrator = talentEnrichOrchestrator;
        this.crawlerTalentInfoService = crawlerTalentInfoService;
        this.businessRuleConfigService = businessRuleConfigService;
        this.publicPageCrawlEnabled = publicPageCrawlEnabled;
    }

    /**
     * 列出预置达人标签。
     * 1:1 等价 TalentService.listPresetTags()。
     */
    public List<String> listPresetTags() {
        return businessRuleConfigService.getPresetTalentTags();
    }

    /**
     * 获取达人最新 enrich 任务。
     * 1:1 等价 TalentService.getLatestEnrichTask(UUID)。
     */
    public TalentEnrichTask getLatestEnrichTask(UUID talentId) {
        return talentEnrichTaskMapper.findLatestByTalentId(talentId);
    }

    /**
     * 删除达人。
     * 1:1 等价 TalentService.delete(UUID)。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id) {
        Talent existing = talentMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("达人不存在");
        }
        talentMapper.deleteById(id);
    }

    /**
     * 更新达人标签（无 operator 版本）。
     * 1:1 等价 TalentService.updateTags(UUID, List<String>)。
     */
    public List<String> updateTags(UUID id, List<String> tags) {
        return updateTags(id, tags, null);
    }

    /**
     * 更新达人标签（带 operator 版本）。
     * 1:1 等价 TalentService.updateTags(UUID, List<String>, UUID)。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> updateTags(UUID id, List<String> tags, UUID operatorId) {
        Talent talent = talentMapper.selectById(id);
        if (talent == null) {
            throw BusinessException.notFound("达人不存在");
        }
        List<String> normalized = TalentTagPolicy.normalize(tags, businessRuleConfigService.getPresetTalentTags());
        talent.setTags(normalized);
        talent.setTagUpdatedBy(operatorId);
        OptimisticLockSupport.requireUpdated(talentMapper.updateById(talent));
        return normalized;
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
     * 手动补全达人信息。
     * 1:1 等价 TalentService.manualFill(UUID, Talent) 38 行业务。
     */
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
        if (StringUtils.hasText(request.getTalentLevel())) {
            talent.setTalentLevel(request.getTalentLevel().trim());
            removeUnsupportedField(talent, "talentLevel");
        }
        if (request.getSales30d() != null) {
            talent.setSales30d(request.getSales30d());
            removeUnsupportedField(talent, "sales30d");
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
        talent.setEnrichStatus("SUCCESS");
        talent.setLastEnrichTime(LocalDateTime.now());
        persistTalent(talent);
        return talent;
    }

    private void removeUnsupportedField(Talent talent, String fieldName) {
        List<String> current = talent.getUnsupportedFields();
        if (current == null || current.isEmpty()) {
            talent.setUnsupportedFields(List.of());
            return;
        }
        Set<String> remaining = new LinkedHashSet<>(current);
        remaining.removeIf(field -> fieldName.equalsIgnoreCase(field));
        talent.setUnsupportedFields(new ArrayList<>(remaining));
    }

    private List<String> normalizeUnsupportedFields(Talent talent) {
        Set<String> unsupported = new LinkedHashSet<>();
        if (talent.getUnsupportedFields() != null) {
            unsupported.addAll(talent.getUnsupportedFields());
        }
        if (StringUtils.hasText(talent.getTalentLevel())) {
            unsupported.removeIf(field -> "talentLevel".equalsIgnoreCase(field));
        } else {
            unsupported.add("talentLevel");
        }
        if (talent.getSales30d() != null) {
            unsupported.removeIf(field -> "sales30d".equalsIgnoreCase(field));
        } else {
            unsupported.add("sales30d");
        }
        return new ArrayList<>(unsupported);
    }

    /**
     * 刷新达人信息（含 orchestrate + crawl）。
     * 1:1 等价 TalentService.refresh(UUID) 26 行业务。
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent refresh(UUID talentId) {
        Talent talent = getById(talentId);
        TalentEnrichTask task = createEnrichTask(talent, "RUNNING", null);
        try {
            TalentEnrichOrchestrator.OrchestrateResult orchestrateResult = talentEnrichOrchestrator.enrich(talent, true);
            if (publicPageCrawlEnabled) {
                enrichTalentInfo(talent, true);
            }
            persistTalent(talent);
            if (orchestrateResult.updated()) {
                markEnrichTask(task, "SUCCESS", null);
            } else {
                talent.setEnrichStatus("WAIT_MANUAL");
                talent.setLastEnrichTime(LocalDateTime.now());
                persistTalent(talent);
                markEnrichTask(task, "WAIT_MANUAL", orchestrateResult.message());
            }
            return talent;
        } catch (RuntimeException ex) {
            talent.setEnrichStatus("FAILED");
            talent.setLastEnrichTime(LocalDateTime.now());
            persistTalent(talent);
            markEnrichTask(task, "FAILED", ex.getMessage());
            return talent;
        }
    }

    /**
     * 创建达人 (含抖音号解析/重复校验/enrich 编排)。
     * 1:1 等价 TalentService.create(Talent) 87 行业务编排。
     */
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
        request.setUnsupportedFields(normalizeUnsupportedFields(request));
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

    /**
     * 更新达人资料（仅更新请求中非空字段）。
     * 1:1 等价 TalentService.update(UUID, Talent) 24 行业务。
     */
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

    /**
     * 获取达人收货地址（带认领人校验）。
     * 1:1 等价 TalentService.getShippingAddress(UUID, UUID) 17 行业务。
     */
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

    /**
     * 更新达人收货地址（无 userId，仅写 talent 主表）。
     * 1:1 等价 TalentService.updateShippingAddress(UUID, String, String, String) 8 行业务。
     */
    @Transactional(rollbackFor = Exception.class)
    public Talent updateShippingAddress(
            UUID id,
            String recipientName,
            String recipientPhone,
            String recipientAddress) {
        Talent talent = getById(id);
        TalentAddressPolicy.NormalizedAddress addr = TalentAddressPolicy.normalize(
                recipientName, recipientPhone, recipientAddress);
        talent.setShippingRecipientName(addr.recipientName());
        talent.setShippingRecipientPhone(addr.recipientPhone());
        talent.setShippingRecipientAddress(addr.recipientAddress());
        persistTalent(talent);
        return talent;
    }

    /**
     * 更新达人收货地址（带 userId 认领人校验）。
     * 1:1 等价 TalentService.updateShippingAddress(UUID, UUID, String, String, String) 16 行业务。
     */
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
        TalentAddressPolicy.NormalizedAddress addr = TalentAddressPolicy.normalize(
                recipientName, recipientPhone, recipientAddress);
        claim.setRecipientName(addr.recipientName());
        claim.setRecipientPhone(addr.recipientPhone());
        claim.setRecipientAddress(addr.recipientAddress());
        persistTalentClaim(claim);
        talent.setShippingRecipientName(addr.recipientName());
        talent.setShippingRecipientPhone(addr.recipientPhone());
        talent.setShippingRecipientAddress(addr.recipientAddress());
        return talent;
    }

    /**
     * 创建 enrich task。
     * 1:1 等价 TalentService.createEnrichTask(Talent, String, String)。
     */
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

    /**
     * 更新 enrich task 状态。
     * 1:1 等价 TalentService.markEnrichTask(TalentEnrichTask, String, String)。
     */
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

    /**
     * 持久化达人 (含乐观锁校验)。
     * 1:1 等价 TalentService.persistTalent(Talent)。
     */
    private void persistTalent(Talent talent) {
        OptimisticLockSupport.requireUpdated(talentMapper.updateById(talent));
    }

    private void persistTalentClaim(TalentClaim claim) {
        OptimisticLockSupport.requireUpdated(talentClaimMapper.updateById(claim));
    }

    /**
     * 爬虫补全达人信息。
     * 1:1 等价 TalentService.enrichTalentInfo(Talent, boolean)。
     */
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

    /**
     * 解析 enrich task 的 input value。
     * 1:1 等价 TalentService.resolveInputValue(Talent)。
     */
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

    /**
     * 解析 enrich task 的 input type。
     * 1:1 等价 TalentService.resolveInputType(Talent)。
     */
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
}
