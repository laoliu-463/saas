package com.colonel.saas.domain.sample.infrastructure;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.domain.sample.application.port.SamplePageQueryPort;
import com.colonel.saas.service.sample.SampleQueryService;
import com.colonel.saas.vo.sample.SampleVO;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 寄样列表查询的 Legacy 适配器。
 *
 * <p>先把应用层入口与旧查询实现解耦，后续可在不改 HTTP 合同的前提下替换列表读模型。</p>
 */
@Service
public class LegacySamplePageQueryAdapter implements SamplePageQueryPort {

    private final SampleQueryService sampleQueryService;

    public LegacySamplePageQueryAdapter(@Lazy SampleQueryService sampleQueryService) {
        this.sampleQueryService = sampleQueryService;
    }

    @Override
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

    @Override
    public PageResult<SampleVO> getSamplePage(
            long page,
            long size,
            String keyword,
            String status,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes) {
        return sampleQueryService.getSamplePage(page, size, keyword, status, userId, deptId, dataScope, roleCodes);
    }
}
