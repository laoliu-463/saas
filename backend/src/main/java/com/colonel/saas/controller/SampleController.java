package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequirePermission;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.domain.sample.application.SampleApplicationService;
import com.colonel.saas.dto.SampleApplyRequest;
import com.colonel.saas.dto.SampleTalentQueryRequest;
import com.colonel.saas.dto.sample.LogisticsImportResult;
import com.colonel.saas.dto.sample.SampleActionRequest;
import com.colonel.saas.dto.sample.SampleBatchActionRequest;
import com.colonel.saas.dto.sample.SampleBatchShipRequest;
import com.colonel.saas.dto.sample.SampleCooperationUpdateRequest;
import com.colonel.saas.dto.sample.SampleLogisticsRepairRequest;
import com.colonel.saas.dto.sample.SamplePrivateNoteRequest;
import com.colonel.saas.vo.SampleTalentVO;
import com.colonel.saas.vo.sample.SampleBoardCard;
import com.colonel.saas.vo.sample.SampleCopyTextVO;
import com.colonel.saas.vo.sample.SampleEligibilityCheckVO;
import com.colonel.saas.vo.sample.SampleEditContextVO;
import com.colonel.saas.vo.sample.SampleLogisticsVO;
import com.colonel.saas.vo.sample.SampleProductVO;
import com.colonel.saas.vo.sample.SamplePrivateNoteVO;
import com.colonel.saas.vo.sample.SampleStatusTransitionVO;
import com.colonel.saas.vo.sample.SampleVO;
import com.colonel.saas.vo.sample.StatusLogVO;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * 寄样 HTTP 入口。
 * <p>所有寄样用例统一委派给寄样应用服务。
 */
@Validated
@Tag(name = "寄样管理", description = "寄样申请、寄样列表、达人候选搜索、状态流转与删除接口。")
@RestController
@RequestMapping("/samples")
@RequirePermission("sample:access")
public class SampleController {

    private final SampleApplicationService sampleApplicationService;

    public SampleController(SampleApplicationService sampleApplicationService) {
        this.sampleApplicationService = sampleApplicationService;
    }

    @PostMapping
    public ApiResult<SampleVO> createSample(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "寄样申请请求体。",
                    required = true,
                    content = @Content(examples = @ExampleObject(value = "{\"productId\":\"11111111-1111-1111-1111-111111111111\",\"talentId\":\"test_talent_001\",\"quantity\":1,\"remark\":\"优先安排发货\"}"))
            )
            @Valid @RequestBody SampleApplyRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleApplicationService.createSample(request, userId, roleCodes);
    }

    @PostMapping("/eligibility-check")
    public ApiResult<SampleEligibilityCheckVO> checkEligibility(
            @Valid @RequestBody SampleApplyRequest request,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleApplicationService.checkEligibility(request, roleCodes);
    }

    @GetMapping
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
        return ApiResult.ok(sampleApplicationService.getSamplePage(page, size, keyword, status, channelUserIds, recruiterUserId, productKeyword, shopKeyword, trackingNo, requestNo, talentKeyword, cooperationType, sampleOwnerType, homeworkType, recipientName, recipientPhone, applyStartTime, applyEndTime, homeworkStartTime, homeworkEndTime, logisticsCompany, userId, deptId, dataScope, roleCodes));
    }

    @GetMapping("/talent-candidates")
    public ApiResult<PageResult<SampleTalentVO>> searchTalents(@Valid SampleTalentQueryRequest request) {
        return sampleApplicationService.searchTalents(request);
    }

    @GetMapping("/product-candidates")
    public ApiResult<PageResult<SampleProductVO>> searchProducts(
            @Parameter(description = "页码，从 1 开始。") @RequestParam(defaultValue = "1") @Min(1) long page,
            @Parameter(description = "每页条数。") @RequestParam(defaultValue = "20") @Min(1) @Max(100) long size,
            @Parameter(description = "商品名称或商品 ID。") @RequestParam(required = false) String keyword,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleApplicationService.searchProducts(page, size, keyword, roleCodes);
    }

    @GetMapping("/board")
    public ApiResult<Map<String, List<SampleBoardCard>>> getSampleBoard(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ApiResult.ok(sampleApplicationService.getSampleBoard(userId, deptId, dataScope, roleCodes));
    }

    @GetMapping("/status-transitions")
    public ApiResult<List<SampleStatusTransitionVO>> getStatusTransitions() {
        return sampleApplicationService.getStatusTransitions();
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}")
    public ApiResult<SampleVO> getSampleById(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ApiResult.ok(sampleApplicationService.getSampleById(id, userId, deptId, dataScope, roleCodes));
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/edit-context")
    public ApiResult<SampleEditContextVO> getEditContext(
            @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ApiResult.ok(sampleApplicationService.getEditContext(
                id, userId, deptId, dataScope, roleCodes));
    }

    @PutMapping("/{id:[0-9a-fA-F\\-]{36}}/cooperation-details")
    public ApiResult<SampleEditContextVO> updateCooperationDetails(
            @PathVariable UUID id,
            @Valid @RequestBody SampleCooperationUpdateRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ApiResult.ok(sampleApplicationService.updateCooperationDetails(
                id, request, userId, deptId, dataScope, roleCodes));
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/private-note")
    public ApiResult<SamplePrivateNoteVO> getPrivateNote(
            @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ApiResult.ok(sampleApplicationService.getPrivateNote(
                id, userId, deptId, dataScope, roleCodes));
    }

    @PutMapping("/{id:[0-9a-fA-F\\-]{36}}/private-note")
    public ApiResult<SamplePrivateNoteVO> updatePrivateNote(
            @PathVariable UUID id,
            @Valid @RequestBody SamplePrivateNoteRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ApiResult.ok(sampleApplicationService.updatePrivateNote(
                id, request, userId, deptId, dataScope, roleCodes));
    }

    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/promotion-copy")
    public ApiResult<SampleCopyTextVO> copyPromotion(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ApiResult.ok(sampleApplicationService.copyPromotion(
                id, userId, deptId, dataScope, roleCodes, idempotencyKey));
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/order-copy")
    public ApiResult<SampleCopyTextVO> copyOrder(
            @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ApiResult.ok(sampleApplicationService.copyOrder(
                id, userId, deptId, dataScope, roleCodes));
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/status-logs")
    public ApiResult<List<StatusLogVO>> getStatusLogs(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleApplicationService.getStatusLogs(id, userId, deptId, dataScope, roleCodes);
    }

    @PutMapping("/{id:[0-9a-fA-F\\-]{36}}/status")
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
        return sampleApplicationService.actionSample(id, request, userId, deptId, dataScope, roleCodes);
    }

    @DeleteMapping("/{id:[0-9a-fA-F\\-]{36}}")
    public ApiResult<Void> deleteSample(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleApplicationService.deleteSample(id, userId, deptId, dataScope, roleCodes);
    }

    @RequirePermission("sample:sync-logistics")
    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/logistics/sync")
    public ApiResult<SampleLogisticsVO> syncLogistics(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleApplicationService.syncLogistics(id, userId, deptId, dataScope, roleCodes);
    }

    @RequirePermission("sample:repair-logistics")
    @PutMapping("/{id:[0-9a-fA-F\\-]{36}}/logistics")
    public ApiResult<SampleLogisticsVO> repairLogistics(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @Valid @RequestBody SampleLogisticsRepairRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleApplicationService.repairLogistics(id, request, userId, deptId, dataScope, roleCodes);
    }

    @RequirePermission("sample:refresh-logistics")
    @PostMapping("/{id:[0-9a-fA-F\\-]{36}}/logistics/refresh")
    public ApiResult<SampleVO> refreshLogistics(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleApplicationService.refreshLogistics(id, userId, deptId, dataScope, roleCodes);
    }

    @GetMapping("/{id:[0-9a-fA-F\\-]{36}}/logistics")
    public ApiResult<SampleLogisticsVO> getSampleLogistics(
            @Parameter(description = "寄样申请 ID，使用 UUID 格式。") @PathVariable UUID id,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return ApiResult.ok(sampleApplicationService.getSampleLogistics(id, userId, deptId, dataScope, roleCodes));
    }

    @RequirePermission("sample:sync-all-logistics")
    @PostMapping("/logistics/sync-all")
    public ApiResult<Map<String, Integer>> syncAllLogistics(
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleApplicationService.syncAllLogistics(roleCodes);
    }

    @RequirePermission("sample:download-logistics-import-template")
    @GetMapping("/logistics/import-template")
    public void downloadLogisticsImportTemplate(HttpServletResponse response) throws IOException {
        sampleApplicationService.downloadLogisticsImportTemplate(response);
    }

    @RequirePermission("sample:import-logistics-tracking")
    @PostMapping(value = "/logistics/import", consumes = "multipart/form-data")
    public ApiResult<LogisticsImportResult> importLogisticsTracking(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean allowOverwrite,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleApplicationService.importLogisticsTracking(file, allowOverwrite, userId, roleCodes);
    }

    @RequirePermission("sample:batch-approve")
    @PostMapping("/batch-approve")
    public ApiResult<Map<String, Integer>> batchApprove(
            @Valid @RequestBody SampleBatchActionRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleApplicationService.batchApprove(request, userId, deptId, dataScope, roleCodes);
    }

    @RequirePermission("sample:batch-reject")
    @PostMapping("/batch-reject")
    public ApiResult<Map<String, Integer>> batchReject(
            @Valid @RequestBody SampleBatchActionRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleApplicationService.batchReject(request, userId, deptId, dataScope, roleCodes);
    }

    @RequirePermission("sample:batch-ship")
    @PostMapping("/batch-ship")
    public ApiResult<Map<String, Integer>> batchShip(
            @Valid @RequestBody SampleBatchShipRequest request,
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute(value = "deptId", required = false) UUID deptId,
            @RequestAttribute(value = "dataScope", required = false) DataScope dataScope,
            @RequestAttribute(value = "roleCodes", required = false) Object roleCodes) {
        return sampleApplicationService.batchShip(request, userId, deptId, dataScope, roleCodes);
    }

    @RequirePermission("sample:export-samples")
    @GetMapping("/exports")
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
        sampleApplicationService.exportSamples(status, keyword, channelUserIds, recruiterUserId, productKeyword, shopKeyword, trackingNo, requestNo, talentKeyword, cooperationType, sampleOwnerType, homeworkType, recipientName, recipientPhone, applyStartTime, applyEndTime, homeworkStartTime, homeworkEndTime, logisticsCompany, userId, deptId, dataScope, roleCodes, response);
    }
}
