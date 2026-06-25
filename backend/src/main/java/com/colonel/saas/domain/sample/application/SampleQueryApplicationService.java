package com.colonel.saas.domain.sample.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.domain.sample.facade.SampleDomainFacade;
import com.colonel.saas.service.sample.SampleQueryService;
import com.colonel.saas.vo.sample.SampleBoardCard;
import com.colonel.saas.vo.sample.SampleLogisticsVO;
import com.colonel.saas.vo.sample.SampleVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 寄样查询应用层（DDD-SAMPLE-007 Batch3 Replace）。
 *
 * <p>开关 {@code ddd.refactor.sample-application.enabled=true} 且根开关开启时，
 * 按 ID 读路径先走 {@link SampleDomainFacade} 存在性检查，再委派 {@link SampleQueryService}。</p>
 */
@Service
public class SampleQueryApplicationService {

    private final SampleQueryService sampleQueryService;
    private final SampleDomainFacade sampleDomainFacade;
    private final DddRefactorProperties dddRefactorProperties;

    public SampleQueryApplicationService(
            SampleQueryService sampleQueryService,
            SampleDomainFacade sampleDomainFacade,
            DddRefactorProperties dddRefactorProperties) {
        this.sampleQueryService = sampleQueryService;
        this.sampleDomainFacade = sampleDomainFacade;
        this.dddRefactorProperties = dddRefactorProperties;
    }

    public boolean isRoutingEnabled() {
        return dddRefactorProperties.isEnabled()
                && dddRefactorProperties.getSampleApplication().isEnabled();
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
        return sampleQueryService.getSamplePage(
                page, size, keyword, status, channelUserIds, recruiterUserId, productKeyword, shopKeyword,
                trackingNo, requestNo, talentKeyword, cooperationType, sampleOwnerType, homeworkType,
                recipientName, recipientPhone, applyStartTime, applyEndTime, homeworkStartTime, homeworkEndTime,
                logisticsCompany, userId, deptId, dataScope, roleCodes);
    }

    public PageResult<SampleVO> getSamplePage(
            long page, long size, String keyword, String status,
            UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleQueryService.getSamplePage(page, size, keyword, status, userId, deptId, dataScope, roleCodes);
    }

    public SampleVO getSampleById(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        if (isRoutingEnabled()) {
            assertSampleExistsViaFacade(id);
        }
        return sampleQueryService.getSampleById(id, userId, deptId, dataScope, roleCodes);
    }

    public void exportSamples(
            String status, String keyword, List<UUID> channelUserIds, UUID recruiterUserId,
            String productKeyword, String shopKeyword, String trackingNo, String requestNo, String talentKeyword,
            String cooperationType, String sampleOwnerType, String homeworkType, String recipientName,
            String recipientPhone, LocalDateTime applyStartTime, LocalDateTime applyEndTime,
            LocalDateTime homeworkStartTime, LocalDateTime homeworkEndTime, String logisticsCompany,
            UUID userId, UUID deptId, DataScope dataScope, Object roleCodes,
            HttpServletResponse response) throws IOException {
        sampleQueryService.exportSamples(
                status, keyword, channelUserIds, recruiterUserId, productKeyword, shopKeyword, trackingNo,
                requestNo, talentKeyword, cooperationType, sampleOwnerType, homeworkType, recipientName,
                recipientPhone, applyStartTime, applyEndTime, homeworkStartTime, homeworkEndTime, logisticsCompany,
                userId, deptId, dataScope, roleCodes, response);
    }

    public Map<String, List<SampleBoardCard>> getSampleBoard(
            UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleQueryService.getSampleBoard(userId, deptId, dataScope, roleCodes);
    }

    public SampleLogisticsVO getSampleLogistics(
            UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        if (isRoutingEnabled()) {
            assertSampleExistsViaFacade(id);
        }
        return sampleQueryService.getSampleLogistics(id, userId, deptId, dataScope, roleCodes);
    }

    private void assertSampleExistsViaFacade(UUID sampleRequestId) {
        if (sampleRequestId == null || !sampleDomainFacade.existsById(sampleRequestId)) {
            throw BusinessException.notFound("Sample request not found");
        }
    }
}
