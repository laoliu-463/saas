package com.colonel.saas.domain.sample.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.enums.SampleStatus;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.dto.SampleApplyRequest;
import com.colonel.saas.dto.SampleTalentQueryRequest;
import com.colonel.saas.dto.sample.LogisticsImportResult;
import com.colonel.saas.dto.sample.SampleActionRequest;
import com.colonel.saas.dto.sample.SampleBatchActionRequest;
import com.colonel.saas.dto.sample.SampleBatchShipRequest;
import com.colonel.saas.dto.sample.SampleCooperationUpdateRequest;
import com.colonel.saas.dto.sample.SamplePrivateNoteRequest;
import com.colonel.saas.domain.sample.policy.SampleCooperationActionPolicy;
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
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 寄样统一应用服务（DDD-SAMPLE-001）。
 * <p>HTTP 入口只依赖本服务；内部继续委派已拆分的命令/查询应用服务，保持行为兼容。</p>
 */
@Service
public class SampleApplicationService {

    private final SampleQueryApplicationService sampleQueryApplicationService;
    private final SampleCommandApplicationService sampleCommandApplicationService;
    private final SampleCooperationActionPolicy cooperationActionPolicy;
    private final SampleCooperationApplicationService sampleCooperationApplicationService;

    /** 保留给现有单元测试和兼容调用方的构造器。 */
    public SampleApplicationService(
            SampleQueryApplicationService sampleQueryApplicationService,
            SampleCommandApplicationService sampleCommandApplicationService) {
        this(sampleQueryApplicationService, sampleCommandApplicationService, null, null);
    }

    /** Spring 运行路径使用带权限策略和合作单能力的完整构造器。 */
    @Autowired
    public SampleApplicationService(
            SampleQueryApplicationService sampleQueryApplicationService,
            SampleCommandApplicationService sampleCommandApplicationService,
            SampleCooperationActionPolicy cooperationActionPolicy,
            SampleCooperationApplicationService sampleCooperationApplicationService) {
        this.sampleQueryApplicationService = sampleQueryApplicationService;
        this.sampleCommandApplicationService = sampleCommandApplicationService;
        this.cooperationActionPolicy = cooperationActionPolicy;
        this.sampleCooperationApplicationService = sampleCooperationApplicationService;
    }

    public ApiResult<SampleVO> createSample(SampleApplyRequest request, UUID userId, Object roleCodes) {
        ApiResult<SampleVO> result = sampleCommandApplicationService.createSample(request, userId, roleCodes);
        enrichOne(result == null ? null : result.getData(), userId, roleCodes);
        return result;
    }

    public ApiResult<SampleEligibilityCheckVO> checkEligibility(SampleApplyRequest request, Object roleCodes) {
        return sampleCommandApplicationService.checkEligibility(request, roleCodes);
    }

    public PageResult<SampleVO> getSamplePage(
            long page,
            long size,
            String keyword,
            String status,
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
            Object roleCodes) {
        PageResult<SampleVO> result = sampleQueryApplicationService.getSamplePage(
                page, size, keyword, status, channelUserIds, recruiterUserId, productKeyword, shopKeyword,
                trackingNo, requestNo, talentKeyword, cooperationType, sampleOwnerType, homeworkType,
                recipientName, recipientPhone, applyStartTime, applyEndTime, homeworkStartTime, homeworkEndTime,
                logisticsCompany, userId, deptId, dataScope, roleCodes);
        enrichMany(result == null ? null : result.getRecords(), userId, roleCodes);
        return result;
    }

    public ApiResult<PageResult<SampleTalentVO>> searchTalents(SampleTalentQueryRequest request) {
        return sampleCommandApplicationService.searchTalents(request);
    }

    public ApiResult<PageResult<SampleProductVO>> searchProducts(
            long page, long size, String keyword, Object roleCodes) {
        return sampleCommandApplicationService.searchProducts(page, size, keyword, roleCodes);
    }

    public Map<String, List<SampleBoardCard>> getSampleBoard(
            UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleQueryApplicationService.getSampleBoard(userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<List<SampleStatusTransitionVO>> getStatusTransitions() {
        return sampleCommandApplicationService.getStatusTransitions();
    }

    public SampleVO getSampleById(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        SampleVO sample = sampleQueryApplicationService.getSampleById(id, userId, deptId, dataScope, roleCodes);
        enrichMany(sample == null ? List.of() : List.of(sample), userId, roleCodes);
        return sample;
    }

    public SampleEditContextVO getEditContext(
            UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleCooperationApplicationService.getEditContext(id, userId, deptId, dataScope, roleCodes);
    }

    public SampleEditContextVO updateCooperationDetails(
            UUID id,
            SampleCooperationUpdateRequest request,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes) {
        return sampleCooperationApplicationService.updateCooperationDetails(
                id, request, userId, deptId, dataScope, roleCodes);
    }

    public SamplePrivateNoteVO getPrivateNote(
            UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleCooperationApplicationService.getPrivateNote(id, userId, deptId, dataScope, roleCodes);
    }

    public SamplePrivateNoteVO updatePrivateNote(
            UUID id,
            SamplePrivateNoteRequest request,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes) {
        return sampleCooperationApplicationService.updatePrivateNote(
                id, request, userId, deptId, dataScope, roleCodes);
    }

    public SampleCopyTextVO copyPromotion(
            UUID id,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes,
            String idempotencyKey) {
        if (sampleCooperationApplicationService == null) {
            throw com.colonel.saas.common.exception.BusinessException.stateInvalid(
                    "商品推广复制能力不可用，请检查应用服务装配");
        }
        return sampleCooperationApplicationService.copyPromotion(
                id, userId, deptId, dataScope, roleCodes, idempotencyKey);
    }

    public SampleCopyTextVO copyOrder(
            UUID id,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes) {
        if (sampleCooperationApplicationService == null) {
            throw com.colonel.saas.common.exception.BusinessException.stateInvalid(
                    "订单复制能力不可用，请检查应用服务装配");
        }
        return sampleCooperationApplicationService.copyOrder(
                id, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<List<StatusLogVO>> getStatusLogs(
            UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleCommandApplicationService.getStatusLogs(id, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<SampleVO> actionSample(
            UUID id, SampleActionRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        ApiResult<SampleVO> result = sampleCommandApplicationService.actionSample(
                id, request, userId, deptId, dataScope, roleCodes);
        enrichOne(result == null ? null : result.getData(), userId, roleCodes);
        return result;
    }

    public ApiResult<Void> deleteSample(
            UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleCommandApplicationService.deleteSample(id, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<SampleLogisticsVO> syncLogistics(
            UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleCommandApplicationService.syncLogistics(id, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<SampleVO> refreshLogistics(
            UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        ApiResult<SampleVO> result = sampleCommandApplicationService.refreshLogistics(
                id, userId, deptId, dataScope, roleCodes);
        enrichOne(result == null ? null : result.getData(), userId, roleCodes);
        return result;
    }

    public SampleLogisticsVO getSampleLogistics(
            UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleQueryApplicationService.getSampleLogistics(id, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<Map<String, Integer>> syncAllLogistics(Object roleCodes) {
        return sampleCommandApplicationService.syncAllLogistics(roleCodes);
    }

    public void downloadLogisticsImportTemplate(HttpServletResponse response) throws IOException {
        sampleCommandApplicationService.downloadLogisticsImportTemplate(response);
    }

    public ApiResult<LogisticsImportResult> importLogisticsTracking(
            MultipartFile file, boolean allowOverwrite, UUID userId, Object roleCodes) {
        return sampleCommandApplicationService.importLogisticsTracking(file, allowOverwrite, userId, roleCodes);
    }

    public ApiResult<Map<String, Integer>> batchApprove(
            SampleBatchActionRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleCommandApplicationService.batchApprove(request, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<Map<String, Integer>> batchReject(
            SampleBatchActionRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleCommandApplicationService.batchReject(request, userId, deptId, dataScope, roleCodes);
    }

    public ApiResult<Map<String, Integer>> batchShip(
            SampleBatchShipRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleCommandApplicationService.batchShip(request, userId, deptId, dataScope, roleCodes);
    }

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
        sampleQueryApplicationService.exportSamples(
                status, keyword, channelUserIds, recruiterUserId, productKeyword, shopKeyword, trackingNo, requestNo,
                talentKeyword, cooperationType, sampleOwnerType, homeworkType, recipientName, recipientPhone,
                applyStartTime, applyEndTime, homeworkStartTime, homeworkEndTime, logisticsCompany,
                userId, deptId, dataScope, roleCodes, response);
    }

    private void enrichMany(List<SampleVO> samples, UUID currentUserId, Object roleCodes) {
        if (samples == null || samples.isEmpty() || cooperationActionPolicy == null) {
            return;
        }
        for (SampleVO sample : samples) {
            enrichOne(sample, currentUserId, roleCodes);
        }
    }

    private void enrichOne(
            SampleVO sample,
            UUID currentUserId,
            Object roleCodes) {
        if (sample == null || cooperationActionPolicy == null) {
            return;
        }
        SampleStatus status = SampleStatus.fromApiStatus(sample.getStatus());
        sample.setActionAvailability(cooperationActionPolicy.availability(
                status,
                sample.getApplicantUserId(),
                currentUserId,
                roleCodes));
    }
}
