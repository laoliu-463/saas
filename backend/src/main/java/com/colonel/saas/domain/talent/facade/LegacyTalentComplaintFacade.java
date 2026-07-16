package com.colonel.saas.domain.talent.facade;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.talent.application.TalentComplaintApplicationService;
import com.colonel.saas.domain.talent.facade.dto.TalentComplaintRiskDTO;
import com.colonel.saas.dto.talent.TalentComplaintCreateRequest;
import com.colonel.saas.dto.talent.TalentComplaintRiskRequest;
import com.colonel.saas.vo.talent.TalentComplaintReminderVO;
import com.colonel.saas.vo.talent.TalentComplaintVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 现阶段模块化单体内的投诉门面实现。 */
@Service
public class LegacyTalentComplaintFacade implements TalentComplaintFacade {

    private final TalentComplaintApplicationService applicationService;

    public LegacyTalentComplaintFacade(TalentComplaintApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public TalentComplaintVO create(
            UUID sampleRequestId,
            UUID talentId,
            UUID productId,
            UUID reporterUserId,
            TalentComplaintCreateRequest request,
            List<? extends MultipartFile> files) {
        return applicationService.create(
                sampleRequestId, talentId, productId, reporterUserId, request, files);
    }

    @Override
    public List<TalentComplaintRiskDTO> loadRisks(
            TalentComplaintRiskRequest request,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes) {
        return applicationService.loadRisks(request, userId, deptId, dataScope, roleCodes);
    }

    @Override
    public TalentComplaintVO getDetail(UUID complaintId, Object roleCodes) {
        return applicationService.getDetail(complaintId, roleCodes);
    }

    @Override
    public long unreadCount(UUID recipientUserId, Object roleCodes) {
        return applicationService.unreadCount(recipientUserId, roleCodes);
    }

    @Override
    public List<TalentComplaintReminderVO> listReminders(
            UUID recipientUserId,
            Object roleCodes,
            LocalDateTime beforeCreateTime,
            UUID beforeId,
            int limit) {
        return applicationService.listReminders(
                recipientUserId, roleCodes, beforeCreateTime, beforeId, limit);
    }

    @Override
    public TalentComplaintReminderVO markReminderRead(
            UUID reminderId,
            UUID recipientUserId,
            Object roleCodes) {
        return applicationService.markReminderRead(reminderId, recipientUserId, roleCodes);
    }

    @Override
    public AttachmentDownload downloadAttachment(
            UUID complaintId,
            UUID attachmentId,
            Object roleCodes) {
        return applicationService.downloadAttachment(complaintId, attachmentId, roleCodes);
    }
}
