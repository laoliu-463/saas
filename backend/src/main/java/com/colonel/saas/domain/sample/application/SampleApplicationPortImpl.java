package com.colonel.saas.domain.sample.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.common.exception.ValidateException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.sample.api.ApplySampleFromProductCommand;
import com.colonel.saas.domain.sample.api.ApplySampleFromProductResult;
import com.colonel.saas.domain.sample.api.SampleApplicationPort;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.domain.talent.facade.TalentDomainFacade;
import com.colonel.saas.domain.talent.facade.dto.TalentReadDTO;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.gateway.douyin.DouyinQuickSampleGateway;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.service.CrawlerTalentInfoService;
import com.colonel.saas.service.SampleEligibilityService;
import com.colonel.saas.service.SampleStatusLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@link SampleApplicationPort} 的寄样域实现。
 * <p>
 * 负责商品域快速寄样入口的寄样创建全流程：达人解析 → 私海校验 → 去重校验 →
 * 资质评估 → 外部网关调用 → 寄样单落库 → 状态日志 → 领域事件。
 * </p>
 *
 * @see SampleApplicationPort
 * @see ApplySampleFromProductCommand
 */
@Slf4j
@Service
public class SampleApplicationPortImpl implements SampleApplicationPort {

    /** 申请来源：抖店外部快速寄样 */
    public static final String APPLY_SOURCE_DOUYIN_QUICK = "DOUYIN_QUICK_SAMPLE";
    /** 申请来源：系统内本地降级 */
    public static final String APPLY_SOURCE_LOCAL_FALLBACK = "LOCAL_FALLBACK";
    /** 降级类型标识：本地降级 */
    public static final String FALLBACK_TYPE_LOCAL = "LOCAL_FALLBACK";
    /** 网关状态：当前 SDK 不支持 */
    public static final String GATEWAY_STATUS_UNSUPPORTED = "UNSUPPORTED_BY_SDK";
    /** 降级提示消息 */
    public static final String FALLBACK_MESSAGE = "抖店外部寄样暂未接通，已创建系统内寄样申请";
    /** 寄样申请状态：待审核 */
    private static final int SAMPLE_STATUS_PENDING_AUDIT = 1;
    /** 寄样申请状态：已驳回（去重排除） */
    private static final int SAMPLE_STATUS_REJECTED = 7;
    /** 申请编号日期格式 */
    private static final DateTimeFormatter REQUEST_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final CrawlerTalentInfoService crawlerTalentInfoService;
    private final TalentDomainFacade talentDomainFacade;
    private final SampleRequestMapper sampleRequestMapper;
    private final ConfigDomainFacade configDomainFacade;
    private final SampleEligibilityService sampleEligibilityService;
    private final SampleStatusLogService sampleStatusLogService;
    private final DouyinQuickSampleGateway douyinQuickSampleGateway;
    private final SampleDomainEventPublisher sampleDomainEventPublisher;
    private final CurrentUserPermissionChecker currentUserPermissionChecker;

    public SampleApplicationPortImpl(
            CrawlerTalentInfoService crawlerTalentInfoService,
            TalentDomainFacade talentDomainFacade,
            SampleRequestMapper sampleRequestMapper,
            ConfigDomainFacade configDomainFacade,
            SampleEligibilityService sampleEligibilityService,
            SampleStatusLogService sampleStatusLogService,
            DouyinQuickSampleGateway douyinQuickSampleGateway,
            SampleDomainEventPublisher sampleDomainEventPublisher,
            CurrentUserPermissionChecker currentUserPermissionChecker) {
        this.crawlerTalentInfoService = crawlerTalentInfoService;
        this.talentDomainFacade = talentDomainFacade;
        this.sampleRequestMapper = sampleRequestMapper;
        this.configDomainFacade = configDomainFacade;
        this.sampleEligibilityService = sampleEligibilityService;
        this.sampleStatusLogService = sampleStatusLogService;
        this.douyinQuickSampleGateway = douyinQuickSampleGateway;
        this.sampleDomainEventPublisher = sampleDomainEventPublisher;
        this.currentUserPermissionChecker = currentUserPermissionChecker;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplySampleFromProductResult applyFromProduct(ApplySampleFromProductCommand cmd) {
        ApplySampleFromProductResult result = new ApplySampleFromProductResult();
        for (String talentId : cmd.talentIds()) {
            ApplySampleFromProductResult.TalentResult item = new ApplySampleFromProductResult.TalentResult();
            item.setTalentId(talentId);
            try {
                UUID sampleId = createSingleSample(cmd, talentId, item);
                item.setSuccess(true);
                item.setSampleRequestId(sampleId);
                if (!StringUtils.hasText(item.getMessage())) {
                    item.setMessage(item.isExternalApplied()
                            ? "系统内寄样申请已提交"
                            : FALLBACK_MESSAGE);
                }
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception ex) {
                item.setSuccess(false);
                item.setMessage(resolveErrorMessage(ex));
                result.setFailureCount(result.getFailureCount() + 1);
                log.debug("Quick sample apply failed for talentId={}: {}", talentId, ex.getMessage());
            }
            result.getItems().add(item);
        }
        result.setSuccess(result.getSuccessCount() > 0 && result.getFailureCount() == 0);
        return result;
    }

    private UUID createSingleSample(
            ApplySampleFromProductCommand cmd,
            String talentExternalId,
            ApplySampleFromProductResult.TalentResult item) {
        /* 解析达人外部信息 */
        CrawlerTalentInfo talentInfo = resolveSampleTalentInfo(talentExternalId);
        /* 查找已有达人记录，不存在则自动创建 */
        Talent talent = toTalentEntity(findOrCreateTalent(talentInfo));
        /* 校验达人是否在指定渠道人员私海中（管理员跳过） */
        ensureChannelTalentClaim(cmd.channelUserId(), talent.getId(), cmd.roleCodes());
        /* 去重校验（管理员和主管跳过） */
        checkSevenDaysLimit(cmd.channelUserId(), talent.getId(), cmd.relationId(), cmd.roleCodes());
        /* 达人资质评估 */
        SampleEligibilityService.EligibilityResult eligibility =
                sampleEligibilityService.evaluate(talent, talentInfo);
        if (!eligibility.eligible() && !StringUtils.hasText(cmd.remark())) {
            throw BusinessException.stateInvalid("达人未满足默认寄样标准，请填写备注说明申请原因");
        }

        /* 默认降级模式 */
        item.setExternalApplied(false);
        item.setFallback(true);
        item.setGatewayStatus(GATEWAY_STATUS_UNSUPPORTED);
        item.setFallbackType(FALLBACK_TYPE_LOCAL);
        item.setExternalApplyId(null);

        /* 尝试调用外部抖店网关 */
        DouyinQuickSampleGateway.QuickSampleApplyResult externalResult = null;
        if (cmd.externalEnabled() && cmd.externalSupported()) {
            externalResult = douyinQuickSampleGateway.apply(new DouyinQuickSampleGateway.QuickSampleApplyCommand(
                    cmd.relationId().toString(),
                    cmd.productId(),
                    cmd.activityId(),
                    talentExternalId,
                    cmd.skuId(),
                    cmd.spec(),
                    cmd.quantity(),
                    trimToNull(cmd.receiverName()),
                    trimToNull(cmd.receiverPhone()),
                    trimToNull(cmd.receiverAddress()),
                    cmd.remark(),
                    cmd.userId() == null ? null : cmd.userId().toString()));
            if (externalResult != null && externalResult.success()) {
                item.setExternalApplied(true);
                item.setExternalApplyId(externalResult.externalApplyId());
                item.setFallback(false);
            }
        }

        /* 构建寄样申请实体 */
        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setRequestNo(generateRequestNo());
        sample.setTalentId(talent.getId());
        sample.setTalentUid(talentInfo.getTalentId());
        sample.setTalentNickname(talentInfo.getNickname());
        sample.setTalentFansCount(talentInfo.getFansCount());
        sample.setTalentCreditScore(talentInfo.getCreditScore());
        sample.setTalentMainCategory(talentInfo.getMainCategory());
        sample.setProductId(cmd.relationId());
        sample.setUserId(cmd.userId());
        sample.setDeptId(cmd.channelId());
        sample.setChannelUserId(cmd.channelUserId());
        sample.setChannelDeptId(cmd.channelId());
        sample.setExpectedSampleNum(cmd.quantity());
        sample.setActualSampleNum(0);
        sample.setRecipientName(trimToNull(cmd.receiverName()));
        sample.setRecipientPhone(trimToNull(cmd.receiverPhone()));
        sample.setRecipientAddress(trimToNull(cmd.receiverAddress()));
        sample.setStatus(SAMPLE_STATUS_PENDING_AUDIT);
        sample.setRemark(buildRemark(cmd));

        if (item.isExternalApplied() && externalResult != null) {
            sample.setApplySource(APPLY_SOURCE_DOUYIN_QUICK);
            sample.setExternalApplyId(externalResult.externalApplyId());
            sample.setExternalStatus(externalResult.externalStatus());
            sample.setExternalRawPayload(externalResult.rawPayload());
        } else {
            sample.setApplySource(APPLY_SOURCE_LOCAL_FALLBACK);
            item.setFallback(true);
            item.setExternalApplied(false);
            item.setGatewayStatus(GATEWAY_STATUS_UNSUPPORTED);
            item.setFallbackType(FALLBACK_TYPE_LOCAL);
            item.setMessage(FALLBACK_MESSAGE);
        }

        sample.setExtraData(buildExtraData(cmd, eligibility, item.isExternalApplied()));
        sampleRequestMapper.insert(sample);

        /* 回写收货地址到认领记录 */
        writeBackClaimAddress(cmd.channelUserId(), talent.getId(), sample);
        /* 记录初始状态日志 */
        sampleStatusLogService.log(sample.getId(), null, sample.getStatus(),
                cmd.userId(), "quick sample from product library");
        /* 发布寄样创建领域事件 */
        sampleDomainEventPublisher.publishSampleCreated(
                sample,
                cmd.productSnapshotTitle(),
                null,
                cmd.assigneeId(),
                cmd.activityId());

        return sample.getId();
    }

    // --- Private helpers (ported from ProductQuickSampleService) ---

    private CrawlerTalentInfo resolveSampleTalentInfo(String talentId) {
        if (!StringUtils.hasText(talentId)) {
            throw new ValidateException("talentId 不能为空");
        }
        String normalizedTalentId = talentId.trim();
        CrawlerTalentInfo info = crawlerTalentInfoService.findByTalentId(normalizedTalentId);
        if (info != null) {
            return info;
        }
        TalentReadDTO manualTalent = talentDomainFacade.findByDouyinUid(normalizedTalentId);
        if (manualTalent == null) {
            throw BusinessException.notFound("达人不存在");
        }
        return buildCrawlerSnapshotFromTalent(manualTalent, normalizedTalentId);
    }

    private CrawlerTalentInfo buildCrawlerSnapshotFromTalent(TalentReadDTO talent, String selectedTalentId) {
        CrawlerTalentInfo info = new CrawlerTalentInfo();
        info.setTalentId(StringUtils.hasText(talent.douyinUid()) ? talent.douyinUid() : selectedTalentId);
        info.setNickname(talent.nickname());
        info.setAvatarUrl(talent.avatarUrl());
        info.setFansCount(talent.fansCount());
        info.setMainCategory(StringUtils.hasText(talent.mainCategory()) ? talent.mainCategory() : talent.categories());
        info.setRegion(talent.ipLocation());
        return info;
    }

    private TalentReadDTO findOrCreateTalent(CrawlerTalentInfo talentInfo) {
        return talentDomainFacade.findOrCreateSampleTalent(
                talentInfo.getTalentId(),
                talentInfo.getNickname(),
                talentInfo.getFansCount());
    }

    private static Talent toTalentEntity(TalentReadDTO dto) {
        if (dto == null) {
            return null;
        }
        Talent talent = new Talent();
        talent.setId(dto.id());
        talent.setDouyinUid(dto.douyinUid());
        talent.setDouyinNo(dto.douyinNo());
        talent.setNickname(dto.nickname());
        talent.setFans(dto.fansCount());
        talent.setStatus(dto.status());
        talent.setAvatarUrl(dto.avatarUrl());
        talent.setMainCategory(dto.mainCategory());
        talent.setCategories(dto.categories());
        talent.setIpLocation(dto.ipLocation());
        return talent;
    }

    private void ensureChannelTalentClaim(UUID userId, UUID talentId, Object roleCodes) {
        if (currentUserPermissionChecker.hasAnyRole(roleCodes, RoleCodes.ADMIN)) {
            return;
        }
        if (userId == null || talentId == null) {
            throw new ValidateException("达人信息不完整");
        }
        if (!talentDomainFacade.hasActiveClaim(talentId, userId)) {
            throw new ForbiddenException("该达人未在你的私海中，请先认领后再申请寄样");
        }
    }

    private void checkSevenDaysLimit(UUID userId, UUID talentId, UUID productId, Object roleCodes) {
        if (currentUserPermissionChecker.hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.CHANNEL_LEADER)) {
            return;
        }
        if (!configDomainFacade.isSampleLimitEnabled()) {
            return;
        }
        int restrictDays = configDomainFacade.getSampleLimitDays();
        LocalDateTime since = LocalDateTime.now().minusDays(restrictDays);
        Long count = sampleRequestMapper.selectCount(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getChannelUserId, userId)
                .eq(SampleRequest::getTalentId, talentId)
                .eq(SampleRequest::getProductId, productId)
                .ne(SampleRequest::getStatus, SAMPLE_STATUS_REJECTED)
                .ge(SampleRequest::getCreateTime, since));
        if (count != null && count > 0) {
            throw BusinessException.duplicate("Duplicate sample request is blocked within " + restrictDays + " days");
        }
    }

    private Map<String, Object> buildExtraData(
            ApplySampleFromProductCommand cmd,
            SampleEligibilityService.EligibilityResult eligibility,
            boolean externalApplied) {
        Map<String, Object> extra = new LinkedHashMap<>();
        Map<String, Object> eligibilityCheck = new LinkedHashMap<>();
        eligibilityCheck.put("passed", eligibility.eligible());
        eligibilityCheck.put("reasons", eligibility.reasons());
        extra.put("eligibilityCheck", eligibilityCheck);
        extra.put("applySource", externalApplied ? APPLY_SOURCE_DOUYIN_QUICK : APPLY_SOURCE_LOCAL_FALLBACK);
        extra.put("specification", trimToNull(cmd.spec()));
        extra.put("externalApply", externalApplied);
        extra.put("applyChannel", "QUICK_PRODUCT_LIBRARY");
        extra.put("gatewayStatus", externalApplied ? "REAL_CONNECTED" : GATEWAY_STATUS_UNSUPPORTED);
        extra.put("fallbackType", externalApplied ? null : FALLBACK_TYPE_LOCAL);
        return extra;
    }

    private String buildRemark(ApplySampleFromProductCommand cmd) {
        StringBuilder remark = new StringBuilder();
        if (StringUtils.hasText(cmd.spec())) {
            remark.append("规格: ").append(cmd.spec().trim());
        }
        if (StringUtils.hasText(cmd.remark())) {
            if (!remark.isEmpty()) {
                remark.append("；");
            }
            remark.append(cmd.remark().trim());
        }
        return remark.isEmpty() ? null : remark.toString();
    }

    private void writeBackClaimAddress(UUID channelUserId, UUID talentId, SampleRequest sample) {
        if (channelUserId == null || talentId == null) {
            return;
        }
        String name = sample.getRecipientName();
        String phone = sample.getRecipientPhone();
        String address = sample.getRecipientAddress();
        if (!StringUtils.hasText(name) && !StringUtils.hasText(phone) && !StringUtils.hasText(address)) {
            return;
        }
        talentDomainFacade.writeBackClaimAddress(
                channelUserId,
                talentId,
                sample.getRecipientName(),
                sample.getRecipientPhone(),
                sample.getRecipientAddress());
        log.debug("T-ADDR: writeback claim address for talent={}, channel={}", talentId, channelUserId);
    }

    private String generateRequestNo() {
        return "QS" + LocalDateTime.now().format(REQUEST_NO_DATE)
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String resolveErrorMessage(Exception ex) {
        if (ex instanceof BusinessException businessException) {
            return businessException.getMessage();
        }
        if (ex instanceof ForbiddenException forbiddenException) {
            return forbiddenException.getMessage();
        }
        if (ex instanceof ValidateException validateException) {
            return validateException.getMessage();
        }
        return ex.getMessage() == null ? "申请失败" : ex.getMessage();
    }
}
