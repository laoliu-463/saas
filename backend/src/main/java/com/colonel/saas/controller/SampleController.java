package com.colonel.saas.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.common.base.BaseController;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.enums.SampleStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.common.exception.OptimisticLockSupport;
import com.colonel.saas.common.exception.ValidateException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.sample.LogisticsImportResult;
import com.colonel.saas.dto.sample.SampleActionRequest;
import com.colonel.saas.dto.sample.SampleBatchActionRequest;
import com.colonel.saas.dto.sample.SampleBatchShipItem;
import com.colonel.saas.dto.sample.SampleBatchShipRequest;
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
import com.colonel.saas.service.SampleLogisticsSubscriptionService;
import com.colonel.saas.service.SampleLogisticsSyncService;
import com.colonel.saas.service.SampleWriteTransactionService;
import com.colonel.saas.service.sample.SampleApplicationService;
import com.colonel.saas.vo.SampleTalentVO;
import com.colonel.saas.vo.sample.LogisticsTraceVO;
import com.colonel.saas.vo.sample.SampleBoardCard;
import com.colonel.saas.vo.sample.SampleEligibilityCheckVO;
import com.colonel.saas.vo.sample.SampleLogisticsVO;
import com.colonel.saas.vo.sample.SampleProductVO;
import com.colonel.saas.vo.sample.SampleStatusTransitionVO;
import com.colonel.saas.vo.sample.SampleVO;
import com.colonel.saas.vo.sample.StatusLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
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


/**
 * 寄样 HTTP 入口，业务实现下沉到 SampleApplicationService。
 */
@Validated
@Tag(name = "寄样管理", description = "寄样申请、寄样列表、达人候选搜索、状态流转与删除接口。")
@RestController
@RequestMapping("/samples")
@RequireRoles({RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.CHANNEL_LEADER, RoleCodes.CHANNEL_STAFF, RoleCodes.OPS_STAFF})
public class SampleController extends SampleApplicationService {

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
            com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade,
            ProductService productService,
            SampleEligibilityService sampleEligibilityService,
            SampleLogisticsSyncService sampleLogisticsSyncService,
            SampleLogisticsImportService sampleLogisticsImportService,
            SampleLogisticsSubscriptionService sampleLogisticsSubscriptionService,
            SampleDomainEventPublisher sampleDomainEventPublisher,
            SampleWriteTransactionService sampleWriteTransactionService) {
        super(sampleRequestMapper, productMapper, productOperationStateMapper, productSnapshotMapper, sysUserMapper, talentMapper, talentClaimMapper, sampleStatusLogService, sampleStatusLogMapper, crawlerTalentInfoService, configDomainFacade, productService, sampleEligibilityService, sampleLogisticsSyncService, sampleLogisticsImportService, sampleLogisticsSubscriptionService, sampleDomainEventPublisher, sampleWriteTransactionService);
    }

    @PostMapping
    @Override
    public ApiResult<SampleVO> createSample(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "寄样申请请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"productId\":\"11111111-1111-1111-1111-111111111111\",\"talentId\":\"test_talent_001\",\"quantity\":1,\"remark\":\"优先安排发货\"}"))
            )
            @Valid @RequestBody SampleApplyRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return super.createSample(request, userId, roleCodes);
    }

    @PostMapping("/eligibility-check")
    @Override
    public ApiResult<SampleEligibilityCheckVO> checkEligibility(
            @Valid @RequestBody SampleApplyRequest request,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return super.checkEligibility(request, roleCodes);
    }

    @GetMapping
    @Override
    public ApiResult<PageResult<SampleVO>> getSamplePage(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @Parameter(description = "关键字，可匹配达人昵称、达人 UID、寄样单号或商品名称。") @RequestParam(required = false) String keyword,
            @Parameter(description = "寄样状态。可用值包括 PENDING_AUDIT、PENDING_SHIP、SHIPPING、DELIVERED、PENDING_HOMEWORK、COMPLETED、REJECTED、CLOSED。") @RequestParam(required = false) String status,
            @Parameter(description = "渠道负责人用户 ID 列表（多选，IN 查询），与数据权限范围叠加。") @RequestParam(required = false) List<UUID> channelUserIds,
            @Parameter(description = "招商负责人用户 ID。") @RequestParam(required = false) UUID recruiterUserId,
            @Parameter(description = "商品 ID 或商品名称。") @RequestParam(required = false) String productKeyword,
            @Parameter(description = "店铺 ID 或店铺名称。") @RequestParam(required = false) String shopKeyword,
            @Parameter(description = "物流单号（精确匹配）。") @RequestParam(required = false) String trackingNo,
            @Parameter(description = "申请编号 / 合作单号（精确匹配）。") @RequestParam(required = false) String requestNo,
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
        return super.getSamplePage(page, size, keyword, status, channelUserIds, recruiterUserId, productKeyword, shopKeyword, trackingNo, requestNo, talentKeyword, cooperationType, sampleOwnerType, homeworkType, recipientName, recipientPhone, applyStartTime, applyEndTime, homeworkStartTime, homeworkEndTime, logisticsCompany, userId, deptId, dataScope, roleCodes);
    }

    @GetMapping("/talent-candidates")
    @Override
    public ApiResult<PageResult<SampleTalentVO>> searchTalents(@Valid SampleTalentQueryRequest request) {
        return super.searchTalents(request);
    }

    @GetMapping("/product-candidates")
    @Override
    public ApiResult<PageResult<SampleProductVO>> searchProducts(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @Parameter(description = "商品名称或商品 ID。") @RequestParam(required = false) String keyword,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return super.searchProducts(page, size, keyword, roleCodes);
    }

    @GetMapping("/board")
    @Override
    public ApiResult<Map<String, List<SampleBoardCard>>> getSampleBoard(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return super.getSampleBoard(userId, deptId, dataScope, roleCodes);
    }

    @GetMapping("/status-transitions")
    @Override
    public ApiResult<List<SampleStatusTransitionVO>> getStatusTransitions() {
        return super.getStatusTransitions();
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    @Override
    public ApiResult<SampleVO> getSampleById(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return super.getSampleById(id, userId, deptId, dataScope, roleCodes);
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/status-logs")
    @Override
    public ApiResult<List<StatusLogVO>> getStatusLogs(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return super.getStatusLogs(id, userId, deptId, dataScope, roleCodes);
    }

    @PutMapping("/{id:[0-9a-fA-F\\-]{36}}/status")
    @Override
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
        return super.actionSample(id, request, userId, deptId, dataScope, roleCodes);
    }

    @DeleteMapping("/{id:[0-9a-fA-F\\-]{36}}")
    @Override
    public ApiResult<Void> deleteSample(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return super.deleteSample(id, userId, deptId, dataScope, roleCodes);
    }

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/logistics/sync")
    @Override
    public ApiResult<SampleLogisticsVO> syncLogistics(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return super.syncLogistics(id, userId, deptId, dataScope, roleCodes);
    }

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/logistics/refresh")
    @Override
    public ApiResult<SampleVO> refreshLogistics(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return super.refreshLogistics(id, userId, deptId, dataScope, roleCodes);
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/logistics")
    @Override
    public ApiResult<SampleLogisticsVO> getSampleLogistics(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return super.getSampleLogistics(id, userId, deptId, dataScope, roleCodes);
    }

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @PostMapping("/logistics/sync-all")
    @Override
    public ApiResult<Map<String, Integer>> syncAllLogistics(
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return super.syncAllLogistics(roleCodes);
    }

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @GetMapping("/logistics/import-template")
    @Override
    public void downloadLogisticsImportTemplate(HttpServletResponse response) throws IOException {
        super.downloadLogisticsImportTemplate(response);
    }

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @PostMapping(value = "/logistics/import", consumes = "multipart/form-data")
    @Override
    public ApiResult<LogisticsImportResult> importLogisticsTracking(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean allowOverwrite,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return super.importLogisticsTracking(file, allowOverwrite, userId, roleCodes);
    }

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_STAFF})
    @PostMapping("/batch-approve")
    @Override
    public ApiResult<Map<String, Integer>> batchApprove(
            @Valid @RequestBody SampleBatchActionRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return super.batchApprove(request, userId, deptId, dataScope, roleCodes);
    }

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_STAFF})
    @PostMapping("/batch-reject")
    @Override
    public ApiResult<Map<String, Integer>> batchReject(
            @Valid @RequestBody SampleBatchActionRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return super.batchReject(request, userId, deptId, dataScope, roleCodes);
    }

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.OPS_STAFF})
    @PostMapping("/batch-ship")
    @Override
    public ApiResult<Map<String, Integer>> batchShip(
            @Valid @RequestBody SampleBatchShipRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return super.batchShip(request, userId, deptId, dataScope, roleCodes);
    }

    @RequireRoles({RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF, RoleCodes.OPS_STAFF, RoleCodes.CHANNEL_LEADER})
    @GetMapping("/exports")
    @Override
    public void exportSamples(
            @Parameter(description = "寄样状态。") @RequestParam(required = false) String status,
            @Parameter(description = "关键字。") @RequestParam(required = false) String keyword,
            @Parameter(description = "渠道负责人用户 ID 列表（多选，IN 查询）。") @RequestParam(required = false) List<UUID> channelUserIds,
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
        super.exportSamples(status, keyword, channelUserIds, recruiterUserId, productKeyword, shopKeyword, trackingNo, requestNo, talentKeyword, cooperationType, sampleOwnerType, homeworkType, recipientName, recipientPhone, applyStartTime, applyEndTime, homeworkStartTime, homeworkEndTime, logisticsCompany, userId, deptId, dataScope, roleCodes, response);
    }
}
