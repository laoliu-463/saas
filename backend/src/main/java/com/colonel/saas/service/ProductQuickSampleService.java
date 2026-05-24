package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.common.exception.ValidateException;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.product.QuickSampleApplyRequest;
import com.colonel.saas.dto.product.QuickSampleApplyResponse;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.gateway.douyin.DouyinQuickSampleGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ProductQuickSampleService {

    public static final String APPLY_SOURCE_QUICK_PRODUCT_LIBRARY = "quick_product_library";
    public static final String APPLY_SOURCE_DOUYIN_QUICK = "DOUYIN_QUICK_SAMPLE";
    public static final String APPLY_SOURCE_LOCAL_FALLBACK = "LOCAL_FALLBACK";
    public static final String FALLBACK_TYPE_LOCAL = "LOCAL_FALLBACK";
    public static final String GATEWAY_STATUS_UNSUPPORTED = "UNSUPPORTED_BY_SDK";
    public static final String FALLBACK_MESSAGE = "抖店外部寄样暂未接通，已创建系统内寄样申请";
    private static final int SAMPLE_STATUS_PENDING_AUDIT = 1;
    private static final int SAMPLE_STATUS_REJECTED = 7;
    private static final DateTimeFormatter REQUEST_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductService productService;
    private final ProductSnapshotMapper productSnapshotMapper;
    private final ProductOperationStateMapper productOperationStateMapper;
    private final SampleRequestMapper sampleRequestMapper;
    private final TalentMapper talentMapper;
    private final TalentClaimMapper talentClaimMapper;
    private final CrawlerTalentInfoService crawlerTalentInfoService;
    private final SampleEligibilityService sampleEligibilityService;
    private final BusinessRuleConfigService businessRuleConfigService;
    private final SampleStatusLogService sampleStatusLogService;
    private final DouyinQuickSampleGateway douyinQuickSampleGateway;
    private final SampleDomainEventPublisher sampleDomainEventPublisher;
    private final boolean douyinQuickSampleEnabled;

    public ProductQuickSampleService(
            ProductService productService,
            ProductSnapshotMapper productSnapshotMapper,
            ProductOperationStateMapper productOperationStateMapper,
            SampleRequestMapper sampleRequestMapper,
            TalentMapper talentMapper,
            TalentClaimMapper talentClaimMapper,
            CrawlerTalentInfoService crawlerTalentInfoService,
            SampleEligibilityService sampleEligibilityService,
            BusinessRuleConfigService businessRuleConfigService,
            SampleStatusLogService sampleStatusLogService,
            DouyinQuickSampleGateway douyinQuickSampleGateway,
            SampleDomainEventPublisher sampleDomainEventPublisher,
            @Value("${app.douyin.quick-sample.enabled:false}") boolean douyinQuickSampleEnabled) {
        this.productService = productService;
        this.productSnapshotMapper = productSnapshotMapper;
        this.productOperationStateMapper = productOperationStateMapper;
        this.sampleRequestMapper = sampleRequestMapper;
        this.talentMapper = talentMapper;
        this.talentClaimMapper = talentClaimMapper;
        this.crawlerTalentInfoService = crawlerTalentInfoService;
        this.sampleEligibilityService = sampleEligibilityService;
        this.businessRuleConfigService = businessRuleConfigService;
        this.sampleStatusLogService = sampleStatusLogService;
        this.douyinQuickSampleGateway = douyinQuickSampleGateway;
        this.sampleDomainEventPublisher = sampleDomainEventPublisher;
        this.douyinQuickSampleEnabled = douyinQuickSampleEnabled;
    }

    @Transactional(rollbackFor = Exception.class)
    public QuickSampleApplyResponse applyQuickSample(
            UUID relationId,
            QuickSampleApplyRequest request,
            UUID userId,
            UUID deptId,
            Object roleCodes) {
        ensureChannelRole(roleCodes);
        Product product = productService.getById(relationId);
        if (product == null) {
            throw BusinessException.notFound("商品不存在");
        }
        ensureDisplaying(product);

        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        boolean externalEnabled = douyinQuickSampleEnabled;
        boolean externalSupported = douyinQuickSampleGateway.isSupported();
        DouyinQuickSampleGateway.SupportStatus supportStatus = douyinQuickSampleGateway.supportStatus();
        QuickSampleApplyResponse response = new QuickSampleApplyResponse();
        response.setExternalEnabled(externalEnabled);
        response.setExternalSupported(externalSupported);
        response.setGatewayStatus(supportStatus == null ? null : supportStatus.name());
        response.setFallbackType(!externalSupported ? FALLBACK_TYPE_LOCAL : null);
        response.setMessage(!externalSupported ? FALLBACK_MESSAGE : null);
        for (String talentId : request.getTalentIds()) {
            QuickSampleApplyResponse.QuickSampleApplyItemResult item = new QuickSampleApplyResponse.QuickSampleApplyItemResult();
            item.setTalentId(talentId);
            try {
                UUID sampleId = createSingleSample(
                        product, request, talentId, quantity, userId, deptId, roleCodes, item, externalEnabled, externalSupported);
                item.setSuccess(true);
                item.setSampleRequestId(sampleId);
                if (!StringUtils.hasText(item.getMessage())) {
                    item.setMessage(item.isExternalApplied()
                            ? "系统内寄样申请已提交"
                            : FALLBACK_MESSAGE);
                }
                response.setSuccessCount(response.getSuccessCount() + 1);
            } catch (Exception ex) {
                item.setSuccess(false);
                item.setMessage(resolveErrorMessage(ex));
                response.setFailureCount(response.getFailureCount() + 1);
                log.debug("Quick sample apply failed for talentId={}: {}", talentId, ex.getMessage());
            }
            response.getItems().add(item);
        }
        response.setSuccess(response.getSuccessCount() > 0 && response.getFailureCount() == 0);
        return response;
    }

    private UUID createSingleSample(
            Product product,
            QuickSampleApplyRequest request,
            String talentExternalId,
            int quantity,
            UUID userId,
            UUID deptId,
            Object roleCodes,
            QuickSampleApplyResponse.QuickSampleApplyItemResult item,
            boolean externalEnabled,
            boolean externalSupported) {
        CrawlerTalentInfo talentInfo = resolveSampleTalentInfo(talentExternalId);
        Talent talent = findOrCreateTalent(talentInfo);
        ensureChannelTalentClaim(userId, talent.getId(), roleCodes);
        checkSevenDaysLimit(userId, talent.getId(), product.getId(), roleCodes);
        SampleEligibilityService.EligibilityResult eligibility = sampleEligibilityService.evaluate(talent, talentInfo);
        if (!eligibility.eligible() && !StringUtils.hasText(request.getRemark())) {
            throw BusinessException.stateInvalid("达人未满足默认寄样标准，请填写备注说明申请原因");
        }

        ProductSnapshot snapshot = productSnapshotMapper.selectById(product.getId());
        resolveDisplayingState(product);
        item.setExternalApplied(false);
        item.setFallback(true);
        item.setGatewayStatus(GATEWAY_STATUS_UNSUPPORTED);
        item.setFallbackType(FALLBACK_TYPE_LOCAL);
        item.setExternalApplyId(null);
        DouyinQuickSampleGateway.QuickSampleApplyResult externalResult = null;
        if (externalEnabled && externalSupported) {
            externalResult = douyinQuickSampleGateway.apply(new DouyinQuickSampleGateway.QuickSampleApplyCommand(
                    product.getId().toString(),
                    snapshot == null ? null : snapshot.getProductId(),
                    snapshot == null ? null : snapshot.getActivityId(),
                    talentExternalId,
                    request.getSkuId(),
                    request.getSpecification(),
                    quantity,
                    trimToNull(request.getRecipientName()),
                    trimToNull(request.getRecipientPhone()),
                    trimToNull(request.getRecipientAddress()),
                    request.getRemark(),
                    userId == null ? null : userId.toString()));
            if (externalResult != null && externalResult.success()) {
                item.setExternalApplied(true);
                item.setExternalApplyId(externalResult.externalApplyId());
                item.setFallback(false);
            }
        }

        SampleRequest sample = new SampleRequest();
        sample.setId(UUID.randomUUID());
        sample.setRequestNo(generateRequestNo());
        sample.setTalentId(talent.getId());
        sample.setTalentUid(talentInfo.getTalentId());
        sample.setTalentNickname(talentInfo.getNickname());
        sample.setTalentFansCount(talentInfo.getFansCount());
        sample.setTalentCreditScore(talentInfo.getCreditScore());
        sample.setTalentMainCategory(talentInfo.getMainCategory());
        sample.setProductId(product.getId());
        sample.setUserId(userId);
        sample.setDeptId(deptId);
        sample.setChannelUserId(userId);
        sample.setChannelDeptId(deptId);
        sample.setExpectedSampleNum(quantity);
        sample.setActualSampleNum(0);
        sample.setRecipientName(trimToNull(request.getRecipientName()));
        sample.setRecipientPhone(trimToNull(request.getRecipientPhone()));
        sample.setRecipientAddress(trimToNull(request.getRecipientAddress()));
        sample.setStatus(SAMPLE_STATUS_PENDING_AUDIT);
        sample.setRemark(buildRemark(request));
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
        sample.setExtraData(buildExtraData(request, eligibility, item.isExternalApplied()));
        sampleRequestMapper.insert(sample);
        sampleStatusLogService.log(sample.getId(), null, sample.getStatus(), userId, "quick sample from product library");
        sampleDomainEventPublisher.publishSampleCreated(
                sample,
                product.getName(),
                null,
                resolveRecruiterId(product),
                snapshot == null || snapshot.getActivityId() == null ? null : snapshot.getActivityId());
        return sample.getId();
    }

    private ProductOperationState resolveDisplayingState(Product product) {
        ProductSnapshot snapshot = productSnapshotMapper.selectById(product.getId());
        if (snapshot == null) {
            throw BusinessException.stateInvalid("商品快照不存在");
        }
        ProductOperationState state = productOperationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getActivityId, snapshot.getActivityId())
                .eq(ProductOperationState::getProductId, snapshot.getProductId())
                .last("LIMIT 1"));
        if (state == null || !ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus())) {
            throw BusinessException.stateInvalid("仅展示中的商品可发起快速寄样");
        }
        return state;
    }

    private void ensureDisplaying(Product product) {
        resolveDisplayingState(product);
    }

    private void ensureChannelRole(Object roleCodes) {
        if (!hasAnyRole(roleCodes, RoleCodes.CHANNEL_STAFF, RoleCodes.CHANNEL_LEADER)
                && !hasAnyRole(roleCodes, RoleCodes.ADMIN)) {
            throw new ForbiddenException("仅渠道角色可使用快速寄样");
        }
    }

    private void ensureChannelTalentClaim(UUID userId, UUID talentId, Object roleCodes) {
        if (hasAnyRole(roleCodes, RoleCodes.ADMIN)) {
            return;
        }
        if (userId == null || talentId == null) {
            throw new ValidateException("达人信息不完整");
        }
        if (talentClaimMapper.findActiveByTalentAndUser(talentId, userId) == null) {
            throw new ForbiddenException("该达人未在你的私海中，请先认领后再申请寄样");
        }
    }

    private void checkSevenDaysLimit(UUID userId, UUID talentId, UUID productId, Object roleCodes) {
        if (hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.CHANNEL_LEADER)) {
            return;
        }
        if (!businessRuleConfigService.isSampleRestrictEnabled()) {
            return;
        }
        int restrictDays = businessRuleConfigService.getSampleRestrictDays();
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

    private CrawlerTalentInfo resolveSampleTalentInfo(String talentId) {
        if (!StringUtils.hasText(talentId)) {
            throw new ValidateException("talentId 不能为空");
        }
        CrawlerTalentInfo info = crawlerTalentInfoService.findByTalentId(talentId.trim());
        if (info == null) {
            throw BusinessException.notFound("达人不存在");
        }
        return info;
    }

    private Talent findOrCreateTalent(CrawlerTalentInfo talentInfo) {
        Talent existing = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, talentInfo.getTalentId())
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid(talentInfo.getTalentId());
        talent.setNickname(talentInfo.getNickname());
        talent.setStatus(1);
        talentMapper.insert(talent);
        return talent;
    }

    private Map<String, Object> buildExtraData(
            QuickSampleApplyRequest request,
            SampleEligibilityService.EligibilityResult eligibility,
            boolean externalApplied) {
        Map<String, Object> extra = new LinkedHashMap<>();
        Map<String, Object> eligibilityCheck = new LinkedHashMap<>();
        eligibilityCheck.put("passed", eligibility.eligible());
        eligibilityCheck.put("reasons", eligibility.reasons());
        extra.put("eligibilityCheck", eligibilityCheck);
        extra.put("applySource", externalApplied ? APPLY_SOURCE_DOUYIN_QUICK : APPLY_SOURCE_LOCAL_FALLBACK);
        extra.put("specification", trimToNull(request.getSpecification()));
        extra.put("externalApply", externalApplied);
        extra.put("applyChannel", "QUICK_PRODUCT_LIBRARY");
        extra.put("gatewayStatus", externalApplied ? "REAL_CONNECTED" : GATEWAY_STATUS_UNSUPPORTED);
        extra.put("fallbackType", externalApplied ? null : FALLBACK_TYPE_LOCAL);
        return extra;
    }

    private String buildRemark(QuickSampleApplyRequest request) {
        StringBuilder remark = new StringBuilder();
        if (StringUtils.hasText(request.getSpecification())) {
            remark.append("规格: ").append(request.getSpecification().trim());
        }
        if (StringUtils.hasText(request.getRemark())) {
            if (!remark.isEmpty()) {
                remark.append("；");
            }
            remark.append(request.getRemark().trim());
        }
        return remark.isEmpty() ? null : remark.toString();
    }

    private UUID resolveRecruiterId(Product product) {
        ProductSnapshot snapshot = productSnapshotMapper.selectById(product.getId());
        if (snapshot == null) {
            return null;
        }
        ProductOperationState state = productOperationStateMapper.selectOne(
                new LambdaQueryWrapper<ProductOperationState>()
                        .eq(ProductOperationState::getActivityId, snapshot.getActivityId())
                        .eq(ProductOperationState::getProductId, snapshot.getProductId())
                        .last("LIMIT 1"));
        return state == null ? null : state.getAssigneeId();
    }

    private String generateRequestNo() {
        return "QS" + LocalDateTime.now().format(REQUEST_NO_DATE) + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean hasAnyRole(Object roleCodes, String... expectedRoles) {
        if (roleCodes == null || expectedRoles == null || expectedRoles.length == 0) {
            return false;
        }
        java.util.Set<String> expected = java.util.Arrays.stream(expectedRoles)
                .map(role -> role == null ? "" : role.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        if (roleCodes instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> item == null ? "" : item.toString().trim().toLowerCase(Locale.ROOT))
                    .anyMatch(expected::contains);
        }
        String raw = roleCodes.toString();
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        for (String role : raw.replace("[", "").replace("]", "").split(",")) {
            if (expected.contains(role.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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
