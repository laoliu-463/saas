package com.colonel.saas.domain.talent.facade;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.talent.facade.dto.TalentComplaintRiskDTO;
import com.colonel.saas.dto.talent.TalentComplaintCreateRequest;
import com.colonel.saas.dto.talent.TalentComplaintRiskRequest;
import com.colonel.saas.vo.talent.TalentComplaintReminderVO;
import com.colonel.saas.vo.talent.TalentComplaintVO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 达人投诉域公开门面。 */
public interface TalentComplaintFacade {

    TalentComplaintVO create(
            UUID sampleRequestId,
            UUID talentId,
            UUID productId,
            UUID reporterUserId,
            TalentComplaintCreateRequest request,
            List<? extends MultipartFile> files);

    List<TalentComplaintRiskDTO> loadRisks(
            TalentComplaintRiskRequest request,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes);

    TalentComplaintVO getDetail(UUID complaintId, Object roleCodes);

    long unreadCount(UUID recipientUserId, Object roleCodes);

    List<TalentComplaintReminderVO> listReminders(
            UUID recipientUserId,
            Object roleCodes,
            LocalDateTime beforeCreateTime,
            UUID beforeId,
            int limit);

    TalentComplaintReminderVO markReminderRead(
            UUID reminderId,
            UUID recipientUserId,
            Object roleCodes);

    AttachmentDownload downloadAttachment(
            UUID complaintId,
            UUID attachmentId,
            Object roleCodes);

    record AttachmentDownload(String originalName, String contentType, byte[] content) {
        public AttachmentDownload {
            content = content == null ? new byte[0] : content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
