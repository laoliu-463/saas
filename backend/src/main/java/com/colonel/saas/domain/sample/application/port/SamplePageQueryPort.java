package com.colonel.saas.domain.sample.application.port;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.vo.sample.SampleVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 寄样列表查询端口。
 *
 * <p>只承载列表分页查询的输入和结果，不包含状态流转、物流同步或订单事实处理。</p>
 */
public interface SamplePageQueryPort {

    PageResult<SampleVO> getSamplePage(
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
            Object roleCodes);

    PageResult<SampleVO> getSamplePage(
            long page,
            long size,
            String keyword,
            String status,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes);
}
