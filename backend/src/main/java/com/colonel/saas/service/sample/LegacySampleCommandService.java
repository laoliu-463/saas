package com.colonel.saas.service.sample;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.dto.SampleApplyRequest;
import com.colonel.saas.dto.sample.LogisticsImportResult;
import com.colonel.saas.dto.sample.SampleActionRequest;
import com.colonel.saas.dto.sample.SampleBatchActionRequest;
import com.colonel.saas.dto.sample.SampleBatchShipRequest;
import com.colonel.saas.dto.sample.SampleLogisticsRepairRequest;
import com.colonel.saas.dto.SampleTalentQueryRequest;
import com.colonel.saas.vo.SampleTalentVO;
import com.colonel.saas.vo.sample.SampleBoardCard;
import com.colonel.saas.vo.sample.SampleEligibilityCheckVO;
import com.colonel.saas.vo.sample.SampleLogisticsVO;
import com.colonel.saas.vo.sample.SampleProductVO;
import com.colonel.saas.vo.sample.SampleStatusTransitionVO;
import com.colonel.saas.vo.sample.SampleVO;
import com.colonel.saas.vo.sample.StatusLogVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 寄样命令服务实现：委托给 {@link SampleApplicationService} 执行实际状态变更操作。
 *
 * <p>当前实现采用委托模式，将所有写操作委托给现有的 {@link SampleApplicationService}。
 * 后续可逐步将命令逻辑从 SampleApplicationService 迁移到本服务中，实现真正的逻辑分离。
 *
 * <p>设计原则：
 * <ul>
 *   <li>只写：本服务中的方法应产生状态变更</li>
 *   <li>不变性：字段、筛选条件、导出列顺序保持与原接口完全一致</li>
 *   <li>兼容性：保持原有 API 路径、输入参数、输出字段不变</li>
 * </ul>
 *
 * @see SampleApplicationService
 * @see SampleCommandService
 */
@Service
public class LegacySampleCommandService implements SampleCommandService {

    private final SampleApplicationService sampleApplicationService;

    public LegacySampleCommandService(
            @Qualifier("sampleQueryApplicationDelegate") SampleApplicationService sampleApplicationService) {
        this.sampleApplicationService = sampleApplicationService;
    }

    @Override
    public ApiResult<SampleVO> createSample(SampleApplyRequest request, UUID userId, Object roleCodes) {
        return sampleApplicationService.createSample(request, userId, roleCodes);
    }

    @Override
    public ApiResult<SampleEligibilityCheckVO> checkEligibility(SampleApplyRequest request, Object roleCodes) {
        return sampleApplicationService.checkEligibility(request, roleCodes);
    }

    @Override
    public ApiResult<PageResult<SampleTalentVO>> searchTalents(SampleTalentQueryRequest request) {
        return sampleApplicationService.searchTalents(request);
    }

    @Override
    public ApiResult<PageResult<SampleProductVO>> searchProducts(long page, long size, String keyword, Object roleCodes) {
        return sampleApplicationService.searchProducts(page, size, keyword, roleCodes);
    }

    @Override
    public ApiResult<List<SampleStatusTransitionVO>> getStatusTransitions() {
        return sampleApplicationService.getStatusTransitions();
    }

    @Override
    public ApiResult<List<StatusLogVO>> getStatusLogs(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleApplicationService.getStatusLogs(id, userId, deptId, dataScope, roleCodes);
    }

    @Override
    public ApiResult<SampleVO> actionSample(UUID id, SampleActionRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleApplicationService.actionSample(id, request, userId, deptId, dataScope, roleCodes);
    }

    @Override
    public ApiResult<Void> deleteSample(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleApplicationService.deleteSample(id, userId, deptId, dataScope, roleCodes);
    }

    @Override
    public ApiResult<SampleLogisticsVO> syncLogistics(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleApplicationService.syncLogistics(id, userId, deptId, dataScope, roleCodes);
    }

    @Override
    public ApiResult<SampleLogisticsVO> repairLogistics(
            UUID id,
            SampleLogisticsRepairRequest request,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes) {
        return sampleApplicationService.repairLogistics(id, request, userId, deptId, dataScope, roleCodes);
    }

    @Override
    public ApiResult<SampleVO> refreshLogistics(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleApplicationService.refreshLogistics(id, userId, deptId, dataScope, roleCodes);
    }

    @Override
    public ApiResult<Map<String, Integer>> syncAllLogistics(Object roleCodes) {
        return sampleApplicationService.syncAllLogistics(roleCodes);
    }

    @Override
    public void downloadLogisticsImportTemplate(HttpServletResponse response) throws IOException {
        sampleApplicationService.downloadLogisticsImportTemplate(response);
    }

    @Override
    public ApiResult<LogisticsImportResult> importLogisticsTracking(
            org.springframework.web.multipart.MultipartFile file,
            boolean allowOverwrite,
            UUID userId,
            Object roleCodes) {
        return sampleApplicationService.importLogisticsTracking(file, allowOverwrite, userId, roleCodes);
    }

    @Override
    public ApiResult<Map<String, Integer>> batchApprove(SampleBatchActionRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleApplicationService.batchApprove(request, userId, deptId, dataScope, roleCodes);
    }

    @Override
    public ApiResult<Map<String, Integer>> batchReject(SampleBatchActionRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleApplicationService.batchReject(request, userId, deptId, dataScope, roleCodes);
    }

    @Override
    public ApiResult<Map<String, Integer>> batchShip(SampleBatchShipRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleApplicationService.batchShip(request, userId, deptId, dataScope, roleCodes);
    }

    @Override
    public void exportSamples(
            String status,
            String keyword,
            List<UUID> channelUserIds,
            UUID recruiterUserId,
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
            String logisticsCompany,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes,
            HttpServletResponse response) throws IOException {
        sampleApplicationService.exportSamples(
                status, keyword, channelUserIds, recruiterUserId,
                productKeyword, shopKeyword, trackingNo, requestNo,
                talentKeyword, cooperationType, sampleOwnerType, homeworkType,
                recipientName, recipientPhone, applyStartTime, applyEndTime,
                homeworkStartTime, homeworkEndTime, logisticsCompany,
                userId, deptId, dataScope, roleCodes, response
        );
    }
}
