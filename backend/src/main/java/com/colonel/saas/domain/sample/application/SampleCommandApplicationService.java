package com.colonel.saas.domain.sample.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.sample.facade.SampleDomainFacade;
import com.colonel.saas.dto.SampleApplyRequest;
import com.colonel.saas.dto.SampleTalentQueryRequest;
import com.colonel.saas.dto.sample.LogisticsImportResult;
import com.colonel.saas.dto.sample.SampleActionRequest;
import com.colonel.saas.dto.sample.SampleBatchActionRequest;
import com.colonel.saas.dto.sample.SampleBatchShipRequest;
import com.colonel.saas.dto.sample.SampleLogisticsRepairRequest;
import com.colonel.saas.service.sample.SampleCommandService;
import com.colonel.saas.vo.SampleTalentVO;
import com.colonel.saas.vo.sample.SampleEligibilityCheckVO;
import com.colonel.saas.vo.sample.SampleLogisticsVO;
import com.colonel.saas.vo.sample.SampleProductVO;
import com.colonel.saas.vo.sample.SampleStatusTransitionVO;
import com.colonel.saas.vo.sample.SampleVO;
import com.colonel.saas.vo.sample.StatusLogVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 寄样命令应用层（DDD-SAMPLE-007 Batch3 Replace）。
 *
 * <p>写路径统一经本服务；开关开启时按 ID 操作先走 {@link SampleDomainFacade} 存在性检查。</p>
 */
@Service
public class SampleCommandApplicationService {

    private final SampleCommandService sampleCommandService;
    private final SampleDomainFacade sampleDomainFacade;
    private final DddRefactorProperties dddRefactorProperties;

    public SampleCommandApplicationService(
            SampleCommandService sampleCommandService,
            SampleDomainFacade sampleDomainFacade,
            DddRefactorProperties dddRefactorProperties) {
        this.sampleCommandService = sampleCommandService;
        this.sampleDomainFacade = sampleDomainFacade;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    public boolean isRoutingEnabled() {
        return dddRefactorProperties.isEnabled()
                && dddRefactorProperties.getSampleApplication().isEnabled();
    }

    public ApiResult<SampleVO> createSample(SampleApplyRequest request, UUID userId, Object roleCodes) {
        return sampleCommandService.createSample(request, userId, roleCodes);
    }

    public ApiResult<SampleEligibilityCheckVO> checkEligibility(SampleApplyRequest request, Object roleCodes) {
        return sampleCommandService.checkEligibility(request, roleCodes);
    }

    public ApiResult<PageResult<SampleTalentVO>> searchTalents(SampleTalentQueryRequest request) {
        return sampleCommandService.searchTalents(request);
    }

    public ApiResult<PageResult<SampleProductVO>> searchProducts(
            long page, long size, String keyword, Object roleCodes) {
        return sampleCommandService.searchProducts(page, size, keyword, roleCodes);
    }

    public ApiResult<List<SampleStatusTransitionVO>> getStatusTransitions() {
        return sampleCommandService.getStatusTransitions();
    }

    public ApiResult<List<StatusLogVO>> getStatusLogs(
            UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        if (isRoutingEnabled()) {
            assertSampleExistsViaFacade(id);
        }
        return sampleCommandService.getStatusLogs(id, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<SampleVO> actionSample(
            UUID id, SampleActionRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        if (isRoutingEnabled()) {
            assertSampleExistsViaFacade(id);
        }
        return sampleCommandService.actionSample(id, request, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<Void> deleteSample(
            UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        if (isRoutingEnabled()) {
            assertSampleExistsViaFacade(id);
        }
        return sampleCommandService.deleteSample(id, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<SampleLogisticsVO> syncLogistics(
            UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        if (isRoutingEnabled()) {
            assertSampleExistsViaFacade(id);
        }
        return sampleCommandService.syncLogistics(id, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<SampleLogisticsVO> repairLogistics(
            UUID id,
            SampleLogisticsRepairRequest request,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes) {
        if (isRoutingEnabled()) {
            assertSampleExistsViaFacade(id);
        }
        return sampleCommandService.repairLogistics(id, request, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<SampleVO> refreshLogistics(
            UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        if (isRoutingEnabled()) {
            assertSampleExistsViaFacade(id);
        }
        return sampleCommandService.refreshLogistics(id, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<Map<String, Integer>> syncAllLogistics(Object roleCodes) {
        return sampleCommandService.syncAllLogistics(roleCodes);
    }

    public void downloadLogisticsImportTemplate(HttpServletResponse response) throws IOException {
        sampleCommandService.downloadLogisticsImportTemplate(response);
    }

    public ApiResult<LogisticsImportResult> importLogisticsTracking(
            MultipartFile file, boolean allowOverwrite, UUID userId, Object roleCodes) {
        return sampleCommandService.importLogisticsTracking(file, allowOverwrite, userId, roleCodes);
    }

    public ApiResult<Map<String, Integer>> batchApprove(
            SampleBatchActionRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleCommandService.batchApprove(request, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<Map<String, Integer>> batchReject(
            SampleBatchActionRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleCommandService.batchReject(request, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<Map<String, Integer>> batchShip(
            SampleBatchShipRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleCommandService.batchShip(request, userId, deptId, dataScope, roleCodes);
    }

    public void exportSamples(
            String status, String keyword, List<UUID> channelUserIds, UUID recruiterUserId,
            String productKeyword, String shopKeyword, String trackingNo, String requestNo, String talentKeyword,
            String cooperationType, String sampleOwnerType, String homeworkType, String recipientName,
            String recipientPhone, LocalDateTime applyStartTime, LocalDateTime applyEndTime,
            LocalDateTime homeworkStartTime, LocalDateTime homeworkEndTime, String logisticsCompany,
            UUID userId, UUID deptId, DataScope dataScope, Object roleCodes,
            HttpServletResponse response) throws IOException {
        sampleCommandService.exportSamples(
                status, keyword, channelUserIds, recruiterUserId, productKeyword, shopKeyword, trackingNo,
                requestNo, talentKeyword, cooperationType, sampleOwnerType, homeworkType, recipientName,
                recipientPhone, applyStartTime, applyEndTime, homeworkStartTime, homeworkEndTime, logisticsCompany,
                userId, deptId, dataScope, roleCodes, response);
    }

    private void assertSampleExistsViaFacade(UUID sampleRequestId) {
        if (sampleRequestId == null || !sampleDomainFacade.existsById(sampleRequestId)) {
            throw BusinessException.notFound("Sample request not found");
        }
    }
}
