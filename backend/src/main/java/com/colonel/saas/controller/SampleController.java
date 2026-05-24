package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.common.exception.ValidateException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.sample.LogisticsImportResult;
import com.colonel.saas.domain.sample.event.SampleDomainEventPublisher;
import com.colonel.saas.gateway.logistics.query.LogisticsQueryResult;
import com.colonel.saas.entity.SampleLogisticsTrace;
import com.colonel.saas.dto.SampleTalentQueryRequest;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.entity.SampleStatusLog;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.SampleStatusLogMapper;
import com.colonel.saas.mapper.SysUserMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.CrawlerTalentInfoService;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.colonel.saas.service.ProductService;
import com.colonel.saas.service.SampleStatusLogService;
import com.colonel.saas.service.SampleEligibilityService;
import com.colonel.saas.dto.SampleApplyRequest;
import com.colonel.saas.service.SampleLogisticsImportService;
import com.colonel.saas.service.SampleLogisticsSyncService;
import com.colonel.saas.vo.SampleTalentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Validated
@Tag(name = "寄样管理", description = "寄样申请、寄样列表、达人候选搜索、状态流转与删除接口。")
@Slf4j
@RestController
@RequestMapping("/samples")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.OPS_STAFF})
public class SampleController extends BaseController {

    private static final DateTimeFormatter REQUEST_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long BOARD_BATCH_SIZE = 2000L;
    private static final long PRODUCT_KEYWORD_BATCH_SIZE = 500L;
    private static final long EXPORT_BATCH_SIZE = 2000L;
    private static final String APPLY_SOURCE_MANUAL = "MANUAL";
    private static final String APPLY_SOURCE_INTERNAL_QUICK_SAMPLE = "INTERNAL_QUICK_SAMPLE";

    private final SampleRequestMapper sampleRequestMapper;
    private final ProductMapper productMapper;
    private final ProductOperationStateMapper productOperationStateMapper;
    private final ProductSnapshotMapper productSnapshotMapper;
    private final SysUserMapper sysUserMapper;
    private final TalentMapper talentMapper;
    private final TalentClaimMapper talentClaimMapper;
    private final SampleStatusLogService sampleStatusLogService;
    private final SampleStatusLogMapper sampleStatusLogMapper;
    private final CrawlerTalentInfoService crawlerTalentInfoService;
    private final BusinessRuleConfigService businessRuleConfigService;
    private final ProductService productService;
    private final SampleEligibilityService sampleEligibilityService;
    private final SampleLogisticsSyncService sampleLogisticsSyncService;
    private final SampleLogisticsImportService sampleLogisticsImportService;
    private final SampleDomainEventPublisher sampleDomainEventPublisher;

    public SampleController(
            SampleRequestMapper sampleRequestMapper,
            ProductMapper productMapper,
            ProductOperationStateMapper productOperationStateMapper,
            ProductSnapshotMapper productSnapshotMapper,
            SysUserMapper sysUserMapper,
            TalentMapper talentMapper,
            TalentClaimMapper talentClaimMapper,
            SampleStatusLogService sampleStatusLogService,
            SampleStatusLogMapper sampleStatusLogMapper,
            CrawlerTalentInfoService crawlerTalentInfoService,
            BusinessRuleConfigService businessRuleConfigService,
            ProductService productService,
            SampleEligibilityService sampleEligibilityService,
            SampleLogisticsSyncService sampleLogisticsSyncService,
            SampleLogisticsImportService sampleLogisticsImportService,
            SampleDomainEventPublisher sampleDomainEventPublisher) {
        this.sampleRequestMapper = sampleRequestMapper;
        this.productMapper = productMapper;
        this.productOperationStateMapper = productOperationStateMapper;
        this.productSnapshotMapper = productSnapshotMapper;
        this.sysUserMapper = sysUserMapper;
        this.talentMapper = talentMapper;
        this.talentClaimMapper = talentClaimMapper;
        this.sampleStatusLogService = sampleStatusLogService;
        this.sampleStatusLogMapper = sampleStatusLogMapper;
        this.crawlerTalentInfoService = crawlerTalentInfoService;
        this.businessRuleConfigService = businessRuleConfigService;
        this.productService = productService;
        this.sampleEligibilityService = sampleEligibilityService;
        this.sampleLogisticsSyncService = sampleLogisticsSyncService;
        this.sampleLogisticsImportService = sampleLogisticsImportService;
        this.sampleDomainEventPublisher = sampleDomainEventPublisher;
    }

    @Operation(summary = "创建寄样申请", description = "发起寄样申请并初始化寄样状态，用于达人寄样闭环的起点。")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<SampleVO> createSample(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "寄样申请请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"productId\":\"11111111-1111-1111-1111-111111111111\",\"talentId\":\"test_talent_001\",\"quantity\":1,\"remark\":\"优先安排发货\"}"))
            )
            @Valid @RequestBody SampleApplyRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        ensureSampleApplyPermission(roleCodes);
        Product product = requireProduct(request.getProductId());
        // 寄样前必须先加入商品库
        ProductSnapshot snapshot = productSnapshotMapper.selectById(product.getId());
        if (snapshot != null) {
            ProductOperationState state = productOperationStateMapper.selectOne(
                    new LambdaQueryWrapper<ProductOperationState>()
                            .eq(ProductOperationState::getActivityId, snapshot.getActivityId())
                            .eq(ProductOperationState::getProductId, snapshot.getProductId())
                            .last("LIMIT 1"));
            if (state == null || !Boolean.TRUE.equals(state.getSelectedToLibrary())) {
                throw BusinessException.stateInvalid("该商品尚未加入商品库，请先审核并加入商品库后再进行寄样操作");
            }
        }
        CrawlerTalentInfo talentInfo = resolveSampleTalentInfo(request.getTalentId());
        Talent talent = findOrCreateTalentFromCrawler(talentInfo);
        ensureChannelTalentClaim(userId, talent.getId(), roleCodes);
        checkSevenDaysLimit(userId, talent.getId(), product.getId(), roleCodes);
        SampleEligibilityService.EligibilityResult eligibility = ensureEligibilityReasonIfNeeded(request, talent, talentInfo);

        SampleRequest sample = new SampleRequest();
        UUID currentDeptId = resolveUserDeptId(userId);
        sample.setId(UUID.randomUUID());
        sample.setRequestNo(generateRequestNo());
        sample.setTalentId(talent.getId());
        sample.setTalentUid(talentInfo.getTalentId());
        sample.setTalentNickname(StringUtils.hasText(request.getTalentNickname()) ? request.getTalentNickname() : talentInfo.getNickname());
        sample.setTalentFansCount(request.getTalentFansCount() != null ? request.getTalentFansCount() : talentInfo.getFansCount());
        sample.setTalentCreditScore(request.getTalentCreditScore() != null ? request.getTalentCreditScore() : talentInfo.getCreditScore());
        sample.setTalentMainCategory(StringUtils.hasText(request.getTalentMainCategory()) ? request.getTalentMainCategory() : talentInfo.getMainCategory());
        sample.setProductId(product.getId());
        sample.setUserId(userId);
        sample.setDeptId(currentDeptId);
        sample.setChannelUserId(userId);
        sample.setChannelDeptId(currentDeptId);
        sample.setExpectedSampleNum(request.getQuantity());
        sample.setActualSampleNum(0);
        sample.setRecipientName(trimToNull(request.getRecipientName()));
        sample.setRecipientPhone(trimToNull(request.getRecipientPhone()));
        sample.setRecipientAddress(trimToNull(request.getRecipientAddress()));
        sample.setStatus(SampleStatus.PENDING_AUDIT.code);
        sample.setRemark(request.getRemark());
        sample.setExtraData(buildSampleExtraData(request, eligibility));
        sampleRequestMapper.insert(sample);
        sampleStatusLogService.log(sample.getId(), null, sample.getStatus(), userId, "create sample request");
        sampleDomainEventPublisher.publishSampleCreated(
                sample,
                product.getName(),
                resolveUserDisplayName(userId),
                resolveColonelUserId(product),
                product.getActivityId() == null ? null : String.valueOf(product.getActivityId()));

        return ok(toVO(sample, product, product.getName(), sample.getTalentNickname()));
    }

    @Operation(summary = "寄样资格预检", description = "按当前寄样默认标准检查达人是否满足要求；不满足时前端需提醒并要求填写申请原因。")
    @PostMapping("/eligibility-check")
    public ApiResult<SampleEligibilityCheckVO> checkEligibility(
            @Valid @RequestBody SampleApplyRequest request,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        ensureSampleApplyPermission(roleCodes);
        CrawlerTalentInfo talentInfo = resolveSampleTalentInfo(request.getTalentId());
        Talent talent = findOrCreateTalentFromCrawler(talentInfo);
        return ok(toEligibilityVO(sampleEligibilityService.evaluate(talent, talentInfo)));
    }

    @Operation(summary = "寄样分页", description = "分页查询寄样申请列表，用于寄样业务页面。")
    @GetMapping
    public ApiResult<PageResult<SampleVO>> getSamplePage(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @Parameter(description = "关键字，可匹配达人昵称、达人 UID、寄样单号或商品名称。") @RequestParam(required = false) String keyword,
            @Parameter(description = "寄样状态。可用值包括 PENDING_AUDIT、PENDING_SHIP、SHIPPING、DELIVERED、PENDING_HOMEWORK、COMPLETED、REJECTED、CLOSED。") @RequestParam(required = false) String status,
            @Parameter(description = "渠道负责人用户 ID。") @RequestParam(required = false) UUID channelUserId,
            @Parameter(description = "招商负责人用户 ID。") @RequestParam(required = false) UUID recruiterUserId,
            @Parameter(description = "商品 ID 或商品名称。") @RequestParam(required = false) String productKeyword,
            @Parameter(description = "店铺 ID 或店铺名称。") @RequestParam(required = false) String shopKeyword,
            @Parameter(description = "物流单号。") @RequestParam(required = false) String trackingNo,
            @Parameter(description = "申请编号 / 合作单号。") @RequestParam(required = false) String requestNo,
            @Parameter(description = "达人昵称或达人号。") @RequestParam(required = false) String talentKeyword,
            @Parameter(description = "合作类型。") @RequestParam(required = false) String cooperationType,
            @Parameter(description = "寄样负责方。") @RequestParam(required = false) String sampleOwnerType,
            @Parameter(description = "交作业类型。") @RequestParam(required = false) String homeworkType,
            @Parameter(description = "收货人姓名。") @RequestParam(required = false) String recipientName,
            @Parameter(description = "收货人手机号。") @RequestParam(required = false) String recipientPhone,
            @Parameter(description = "申请开始时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime applyStartTime,
            @Parameter(description = "申请结束时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime applyEndTime,
            @Parameter(description = "交作业 / 完成开始时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime homeworkStartTime,
            @Parameter(description = "交作业 / 完成结束时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime homeworkEndTime,
            @Parameter(description = "物流公司。") @RequestParam(required = false) String logisticsCompany,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) com.colonel.saas.common.enums.DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        Page<SampleRequest> pageReq = new Page<>(page, size);
        QueryWrapper<SampleRequest> wrapper = new QueryWrapper<>();

        if (isOpsStaffOnly(roleCodes)) {
            if (!StringUtils.hasText(status)) {
                status = "PENDING_SHIP";
            }
            ensureOpsVisibleStatus(status);
        }

        // 招商专员在个人范围下，默认展示待审核单据
        if (hasAnyRole(roleCodes, RoleCodes.BIZ_STAFF) && !hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_LEADER) && !StringUtils.hasText(status)) {
            status = "PENDING_AUDIT";
        }

        applySampleQueryFilters(
                wrapper,
                status,
                keyword,
                channelUserId,
                productKeyword,
                shopKeyword,
                trackingNo,
                requestNo,
                talentKeyword,
                cooperationType,
                sampleOwnerType,
                homeworkType,
                recipientName,
                recipientPhone,
                applyStartTime,
                applyEndTime,
                homeworkStartTime,
                homeworkEndTime,
                logisticsCompany);

        IPage<SampleRequest> samplePage;
        // 招商专员且数据范围为个人：按“我负责的商品”过滤
        if (dataScope == com.colonel.saas.common.enums.DataScope.PERSONAL && hasAnyRole(roleCodes, RoleCodes.BIZ_STAFF) && !hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_LEADER)) {
            samplePage = recruiterUserId == null
                    ? sampleRequestMapper.findPageForAuditor(pageReq, userId, wrapper)
                    : sampleRequestMapper.findPageForAuditor(pageReq, userId, wrapper, recruiterUserId);
        } else {
            samplePage = recruiterUserId == null
                    ? sampleRequestMapper.findPageWithScope(pageReq, wrapper)
                    : sampleRequestMapper.findPageWithScope(pageReq, wrapper, recruiterUserId);
        }

        Map<UUID, Product> productMap = loadProducts(samplePage.getRecords().stream()
                .map(SampleRequest::getProductId)
                .collect(Collectors.toSet()));

        List<SampleVO> records = samplePage.getRecords().stream()
                .map(item -> toVO(
                        item,
                        productMap.get(item.getProductId()),
                        productMap.get(item.getProductId()) == null ? null : productMap.get(item.getProductId()).getName(),
                        item.getTalentNickname()))
                .toList();

        Page<SampleVO> voPage = new Page<>(samplePage.getCurrent(), samplePage.getSize(), samplePage.getTotal());
        voPage.setRecords(records);
        return okPage(voPage);
    }

    public ApiResult<PageResult<SampleVO>> getSamplePage(
            long page,
            long size,
            String keyword,
            String status,
            UUID userId,
            UUID deptId,
            com.colonel.saas.common.enums.DataScope dataScope,
            Object roleCodes) {
        return getSamplePage(page, size, keyword, status, null, null, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<PageResult<SampleVO>> getSamplePage(
            long page,
            long size,
            String keyword,
            String status,
            UUID channelUserId,
            UUID recruiterUserId,
            UUID userId,
            UUID deptId,
            com.colonel.saas.common.enums.DataScope dataScope,
            Object roleCodes) {
        return getSamplePage(
                page, size, keyword, status, channelUserId, recruiterUserId,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                userId, deptId, dataScope, roleCodes);
    }

    @Operation(summary = "寄样达人搜索", description = "搜索可用于寄样申请的达人候选数据，数据来源于达人抓取结果。")
    @GetMapping("/talent-candidates")
    public ApiResult<PageResult<SampleTalentVO>> searchTalents(@Valid SampleTalentQueryRequest request) {
        IPage<SampleTalentVO> page = crawlerTalentInfoService.searchTalents(
                request.getKeyword(),
                request.getRegion(),
                request.getMinFans(),
                request.getMaxFans(),
                request.getMinScore(),
                request.getPage(),
                request.getSize()
        );
        return okPage(page);
    }

    @Operation(summary = "寄样商品搜索", description = "搜索可用于寄样申请的商品候选数据，返回可直接用于创建寄样申请的商品主键。")
    @GetMapping("/product-candidates")
    public ApiResult<PageResult<SampleProductVO>> searchProducts(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @Parameter(description = "商品名称或商品 ID。") @RequestParam(required = false) String keyword,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        ensureSampleApplyPermission(roleCodes);
        IPage<Product> productPage = productService.getSelectedLibraryPage(page, size, keyword, null);
        Page<SampleProductVO> result = new Page<>(productPage.getCurrent(), productPage.getSize(), productPage.getTotal());
        result.setRecords(productPage.getRecords().stream()
                .map(product -> new SampleProductVO(product.getId(), product.getProductId(), product.getName()))
                .toList());
        return okPage(result);
    }

    ApiResult<PageResult<SampleProductVO>> searchProducts(long page, long size, String keyword) {
        return searchProducts(page, size, keyword, null);
    }

    @Operation(summary = "寄样看板", description = "按状态分组返回全量寄样单，用于看板视图。")
    @GetMapping("/board")
    public ApiResult<Map<String, List<SampleBoardCard>>> getSampleBoard(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        if (isOpsStaffOnly(roleCodes)) {
            throw new ForbiddenException("运营角色仅可通过寄样发货台查看待发货及物流数据");
        }
        QueryWrapper<SampleRequest> wrapper = new QueryWrapper<>();
        List<SampleRequest> allSamples = loadBoardSamples(wrapper);

        Map<UUID, Product> productMap = loadProducts(allSamples.stream()
                .map(SampleRequest::getProductId)
                .collect(Collectors.toSet()));

        Map<String, List<SampleBoardCard>> board = new HashMap<>();
        for (SampleStatus s : SampleStatus.values()) {
            String key = toLegacyStatus(s);
            board.putIfAbsent(key, new ArrayList<>());
        }

        for (SampleRequest sample : allSamples) {
            SampleStatus internalStatus = SampleStatus.fromCode(sample.getStatus());
            String legacyStatus = toLegacyStatus(internalStatus);
            Product product = productMap.get(sample.getProductId());
            board.get(legacyStatus).add(toBoardCard(sample, product, internalStatus));
        }

        return ok(board);
    }

    ApiResult<Map<String, List<SampleBoardCard>>> getSampleBoard(UUID userId, UUID deptId, DataScope dataScope) {
        return getSampleBoard(userId, deptId, dataScope, null);
    }

    @Operation(summary = "寄样状态流转矩阵", description = "返回寄样状态机的动作、前置状态、后置状态、角色、必填字段和错误文案，用于前端按钮与验收核验。")
    @GetMapping("/status-transitions")
    public ApiResult<List<SampleStatusTransitionVO>> getStatusTransitions() {
        return ok(buildStatusTransitions());
    }

    private List<SampleRequest> loadBoardSamples(QueryWrapper<SampleRequest> wrapper) {
        List<SampleRequest> result = new ArrayList<>();
        long current = 1L;
        while (true) {
            IPage<SampleRequest> scopedPage = sampleRequestMapper.findPageWithScope(new Page<>(current, BOARD_BATCH_SIZE), wrapper);
            List<SampleRequest> records = scopedPage.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            result.addAll(records);
            if (current >= scopedPage.getPages()) {
                break;
            }
            current++;
        }
        return result;
    }

    @Operation(summary = "寄样详情", description = "查询单个寄样申请详情。")
    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    public ApiResult<SampleVO> getSampleById(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        SampleRequest sample = requireSample(id, userId, deptId, dataScope, roleCodes);
        Product product = productMapper.selectById(sample.getProductId());
        return ok(toVO(
                sample,
                product,
                product == null ? null : product.getName(),
                sample.getTalentNickname()));
    }

    ApiResult<SampleVO> getSampleById(UUID id, UUID userId, UUID deptId, DataScope dataScope) {
        return getSampleById(id, userId, deptId, dataScope, null);
    }

    @Operation(summary = "寄样状态日志", description = "查询寄样申请的状态变更历史记录。")
    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/status-logs")
    public ApiResult<List<StatusLogVO>> getStatusLogs(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        requireSample(id, userId, deptId, dataScope, roleCodes);
        List<SampleStatusLog> logs = sampleStatusLogMapper.selectList(
                new LambdaQueryWrapper<SampleStatusLog>()
                        .eq(SampleStatusLog::getRequestId, id)
                        .orderByDesc(SampleStatusLog::getOperateTime));
        List<StatusLogVO> voList = logs.stream().map(log -> {
            StatusLogVO vo = new StatusLogVO();
            vo.setId(log.getId());
            vo.setFromStatus(log.getFromStatus() == null ? null : toLegacyStatus(SampleStatus.fromCode(log.getFromStatus())));
            vo.setToStatus(log.getToStatus() == null ? null : toLegacyStatus(SampleStatus.fromCode(log.getToStatus())));
            vo.setOperatorName(resolveUserDisplayName(log.getOperatorId()));
            vo.setOperateTime(log.getOperateTime());
            vo.setRemark(log.getRemark());
            return vo;
        }).toList();
        return ok(voList);
    }

    @Operation(summary = "寄样状态流转", description = "推进寄样申请状态机。动作值必须符合当前状态允许的流转规则。")
    @PutMapping("/{id:[0-9a-fA-F\\-]{36}}/status")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<SampleVO> actionSample(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "寄样状态流转请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"action\":\"SHIPPING\",\"trackingNo\":\"SF1234567890\",\"reason\":\"顺丰发出\"}"))
            )
            @Valid @RequestBody SampleActionRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        String action = normalizeAction(request.getAction());
        ensureActionRolePermission(action, roleCodes);
        SampleRequest sample = requireSample(id, userId, deptId, dataScope, roleCodes);
        LocalDateTime now = LocalDateTime.now();
        int fromStatus = sample.getStatus();
        SampleStatus current = SampleStatus.fromCode(fromStatus);

        if ("PENDING_SHIP".equals(action)) {
            ensureTransition(current, SampleStatus.PENDING_AUDIT);
            sample.setStatus(SampleStatus.PENDING_SHIP.code);
            sample.setAuditTime(now);
        } else if ("REJECTED".equals(action)) {
            ensureTransition(current, SampleStatus.PENDING_AUDIT);
            if (!StringUtils.hasText(request.getReason())) {
                throw BusinessException.param("reason is required when reject sample request");
            }
            sample.setStatus(SampleStatus.REJECTED.code);
            sample.setRejectReason(request.getReason());
            sample.setAuditTime(now);
        } else if ("SHIPPING".equals(action)) {
            ensureTransition(current, SampleStatus.PENDING_SHIP);
            if (!StringUtils.hasText(request.getTrackingNo())) {
                throw BusinessException.param("trackingNo is required when shipping");
            }
            sample.setStatus(SampleStatus.SHIPPING.code);
            sample.setTrackingNo(request.getTrackingNo());
            sample.setShipperCode(request.getShipperCode());
            putExtraValue(sample, "logisticsSource", "MANUAL");
            sample.setShipTime(now);
        } else if ("DELIVERED".equals(action)) {
            ensureTransition(current, SampleStatus.SHIPPING);
            sample.setStatus(SampleStatus.DELIVERED.code);
            sample.setDeliverTime(now);
        } else if ("PENDING_HOMEWORK".equals(action)) {
            if (current == SampleStatus.SHIPPING) {
                sample.setDeliverTime(now);
            } else {
                ensureTransition(current, SampleStatus.DELIVERED);
            }
            putExtraValueIfMissing(sample, "logisticsSource", "MANUAL");
            sample.setStatus(SampleStatus.PENDING_HOMEWORK.code);
        } else if ("COMPLETED".equals(action)) {
            ensureTransition(current, SampleStatus.PENDING_HOMEWORK);
            sample.setStatus(SampleStatus.COMPLETED.code);
            sample.setCompleteTime(now);
        } else if ("CLOSED".equals(action)) {
            ensureTransition(current, SampleStatus.PENDING_HOMEWORK);
            sample.setStatus(SampleStatus.CLOSED.code);
            sample.setCloseTime(now);
            sample.setCloseReason(request.getReason());
        } else {
            throw BusinessException.param("Unsupported action: " + request.getAction());
        }

        persistSample(sample);
        sampleStatusLogService.log(sample.getId(), fromStatus, sample.getStatus(), userId, request.getReason());
        publishActionDomainEvent(action, sample, userId, now, request.getReason());
        Product product = productMapper.selectById(sample.getProductId());
        return ok(toVO(
                sample,
                product,
                product == null ? null : product.getName(),
                sample.getTalentNickname()));
    }

    @Operation(summary = "删除寄样", description = "删除寄样申请。仅待审核或已拒绝的寄样单允许删除。")
    @DeleteMapping("/{id:[0-9a-fA-F\\-]{36}}")
    public ApiResult<Void> deleteSample(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        ensureSampleDeletePermission(roleCodes);
        SampleRequest sample = requireSample(id, userId, deptId, dataScope, roleCodes);
        SampleStatus status = SampleStatus.fromCode(sample.getStatus());
        if (status != SampleStatus.PENDING_AUDIT && status != SampleStatus.REJECTED) {
            throw BusinessException.stateInvalid("Only pending/rejected sample can be deleted");
        }
        sampleRequestMapper.deleteById(id);
        return ok();
    }

    @Operation(summary = "手动刷新物流状态", description = "手动触发物流状态查询，若已签收则自动推进寄样单状态。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/logistics/sync")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<SampleLogisticsVO> syncLogistics(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        SampleRequest sample = requireSample(id, userId, deptId, dataScope, roleCodes);
        ensureLogisticsSyncPermission(roleCodes);
        LogisticsQueryResult result = sampleLogisticsSyncService.syncOne(sample.getId());
        sample = sampleRequestMapper.selectById(id);
        return ok(toLogisticsVO(sample, result));
    }

    @Operation(summary = "手动刷新物流状态（兼容路径）", description = "与 /logistics/sync 等价。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/logistics/refresh")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<SampleVO> refreshLogistics(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        SampleRequest sample = requireSample(id, userId, deptId, dataScope, roleCodes);
        sampleLogisticsSyncService.syncOne(sample.getId());
        sample = sampleRequestMapper.selectById(id);
        Product product = productMapper.selectById(sample.getProductId());
        return ok(toVO(
                sample,
                product,
                product == null ? null : product.getName(),
                sample.getTalentNickname()));
    }

    @Operation(summary = "查看物流轨迹", description = "返回寄样单物流状态与轨迹时间线。")
    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/logistics")
    public ApiResult<SampleLogisticsVO> getSampleLogistics(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        SampleRequest sample = requireSample(id, userId, deptId, dataScope, roleCodes);
        List<SampleLogisticsTrace> traces = sampleLogisticsSyncService.listTraces(id);
        return ok(toLogisticsVO(sample, traces));
    }

    @Operation(summary = "批量同步物流", description = "运营/管理员批量同步快递中寄样单物流状态。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @PostMapping("/logistics/sync-all")
    public ApiResult<Map<String, Integer>> syncAllLogistics(
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        ensureLogisticsSyncPermission(roleCodes);
        SampleLogisticsSyncService.SyncBatchSummary summary = sampleLogisticsSyncService.syncPendingInTransit(100);
        return ok(Map.of(
                "total", summary.total(),
                "success", summary.success(),
                "failed", summary.failed(),
                "skipped", summary.skipped()));
    }

    @Operation(summary = "下载物流导入模板", description = "下载 Excel 批量导入物流单号模板。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @GetMapping("/logistics/import-template")
    public void downloadLogisticsImportTemplate(HttpServletResponse response) throws IOException {
        byte[] bytes = sampleLogisticsImportService.generateTemplate();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"sample-logistics-import-template.xlsx\"");
        response.getOutputStream().write(bytes);
        response.flushBuffer();
    }

    @Operation(summary = "Excel 批量导入物流单号", description = "逐行校验，部分成功部分失败。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @PostMapping(value = "/logistics/import", consumes = "multipart/form-data")
    public ApiResult<LogisticsImportResult> importLogisticsTracking(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean allowOverwrite,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ok(sampleLogisticsImportService.importTrackingNumbers(file, userId, roleCodes, allowOverwrite));
    }

    @Operation(summary = "批量审批通过", description = "批量将 PENDING_AUDIT 的寄样申请审批为待发货。仅招商角色可操作。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_STAFF})
    @PostMapping("/batch-approve")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Map<String, Integer>> batchApprove(
            @Valid @RequestBody SampleBatchActionRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        ensureActionRolePermission("PENDING_SHIP", roleCodes);
        LocalDateTime now = LocalDateTime.now();
        int success = 0;
        int fail = 0;
        for (String requestNo : request.getRequestNos()) {
            try {
                SampleRequest sample = requireSampleByRequestNo(requestNo, userId, deptId, dataScope, roleCodes);
                SampleStatus current = SampleStatus.fromCode(sample.getStatus());
                ensureTransition(current, SampleStatus.PENDING_AUDIT);
                int fromStatus = sample.getStatus();
                sample.setStatus(SampleStatus.PENDING_SHIP.code);
                sample.setAuditTime(now);
                persistSample(sample);
                sampleStatusLogService.log(sample.getId(), fromStatus, sample.getStatus(), userId, request.getRemark());
                sampleDomainEventPublisher.publishSampleApproved(
                        sample, resolveColonelUserId(productMapper.selectById(sample.getProductId())), userId, now);
                success++;
            } catch (BusinessException | ForbiddenException e) {
                log.warn("Batch approve failed for requestNo={}: {}", requestNo, e.getMessage());
                fail++;
            }
        }
        return ok(Map.of("success", success, "fail", fail));
    }

    @Operation(summary = "批量驳回", description = "批量将 PENDING_AUDIT 的寄样申请驳回。仅招商角色可操作，驳回原因必填。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_STAFF})
    @PostMapping("/batch-reject")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Map<String, Integer>> batchReject(
            @Valid @RequestBody SampleBatchActionRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        if (!StringUtils.hasText(request.getRemark())) {
            throw BusinessException.param("remark is required when batch reject");
        }
        ensureActionRolePermission("REJECTED", roleCodes);
        LocalDateTime now = LocalDateTime.now();
        int success = 0;
        int fail = 0;
        for (String requestNo : request.getRequestNos()) {
            try {
                SampleRequest sample = requireSampleByRequestNo(requestNo, userId, deptId, dataScope, roleCodes);
                SampleStatus current = SampleStatus.fromCode(sample.getStatus());
                ensureTransition(current, SampleStatus.PENDING_AUDIT);
                int fromStatus = sample.getStatus();
                sample.setStatus(SampleStatus.REJECTED.code);
                sample.setRejectReason(request.getRemark());
                sample.setAuditTime(now);
                persistSample(sample);
                sampleStatusLogService.log(sample.getId(), fromStatus, sample.getStatus(), userId, request.getRemark());
                sampleDomainEventPublisher.publishSampleRejected(sample, userId, request.getRemark(), now);
                success++;
            } catch (BusinessException | ForbiddenException e) {
                log.warn("Batch reject failed for requestNo={}: {}", requestNo, e.getMessage());
                fail++;
            }
        }
        return ok(Map.of("success", success, "fail", fail));
    }

    @Operation(summary = "批量发货", description = "批量将 PENDING_SHIP 的寄样单标记为发货（SHIPPING），同时录入物流单号。仅运营角色可操作。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @PostMapping("/batch-ship")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Map<String, Integer>> batchShip(
            @Valid @RequestBody SampleBatchShipRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        ensureActionRolePermission("SHIPPING", roleCodes);
        LocalDateTime now = LocalDateTime.now();
        int success = 0;
        int fail = 0;
        for (SampleBatchShipItem item : request.getItems()) {
            try {
                SampleRequest sample = requireSampleByRequestNo(item.getRequestNo(), userId, deptId, dataScope, roleCodes);
                SampleStatus current = SampleStatus.fromCode(sample.getStatus());
                ensureTransition(current, SampleStatus.PENDING_SHIP);
                int fromStatus = sample.getStatus();
                sample.setStatus(SampleStatus.SHIPPING.code);
                sample.setTrackingNo(item.getTrackingNo());
                sample.setShipperCode(item.getShipperCode());
                putExtraValue(sample, "logisticsSource", "MANUAL");
                sample.setShipTime(now);
                persistSample(sample);
                sampleStatusLogService.log(sample.getId(), fromStatus, sample.getStatus(), userId, item.getTrackingNo());
                sampleDomainEventPublisher.publishSampleShipped(sample, userId, now);
                success++;
            } catch (BusinessException | ForbiddenException e) {
                log.warn("Batch ship failed for requestNo={}: {}", item.getRequestNo(), e.getMessage());
                fail++;
            }
        }
        return ok(Map.of("success", success, "fail", fail));
    }

    @Operation(summary = "寄样导出 CSV", description = "导出寄样申请列表为 CSV 文件，支持状态筛选和关键字搜索。")
    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.OPS_STAFF})
    @GetMapping("/exports")
    public void exportSamples(
            @Parameter(description = "寄样状态。") @RequestParam(required = false) String status,
            @Parameter(description = "关键字。") @RequestParam(required = false) String keyword,
            @Parameter(description = "渠道负责人用户 ID。") @RequestParam(required = false) UUID channelUserId,
            @Parameter(description = "招商负责人用户 ID。") @RequestParam(required = false) UUID recruiterUserId,
            @Parameter(description = "商品 ID 或商品名称。") @RequestParam(required = false) String productKeyword,
            @Parameter(description = "店铺 ID 或店铺名称。") @RequestParam(required = false) String shopKeyword,
            @Parameter(description = "物流单号。") @RequestParam(required = false) String trackingNo,
            @Parameter(description = "申请编号 / 合作单号。") @RequestParam(required = false) String requestNo,
            @Parameter(description = "达人昵称或达人号。") @RequestParam(required = false) String talentKeyword,
            @Parameter(description = "合作类型。") @RequestParam(required = false) String cooperationType,
            @Parameter(description = "寄样负责方。") @RequestParam(required = false) String sampleOwnerType,
            @Parameter(description = "交作业类型。") @RequestParam(required = false) String homeworkType,
            @Parameter(description = "收货人姓名。") @RequestParam(required = false) String recipientName,
            @Parameter(description = "收货人手机号。") @RequestParam(required = false) String recipientPhone,
            @Parameter(description = "申请开始时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime applyStartTime,
            @Parameter(description = "申请结束时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime applyEndTime,
            @Parameter(description = "交作业 / 完成开始时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime homeworkStartTime,
            @Parameter(description = "交作业 / 完成结束时间。") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime homeworkEndTime,
            @Parameter(description = "物流公司。") @RequestParam(required = false) String logisticsCompany,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes,
            HttpServletResponse response) throws IOException {
        ensureSampleExportPermission(roleCodes);
        // Validate status early, before committing response headers
        if (StringUtils.hasText(status)) {
            parseStatus(status);
        }

        QueryWrapper<SampleRequest> wrapper = new QueryWrapper<>();
        applySampleQueryFilters(
                wrapper,
                status,
                keyword,
                channelUserId,
                productKeyword,
                shopKeyword,
                trackingNo,
                requestNo,
                talentKeyword,
                cooperationType,
                sampleOwnerType,
                homeworkType,
                recipientName,
                recipientPhone,
                applyStartTime,
                applyEndTime,
                homeworkStartTime,
                homeworkEndTime,
                logisticsCompany);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"samples.csv\"");
        PrintWriter writer = response.getWriter();
        try {
            writer.write('\ufeff');
            writer.println("寄样单号,达人昵称,商品名称,状态,招商负责人,收件人,收件电话,收件地址,物流单号,驳回原因,备注,创建时间");

            Map<UUID, Product> productCache = new HashMap<>();
            long current = 1L;
            while (true) {
                Page<SampleRequest> exportPage = new Page<>(current, EXPORT_BATCH_SIZE);
                IPage<SampleRequest> pageResult = recruiterUserId == null
                        ? sampleRequestMapper.findPageWithScope(exportPage, wrapper)
                        : sampleRequestMapper.findPageWithScope(exportPage, wrapper, recruiterUserId);
                List<SampleRequest> records = pageResult.getRecords();
                if (records == null || records.isEmpty()) {
                    break;
                }
                Set<UUID> productIds = records.stream().map(SampleRequest::getProductId).collect(Collectors.toSet());
                productIds.removeAll(productCache.keySet());
                if (!productIds.isEmpty()) {
                    productCache.putAll(loadProducts(productIds));
                }
                for (SampleRequest sample : records) {
                    Product product = productCache.get(sample.getProductId());
                    SampleStatus s = SampleStatus.fromCode(sample.getStatus());
                    writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                            csvEscape(sample.getRequestNo()),
                            csvEscape(sample.getTalentNickname()),
                            csvEscape(product == null ? null : product.getName()),
                            csvEscape(s.apiStatus),
                            csvEscape(resolveUserDisplayName(sample.getChannelUserId())),
                            csvEscape(sample.getRecipientName()),
                            csvEscape(sample.getRecipientPhone()),
                            csvEscape(sample.getRecipientAddress()),
                            csvEscape(sample.getTrackingNo()),
                            csvEscape(sample.getRejectReason()),
                            csvEscape(sample.getRemark()),
                            sample.getCreateTime());
                }
                if (current >= pageResult.getPages()) {
                    break;
                }
                current++;
            }
        } catch (Exception e) {
            log.error("CSV export failed for user={}", userId, e);
            response.reset();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\":\"Export failed\"}");
            return;
        }
        writer.flush();
    }

    public void exportSamples(
            String status,
            String keyword,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes,
            HttpServletResponse response) throws IOException {
        exportSamples(status, keyword, null, null, userId, deptId, dataScope, roleCodes, response);
    }

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.OPS_STAFF})
    public void exportSamples(
            String status,
            String keyword,
            UUID channelUserId,
            UUID recruiterUserId,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes,
            HttpServletResponse response) throws IOException {
        exportSamples(
                status, keyword, channelUserId, recruiterUserId,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                userId, deptId, dataScope, roleCodes, response);
    }

    private SampleRequest requireSampleByRequestNo(String requestNo, UUID currentUserId, UUID currentDeptId, DataScope dataScope, Object roleCodes) {
        SampleRequest sample = sampleRequestMapper.selectOne(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getRequestNo, requestNo)
                .last("LIMIT 1"));
        if (sample == null) {
            throw BusinessException.notFound("Sample request not found: " + requestNo);
        }
        assertCanAccessSample(sample, currentUserId, currentDeptId, dataScope, roleCodes);
        return sample;
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void ensureTransition(SampleStatus current, SampleStatus expected) {
        if (current != expected) {
            throw BusinessException.stateInvalid("Current status does not allow this action: expected "
                    + expected.apiStatus + " but was " + current.apiStatus);
        }
    }

    private SampleRequest requireSample(UUID id, UUID currentUserId, UUID currentDeptId, DataScope dataScope, Object roleCodes) {
        SampleRequest sample = sampleRequestMapper.selectById(id);
        if (sample == null) {
            throw BusinessException.notFound("Sample request not found");
        }
        assertCanAccessSample(sample, currentUserId, currentDeptId, dataScope, roleCodes);
        ensureRoleCanAccessSample(sample, roleCodes);
        return sample;
    }

    private void assertCanAccessSample(SampleRequest sample, UUID currentUserId, UUID currentDeptId, DataScope dataScope, Object roleCodes) {
        if (sample == null || dataScope == null || dataScope == DataScope.ALL) {
            return;
        }
        if (hasAnyRole(roleCodes, RoleCodes.ADMIN)) {
            return;
        }
        if (hasAnyRole(roleCodes, RoleCodes.BIZ_STAFF) && isSampleProductAssignedToUser(sample, currentUserId)) {
            return;
        }
        if (dataScope == DataScope.PERSONAL) {
            if (currentUserId == null || !currentUserId.equals(sample.getChannelUserId())) {
                throw new ForbiddenException("无权访问该寄样单");
            }
            return;
        }
        UUID ownerDeptId = sample.getDeptId();
        if (ownerDeptId == null) {
            ownerDeptId = resolveUserDeptId(sample.getChannelUserId());
        }
        if (currentDeptId == null || ownerDeptId == null || !currentDeptId.equals(ownerDeptId)) {
            throw new ForbiddenException("无权访问该寄样单");
        }
    }

    private boolean isSampleProductAssignedToUser(SampleRequest sample, UUID userId) {
        if (sample == null || sample.getProductId() == null || userId == null) {
            return false;
        }
        String sourceProductId = resolveSampleSourceProductId(sample.getProductId());
        if (!StringUtils.hasText(sourceProductId)) {
            return false;
        }
        return productOperationStateMapper.selectCount(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getProductId, sourceProductId)
                .eq(ProductOperationState::getAssigneeId, userId)) > 0;
    }

    private String resolveSampleSourceProductId(UUID productPrimaryId) {
        Product product = productMapper.selectById(productPrimaryId);
        if (product != null && StringUtils.hasText(product.getProductId())) {
            return product.getProductId();
        }
        ProductSnapshot snapshot = productSnapshotMapper.selectById(productPrimaryId);
        if (snapshot != null && StringUtils.hasText(snapshot.getProductId())) {
            return snapshot.getProductId();
        }
        return null;
    }

    private UUID resolveUserDeptId(UUID userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user == null ? null : user.getDeptId();
    }

    private void ensureSampleApplyPermission(Object roleCodes) {
        if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF)) {
            throw new ForbiddenException("仅渠道角色可以发起寄样申请");
        }
    }

    private void ensureSampleDeletePermission(Object roleCodes) {
        if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF)) {
            throw new ForbiddenException("仅渠道角色可以删除寄样申请");
        }
    }

    private void ensureRoleCanAccessSample(SampleRequest sample, Object roleCodes) {
        if (sample == null || !isOpsStaffOnly(roleCodes)) {
            return;
        }
        if (!isOpsVisibleStatusCode(sample.getStatus())) {
            throw new ForbiddenException("运营仅可查看待发货及后续物流寄样单");
        }
    }

    private void ensureActionRolePermission(String action, Object roleCodes) {
        switch (action) {
            case "PENDING_SHIP", "REJECTED" -> {
                if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_STAFF)) {
                    throw new ForbiddenException("仅招商角色可以审核寄样");
                }
            }
            case "SHIPPING", "DELIVERED", "PENDING_HOMEWORK" -> {
                if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.OPS_STAFF)) {
                    throw new ForbiddenException("仅运营角色可以推进物流状态");
                }
            }
            case "COMPLETED", "CLOSED" -> throw new ForbiddenException("完成与关闭状态仅允许系统自动推进");
            default -> {
            }
        }
    }

    private List<SampleStatusTransitionVO> buildStatusTransitions() {
        return List.of(
                new SampleStatusTransitionVO(
                        "PENDING_SHIP",
                        "审核通过",
                        List.of("APPROVED"),
                        List.of("PENDING_AUDIT"),
                        "PENDING_SHIP",
                        "PENDING_SHIP",
                        List.of(RoleCodes.ADMIN, RoleCodes.BIZ_STAFF),
                        "USER",
                        true,
                        List.of(),
                        null,
                        "expected PENDING_AUDIT but was {actual}",
                        null,
                        "PUT /samples/{id}/status",
                        "POST /samples/batch-approve",
                        "招商审核通过"),
                new SampleStatusTransitionVO(
                        "REJECTED",
                        "审核拒绝",
                        List.of(),
                        List.of("PENDING_AUDIT"),
                        "REJECTED",
                        "REJECTED",
                        List.of(RoleCodes.ADMIN, RoleCodes.BIZ_STAFF),
                        "USER",
                        true,
                        List.of("reason"),
                        "reason is required when reject sample request",
                        "expected PENDING_AUDIT but was {actual}",
                        null,
                        "PUT /samples/{id}/status",
                        "POST /samples/batch-reject",
                        "招商审核拒绝"),
                new SampleStatusTransitionVO(
                        "SHIPPING",
                        "录入物流",
                        List.of("SHIPPED"),
                        List.of("PENDING_SHIP"),
                        "SHIPPED",
                        "SHIPPING",
                        List.of(RoleCodes.ADMIN, RoleCodes.OPS_STAFF),
                        "USER",
                        true,
                        List.of("trackingNo"),
                        "trackingNo is required when shipping",
                        "expected PENDING_SHIP but was {actual}",
                        null,
                        "PUT /samples/{id}/status",
                        "POST /samples/batch-ship",
                        "运营录入物流单号"),
                new SampleStatusTransitionVO(
                        "DELIVERED",
                        "物流签收",
                        List.of(),
                        List.of("SHIPPED"),
                        "SHIPPED",
                        "DELIVERED",
                        List.of(RoleCodes.ADMIN, RoleCodes.OPS_STAFF),
                        "USER",
                        true,
                        List.of(),
                        null,
                        "expected SHIPPING but was {actual}",
                        null,
                        "PUT /samples/{id}/status",
                        null,
                        "物流签收回调或运营确认签收"),
                new SampleStatusTransitionVO(
                        "PENDING_HOMEWORK",
                        "待交作业",
                        List.of("SIGNED", "PENDING_TASK"),
                        List.of("SHIPPED", "DELIVERED"),
                        "PENDING_TASK",
                        "PENDING_HOMEWORK",
                        List.of(RoleCodes.ADMIN, RoleCodes.OPS_STAFF),
                        "USER",
                        true,
                        List.of(),
                        null,
                        "expected DELIVERED but was {actual}",
                        null,
                        "PUT /samples/{id}/status",
                        null,
                        "签收后进入待交作业"),
                new SampleStatusTransitionVO(
                        "COMPLETED",
                        "作业完成",
                        List.of("FINISHED"),
                        List.of("PENDING_TASK"),
                        "FINISHED",
                        "COMPLETED",
                        List.of(),
                        "SYSTEM",
                        false,
                        List.of(),
                        null,
                        "expected PENDING_HOMEWORK but was {actual}",
                        "完成与关闭状态仅允许系统自动推进",
                        null,
                        null,
                        "订单同步自动完成"),
                new SampleStatusTransitionVO(
                        "CLOSED",
                        "超时关闭",
                        List.of(),
                        List.of("PENDING_TASK"),
                        "CLOSED",
                        "CLOSED",
                        List.of(),
                        "SYSTEM",
                        false,
                        List.of("reason"),
                        null,
                        "expected PENDING_HOMEWORK but was {actual}",
                        "完成与关闭状态仅允许系统自动推进",
                        null,
                        null,
                        "待交作业超时自动关闭"));
    }

    private void ensureLogisticsSyncPermission(Object roleCodes) {
        if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.OPS_STAFF)) {
            throw new ForbiddenException("仅运营或管理员可触发物流同步");
        }
    }

    private SampleLogisticsVO toLogisticsVO(SampleRequest sample, LogisticsQueryResult queryResult) {
        SampleLogisticsVO vo = toLogisticsVO(sample, sampleLogisticsSyncService.listTraces(sample.getId()));
        if (queryResult != null) {
            vo.setQuerySuccess(queryResult.isSuccess());
            vo.setQueryErrorCode(queryResult.getErrorCode());
            vo.setQueryErrorMessage(queryResult.getErrorMessage());
            vo.setProvider(queryResult.getProvider());
        }
        return vo;
    }

    private SampleLogisticsVO toLogisticsVO(SampleRequest sample, List<SampleLogisticsTrace> traces) {
        SampleLogisticsVO vo = new SampleLogisticsVO();
        vo.setSampleRequestId(sample.getId());
        vo.setRequestNo(sample.getRequestNo());
        vo.setTrackingNo(sample.getTrackingNo());
        vo.setLogisticsCompany(sample.getShipperCode());
        vo.setLogisticsStatus(sample.getLogisticsStatus());
        vo.setLogisticsStatusName(sample.getLogisticsStatusName());
        vo.setLogisticsLastQueryAt(sample.getLogisticsLastQueryAt());
        vo.setLogisticsLastError(sample.getLogisticsLastError());
        vo.setSignedAt(sample.getSignedAt());
        vo.setStatus(toLegacyStatus(SampleStatus.fromCode(sample.getStatus())));
        if (traces != null) {
            vo.setTraces(traces.stream().map(trace -> {
                LogisticsTraceVO item = new LogisticsTraceVO();
                item.setTraceTime(trace.getTraceTime());
                item.setTraceContent(trace.getTraceContent());
                item.setStatusCode(trace.getStatusCode());
                item.setStatusName(trace.getStatusName());
                return item;
            }).toList());
        }
        return vo;
    }

    private void ensureSampleExportPermission(Object roleCodes) {
        if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.OPS_STAFF)) {
            throw new ForbiddenException("仅管理员、招商或运营可导出寄样数据");
        }
    }

    private Product requireProduct(UUID productId) {
        Product product = productMapper.selectById(productId);
        if (product != null) {
            return product;
        }

        ProductSnapshot snapshot = productSnapshotMapper.selectById(productId);
        if (snapshot != null && StringUtils.hasText(snapshot.getProductId())) {
            product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                    .eq(Product::getProductId, snapshot.getProductId())
                    .last("LIMIT 1"));
            if (product == null) {
                product = materializeProductFromSnapshot(snapshot);
                productMapper.insert(product);
            }
        }
        if (product == null) {
            throw new ValidateException("Selected product does not exist");
        }
        return product;
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

    private CrawlerTalentInfo requireCrawlerTalent(String talentId) {
        CrawlerTalentInfo talentInfo = crawlerTalentInfoService.findByTalentId(talentId);
        if (talentInfo == null) {
            throw new ValidateException("Selected talent does not exist");
        }
        return talentInfo;
    }

    private CrawlerTalentInfo resolveSampleTalentInfo(String talentId) {
        CrawlerTalentInfo talentInfo = crawlerTalentInfoService.findByTalentId(talentId);
        if (talentInfo != null) {
            return talentInfo;
        }
        Talent manualTalent = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, talentId)
                .last("limit 1"));
        if (manualTalent == null) {
            throw new ValidateException("Selected talent does not exist");
        }
        return buildCrawlerSnapshotFromTalent(manualTalent, talentId);
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

    private Talent findOrCreateTalentFromCrawler(CrawlerTalentInfo info) {
        Talent existing = talentMapper.selectOne(new LambdaQueryWrapper<Talent>()
                .eq(Talent::getDouyinUid, info.getTalentId())
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        Talent talent = new Talent();
        talent.setDouyinUid(info.getTalentId());
        talent.setNickname(info.getNickname());
        talent.setFans(info.getFansCount());
        talent.setStatus(1);
        talentMapper.insert(talent);
        return talent;
    }

    private void ensureChannelTalentClaim(UUID userId, UUID talentId, Object roleCodes) {
        if (!hasAnyRole(roleCodes, RoleCodes.CHANNEL_STAFF, RoleCodes.CHANNEL_LEADER)
                || hasAnyRole(roleCodes, RoleCodes.ADMIN)) {
            return;
        }
        if (userId == null || talentId == null) {
            throw new ValidateException("该达人信息不完整，请重新选择");
        }
        if (talentClaimMapper.findActiveByTalentAndUser(talentId, userId) == null) {
            throw new ForbiddenException("该达人未在你的私海中，请先认领后再申请寄样");
        }
    }

    private void checkSevenDaysLimit(UUID userId, UUID talentId, UUID productId, Object roleCodes) {
        if (isExemptFromSevenDaysLimit(roleCodes)) {
            return;
        }
        if (!businessRuleConfigService.isSampleRestrictEnabled()) {
            return;
        }
        int restrictDays = businessRuleConfigService.getSampleRestrictDays();
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(restrictDays);
        Long count = sampleRequestMapper.selectCount(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getChannelUserId, userId)
                .eq(SampleRequest::getTalentId, talentId)
                .eq(SampleRequest::getProductId, productId)
                .ne(SampleRequest::getStatus, SampleStatus.REJECTED.code)
                .ge(SampleRequest::getCreateTime, sevenDaysAgo));
        if (count != null && count > 0) {
            throw BusinessException.duplicate("Duplicate sample request is blocked within " + restrictDays + " days");
        }
    }

    private boolean isExemptFromSevenDaysLimit(Object roleCodes) {
        if (roleCodes == null) {
            return false;
        }
        if (roleCodes instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> item == null ? "" : item.toString().trim().toLowerCase(Locale.ROOT))
                    .anyMatch(this::isExemptRoleCode);
        }
        String raw = roleCodes.toString();
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        for (String role : normalized.split(",")) {
            if (isExemptRoleCode(role.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isExemptRoleCode(String roleCode) {
        return RoleCodes.ADMIN.equals(roleCode)
                || RoleCodes.CHANNEL_LEADER.equals(roleCode);
    }

    private boolean hasAnyRole(Object roleCodes, String... expectedRoles) {
        if (roleCodes == null || expectedRoles == null || expectedRoles.length == 0) {
            return false;
        }
        Set<String> expected = java.util.Arrays.stream(expectedRoles)
                .map(role -> role == null ? "" : role.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (roleCodes instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> item == null ? "" : item.toString().trim().toLowerCase(Locale.ROOT))
                    .anyMatch(expected::contains);
        }
        String raw = roleCodes.toString();
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        for (String role : normalized.split(",")) {
            if (expected.contains(role.trim())) {
                return true;
            }
        }
        return false;
    }

    private SampleEligibilityService.EligibilityResult ensureEligibilityReasonIfNeeded(
            SampleApplyRequest request,
            Talent talent,
            CrawlerTalentInfo talentInfo) {
        SampleEligibilityService.EligibilityResult result = sampleEligibilityService.evaluate(talent, talentInfo);
        if (result.eligible()) {
            return result;
        }
        if (!StringUtils.hasText(request.getRemark())) {
            throw BusinessException.stateInvalid("达人未满足默认寄样标准，请先填写申请原因后再提交");
        }
        return result;
    }

    private Map<String, Object> buildSampleExtraData(
            SampleApplyRequest request,
            SampleEligibilityService.EligibilityResult eligibility) {
        Map<String, Object> extra = new LinkedHashMap<>();
        Map<String, Object> eligibilityCheck = new LinkedHashMap<>();
        eligibilityCheck.put("passed", eligibility.eligible());
        eligibilityCheck.put("failedRules", classifyEligibilityFailures(eligibility.reasons()));
        eligibilityCheck.put("reasons", eligibility.reasons());
        String applySource = normalizeApplySource(request.getApplySource());
        extra.put("eligibilityCheck", eligibilityCheck);
        extra.put("applyReason", StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
        extra.put("requirementSnapshot", buildRequirementSnapshot(eligibility));
        extra.put("addressSource", "manual");
        extra.put("applySource", applySource);
        extra.put("externalApply", false);
        extra.put("applyChannel", "INTERNAL_SAMPLE_REQUEST");
        return extra;
    }

    private String normalizeApplySource(String applySource) {
        if (!StringUtils.hasText(applySource)) {
            return APPLY_SOURCE_MANUAL;
        }
        String normalized = applySource.trim().toUpperCase(Locale.ROOT);
        return APPLY_SOURCE_INTERNAL_QUICK_SAMPLE.equals(normalized)
                ? APPLY_SOURCE_INTERNAL_QUICK_SAMPLE
                : APPLY_SOURCE_MANUAL;
    }

    private Map<String, Object> buildRequirementSnapshot(SampleEligibilityService.EligibilityResult eligibility) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("min30DaySales", eligibility.standard().min30DaySales());
        snapshot.put("minLevel", eligibility.standard().minLevel());
        snapshot.put("actual30DaySales", eligibility.actual().monthlySales());
        snapshot.put("actualLevel", eligibility.actual().level());
        if (eligibility.standard().raw() != null && !eligibility.standard().raw().isEmpty()) {
            snapshot.put("rawStandard", eligibility.standard().raw());
        }
        return snapshot;
    }

    private List<String> classifyEligibilityFailures(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return List.of();
        }
        List<String> failedRules = new ArrayList<>();
        for (String reason : reasons) {
            if (!StringUtils.hasText(reason)) {
                continue;
            }
            if (reason.contains("销售额")) {
                failedRules.add("min30DaySales");
                continue;
            }
            if (reason.contains("等级")) {
                failedRules.add("minLevel");
                continue;
            }
            failedRules.add("custom");
        }
        return failedRules;
    }

    private SampleEligibilityCheckVO toEligibilityVO(SampleEligibilityService.EligibilityResult result) {
        SampleEligibilityCheckVO vo = new SampleEligibilityCheckVO();
        vo.setEligible(result.eligible());
        vo.setNeedReason(!result.eligible());
        vo.setReasons(result.reasons());
        vo.setMin30DaySales(result.standard().min30DaySales());
        vo.setMinLevel(result.standard().minLevel());
        vo.setCurrent30DaySales(result.actual().monthlySales());
        vo.setCurrentLevel(result.actual().level());
        return vo;
    }

    private String generateRequestNo() {
        String date = LocalDateTime.now().format(REQUEST_NO_DATE);
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "SM" + date + unique;
    }

    private Map<UUID, Product> loadProducts(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Product> products = productMapper.selectBatchIds(ids);
        Map<UUID, Product> map = new HashMap<>();
        for (Product product : products) {
            map.put(product.getId(), product);
        }
        return map;
    }

    private Set<UUID> loadMatchedProductIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Set.of();
        }
        QueryWrapper<Product> wrapper = new QueryWrapper<Product>()
                .select("id")
                .and(query -> query.like("name", keyword).or().like("product_id", keyword))
                .last("LIMIT " + PRODUCT_KEYWORD_BATCH_SIZE);
        return productMapper.selectList(wrapper).stream()
                .map(Product::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void applySampleQueryFilters(
            QueryWrapper<SampleRequest> wrapper,
            String status,
            String keyword,
            UUID channelUserId,
            String productKeyword,
            String shopKeyword,
            String trackingNo,
            String requestNo,
            String talentKeyword,
            String cooperationType,
            String sampleOwnerType,
            String homeworkType,
            String recipientName,
            String recipientPhone,
            LocalDateTime applyStartTime,
            LocalDateTime applyEndTime,
            LocalDateTime homeworkStartTime,
            LocalDateTime homeworkEndTime,
            String logisticsCompany) {
        if (StringUtils.hasText(status)) {
            wrapper.eq("sr.status", parseStatus(status).code);
        }
        if (StringUtils.hasText(keyword)) {
            Set<UUID> matchedProductIds = loadMatchedProductIds(keyword.trim());
            wrapper.and(query -> {
                query.like("sr.talent_nickname", keyword.trim())
                        .or()
                        .like("sr.talent_uid", keyword.trim())
                        .or()
                        .like("sr.request_no", keyword.trim());
                if (!matchedProductIds.isEmpty()) {
                    query.or().in("sr.product_id", matchedProductIds);
                }
            });
        }
        if (channelUserId != null) {
            wrapper.eq("sr.channel_user_id", channelUserId);
        }
        if (StringUtils.hasText(productKeyword)) {
            applyProductIdsFilter(wrapper, loadMatchedProductIds(productKeyword.trim()));
        }
        if (StringUtils.hasText(shopKeyword)) {
            applyProductIdsFilter(wrapper, loadMatchedProductIdsByShop(shopKeyword.trim()));
        }
        if (StringUtils.hasText(trackingNo)) {
            wrapper.like("sr.tracking_no", trackingNo.trim());
        }
        if (StringUtils.hasText(requestNo)) {
            wrapper.like("sr.request_no", requestNo.trim());
        }
        if (StringUtils.hasText(talentKeyword)) {
            String trimmed = talentKeyword.trim();
            wrapper.and(query -> query.like("sr.talent_nickname", trimmed).or().like("sr.talent_uid", trimmed));
        }
        if (StringUtils.hasText(cooperationType)) {
            wrapper.apply("sr.extra_data ->> 'cooperationType' = {0}", cooperationType.trim());
        }
        if (StringUtils.hasText(sampleOwnerType)) {
            wrapper.apply("sr.extra_data ->> 'sampleOwnerType' = {0}", sampleOwnerType.trim());
        }
        if (StringUtils.hasText(homeworkType)) {
            String normalized = homeworkType.trim().toUpperCase(Locale.ROOT);
            if ("HAS_ORDER".equals(normalized)) {
                wrapper.eq("sr.status", SampleStatus.COMPLETED.code);
            } else if ("NO_ORDER".equals(normalized)) {
                wrapper.eq("sr.status", SampleStatus.PENDING_HOMEWORK.code);
            } else {
                wrapper.apply("sr.extra_data ->> 'homeworkType' = {0}", homeworkType.trim());
            }
        }
        if (StringUtils.hasText(recipientName)) {
            wrapper.like("sr.recipient_name", recipientName.trim());
        }
        if (StringUtils.hasText(recipientPhone)) {
            wrapper.like("sr.recipient_phone", recipientPhone.trim());
        }
        if (applyStartTime != null) {
            wrapper.ge("sr.create_time", applyStartTime);
        }
        if (applyEndTime != null) {
            wrapper.le("sr.create_time", applyEndTime);
        }
        if (homeworkStartTime != null) {
            wrapper.and(query -> query.ge("sr.complete_time", homeworkStartTime).or().ge("sr.signed_at", homeworkStartTime));
        }
        if (homeworkEndTime != null) {
            wrapper.and(query -> query.le("sr.complete_time", homeworkEndTime).or().le("sr.signed_at", homeworkEndTime));
        }
        if (StringUtils.hasText(logisticsCompany)) {
            wrapper.eq("sr.shipper_code", logisticsCompany.trim());
        }
    }

    private void applyProductIdsFilter(QueryWrapper<SampleRequest> wrapper, Set<UUID> productIds) {
        if (productIds.isEmpty()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.in("sr.product_id", productIds);
    }

    private Set<UUID> loadMatchedProductIdsByShop(String keyword) {
        QueryWrapper<ProductSnapshot> wrapper = new QueryWrapper<ProductSnapshot>()
                .select("id")
                .and(query -> {
                    query.like("shop_name", keyword);
                    Long shopId = parseLongOrNull(keyword);
                    if (shopId != null) {
                        query.or().eq("shop_id", shopId);
                    }
                })
                .last("LIMIT " + PRODUCT_KEYWORD_BATCH_SIZE);
        return productSnapshotMapper.selectList(wrapper).stream()
                .map(ProductSnapshot::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Long parseLongOrNull(String value) {
        try {
            return StringUtils.hasText(value) ? Long.parseLong(value.trim()) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private SampleVO toVO(SampleRequest sample, Product product, String productName, String talentName) {
        Product resolvedProduct = product;
        if (resolvedProduct == null && sample.getProductId() != null) {
            resolvedProduct = productMapper.selectById(sample.getProductId());
        }
        ProductSnapshot snapshot = resolvedProduct == null || resolvedProduct.getId() == null
                ? null
                : productSnapshotMapper.selectById(resolvedProduct.getId());
        UUID colonelUserId = resolveColonelUserId(resolvedProduct);
        SampleVO vo = new SampleVO();
        vo.setId(sample.getId());
        vo.setRequestNo(sample.getRequestNo());
        vo.setTalentId(sample.getTalentId());
        vo.setTalentUid(sample.getTalentUid());
        vo.setTalentFansCount(sample.getTalentFansCount());
        vo.setTalentCreditScore(sample.getTalentCreditScore() == null ? null : sample.getTalentCreditScore().toPlainString());
        vo.setTalentMainCategory(sample.getTalentMainCategory());
        vo.setTalentName(StringUtils.hasText(talentName) ? talentName : sample.getTalentNickname());
        vo.setProductId(sample.getProductId());
        vo.setProductExternalId(resolveProductExternalId(resolvedProduct, snapshot));
        vo.setProductName(productName);
        vo.setProductCover(resolveProductCover(resolvedProduct, snapshot));
        vo.setProductPriceText(resolveProductPriceText(resolvedProduct, snapshot));
        vo.setShopId(snapshot == null || snapshot.getShopId() == null ? null : String.valueOf(snapshot.getShopId()));
        vo.setShopName(snapshot == null ? null : snapshot.getShopName());
        vo.setQuantity(sample.getExpectedSampleNum() == null ? 1 : sample.getExpectedSampleNum());
        vo.setApplicantUserId(sample.getUserId());
        vo.setApplicantName(resolveUserDisplayName(sample.getUserId()));
        vo.setChannelUserId(sample.getChannelUserId());
        vo.setChannelUserName(resolveUserDisplayName(sample.getChannelUserId()));
        vo.setColonelUserId(colonelUserId);
        vo.setColonelUserName(resolveUserDisplayName(colonelUserId));
        vo.setTrackingNo(sample.getTrackingNo());
        vo.setShipperCode(sample.getShipperCode());
        vo.setLogisticsCompany(sample.getShipperCode());
        vo.setRecipientName(sample.getRecipientName());
        vo.setRecipientPhone(sample.getRecipientPhone());
        vo.setRecipientAddress(sample.getRecipientAddress());
        vo.setLogisticsSource(readExtraText(sample.getExtraData(), "logisticsSource"));
        vo.setLogisticsStatus(sample.getLogisticsStatus());
        vo.setLogisticsStatusName(sample.getLogisticsStatusName());
        vo.setLogisticsLastQueryAt(sample.getLogisticsLastQueryAt());
        vo.setLogisticsLastError(sample.getLogisticsLastError());
        vo.setSignedAt(sample.getSignedAt());
        vo.setRejectReason(sample.getRejectReason());
        vo.setCloseReason(sample.getCloseReason());
        vo.setRemark(sample.getRemark());
        vo.setApplyReason(readExtraText(sample.getExtraData(), "applyReason"));
        vo.setApplySource(readExtraText(sample.getExtraData(), "applySource"));
        vo.setApplySourceLabel(resolveApplySourceLabel(vo.getApplySource()));
        vo.setCooperationType(readExtraText(sample.getExtraData(), "cooperationType"));
        vo.setCooperationTypeLabel(resolveOptionLabel(vo.getCooperationType(), "免费寄样"));
        vo.setSampleOwnerType(readExtraText(sample.getExtraData(), "sampleOwnerType"));
        vo.setSampleOwnerTypeLabel(resolveOptionLabel(vo.getSampleOwnerType(), "商家"));
        vo.setHomeworkType(readExtraText(sample.getExtraData(), "homeworkType"));
        vo.setHomeworkTypeLabel(resolveHomeworkTypeLabel(vo.getHomeworkType(), sample));
        vo.setEligibilityCheck(readExtraMap(sample.getExtraData(), "eligibilityCheck"));
        vo.setRequirementSnapshot(readExtraMap(sample.getExtraData(), "requirementSnapshot"));
        vo.setCreateTime(sample.getCreateTime());
        vo.setUpdateTime(sample.getUpdateTime());
        vo.setShipTime(sample.getShipTime());
        vo.setDeliverTime(sample.getDeliverTime());
        vo.setCompleteTime(sample.getCompleteTime());
        vo.setStatus(toLegacyStatus(SampleStatus.fromCode(sample.getStatus())));
        return vo;
    }

    private String resolveProductExternalId(Product product, ProductSnapshot snapshot) {
        if (product != null && StringUtils.hasText(product.getProductId())) {
            return product.getProductId();
        }
        return snapshot == null ? null : snapshot.getProductId();
    }

    private String resolveProductCover(Product product, ProductSnapshot snapshot) {
        if (product != null && StringUtils.hasText(product.getCover())) {
            return product.getCover();
        }
        return snapshot == null ? null : snapshot.getCover();
    }

    private String resolveProductPriceText(Product product, ProductSnapshot snapshot) {
        if (snapshot != null && StringUtils.hasText(snapshot.getPriceText())) {
            return snapshot.getPriceText();
        }
        Long price = product == null ? null : product.getPrice();
        return price == null ? null : "¥" + (price / 100.0);
    }

    private String resolveApplySourceLabel(String applySource) {
        if (APPLY_SOURCE_INTERNAL_QUICK_SAMPLE.equals(applySource)) {
            return "内部寄样";
        }
        return "手动申请";
    }

    private String resolveOptionLabel(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return switch (value) {
            case "FREE_SAMPLE" -> "免费寄样";
            case "PAID_SAMPLE" -> "付费寄样";
            case "EXCHANGE_SAMPLE" -> "置换寄样";
            case "MERCHANT" -> "商家";
            case "COLONEL" -> "团长";
            case "OTHER" -> "其他";
            default -> value;
        };
    }

    private String resolveHomeworkTypeLabel(String homeworkType, SampleRequest sample) {
        if (StringUtils.hasText(homeworkType)) {
            return switch (homeworkType) {
                case "HAS_ORDER" -> "有订单";
                case "NO_ORDER" -> "无订单";
                case "PARTIAL" -> "部分完成";
                default -> homeworkType;
            };
        }
        SampleStatus status = SampleStatus.fromCode(sample.getStatus());
        if (status == SampleStatus.COMPLETED) {
            return "有订单";
        }
        if (status == SampleStatus.PENDING_HOMEWORK) {
            return "待交作业";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readExtraMap(Map<String, Object> extraData, String key) {
        if (extraData == null || extraData.isEmpty()) {
            return Map.of();
        }
        Object value = extraData.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String readExtraText(Map<String, Object> extraData, String key) {
        if (extraData == null || extraData.isEmpty()) {
            return null;
        }
        Object value = extraData.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private void putExtraValue(SampleRequest sample, String key, Object value) {
        Map<String, Object> extra = sample.getExtraData() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(sample.getExtraData());
        extra.put(key, value);
        sample.setExtraData(extra);
    }

    private void putExtraValueIfMissing(SampleRequest sample, String key, Object value) {
        Map<String, Object> extra = sample.getExtraData() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(sample.getExtraData());
        extra.putIfAbsent(key, value);
        sample.setExtraData(extra);
    }

    private void publishActionDomainEvent(
            String action,
            SampleRequest sample,
            UUID userId,
            LocalDateTime now,
            String reason) {
        Product product = productMapper.selectById(sample.getProductId());
        UUID recruiterId = resolveColonelUserId(product);
        switch (action) {
            case "PENDING_SHIP" -> sampleDomainEventPublisher.publishSampleApproved(sample, recruiterId, userId, now);
            case "REJECTED" -> sampleDomainEventPublisher.publishSampleRejected(sample, userId, reason, now);
            case "SHIPPING" -> sampleDomainEventPublisher.publishSampleShipped(sample, userId, now);
            case "PENDING_HOMEWORK" -> sampleDomainEventPublisher.publishSampleSigned(
                    sample, sample.getSignedAt() != null ? sample.getSignedAt() : now);
            case "COMPLETED" -> sampleDomainEventPublisher.publishSampleCompleted(sample, null, now);
            case "CLOSED" -> sampleDomainEventPublisher.publishSampleClosed(sample, reason, now);
            default -> {
            }
        }
    }

    private UUID resolveColonelUserId(Product product) {
        if (product == null || !StringUtils.hasText(product.getProductId()) || product.getActivityId() == null) {
            return null;
        }
        ProductOperationState state = productOperationStateMapper.selectOne(new LambdaQueryWrapper<ProductOperationState>()
                .eq(ProductOperationState::getActivityId, String.valueOf(product.getActivityId()))
                .eq(ProductOperationState::getProductId, product.getProductId())
                .last("limit 1"));
        return state == null ? null : state.getAssigneeId();
    }

    private String resolveUserDisplayName(UUID userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        String realName = normalizeDisplayText(user.getRealName());
        String username = normalizeDisplayText(user.getUsername());
        if (StringUtils.hasText(realName) && StringUtils.hasText(username)) {
            return realName + " (" + username + ")";
        }
        if (StringUtils.hasText(realName)) {
            return realName;
        }
        if (StringUtils.hasText(username)) {
            return username;
        }
        return null;
    }

    private String normalizeDisplayText(String value) {
        return value == null ? null : value.trim();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private SampleStatus parseStatus(String status) {
        try {
            return SampleStatus.fromApiStatus(status.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw BusinessException.param("Invalid status: " + status);
        }
    }

    private String normalizeAction(String action) {
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVED" -> "PENDING_SHIP";
            case "SHIPPED" -> "SHIPPING";
            case "SIGNED" -> "PENDING_HOMEWORK";
            case "PENDING_TASK" -> "PENDING_HOMEWORK";
            case "FINISHED" -> "COMPLETED";
            default -> normalized;
        };
    }

    private void ensureOpsVisibleStatus(String status) {
        SampleStatus sampleStatus = parseStatus(status);
        if (!isOpsVisibleStatusCode(sampleStatus.code)) {
            throw new ForbiddenException("运营仅可查看待发货及后续物流寄样单");
        }
    }

    private boolean isOpsVisibleStatusCode(Integer statusCode) {
        if (statusCode == null) {
            return false;
        }
        return statusCode.equals(SampleStatus.PENDING_SHIP.code)
                || statusCode.equals(SampleStatus.SHIPPING.code)
                || statusCode.equals(SampleStatus.DELIVERED.code)
                || statusCode.equals(SampleStatus.PENDING_HOMEWORK.code)
                || statusCode.equals(SampleStatus.COMPLETED.code)
                || statusCode.equals(SampleStatus.CLOSED.code);
    }

    private boolean isOpsStaffOnly(Object roleCodes) {
        return hasAnyRole(roleCodes, RoleCodes.OPS_STAFF) && !hasAnyRole(roleCodes, RoleCodes.ADMIN);
    }

    private String toLegacyStatus(SampleStatus status) {
        return switch (status) {
            case SHIPPING, DELIVERED -> "SHIPPED";
            case PENDING_HOMEWORK -> "PENDING_TASK";
            case COMPLETED -> "FINISHED";
            default -> status.apiStatus;
        };
    }

    private LocalDateTime resolveStateEnterTime(SampleRequest sample, SampleStatus status) {
        return switch (status) {
            case PENDING_AUDIT -> sample.getCreateTime();
            case PENDING_SHIP -> sample.getAuditTime();
            case SHIPPING -> sample.getShipTime();
            case DELIVERED -> sample.getDeliverTime();
            case PENDING_HOMEWORK -> sample.getDeliverTime();
            case COMPLETED -> sample.getCompleteTime();
            case REJECTED -> sample.getAuditTime();
            case CLOSED -> sample.getCloseTime();
        };
    }

    private SampleBoardCard toBoardCard(SampleRequest sample, Product product, SampleStatus internalStatus) {
        SampleBoardCard card = new SampleBoardCard();
        card.setId(sample.getId());
        card.setRequestNo(sample.getRequestNo());
        card.setTalentName(sample.getTalentNickname());
        card.setProductId(sample.getProductId());
        card.setProductName(product == null ? null : product.getName());
        card.setQuantity(sample.getExpectedSampleNum() == null ? 1 : sample.getExpectedSampleNum());
        card.setChannelUserName(resolveUserDisplayName(sample.getChannelUserId()));
        card.setTrackingNo(sample.getTrackingNo());
        card.setRejectReason(sample.getRejectReason());
        card.setRemark(sample.getRemark());
        card.setStatus(toLegacyStatus(internalStatus));
        card.setCreateTime(sample.getCreateTime());
        card.setStateEnterTime(resolveStateEnterTime(sample, internalStatus));
        return card;
    }

    private enum SampleStatus {
        PENDING_AUDIT(1, "PENDING_AUDIT"),
        PENDING_SHIP(2, "PENDING_SHIP"),
        SHIPPING(3, "SHIPPING"),
        DELIVERED(4, "DELIVERED"),
        PENDING_HOMEWORK(5, "PENDING_HOMEWORK"),
        COMPLETED(6, "COMPLETED"),
        REJECTED(7, "REJECTED"),
        CLOSED(8, "CLOSED");

        private final Integer code;
        private final String apiStatus;

        SampleStatus(Integer code, String apiStatus) {
            this.code = code;
            this.apiStatus = apiStatus;
        }

        static SampleStatus fromCode(Integer code) {
            for (SampleStatus status : values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
            throw BusinessException.param("Unknown sample status: " + code);
        }

        static SampleStatus fromApiStatus(String status) {
            return switch (status) {
                case "PENDING_TASK" -> PENDING_HOMEWORK;
                case "SHIPPED" -> SHIPPING;
                case "FINISHED" -> COMPLETED;
                default -> SampleStatus.valueOf(status);
            };
        }
    }

    public static class SampleActionRequest {
        @Schema(description = "状态流转动作。可用值包括 PENDING_SHIP、REJECTED、SHIPPING、DELIVERED、PENDING_HOMEWORK、COMPLETED、CLOSED；兼容值包括 APPROVED、SHIPPED、PENDING_TASK、FINISHED。", example = "SHIPPING")
        @NotBlank(message = "action cannot be empty")
        private String action;

        @Schema(description = "原因说明。驳回、关闭等场景建议填写。", example = "顺丰发出")
        private String reason;

        @Schema(description = "物流单号。发货时必填。", example = "SF1234567890")
        private String trackingNo;

        @Schema(description = "快递公司编码。发货时选填，用于物流追踪。", example = "SF")
        private String shipperCode;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public String getTrackingNo() {
            return trackingNo;
        }

        public void setTrackingNo(String trackingNo) {
            this.trackingNo = trackingNo;
        }

        public String getShipperCode() {
            return shipperCode;
        }

        public void setShipperCode(String shipperCode) {
            this.shipperCode = shipperCode;
        }
    }

    public static class SampleBatchActionRequest {
        @Schema(description = "寄样单号列表。", example = "[\"SR20250101001\",\"SR20250101002\"]")
        @NotEmpty(message = "requestNos cannot be empty")
        @Size(max = 100, message = "requestNos size cannot exceed 100")
        private List<String> requestNos;

        @Schema(description = "备注/原因。批量驳回时必填。", example = "商品缺货")
        private String remark;

        public List<String> getRequestNos() {
            return requestNos;
        }

        public void setRequestNos(List<String> requestNos) {
            this.requestNos = requestNos;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    public static class SampleBatchShipRequest {
        @Schema(description = "批量发货列表。", example = "[{\"requestNo\":\"SR20250101001\",\"trackingNo\":\"SF1234567890\"}]")
        @NotEmpty(message = "items cannot be empty")
        @Size(max = 100, message = "items size cannot exceed 100")
        private List<SampleBatchShipItem> items;

        public List<SampleBatchShipItem> getItems() {
            return items;
        }

        public void setItems(List<SampleBatchShipItem> items) {
            this.items = items;
        }
    }

    public static class SampleBatchShipItem {
        @Schema(description = "寄样单号。", example = "SR20250101001")
        @NotBlank(message = "requestNo is required")
        private String requestNo;

        @Schema(description = "物流单号。", example = "SF1234567890")
        @NotBlank(message = "trackingNo is required")
        private String trackingNo;

        @Schema(description = "快递公司编码。", example = "SF")
        private String shipperCode;

        public String getRequestNo() {
            return requestNo;
        }

        public void setRequestNo(String requestNo) {
            this.requestNo = requestNo;
        }

        public String getTrackingNo() {
            return trackingNo;
        }

        public void setTrackingNo(String trackingNo) {
            this.trackingNo = trackingNo;
        }

        public String getShipperCode() {
            return shipperCode;
        }

        public void setShipperCode(String shipperCode) {
            this.shipperCode = shipperCode;
        }
    }

    @Getter
    public static class SampleStatusTransitionVO {
        private final String action;
        private final String label;
        private final List<String> aliases;
        private final List<String> fromStatuses;
        private final String toStatus;
        private final String internalToStatus;
        private final List<String> roleCodes;
        private final String actorType;
        private final boolean userCallable;
        private final List<String> requiredFields;
        private final String missingFieldMessage;
        private final String invalidStateMessage;
        private final String forbiddenMessage;
        private final String endpoint;
        private final String batchEndpoint;
        private final String trigger;

        public SampleStatusTransitionVO(
                String action,
                String label,
                List<String> aliases,
                List<String> fromStatuses,
                String toStatus,
                String internalToStatus,
                List<String> roleCodes,
                String actorType,
                boolean userCallable,
                List<String> requiredFields,
                String missingFieldMessage,
                String invalidStateMessage,
                String forbiddenMessage,
                String endpoint,
                String batchEndpoint,
                String trigger) {
            this.action = action;
            this.label = label;
            this.aliases = aliases;
            this.fromStatuses = fromStatuses;
            this.toStatus = toStatus;
            this.internalToStatus = internalToStatus;
            this.roleCodes = roleCodes;
            this.actorType = actorType;
            this.userCallable = userCallable;
            this.requiredFields = requiredFields;
            this.missingFieldMessage = missingFieldMessage;
            this.invalidStateMessage = invalidStateMessage;
            this.forbiddenMessage = forbiddenMessage;
            this.endpoint = endpoint;
            this.batchEndpoint = batchEndpoint;
            this.trigger = trigger;
        }
    }

    @Data
    public static class SampleVO {
        private UUID id;
        private String requestNo;
        private UUID talentId;
        private String talentUid;
        private String talentName;
        private Long talentFansCount;
        private String talentCreditScore;
        private String talentMainCategory;
        private UUID productId;
        private String productExternalId;
        private String productName;
        private String productCover;
        private String productPriceText;
        private String shopId;
        private String shopName;
        private Integer quantity;
        private UUID applicantUserId;
        private String applicantName;
        private UUID channelUserId;
        private String channelUserName;
        private UUID colonelUserId;
        private String colonelUserName;
        private String trackingNo;
        private String shipperCode;
        private String recipientName;
        private String recipientPhone;
        private String recipientAddress;
        private String logisticsCompany;
        private String logisticsSource;
        private String logisticsStatus;
        private String logisticsStatusName;
        private LocalDateTime logisticsLastQueryAt;
        private String logisticsLastError;
        private LocalDateTime signedAt;
        private String rejectReason;
        private String closeReason;
        private String remark;
        private String applyReason;
        private String applySource;
        private String applySourceLabel;
        private String cooperationType;
        private String cooperationTypeLabel;
        private String sampleOwnerType;
        private String sampleOwnerTypeLabel;
        private String homeworkType;
        private String homeworkTypeLabel;
        private Map<String, Object> eligibilityCheck;
        private Map<String, Object> requirementSnapshot;
        private String status;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private LocalDateTime shipTime;
        private LocalDateTime deliverTime;
        private LocalDateTime completeTime;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getRequestNo() {
            return requestNo;
        }

        public void setRequestNo(String requestNo) {
            this.requestNo = requestNo;
        }

        public UUID getTalentId() {
            return talentId;
        }

        public void setTalentId(UUID talentId) {
            this.talentId = talentId;
        }

        public String getTalentName() {
            return talentName;
        }

        public void setTalentName(String talentName) {
            this.talentName = talentName;
        }

        public UUID getProductId() {
            return productId;
        }

        public void setProductId(UUID productId) {
            this.productId = productId;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public UUID getChannelUserId() {
            return channelUserId;
        }

        public void setChannelUserId(UUID channelUserId) {
            this.channelUserId = channelUserId;
        }

        public String getChannelUserName() {
            return channelUserName;
        }

        public void setChannelUserName(String channelUserName) {
            this.channelUserName = channelUserName;
        }

        public UUID getColonelUserId() {
            return colonelUserId;
        }

        public void setColonelUserId(UUID colonelUserId) {
            this.colonelUserId = colonelUserId;
        }

        public String getColonelUserName() {
            return colonelUserName;
        }

        public void setColonelUserName(String colonelUserName) {
            this.colonelUserName = colonelUserName;
        }

        public String getTrackingNo() {
            return trackingNo;
        }

        public void setTrackingNo(String trackingNo) {
            this.trackingNo = trackingNo;
        }

        public String getShipperCode() {
            return shipperCode;
        }

        public void setShipperCode(String shipperCode) {
            this.shipperCode = shipperCode;
        }

        public String getRecipientName() {
            return recipientName;
        }

        public void setRecipientName(String recipientName) {
            this.recipientName = recipientName;
        }

        public String getRecipientPhone() {
            return recipientPhone;
        }

        public void setRecipientPhone(String recipientPhone) {
            this.recipientPhone = recipientPhone;
        }

        public String getRecipientAddress() {
            return recipientAddress;
        }

        public void setRecipientAddress(String recipientAddress) {
            this.recipientAddress = recipientAddress;
        }

        public String getLogisticsCompany() {
            return logisticsCompany;
        }

        public void setLogisticsCompany(String logisticsCompany) {
            this.logisticsCompany = logisticsCompany;
        }

        public String getLogisticsSource() {
            return logisticsSource;
        }

        public void setLogisticsSource(String logisticsSource) {
            this.logisticsSource = logisticsSource;
        }

        public String getLogisticsStatus() {
            return logisticsStatus;
        }

        public void setLogisticsStatus(String logisticsStatus) {
            this.logisticsStatus = logisticsStatus;
        }

        public String getLogisticsStatusName() {
            return logisticsStatusName;
        }

        public void setLogisticsStatusName(String logisticsStatusName) {
            this.logisticsStatusName = logisticsStatusName;
        }

        public LocalDateTime getLogisticsLastQueryAt() {
            return logisticsLastQueryAt;
        }

        public void setLogisticsLastQueryAt(LocalDateTime logisticsLastQueryAt) {
            this.logisticsLastQueryAt = logisticsLastQueryAt;
        }

        public String getLogisticsLastError() {
            return logisticsLastError;
        }

        public void setLogisticsLastError(String logisticsLastError) {
            this.logisticsLastError = logisticsLastError;
        }

        public LocalDateTime getSignedAt() {
            return signedAt;
        }

        public void setSignedAt(LocalDateTime signedAt) {
            this.signedAt = signedAt;
        }

        public String getRejectReason() {
            return rejectReason;
        }

        public void setRejectReason(String rejectReason) {
            this.rejectReason = rejectReason;
        }

        public String getCloseReason() {
            return closeReason;
        }

        public void setCloseReason(String closeReason) {
            this.closeReason = closeReason;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

        public String getApplyReason() {
            return applyReason;
        }

        public void setApplyReason(String applyReason) {
            this.applyReason = applyReason;
        }

        public String getApplySource() {
            return applySource;
        }

        public void setApplySource(String applySource) {
            this.applySource = applySource;
        }

        public Map<String, Object> getEligibilityCheck() {
            return eligibilityCheck;
        }

        public void setEligibilityCheck(Map<String, Object> eligibilityCheck) {
            this.eligibilityCheck = eligibilityCheck;
        }

        public Map<String, Object> getRequirementSnapshot() {
            return requirementSnapshot;
        }

        public void setRequirementSnapshot(Map<String, Object> requirementSnapshot) {
            this.requirementSnapshot = requirementSnapshot;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
        }

        public LocalDateTime getCompleteTime() {
            return completeTime;
        }

        public void setCompleteTime(LocalDateTime completeTime) {
            this.completeTime = completeTime;
        }
    }

    public static class SampleProductVO {
        private UUID id;
        private String productId;
        private String productName;

        public SampleProductVO(UUID id, String productId, String productName) {
            this.id = id;
            this.productId = productId;
            this.productName = productName;
        }

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getProductId() {
            return productId;
        }

        public void setProductId(String productId) {
            this.productId = productId;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }
    }

    public static class SampleEligibilityCheckVO {
        private boolean eligible;
        private boolean needReason;
        private List<String> reasons;
        private Long min30DaySales;
        private String minLevel;
        private Long current30DaySales;
        private String currentLevel;

        public boolean isEligible() { return eligible; }
        public void setEligible(boolean eligible) { this.eligible = eligible; }
        public boolean isNeedReason() { return needReason; }
        public void setNeedReason(boolean needReason) { this.needReason = needReason; }
        public List<String> getReasons() { return reasons; }
        public void setReasons(List<String> reasons) { this.reasons = reasons; }
        public Long getMin30DaySales() { return min30DaySales; }
        public void setMin30DaySales(Long min30DaySales) { this.min30DaySales = min30DaySales; }
        public String getMinLevel() { return minLevel; }
        public void setMinLevel(String minLevel) { this.minLevel = minLevel; }
        public Long getCurrent30DaySales() { return current30DaySales; }
        public void setCurrent30DaySales(Long current30DaySales) { this.current30DaySales = current30DaySales; }
        public String getCurrentLevel() { return currentLevel; }
        public void setCurrentLevel(String currentLevel) { this.currentLevel = currentLevel; }
    }

    public static class SampleBoardCard {
        private UUID id;
        private String requestNo;
        private String talentName;
        private UUID productId;
        private String productName;
        private Integer quantity;
        private String channelUserName;
        private String trackingNo;
        private String rejectReason;
        private String remark;
        private String status;
        private LocalDateTime createTime;
        private LocalDateTime stateEnterTime;

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public String getRequestNo() { return requestNo; }
        public void setRequestNo(String requestNo) { this.requestNo = requestNo; }
        public String getTalentName() { return talentName; }
        public void setTalentName(String talentName) { this.talentName = talentName; }
        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public String getChannelUserName() { return channelUserName; }
        public void setChannelUserName(String channelUserName) { this.channelUserName = channelUserName; }
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
        public String getRejectReason() { return rejectReason; }
        public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
        public LocalDateTime getStateEnterTime() { return stateEnterTime; }
        public void setStateEnterTime(LocalDateTime stateEnterTime) { this.stateEnterTime = stateEnterTime; }
    }

    public static class SampleLogisticsVO {
        private UUID sampleRequestId;
        private String requestNo;
        private String trackingNo;
        private String logisticsCompany;
        private String logisticsStatus;
        private String logisticsStatusName;
        private LocalDateTime logisticsLastQueryAt;
        private String logisticsLastError;
        private LocalDateTime signedAt;
        private String status;
        private Boolean querySuccess;
        private String queryErrorCode;
        private String queryErrorMessage;
        private String provider;
        private List<LogisticsTraceVO> traces;

        public UUID getSampleRequestId() { return sampleRequestId; }
        public void setSampleRequestId(UUID sampleRequestId) { this.sampleRequestId = sampleRequestId; }
        public String getRequestNo() { return requestNo; }
        public void setRequestNo(String requestNo) { this.requestNo = requestNo; }
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
        public String getLogisticsCompany() { return logisticsCompany; }
        public void setLogisticsCompany(String logisticsCompany) { this.logisticsCompany = logisticsCompany; }
        public String getLogisticsStatus() { return logisticsStatus; }
        public void setLogisticsStatus(String logisticsStatus) { this.logisticsStatus = logisticsStatus; }
        public String getLogisticsStatusName() { return logisticsStatusName; }
        public void setLogisticsStatusName(String logisticsStatusName) { this.logisticsStatusName = logisticsStatusName; }
        public LocalDateTime getLogisticsLastQueryAt() { return logisticsLastQueryAt; }
        public void setLogisticsLastQueryAt(LocalDateTime logisticsLastQueryAt) { this.logisticsLastQueryAt = logisticsLastQueryAt; }
        public String getLogisticsLastError() { return logisticsLastError; }
        public void setLogisticsLastError(String logisticsLastError) { this.logisticsLastError = logisticsLastError; }
        public LocalDateTime getSignedAt() { return signedAt; }
        public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Boolean getQuerySuccess() { return querySuccess; }
        public void setQuerySuccess(Boolean querySuccess) { this.querySuccess = querySuccess; }
        public String getQueryErrorCode() { return queryErrorCode; }
        public void setQueryErrorCode(String queryErrorCode) { this.queryErrorCode = queryErrorCode; }
        public String getQueryErrorMessage() { return queryErrorMessage; }
        public void setQueryErrorMessage(String queryErrorMessage) { this.queryErrorMessage = queryErrorMessage; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public List<LogisticsTraceVO> getTraces() { return traces; }
        public void setTraces(List<LogisticsTraceVO> traces) { this.traces = traces; }
    }

    public static class LogisticsTraceVO {
        private LocalDateTime traceTime;
        private String traceContent;
        private String statusCode;
        private String statusName;

        public LocalDateTime getTraceTime() { return traceTime; }
        public void setTraceTime(LocalDateTime traceTime) { this.traceTime = traceTime; }
        public String getTraceContent() { return traceContent; }
        public void setTraceContent(String traceContent) { this.traceContent = traceContent; }
        public String getStatusCode() { return statusCode; }
        public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
        public String getStatusName() { return statusName; }
        public void setStatusName(String statusName) { this.statusName = statusName; }
    }

    public static class StatusLogVO {
        private UUID id;
        private String fromStatus;
        private String toStatus;
        private String operatorName;
        private LocalDateTime operateTime;
        private String remark;

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public String getFromStatus() { return fromStatus; }
        public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
        public String getToStatus() { return toStatus; }
        public void setToStatus(String toStatus) { this.toStatus = toStatus; }
        public String getOperatorName() { return operatorName; }
        public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
        public LocalDateTime getOperateTime() { return operateTime; }
        public void setOperateTime(LocalDateTime operateTime) { this.operateTime = operateTime; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    private void persistSample(SampleRequest sample) {
        OptimisticLockSupport.requireUpdated(sampleRequestMapper.updateById(sample));
    }
}
