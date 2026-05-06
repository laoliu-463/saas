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
import com.colonel.saas.common.exception.ValidateException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.SampleApplyRequest;
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
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.service.CrawlerTalentInfoService;
import com.colonel.saas.service.BusinessRuleConfigService;
import com.colonel.saas.service.SampleStatusLogService;
import com.colonel.saas.service.SampleEligibilityService;
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
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private final SampleRequestMapper sampleRequestMapper;
    private final ProductMapper productMapper;
    private final ProductOperationStateMapper productOperationStateMapper;
    private final ProductSnapshotMapper productSnapshotMapper;
    private final SysUserMapper sysUserMapper;
    private final TalentMapper talentMapper;
    private final SampleStatusLogService sampleStatusLogService;
    private final SampleStatusLogMapper sampleStatusLogMapper;
    private final CrawlerTalentInfoService crawlerTalentInfoService;
    private final BusinessRuleConfigService businessRuleConfigService;
    private final SampleEligibilityService sampleEligibilityService;

    public SampleController(
            SampleRequestMapper sampleRequestMapper,
            ProductMapper productMapper,
            ProductOperationStateMapper productOperationStateMapper,
            ProductSnapshotMapper productSnapshotMapper,
            SysUserMapper sysUserMapper,
            TalentMapper talentMapper,
            SampleStatusLogService sampleStatusLogService,
            SampleStatusLogMapper sampleStatusLogMapper,
            CrawlerTalentInfoService crawlerTalentInfoService,
            BusinessRuleConfigService businessRuleConfigService,
            SampleEligibilityService sampleEligibilityService) {
        this.sampleRequestMapper = sampleRequestMapper;
        this.productMapper = productMapper;
        this.productOperationStateMapper = productOperationStateMapper;
        this.productSnapshotMapper = productSnapshotMapper;
        this.sysUserMapper = sysUserMapper;
        this.talentMapper = talentMapper;
        this.sampleStatusLogService = sampleStatusLogService;
        this.sampleStatusLogMapper = sampleStatusLogMapper;
        this.crawlerTalentInfoService = crawlerTalentInfoService;
        this.businessRuleConfigService = businessRuleConfigService;
        this.sampleEligibilityService = sampleEligibilityService;
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
        Product product = requireProduct(request.getProductId());
        CrawlerTalentInfo talentInfo = requireCrawlerTalent(request.getTalentId());
        Talent talent = findOrCreateTalentFromCrawler(talentInfo);
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
        sample.setStatus(SampleStatus.PENDING_AUDIT.code);
        sample.setRemark(request.getRemark());
        sample.setExtraData(buildSampleExtraData(request, eligibility));
        sampleRequestMapper.insert(sample);
        sampleStatusLogService.log(sample.getId(), null, sample.getStatus(), userId, "create sample request");

        return ok(toVO(sample, product, product.getName(), sample.getTalentNickname()));
    }

    @Operation(summary = "寄样资格预检", description = "按当前寄样默认标准检查达人是否满足要求；不满足时前端需提醒并要求填写申请原因。")
    @PostMapping("/eligibility-check")
    public ApiResult<SampleEligibilityCheckVO> checkEligibility(
            @Valid @RequestBody SampleApplyRequest request) {
        CrawlerTalentInfo talentInfo = requireCrawlerTalent(request.getTalentId());
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
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        Page<SampleRequest> pageReq = new Page<>(page, size);
        QueryWrapper<SampleRequest> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", parseStatus(status).code);
        }
        if (StringUtils.hasText(keyword)) {
            Set<UUID> matchedProductIds = loadMatchedProductIds(keyword.trim());
            wrapper.and(query -> {
                query.like("talent_nickname", keyword.trim())
                        .or()
                        .like("talent_uid", keyword.trim())
                        .or()
                        .like("request_no", keyword.trim());
                if (!matchedProductIds.isEmpty()) {
                    query.or().in("product_id", matchedProductIds);
                }
            });
        }

        IPage<SampleRequest> samplePage = sampleRequestMapper.findPageWithScope(pageReq, wrapper);
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
            @Parameter(description = "商品名称或商品 ID。") @RequestParam(required = false) String keyword) {
        Page<Product> pageReq = new Page<>(page, size);
        QueryWrapper<Product> wrapper = new QueryWrapper<Product>().orderByDesc("create_time");
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(query -> query.like("name", value).or().like("product_id", value));
        }
        IPage<Product> productPage = productMapper.selectPage(pageReq, wrapper);
        Page<SampleProductVO> result = new Page<>(productPage.getCurrent(), productPage.getSize(), productPage.getTotal());
        result.setRecords(productPage.getRecords().stream()
                .map(product -> new SampleProductVO(product.getId(), product.getProductId(), product.getName()))
                .toList());
        return okPage(result);
    }

    @Operation(summary = "寄样看板", description = "按状态分组返回全量寄样单，用于看板视图。")
    @GetMapping("/board")
    public ApiResult<Map<String, List<SampleBoardCard>>> getSampleBoard(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
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
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        SampleRequest sample = requireSample(id, userId, deptId, dataScope);
        Product product = productMapper.selectById(sample.getProductId());
        return ok(toVO(
                sample,
                product,
                product == null ? null : product.getName(),
                sample.getTalentNickname()));
    }

    @Operation(summary = "寄样状态日志", description = "查询寄样申请的状态变更历史记录。")
    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/status-logs")
    public ApiResult<List<StatusLogVO>> getStatusLogs(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        requireSample(id, userId, deptId, dataScope);
        List<SampleStatusLog> logs = sampleStatusLogMapper.selectList(
                new LambdaQueryWrapper<SampleStatusLog>()
                        .eq(SampleStatusLog::getRequestId, id)
                        .orderByDesc(SampleStatusLog::getOperateTime));
        List<StatusLogVO> voList = logs.stream().map(log -> {
            StatusLogVO vo = new StatusLogVO();
            vo.setId(log.getId());
            vo.setFromStatus(log.getFromStatus() == null ? null : SampleStatus.fromCode(log.getFromStatus()).apiStatus);
            vo.setToStatus(log.getToStatus() == null ? null : SampleStatus.fromCode(log.getToStatus()).apiStatus);
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
        SampleRequest sample = requireSample(id, userId, deptId, dataScope);
        LocalDateTime now = LocalDateTime.now();
        int fromStatus = sample.getStatus();
        SampleStatus current = SampleStatus.fromCode(fromStatus);
        String action = normalizeAction(request.getAction());
        ensureActionRolePermission(action, roleCodes);

        if ("PENDING_SHIP".equals(action)) {
            ensureTransition(current, SampleStatus.PENDING_AUDIT);
            sample.setStatus(SampleStatus.PENDING_SHIP.code);
            sample.setAuditTime(now);
        } else if ("REJECTED".equals(action)) {
            ensureTransition(current, SampleStatus.PENDING_AUDIT);
            if (!StringUtils.hasText(request.getReason())) {
                throw new BusinessException("reason is required when reject sample request");
            }
            sample.setStatus(SampleStatus.REJECTED.code);
            sample.setRejectReason(request.getReason());
            sample.setAuditTime(now);
        } else if ("SHIPPING".equals(action)) {
            ensureTransition(current, SampleStatus.PENDING_SHIP);
            if (!StringUtils.hasText(request.getTrackingNo())) {
                throw new BusinessException("trackingNo is required when shipping");
            }
            sample.setStatus(SampleStatus.SHIPPING.code);
            sample.setTrackingNo(request.getTrackingNo());
            sample.setShipTime(now);
        } else if ("DELIVERED".equals(action)) {
            ensureTransition(current, SampleStatus.SHIPPING);
            sample.setStatus(SampleStatus.DELIVERED.code);
            sample.setDeliverTime(now);
        } else if ("PENDING_HOMEWORK".equals(action)) {
            ensureTransition(current, SampleStatus.DELIVERED);
            sample.setStatus(SampleStatus.PENDING_HOMEWORK.code);
        } else if ("COMPLETED".equals(action)) {
            ensureTransition(current, SampleStatus.PENDING_HOMEWORK);
            sample.setStatus(SampleStatus.COMPLETED.code);
            sample.setCompleteTime(now);
        } else if ("CLOSED".equals(action)) {
            sample.setStatus(SampleStatus.CLOSED.code);
            sample.setCloseTime(now);
            sample.setCloseReason(request.getReason());
        } else {
            throw new BusinessException("Unsupported action: " + request.getAction());
        }

        sampleRequestMapper.updateById(sample);
        sampleStatusLogService.log(sample.getId(), fromStatus, sample.getStatus(), userId, request.getReason());
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
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope) {
        SampleRequest sample = requireSample(id, userId, deptId, dataScope);
        SampleStatus status = SampleStatus.fromCode(sample.getStatus());
        if (status != SampleStatus.PENDING_AUDIT && status != SampleStatus.REJECTED) {
            throw new BusinessException("Only pending/rejected sample can be deleted");
        }
        sampleRequestMapper.deleteById(id);
        return ok();
    }

    @Operation(summary = "批量审批通过", description = "批量将 PENDING_AUDIT 的寄样申请审批为待发货。仅招商角色可操作。")
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
                SampleRequest sample = requireSampleByRequestNo(requestNo, userId, deptId, dataScope);
                SampleStatus current = SampleStatus.fromCode(sample.getStatus());
                ensureTransition(current, SampleStatus.PENDING_AUDIT);
                int fromStatus = sample.getStatus();
                sample.setStatus(SampleStatus.PENDING_SHIP.code);
                sample.setAuditTime(now);
                sampleRequestMapper.updateById(sample);
                sampleStatusLogService.log(sample.getId(), fromStatus, sample.getStatus(), userId, request.getRemark());
                success++;
            } catch (BusinessException | ForbiddenException e) {
                log.warn("Batch approve failed for requestNo={}: {}", requestNo, e.getMessage());
                fail++;
            }
        }
        return ok(Map.of("success", success, "fail", fail));
    }

    @Operation(summary = "批量驳回", description = "批量将 PENDING_AUDIT 的寄样申请驳回。仅招商角色可操作，驳回原因必填。")
    @PostMapping("/batch-reject")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Map<String, Integer>> batchReject(
            @Valid @RequestBody SampleBatchActionRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        if (!StringUtils.hasText(request.getRemark())) {
            throw new BusinessException("remark is required when batch reject");
        }
        ensureActionRolePermission("REJECTED", roleCodes);
        LocalDateTime now = LocalDateTime.now();
        int success = 0;
        int fail = 0;
        for (String requestNo : request.getRequestNos()) {
            try {
                SampleRequest sample = requireSampleByRequestNo(requestNo, userId, deptId, dataScope);
                SampleStatus current = SampleStatus.fromCode(sample.getStatus());
                ensureTransition(current, SampleStatus.PENDING_AUDIT);
                int fromStatus = sample.getStatus();
                sample.setStatus(SampleStatus.REJECTED.code);
                sample.setRejectReason(request.getRemark());
                sample.setAuditTime(now);
                sampleRequestMapper.updateById(sample);
                sampleStatusLogService.log(sample.getId(), fromStatus, sample.getStatus(), userId, request.getRemark());
                success++;
            } catch (BusinessException | ForbiddenException e) {
                log.warn("Batch reject failed for requestNo={}: {}", requestNo, e.getMessage());
                fail++;
            }
        }
        return ok(Map.of("success", success, "fail", fail));
    }

    @Operation(summary = "寄样导出 CSV", description = "导出寄样申请列表为 CSV 文件，支持状态筛选和关键字搜索。")
    @GetMapping("/exports")
    public void exportSamples(
            @Parameter(description = "寄样状态。") @RequestParam(required = false) String status,
            @Parameter(description = "关键字。") @RequestParam(required = false) String keyword,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            HttpServletResponse response) throws IOException {
        // Validate status early, before committing response headers
        if (StringUtils.hasText(status)) {
            parseStatus(status);
        }

        QueryWrapper<SampleRequest> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", SampleStatus.fromApiStatus(status).code);
        }
        if (StringUtils.hasText(keyword)) {
            Set<UUID> matchedProductIds = loadMatchedProductIds(keyword.trim());
            wrapper.and(query -> {
                query.like("talent_nickname", keyword.trim())
                        .or()
                        .like("talent_uid", keyword.trim())
                        .or()
                        .like("request_no", keyword.trim());
                if (!matchedProductIds.isEmpty()) {
                    query.or().in("product_id", matchedProductIds);
                }
            });
        }

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"samples.csv\"");
        PrintWriter writer = response.getWriter();
        try {
            writer.write('\ufeff');
            writer.println("寄样单号,达人昵称,商品名称,状态,招商负责人,物流单号,驳回原因,备注,创建时间");

            Map<UUID, Product> productCache = new HashMap<>();
            long current = 1L;
            while (true) {
                IPage<SampleRequest> pageResult = sampleRequestMapper.findPageWithScope(
                        new Page<>(current, EXPORT_BATCH_SIZE), wrapper);
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
                    writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                            csvEscape(sample.getRequestNo()),
                            csvEscape(sample.getTalentNickname()),
                            csvEscape(product == null ? null : product.getName()),
                            csvEscape(s.apiStatus),
                            csvEscape(resolveUserDisplayName(sample.getChannelUserId())),
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

    private SampleRequest requireSampleByRequestNo(String requestNo, UUID currentUserId, UUID currentDeptId, DataScope dataScope) {
        SampleRequest sample = sampleRequestMapper.selectOne(new LambdaQueryWrapper<SampleRequest>()
                .eq(SampleRequest::getRequestNo, requestNo)
                .last("LIMIT 1"));
        if (sample == null) {
            throw new BusinessException("Sample request not found: " + requestNo);
        }
        assertCanAccessSample(sample, currentUserId, currentDeptId, dataScope);
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
            throw new BusinessException("Current status does not allow this action: " + current.apiStatus);
        }
    }

    private SampleRequest requireSample(UUID id, UUID currentUserId, UUID currentDeptId, DataScope dataScope) {
        SampleRequest sample = sampleRequestMapper.selectById(id);
        if (sample == null) {
            throw new BusinessException("Sample request not found");
        }
        assertCanAccessSample(sample, currentUserId, currentDeptId, dataScope);
        return sample;
    }

    private void assertCanAccessSample(SampleRequest sample, UUID currentUserId, UUID currentDeptId, DataScope dataScope) {
        if (sample == null || dataScope == null || dataScope == DataScope.ALL) {
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

    private UUID resolveUserDeptId(UUID userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user == null ? null : user.getDeptId();
    }

    private void ensureActionRolePermission(String action, Object roleCodes) {
        switch (action) {
            case "PENDING_SHIP", "REJECTED" -> {
                if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF)) {
                    throw new ForbiddenException("仅招商角色可以审核寄样");
                }
            }
            case "SHIPPING", "DELIVERED", "PENDING_HOMEWORK", "COMPLETED", "CLOSED" -> {
                if (!hasAnyRole(roleCodes, RoleCodes.ADMIN, RoleCodes.OPS_STAFF)) {
                    throw new ForbiddenException("仅运营角色可以推进物流与完结状态");
                }
            }
            default -> {
            }
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
        }
        if (product == null) {
            throw new ValidateException("Selected product does not exist");
        }
        return product;
    }

    private CrawlerTalentInfo requireCrawlerTalent(String talentId) {
        CrawlerTalentInfo talentInfo = crawlerTalentInfoService.findByTalentId(talentId);
        if (talentInfo == null) {
            throw new ValidateException("Selected talent does not exist");
        }
        return talentInfo;
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
            throw new BusinessException("Duplicate sample request is blocked within " + restrictDays + " days");
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
                || RoleCodes.BIZ_LEADER.equals(roleCode)
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
            throw new BusinessException("达人未满足默认寄样标准，请先填写申请原因后再提交");
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
        extra.put("eligibilityCheck", eligibilityCheck);
        extra.put("applyReason", StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
        extra.put("requirementSnapshot", buildRequirementSnapshot(eligibility));
        extra.put("addressSource", "manual");
        return extra;
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
        Set<UUID> matched = new HashSet<>();
        long current = 1L;
        while (true) {
            Page<Product> page = new Page<>(current, PRODUCT_KEYWORD_BATCH_SIZE);
            QueryWrapper<Product> wrapper = new QueryWrapper<Product>()
                    .select("id")
                    .and(query -> query.like("name", keyword).or().like("product_id", keyword));
            IPage<Product> result = productMapper.selectPage(page, wrapper);
            List<Product> records = result.getRecords();
            if (records == null || records.isEmpty()) {
                break;
            }
            for (Product product : records) {
                if (product.getId() != null) {
                    matched.add(product.getId());
                }
            }
            if (current >= result.getPages()) {
                break;
            }
            current++;
        }
        return matched;
    }

    private SampleVO toVO(SampleRequest sample, Product product, String productName, String talentName) {
        Product resolvedProduct = product;
        if (resolvedProduct == null && sample.getProductId() != null) {
            resolvedProduct = productMapper.selectById(sample.getProductId());
        }
        UUID colonelUserId = resolveColonelUserId(resolvedProduct);
        SampleVO vo = new SampleVO();
        vo.setId(sample.getId());
        vo.setRequestNo(sample.getRequestNo());
        vo.setTalentId(sample.getTalentId());
        vo.setTalentName(StringUtils.hasText(talentName) ? talentName : sample.getTalentNickname());
        vo.setProductId(sample.getProductId());
        vo.setProductName(productName);
        vo.setQuantity(sample.getExpectedSampleNum() == null ? 1 : sample.getExpectedSampleNum());
        vo.setChannelUserId(sample.getChannelUserId());
        vo.setChannelUserName(resolveUserDisplayName(sample.getChannelUserId()));
        vo.setColonelUserId(colonelUserId);
        vo.setColonelUserName(resolveUserDisplayName(colonelUserId));
        vo.setTrackingNo(sample.getTrackingNo());
        vo.setRejectReason(sample.getRejectReason());
        vo.setCloseReason(sample.getCloseReason());
        vo.setRemark(sample.getRemark());
        vo.setApplyReason(readExtraText(sample.getExtraData(), "applyReason"));
        vo.setEligibilityCheck(readExtraMap(sample.getExtraData(), "eligibilityCheck"));
        vo.setRequirementSnapshot(readExtraMap(sample.getExtraData(), "requirementSnapshot"));
        vo.setCreateTime(sample.getCreateTime());
        vo.setUpdateTime(sample.getUpdateTime());
        vo.setCompleteTime(sample.getCompleteTime());
        vo.setStatus(toLegacyStatus(SampleStatus.fromCode(sample.getStatus())));
        return vo;
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

    private SampleStatus parseStatus(String status) {
        try {
            return SampleStatus.fromApiStatus(status.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BusinessException("Invalid status: " + status);
        }
    }

    private String normalizeAction(String action) {
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "APPROVED" -> "PENDING_SHIP";
            case "SHIPPED" -> "SHIPPING";
            case "SIGNED" -> "DELIVERED";
            case "PENDING_TASK" -> "PENDING_HOMEWORK";
            case "FINISHED" -> "COMPLETED";
            default -> normalized;
        };
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
            throw new BusinessException("Unknown sample status: " + code);
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

    public static class SampleVO {
        private UUID id;
        private String requestNo;
        private UUID talentId;
        private String talentName;
        private UUID productId;
        private String productName;
        private Integer quantity;
        private UUID channelUserId;
        private String channelUserName;
        private UUID colonelUserId;
        private String colonelUserName;
        private String trackingNo;
        private String rejectReason;
        private String closeReason;
        private String remark;
        private String applyReason;
        private Map<String, Object> eligibilityCheck;
        private Map<String, Object> requirementSnapshot;
        private String status;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
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
}
