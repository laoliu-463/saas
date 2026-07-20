package com.colonel.saas.domain.sample.application.port;

import com.colonel.saas.common.enums.DataScope;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SampleExportQueryPort {
    void exportSamples(
            String status, String keyword, List<UUID> channelUserIds, UUID recruiterUserId,
            String productKeyword, String shopKeyword, String trackingNo, String requestNo,
            String talentKeyword, String cooperationType, String sampleOwnerType, String homeworkType,
            String recipientName, String recipientPhone, LocalDateTime applyStartTime, LocalDateTime applyEndTime,
            LocalDateTime homeworkStartTime, LocalDateTime homeworkEndTime, String logisticsCompany,
            UUID userId, UUID deptId, DataScope dataScope, Object roleCodes,
            HttpServletResponse response) throws IOException;
}
