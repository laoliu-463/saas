package com.colonel.saas.service.sample;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.vo.sample.SampleVO;
import com.colonel.saas.vo.sample.SampleBoardCard;
import com.colonel.saas.vo.sample.SampleLogisticsVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 寄样查询服务实现：委托给 {@link SampleApplicationService} 执行实际查询。
 *
 * <p>当前实现采用委托模式，将所有查询操作委托给独立的
 * {@code sampleQueryApplicationDelegate} Bean（见 {@link SampleQueryConfiguration}），
 * 避免委托回 {@link com.colonel.saas.controller.SampleController} 覆盖的查询方法而形成运行时循环。
 * 后续可逐步将查询逻辑从 SampleApplicationService 迁移到本服务中，实现真正的逻辑分离。
 *
 * <p>设计原则：
 * <ul>
 *   <li>只读：本服务中的方法不应产生任何状态变更</li>
 *   <li>不变性：字段、筛选条件、导出列顺序保持与原接口完全一致</li>
 *   <li>兼容性：保持原有 API 路径、输入参数、输出字段不变</li>
 * </ul>
 *
 * @see SampleApplicationService
 * @see SampleQueryService
 */
@Service
public class LegacySampleQueryService implements SampleQueryService {

    private final SampleApplicationService sampleApplicationService;

    public LegacySampleQueryService(
            @Qualifier("sampleQueryApplicationDelegate") SampleApplicationService sampleApplicationService) {
        this.sampleApplicationService = sampleApplicationService;
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
        return sampleApplicationService.getSamplePage(
                page, size, keyword, status, channelUserIds, recruiterUserId,
                productKeyword, shopKeyword, trackingNo, requestNo, talentKeyword,
                cooperationType, sampleOwnerType, homeworkType, recipientName,
                recipientPhone, applyStartTime, applyEndTime, homeworkStartTime,
                homeworkEndTime, logisticsCompany, userId, deptId, dataScope, roleCodes
        ).getData();
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
        return sampleApplicationService.getSamplePage(
                page, size, keyword, status, userId, deptId, dataScope, roleCodes
        ).getData();
    }

    @Override
    public SampleVO getSampleById(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleApplicationService.getSampleById(id, userId, deptId, dataScope, roleCodes).getData();
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

    @Override
    public Map<String, List<SampleBoardCard>> getSampleBoard(UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleApplicationService.getSampleBoard(userId, deptId, dataScope, roleCodes).getData();
    }

    @Override
    public SampleLogisticsVO getSampleLogistics(UUID id, UUID userId, UUID deptId, DataScope dataScope, Object roleCodes) {
        return sampleApplicationService.getSampleLogistics(id, userId, deptId, dataScope, roleCodes).getData();
    }
}
