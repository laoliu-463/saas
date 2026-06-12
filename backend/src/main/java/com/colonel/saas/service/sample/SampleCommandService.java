package com.colonel.saas.service.sample;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.ApiResult;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.dto.SampleApplyRequest;
import com.colonel.saas.dto.sample.LogisticsImportResult;
import com.colonel.saas.dto.sample.SampleActionRequest;
import com.colonel.saas.dto.sample.SampleBatchActionRequest;
import com.colonel.saas.dto.sample.SampleBatchShipRequest;
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

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 寄样状态变更命令服务接口（DDD-SAMPLE-005-FIX）。
 * <p>仅包含会产生状态变更的写操作，查询操作见 {@link SampleQueryService}。
 */
public interface SampleCommandService {

    ApiResult<SampleVO> createSample(SampleApplyRequest request, UUID userId, Object roleCodes);

    ApiResult<SampleEligibilityCheckVO> checkEligibility(SampleApplyRequest request, Object roleCodes);

    ApiResult<PageResult<SampleTalentVO>> searchTalents(SampleTalentQueryRequest request);

    ApiResult<PageResult<SampleProductVO>> searchProducts(long page, long size, String keyword, Object roleCodes);

    ApiResult<List<SampleStatusTransitionVO>> getStatusTransitions();

    ApiResult<List<StatusLogVO>> getStatusLogs(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes);

    ApiResult<SampleVO> actionSample(UUID id, SampleActionRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes);

    ApiResult<Void> deleteSample(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes);

    ApiResult<SampleLogisticsVO> syncLogistics(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes);

    ApiResult<SampleVO> refreshLogistics(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes);

    ApiResult<Map<String, Integer>> syncAllLogistics(Object roleCodes);

    void downloadLogisticsImportTemplate(HttpServletResponse response) throws IOException;

    ApiResult<LogisticsImportResult> importLogisticsTracking(
            org.springframework.web.multipart.MultipartFile file,
            boolean allowOverwrite,
            UUID userId,
            Object roleCodes);

    ApiResult<Map<String, Integer>> batchApprove(SampleBatchActionRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes);

    ApiResult<Map<String, Integer>> batchReject(SampleBatchActionRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes);

    ApiResult<Map<String, Integer>> batchShip(SampleBatchShipRequest request, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes);

    void exportSamples(
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
            HttpServletResponse response) throws IOException;
}