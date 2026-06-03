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
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.gateway.douyin.DouyinQuickSampleGateway;
import com.colonel.saas.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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

/**
 * 商品快速寄样服务。
 * <p>
 * 允许渠道角色（渠道员工/渠道主管/管理员）对"展示中"的商品发起快速寄样申请。
 * 核心流程：
 * <ol>
 *   <li>校验调用方角色权限（仅渠道角色和管理员可用）</li>
 *   <li>校验商品处于展示状态</li>
 *   <li>对每个达人分别执行：达人信息解析 → 私海认领校验 → 七日去重检查 → 资质评估</li>
 *   <li>优先调用抖店外部寄样网关（{@link DouyinQuickSampleGateway}），成功则记录外部申请 ID</li>
 *   <li>外部网关不可用时降级为系统内寄样（{@code LOCAL_FALLBACK}），创建本地寄样申请记录</li>
 *   <li>写入状态日志并发布寄样创建领域事件</li>
 * </ol>
 * </p>
 * <p>
 * 去重规则：同一用户对同一达人同一商品，在配置的限制天数内（默认 7 天）不允许重复申请（已驳回的除外）。
 * 管理员和渠道主管不受去重限制。
 * </p>
 *
 * @see ProductService
 * @see SampleEligibilityService
 * @see DouyinQuickSampleGateway
 * @see BusinessRuleConfigService
 */
@Slf4j
@Service
public class ProductQuickSampleService {

    /** 申请来源：快速商品库入口 */
    public static final String APPLY_SOURCE_QUICK_PRODUCT_LIBRARY = "quick_product_library";
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
    /** 寄样申请状态：已驳回（用于七日去重计算时排除已驳回记录） */
    private static final int SAMPLE_STATUS_REJECTED = 7;
    /** 申请编号日期格式（yyyyMMdd） */
    private static final DateTimeFormatter REQUEST_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 商品服务，用于查询商品信息 */
    private final ProductService productService;
    /** 商品主表 Mapper，用于在寄样落库前确保 sample_request.product_id 有有效外键 */
    private final ProductMapper productMapper;
    /** 商品快照 Mapper，用于获取商品快照详情 */
    private final ProductSnapshotMapper productSnapshotMapper;
    /** 商品运营状态 Mapper，用于校验展示状态 */
    private final ProductOperationStateMapper productOperationStateMapper;
    /** 寄样申请 Mapper，用于持久化寄样申请 */
    private final SampleRequestMapper sampleRequestMapper;
    /** 达人 Mapper，用于查找或创建达人记录 */
    private final TalentMapper talentMapper;
    /** 达人认领 Mapper，用于校验私海认领关系 */
    private final TalentClaimMapper talentClaimMapper;
    /** 爬虫达人信息服务，用于解析达人外部 ID 对应的详细信息 */
    private final CrawlerTalentInfoService crawlerTalentInfoService;
    /** 达人资质评估服务，用于判断达人是否满足寄样标准 */
    private final SampleEligibilityService sampleEligibilityService;
    /** 业务规则配置服务，用于获取去重天数等动态配置 */
    private final BusinessRuleConfigService businessRuleConfigService;
    /** 寄样状态日志服务，用于记录状态变更 */
    private final SampleStatusLogService sampleStatusLogService;
    /** 抖店快速寄样网关，用于调用外部抖店寄样 API */
    private final DouyinQuickSampleGateway douyinQuickSampleGateway;
    /** 寄样领域事件发布器，用于发布寄样创建事件 */
    private final SampleDomainEventPublisher sampleDomainEventPublisher;
    /** 是否启用抖店外部快速寄样（通过配置注入） */
    private final boolean douyinQuickSampleEnabled;

    /**
     * 构造注入所有依赖。
     *
     * @param productService               商品服务
     * @param productSnapshotMapper         商品快照 Mapper
     * @param productOperationStateMapper   商品运营状态 Mapper
     * @param sampleRequestMapper           寄样申请 Mapper
     * @param talentMapper                  达人 Mapper
     * @param talentClaimMapper             达人认领 Mapper
     * @param crawlerTalentInfoService      爬虫达人信息服务
     * @param sampleEligibilityService      达人资质评估服务
     * @param businessRuleConfigService     业务规则配置服务
     * @param sampleStatusLogService        寄样状态日志服务
     * @param douyinQuickSampleGateway      抖店快速寄样网关
     * @param sampleDomainEventPublisher    寄样领域事件发布器
     * @param douyinQuickSampleEnabled      是否启用外部快速寄样
     */
    public ProductQuickSampleService(
            ProductService productService,
            ProductMapper productMapper,
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
        this.productMapper = productMapper;
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

    /**
     * 批量发起快速寄样申请。
     * <p>
     * 遍历请求中的达人列表，逐个创建寄样申请。每个达人独立处理：
     * 单个达人失败不影响其他达人（catch 异常后记录失败，继续处理下一个）。
     * 整体返回汇总结果（成功/失败数量 + 每个达人的明细）。
     * </p>
     * <p>
     * 执行流程：角色权限校验 → 商品展示状态校验 → 遍历达人列表 → 逐个创建寄样申请。
     * 整体在同一事务中执行，任一达人导致的非业务异常将导致全部回滚。
     * </p>
     *
     * @param relationId 商品 relationId（Product 主键）
     * @param request    寄样申请请求（含达人列表、收件信息、备注等）
     * @param userId     操作用户 ID
     * @param deptId     操作用户部门 ID
     * @param roleCodes  操作用户角色集合（Collection 或逗号分隔字符串）
     * @return 申请结果汇总（含每个达人的明细）
     * @throws BusinessException 商品不存在或非展示状态时
     * @throws ForbiddenException 非渠道角色时
     */
    @Transactional(rollbackFor = Exception.class)
    public QuickSampleApplyResponse applyQuickSample(
            UUID relationId,
            QuickSampleApplyRequest request,
            UUID userId,
            UUID deptId,
            Object roleCodes) {
        /* 第一步：校验调用方必须是渠道角色或管理员 */
        ensureChannelRole(roleCodes);
        /* 第二步：解析商品库快照，并确保后续 sample_request.product_id 可引用 product 主表 */
        QuickSampleProductContext productContext = resolveQuickSampleProductContext(relationId);
        Product product = productContext.product();

        /* 寄样数量默认为 1 */
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        /* 管理员代选渠道时使用指定的 channelUserId，否则使用当前登录用户 */
        UUID effectiveChannelUserId = request.getChannelUserId() != null ? request.getChannelUserId() : userId;
        /* 检查外部网关是否可用（配置开关 + SDK 是否支持） */
        boolean externalEnabled = douyinQuickSampleEnabled;
        boolean externalSupported = douyinQuickSampleGateway.isSupported();
        DouyinQuickSampleGateway.SupportStatus supportStatus = douyinQuickSampleGateway.supportStatus();
        /* 构建响应对象，预设网关状态信息 */
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
                        productContext, request, talentId, quantity, userId, deptId, roleCodes, item, externalEnabled, externalSupported, effectiveChannelUserId);
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

    /**
     * 为单个达人创建寄样申请。
     * <p>
     * 核心流程：
     * <ol>
     *   <li>解析达人外部信息（{@link CrawlerTalentInfo}）</li>
     *   <li>查找或创建系统内达人记录</li>
     *   <li>校验达人是否在操作者私海中</li>
     *   <li>七日去重检查</li>
     *   <li>达人资质评估（不满足标准但有备注时仍可申请）</li>
     *   <li>调用外部抖店网关（可选），成功则标记外部申请 ID</li>
     *   <li>构建并持久化寄样申请记录</li>
     *   <li>记录状态日志 + 发布领域事件</li>
     * </ol>
     * </p>
     *
     * @param product           商品实体
     * @param request           寄样申请请求
     * @param talentExternalId  达人外部 ID（抖音 UID）
     * @param quantity          寄样数量
     * @param userId            操作用户 ID
     * @param deptId            操作用户部门 ID
     * @param roleCodes         用户角色集合
     * @param item              当前达人的结果项（通过引用填充）
     * @param externalEnabled   外部网关配置开关
     * @param externalSupported 外部网关 SDK 是否支持
     * @param channelUserId     渠道归属用户 ID（管理员代选时为指定渠道，否则为当前登录用户）
     * @return 新创建的寄样申请 ID
     * @throws BusinessException 达人不存在、商品状态异常、资质不足且无备注时
     * @throws ForbiddenException 达人不在私海中
     * @throws ValidateException  达人 ID 为空或达人信息不完整
     */
    private UUID createSingleSample(
            QuickSampleProductContext productContext,
            QuickSampleApplyRequest request,
            String talentExternalId,
            int quantity,
            UUID userId,
            UUID deptId,
            Object roleCodes,
            QuickSampleApplyResponse.QuickSampleApplyItemResult item,
            boolean externalEnabled,
            boolean externalSupported,
            UUID channelUserId) {
        Product product = productContext.product();
        ProductSnapshot snapshot = productContext.snapshot();
        ProductOperationState state = productContext.state();
        /* 解析达人外部信息 */
        CrawlerTalentInfo talentInfo = resolveSampleTalentInfo(talentExternalId);
        /* 查找已有达人记录，不存在则自动创建 */
        Talent talent = findOrCreateTalent(talentInfo);
        /* 校验达人是否在指定渠道人员私海中（管理员跳过） */
        ensureChannelTalentClaim(channelUserId, talent.getId(), roleCodes);
        /* 七日去重校验（管理员和主管跳过） */
        checkSevenDaysLimit(channelUserId, talent.getId(), product.getId(), roleCodes);
        /* 达人资质评估：不满足标准时必须填写备注说明原因 */
        SampleEligibilityService.EligibilityResult eligibility = sampleEligibilityService.evaluate(talent, talentInfo);
        if (!eligibility.eligible() && !StringUtils.hasText(request.getRemark())) {
            throw BusinessException.stateInvalid("达人未满足默认寄样标准，请填写备注说明申请原因");
        }

        /* 默认设置为降级模式，外部网关成功后覆盖 */
        item.setExternalApplied(false);
        item.setFallback(true);
        item.setGatewayStatus(GATEWAY_STATUS_UNSUPPORTED);
        item.setFallbackType(FALLBACK_TYPE_LOCAL);
        item.setExternalApplyId(null);
        /* 尝试调用外部抖店网关 */
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
            /* 外部网关调用成功，更新结果项 */
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
        /* 达人信息快照（冗余存储，方便查询） */
        sample.setTalentId(talent.getId());
        sample.setTalentUid(talentInfo.getTalentId());
        sample.setTalentNickname(talentInfo.getNickname());
        sample.setTalentFansCount(talentInfo.getFansCount());
        sample.setTalentCreditScore(talentInfo.getCreditScore());
        sample.setTalentMainCategory(talentInfo.getMainCategory());
        sample.setProductId(product.getId());
        sample.setUserId(userId);
        sample.setDeptId(deptId);
        /* 渠道归属人（寄样由指定渠道人员负责，管理员代选时为指定渠道） */
        sample.setChannelUserId(channelUserId);
        sample.setChannelDeptId(deptId);
        sample.setExpectedSampleNum(quantity);
        sample.setActualSampleNum(0);
        sample.setRecipientName(trimToNull(request.getRecipientName()));
        sample.setRecipientPhone(trimToNull(request.getRecipientPhone()));
        sample.setRecipientAddress(trimToNull(request.getRecipientAddress()));
        /* 初始状态为待审核 */
        sample.setStatus(SAMPLE_STATUS_PENDING_AUDIT);
        sample.setRemark(buildRemark(request));
        /* 根据外部申请结果设置申请来源和外部信息 */
        if (item.isExternalApplied() && externalResult != null) {
            sample.setApplySource(APPLY_SOURCE_DOUYIN_QUICK);
            sample.setExternalApplyId(externalResult.externalApplyId());
            sample.setExternalStatus(externalResult.externalStatus());
            sample.setExternalRawPayload(externalResult.rawPayload());
        } else {
            /* 外部网关不可用，降级为本地寄样 */
            sample.setApplySource(APPLY_SOURCE_LOCAL_FALLBACK);
            item.setFallback(true);
            item.setExternalApplied(false);
            item.setGatewayStatus(GATEWAY_STATUS_UNSUPPORTED);
            item.setFallbackType(FALLBACK_TYPE_LOCAL);
            item.setMessage(FALLBACK_MESSAGE);
        }
        /* 存储扩展数据（资质评估结果、网关状态等） */
        sample.setExtraData(buildExtraData(request, eligibility, item.isExternalApplied()));
        sampleRequestMapper.insert(sample);
        /* 回写收货地址到认领记录，供下次寄样自动带入 */
        writeBackClaimAddress(channelUserId, talent.getId(), sample);
        /* 记录初始状态日志 */
        sampleStatusLogService.log(sample.getId(), null, sample.getStatus(), userId, "quick sample from product library");
        /* 发布寄样创建领域事件（异步通知其他子系统） */
        sampleDomainEventPublisher.publishSampleCreated(
                sample,
                product.getName(),
                null,
                state == null ? null : state.getAssigneeId(),
                snapshot == null || snapshot.getActivityId() == null ? null : snapshot.getActivityId());
        return sample.getId();
    }

    /**
     * 解析商品库入口的商品上下文。
     * <p>
     * 商品库前端传入的是 {@code product_snapshot.id}，但 {@code sample_request.product_id}
     * 外键指向 {@code product.id}。因此快速寄样写入前必须把快照商品物化为主商品表事实，
     * 否则大部分真实商品会在插入寄样单时因外键缺失而失败。
     * </p>
     *
     * @param relationId 商品库快照 ID
     * @return 已校验展示状态、且 product 主表存在的商品上下文
     */
    private QuickSampleProductContext resolveQuickSampleProductContext(UUID relationId) {
        Product legacyProduct = productService.getById(relationId);
        if (legacyProduct == null) {
            throw BusinessException.notFound("商品不存在或已不在商品库，请刷新商品后重试");
        }
        ProductSnapshot snapshot = productSnapshotMapper.selectById(legacyProduct.getId());
        if (snapshot == null || !StringUtils.hasText(snapshot.getProductId())) {
            throw BusinessException.notFound("商品快照不存在或商品 ID 缺失，请刷新商品后重试");
        }
        ProductOperationState state = resolveDisplayingState(snapshot);
        Product product = ensurePersistedProduct(snapshot);
        return new QuickSampleProductContext(product, snapshot, state);
    }

    /**
     * 确保商品主表存在对应记录，用于满足寄样单外键约束。
     */
    private Product ensurePersistedProduct(ProductSnapshot snapshot) {
        Product existing = findPersistedProduct(snapshot.getProductId());
        if (existing != null) {
            return existing;
        }
        Product product = materializeProductFromSnapshot(snapshot);
        try {
            productMapper.insert(product);
            return product;
        } catch (DuplicateKeyException ex) {
            Product raced = findPersistedProduct(snapshot.getProductId());
            if (raced != null) {
                return raced;
            }
            throw ex;
        }
    }

    private Product findPersistedProduct(String productId) {
        if (!StringUtils.hasText(productId)) {
            return null;
        }
        return productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getProductId, productId)
                .last("LIMIT 1"));
    }

    private Product materializeProductFromSnapshot(ProductSnapshot snapshot) {
        Product product = new Product();
        product.setId(snapshot.getId());
        product.setProductId(snapshot.getProductId());
        product.setName(StringUtils.hasText(snapshot.getTitle()) ? snapshot.getTitle() : snapshot.getProductId());
        product.setPrice(snapshot.getPrice());
        product.setCover(snapshot.getCover());
        product.setDetailUrl(snapshot.getDetailUrl());
        product.setStatus(snapshot.getStatus() == null ? 1 : snapshot.getStatus());
        product.setCheckStatus(2);
        return product;
    }

    /**
     * 解析并校验商品的展示中状态。
     * <p>
     * 通过商品快照查找对应的运营状态，校验 displayStatus 为 DISPLAYING。
     * 仅展示中的商品才允许发起快速寄样。
     * </p>
     *
     * @param snapshot 商品快照
     * @return 展示中的运营状态
     * @throws BusinessException 商品快照不存在或商品非展示状态时
     */
    private ProductOperationState resolveDisplayingState(ProductSnapshot snapshot) {
        /* 通过 activityId + productId 查询运营状态 */
        ProductOperationState state = productOperationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getActivityId, snapshot.getActivityId())
                .eq(ProductOperationState::getProductId, snapshot.getProductId())
                .last("LIMIT 1"));
        if (state == null || !ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus())) {
            throw BusinessException.stateInvalid("仅展示中的商品可发起快速寄样");
        }
        if (!Boolean.TRUE.equals(state.getSelectedToLibrary())) {
            throw BusinessException.stateInvalid("该商品尚未加入商品库，请先审核并加入商品库后再进行寄样操作");
        }
        return state;
    }

    /**
     * 角色权限校验：仅渠道员工、渠道主管和管理员可使用快速寄样。
     *
     * @param roleCodes 用户角色集合
     * @throws ForbiddenException 不满足角色要求时
     */
    private void ensureChannelRole(Object roleCodes) {
        if (!hasAnyRole(roleCodes, RoleCodes.CHANNEL_STAFF, RoleCodes.CHANNEL_LEADER)
                && !hasAnyRole(roleCodes, RoleCodes.ADMIN)) {
            throw new ForbiddenException("仅渠道角色可使用快速寄样");
        }
    }

    /**
     * 校验达人是否在当前用户的私海中。
     * <p>
     * 管理员跳过此校验。普通用户必须先认领达人后才能申请寄样。
     * </p>
     *
     * @param userId    操作用户 ID
     * @param talentId  系统内达人 ID
     * @param roleCodes 用户角色集合
     * @throws ForbiddenException 达人不在私海中
     * @throws ValidateException  达人或用户信息不完整
     */
    private void ensureChannelTalentClaim(UUID userId, UUID talentId, Object roleCodes) {
        /* 管理员跳过私海校验 */
        if (hasAnyRole(roleCodes, RoleCodes.ADMIN)) {
            return;
        }
        if (userId == null || talentId == null) {
            throw new ValidateException("达人信息不完整");
        }
        /* 查询该达人是否在当前用户名下 */
        if (talentClaimMapper.findActiveByTalentAndUser(talentId, userId) == null) {
            throw new ForbiddenException("该达人未在你的私海中，请先认领后再申请寄样");
        }
    }

    /**
     * 七日去重校验：同一用户对同一达人同一商品不允许在限制天数内重复申请。
     * <p>
     * 管理员和渠道主管跳过此校验。
     * 已驳回的寄样申请（status=REJECTED）不计入去重。
     * 限制天数通过 {@link BusinessRuleConfigService#getSampleRestrictDays()} 动态配置。
     * </p>
     *
     * @param userId    操作用户 ID
     * @param talentId  达人 ID
     * @param productId 商品 ID
     * @param roleCodes 用户角色集合
     * @throws BusinessException 在限制期内已存在相同申请时
     */
    private void checkSevenDaysLimit(UUID userId, UUID talentId, UUID productId, Object roleCodes) {
        /* 管理员和主管不受去重限制 */
        if (hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.CHANNEL_LEADER)) {
            return;
        }
        /* 去重功能可通过配置关闭 */
        if (!businessRuleConfigService.isSampleRestrictEnabled()) {
            return;
        }
        int restrictDays = businessRuleConfigService.getSampleRestrictDays();
        LocalDateTime since = LocalDateTime.now().minusDays(restrictDays);
        Long count = sampleRequestMapper.selectCount(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getChannelUserId, userId)
                .eq(SampleRequest::getTalentId, talentId)
                .eq(SampleRequest::getProductId, productId)
                .ne(SampleRequest::getStatus, SAMPLE_STATUS_REJECTED) /* 已驳回的不计入去重 */
                .ge(SampleRequest::getCreateTime, since));
        if (count != null && count > 0) {
            throw BusinessException.duplicate("Duplicate sample request is blocked within " + restrictDays + " days");
        }
    }

    /**
     * 解析达人外部信息。
     *
     * @param talentId 达人外部 ID（抖音 UID）
     * @return 达人爬虫信息
     * @throws ValidateException talentId 为空时
     * @throws BusinessException 达人不存在时
     */
    private CrawlerTalentInfo resolveSampleTalentInfo(String talentId) {
        if (!StringUtils.hasText(talentId)) {
            throw new ValidateException("talentId 不能为空");
        }
        String normalizedTalentId = talentId.trim();
        CrawlerTalentInfo info = crawlerTalentInfoService.findByTalentId(normalizedTalentId);
        if (info != null) {
            return info;
        }
        Talent manualTalent = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, normalizedTalentId)
                .last("LIMIT 1"));
        if (manualTalent == null) {
            throw BusinessException.notFound("达人不存在");
        }
        return buildCrawlerSnapshotFromTalent(manualTalent, normalizedTalentId);
    }

    private CrawlerTalentInfo buildCrawlerSnapshotFromTalent(Talent talent, String selectedTalentId) {
        CrawlerTalentInfo info = new CrawlerTalentInfo();
        info.setTalentId(StringUtils.hasText(talent.getDouyinUid()) ? talent.getDouyinUid() : selectedTalentId);
        info.setNickname(talent.getNickname());
        info.setAvatarUrl(talent.getAvatarUrl());
        info.setFansCount(talent.getFans());
        info.setMainCategory(StringUtils.hasText(talent.getMainCategory()) ? talent.getMainCategory() : talent.getCategories());
        info.setRegion(talent.getIpLocation());
        return info;
    }

    /**
     * 查找或创建系统内达人记录。
     * <p>
     * 先按 douyinUid 查询已有达人，不存在则自动创建一条初始记录。
     * 新创建的达人状态默认为 1（正常）。
     * </p>
     *
     * @param talentInfo 达人爬虫信息
     * @return 系统内达人实体（新建或已有）
     */
    private Talent findOrCreateTalent(CrawlerTalentInfo talentInfo) {
        /* 先尝试查找已有的达人记录 */
        Talent existing = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, talentInfo.getTalentId())
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        /* 不存在则创建新达人 */
        Talent talent = new Talent();
        talent.setId(UUID.randomUUID());
        talent.setDouyinUid(talentInfo.getTalentId());
        talent.setNickname(talentInfo.getNickname());
        talent.setStatus(1); /* 正常状态 */
        talentMapper.insert(talent);
        return talent;
    }

    /**
     * 构建寄样申请的扩展数据（JSON 存储）。
     * <p>
     * 包含：资质评估结果（是否通过 + 原因列表）、申请来源、规格信息、
     * 是否外部申请、申请渠道、网关状态、降级类型。
     * </p>
     *
     * @param request        寄样申请请求
     * @param eligibility    资质评估结果
     * @param externalApplied 是否通过外部网关申请成功
     * @return 扩展数据 Map
     */
    private Map<String, Object> buildExtraData(
            QuickSampleApplyRequest request,
            SampleEligibilityService.EligibilityResult eligibility,
            boolean externalApplied) {
        Map<String, Object> extra = new LinkedHashMap<>();
        /* 资质评估结果 */
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

    /**
     * 拼接待审核备注。
     * <p>
     * 由规格说明和用户备注拼接，以分号分隔。两者都为空时返回 null。
     * </p>
     *
     * @param request 寄样申请请求
     * @return 拼接后的备注；为空时返回 null
     */
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

    private record QuickSampleProductContext(
            Product product,
            ProductSnapshot snapshot,
            ProductOperationState state) {
    }

    /**
     * 生成寄样申请编号。
     * <p>
     * 格式：QS + 日期(yyyyMMdd) + UUID 前 8 位（大写），如 QS20260527A1B2C3D4。
     * </p>
     *
     * @return 唯一的申请编号
     */
    private String generateRequestNo() {
        return "QS" + LocalDateTime.now().format(REQUEST_NO_DATE) + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /**
     * 去除空白后返回；空白字符串返回 null。
     *
     * @param value 原始字符串
     * @return trim 后的字符串；空白时返回 null
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 判断用户是否拥有指定角色中的任意一个。
     * <p>
     * 支持两种 roleCodes 格式：
     * <ul>
     *   <li>Collection 类型：直接遍历匹配</li>
     *   <li>字符串类型：按逗号分割后匹配（支持 "[ROLE_A, ROLE_B]" 格式）</li>
     * </ul>
     * 匹配时忽略大小写和首尾空白。
     * </p>
     *
     * @param roleCodes      用户角色（Collection 或字符串）
     * @param expectedRoles  需要匹配的期望角色列表
     * @return true 表示拥有任意一个期望角色
     */
    private boolean hasAnyRole(Object roleCodes, String... expectedRoles) {
        if (roleCodes == null || expectedRoles == null || expectedRoles.length == 0) {
            return false;
        }
        /* 将期望角色转为小写 Set */
        java.util.Set<String> expected = java.util.Arrays.stream(expectedRoles)
                .map(role -> role == null ? "" : role.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        /* Collection 类型直接流式匹配 */
        if (roleCodes instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> item == null ? "" : item.toString().trim().toLowerCase(Locale.ROOT))
                    .anyMatch(expected::contains);
        }
        /* 字符串类型：去除方括号后按逗号分割匹配 */
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

    /**
     * 解析异常消息，返回用户友好的错误提示。
     * <p>
     * 优先使用业务异常（BusinessException/ForbiddenException/ValidateException）的消息，
     * 兜底返回原始异常消息或默认 "申请失败"。
     * </p>
     *
     * @param ex 异常对象
     * @return 错误提示消息
     */
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

    /**
     * 寄样创建成功后，将本次填写的收货地址回写到认领记录。
     * <p>仅当地址非空时才回写；认领关系不存在时静默跳过（管理员豁免场景）。</p>
     *
     * @param channelUserId 渠道归属用户 ID
     * @param talentId      达人 ID
     * @param sample        本次创建的寄样申请
     */
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
        TalentClaim claim = talentClaimMapper.findActiveByTalentAndUser(talentId, channelUserId);
        if (claim != null) {
            claim.setRecipientName(name);
            claim.setRecipientPhone(phone);
            claim.setRecipientAddress(address);
            talentClaimMapper.updateById(claim);
            log.debug("T-ADDR: writeback claim address for talent={}, channel={}", talentId, channelUserId);
        }
    }
}
