package com.colonel.saas.domain.sample.infrastructure;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.sample.application.port.SampleExportQueryPort;
import com.colonel.saas.service.sample.SampleQueryService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class LegacySampleExportQueryAdapter implements SampleExportQueryPort {
    private final SampleQueryService sampleQueryService;

    public LegacySampleExportQueryAdapter(@Lazy SampleQueryService sampleQueryService) {
        this.sampleQueryService = sampleQueryService;
    }

    @Override
    public void exportSamples(String status, String keyword, List<UUID> channelUserIds, UUID recruiterUserId,
            String productKeyword, String shopKeyword, String trackingNo, String requestNo, String talentKeyword,
            String cooperationType, String sampleOwnerType, String homeworkType, String recipientName,
            String recipientPhone, LocalDateTime applyStartTime, LocalDateTime applyEndTime,
            LocalDateTime homeworkStartTime, LocalDateTime homeworkEndTime, String logisticsCompany,
            UUID userId, UUID deptId, DataScope dataScope, Object roleCodes, HttpServletResponse response)
            throws IOException {
        sampleQueryService.exportSamples(status, keyword, channelUserIds, recruiterUserId, productKeyword, shopKeyword,
                trackingNo, requestNo, talentKeyword, cooperationType, sampleOwnerType, homeworkType, recipientName,
                recipientPhone, applyStartTime, applyEndTime, homeworkStartTime, homeworkEndTime, logisticsCompany,
                userId, deptId, dataScope, roleCodes, response);
    }
}
