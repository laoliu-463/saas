package com.colonel.saas.domain.talent.application;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.talent.facade.TalentComplaintFacade;
import com.colonel.saas.domain.talent.facade.dto.TalentComplaintRiskDTO;
import com.colonel.saas.domain.talent.infrastructure.ComplaintAttachmentStorage;
import com.colonel.saas.domain.talent.policy.ComplaintImagePolicy;
import com.colonel.saas.domain.talent.policy.TalentComplaintPolicy;
import com.colonel.saas.domain.talent.port.TalentVisibilityLookup;
import com.colonel.saas.domain.user.port.UserRoleRecipientLookup;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.dto.talent.TalentComplaintCreateRequest;
import com.colonel.saas.dto.talent.TalentComplaintRiskRequest;
import com.colonel.saas.entity.TalentComplaint;
import com.colonel.saas.entity.TalentComplaintAttachment;
import com.colonel.saas.entity.TalentComplaintReminder;
import com.colonel.saas.mapper.TalentComplaintAttachmentMapper;
import com.colonel.saas.mapper.TalentComplaintMapper;
import com.colonel.saas.mapper.TalentComplaintReminderMapper;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.vo.talent.TalentComplaintReminderVO;
import com.colonel.saas.vo.talent.TalentComplaintVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 达人投诉、风险摘要和接收人提醒用例。 */
@Service
public class TalentComplaintApplicationService {

    private static final List<String> REMINDER_ROLES = List.of(
            RoleCodes.ADMIN,
            RoleCodes.BIZ_LEADER,
            RoleCodes.CHANNEL_LEADER);

    private final TalentComplaintMapper complaintMapper;
    private final TalentComplaintAttachmentMapper attachmentMapper;
    private final TalentComplaintReminderMapper reminderMapper;
    private final UserRoleRecipientLookup recipientLookup;
    private final TalentComplaintPolicy complaintPolicy;
    private final ComplaintImagePolicy imagePolicy;
    private final ComplaintAttachmentStorage storage;
    private final CurrentUserPermissionChecker permissionChecker;
    private final OperationLogService operationLogService;
    private final TalentVisibilityLookup talentVisibilityLookup;

    public TalentComplaintApplicationService(
            TalentComplaintMapper complaintMapper,
            TalentComplaintAttachmentMapper attachmentMapper,
            TalentComplaintReminderMapper reminderMapper,
            UserRoleRecipientLookup recipientLookup,
            TalentComplaintPolicy complaintPolicy,
            ComplaintImagePolicy imagePolicy,
            ComplaintAttachmentStorage storage,
            CurrentUserPermissionChecker permissionChecker,
            OperationLogService operationLogService,
            TalentVisibilityLookup talentVisibilityLookup) {
        this.complaintMapper = complaintMapper;
        this.attachmentMapper = attachmentMapper;
        this.reminderMapper = reminderMapper;
        this.recipientLookup = recipientLookup;
        this.complaintPolicy = complaintPolicy;
        this.imagePolicy = imagePolicy;
        this.storage = storage;
        this.permissionChecker = permissionChecker;
        this.operationLogService = operationLogService;
        this.talentVisibilityLookup = talentVisibilityLookup;
    }

    @Transactional(rollbackFor = Exception.class)
    public TalentComplaintVO create(
            UUID sampleRequestId,
            UUID talentId,
            UUID productId,
            UUID reporterUserId,
            TalentComplaintCreateRequest request,
            List<? extends MultipartFile> files) {
        requireCreateContext(sampleRequestId, talentId, productId, reporterUserId);
        if (request == null) {
            throw BusinessException.param("投诉请求不能为空");
        }

        // 所有可预见业务校验必须发生在首个数据库/文件写入之前。
        TalentComplaintPolicy.ValidatedComplaint validatedComplaint =
                complaintPolicy.validate(request.reason(), request.content());
        List<ComplaintImagePolicy.ValidatedImage> validatedImages = imagePolicy.validate(files);

        List<String> storedKeys = new ArrayList<>();
        try {
            TalentComplaint complaint = new TalentComplaint();
            complaint.setId(UUID.randomUUID());
            complaint.setSampleRequestId(sampleRequestId);
            complaint.setTalentId(talentId);
            complaint.setProductId(productId);
            complaint.setReporterUserId(reporterUserId);
            complaint.setReasonCode(validatedComplaint.reasonCode());
            complaint.setContent(validatedComplaint.content());
            complaint.setStatus("SUBMITTED");
            complaint.setVersion(0);
            complaint.setCreateBy(reporterUserId);
            complaint.setUpdateBy(reporterUserId);
            requireInserted(complaintMapper.insert(complaint), "投诉提交失败，请重试");

            List<TalentComplaintAttachment> attachments = new ArrayList<>();
            for (ComplaintImagePolicy.ValidatedImage image : validatedImages) {
                ComplaintAttachmentStorage.StoredAttachment stored = storage.store(image);
                storedKeys.add(stored.storageKey());
                registerRollbackCleanup(stored.storageKey());

                TalentComplaintAttachment attachment = new TalentComplaintAttachment();
                attachment.setId(UUID.randomUUID());
                attachment.setComplaintId(complaint.getId());
                attachment.setStorageKey(stored.storageKey());
                attachment.setOriginalName(stored.originalName());
                attachment.setContentType(stored.contentType());
                attachment.setFileSize(stored.fileSize());
                attachment.setSha256(stored.sha256());
                attachment.setCreateBy(reporterUserId);
                attachment.setUpdateBy(reporterUserId);
                requireInserted(attachmentMapper.insert(attachment), "投诉附件保存失败，请重试");
                attachments.add(attachment);
            }

            List<UUID> recipients = distinctRecipients(
                    recipientLookup.findActiveUserIdsByRoleCodes(REMINDER_ROLES));
            for (UUID recipientUserId : recipients) {
                TalentComplaintReminder reminder = new TalentComplaintReminder();
                reminder.setId(UUID.randomUUID());
                reminder.setComplaintId(complaint.getId());
                reminder.setRecipientUserId(recipientUserId);
                reminder.setVersion(0);
                reminder.setCreateBy(reporterUserId);
                reminder.setUpdateBy(reporterUserId);
                requireInserted(reminderMapper.insert(reminder), "投诉提醒创建失败，请重试");
            }

            operationLogService.recordSystemAction(
                    reporterUserId,
                    "TALENT",
                    "COMPLAINT",
                    "POST",
                    "TalentComplaint",
                    complaint.getId().toString(),
                    talentId.toString(),
                    "提交达人投诉");
            return toComplaintVO(complaint, attachments);
        } catch (RuntimeException exception) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                storedKeys.forEach(storage::deleteQuietly);
            }
            throw exception;
        }
    }

    public List<TalentComplaintRiskDTO> loadRisks(
            TalentComplaintRiskRequest request,
            UUID userId,
            UUID deptId,
            DataScope dataScope,
            Object roleCodes) {
        if (request == null || request.talentIds() == null || request.talentIds().isEmpty()) {
            throw BusinessException.param("talentIds 不能为空");
        }
        if (request.talentIds().size() > 100) {
            throw BusinessException.param("talentIds 最多 100 个");
        }
        List<UUID> talentIds = request.talentIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (talentIds.isEmpty()) {
            throw BusinessException.param("talentIds 不能为空");
        }
        boolean unrestricted = dataScope == DataScope.ALL
                || permissionChecker.hasAnyRole(roleCodes, RoleCodes.ADMIN);
        List<UUID> visibleTalentIds = talentVisibilityLookup.retainVisibleTalentIds(
                talentIds, userId, deptId, dataScope, unrestricted);
        if (visibleTalentIds == null || visibleTalentIds.isEmpty()) {
            return List.of();
        }
        List<TalentComplaintMapper.TalentRiskSummary> summaries =
                complaintMapper.selectRiskSummariesByTalentIds(visibleTalentIds);
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }
        return summaries.stream()
                .filter(Objects::nonNull)
                .filter(summary -> summary.talentId() != null)
                .map(summary -> new TalentComplaintRiskDTO(
                        summary.talentId(),
                        summary.complaintCount(),
                        summary.latestComplaintAt()))
                .toList();
    }

    public TalentComplaintVO getDetail(UUID complaintId, Object roleCodes) {
        ensureLeader(roleCodes);
        TalentComplaint complaint = requireComplaint(complaintId);
        List<TalentComplaintAttachment> attachments = attachmentMapper.selectByComplaintId(complaintId);
        return toComplaintVO(complaint, attachments == null ? List.of() : attachments);
    }

    public long unreadCount(UUID recipientUserId, Object roleCodes) {
        ensureLeader(roleCodes);
        requireUser(recipientUserId);
        return reminderMapper.countUnreadByRecipientUserId(recipientUserId);
    }

    public List<TalentComplaintReminderVO> listReminders(
            UUID recipientUserId,
            Object roleCodes,
            LocalDateTime beforeCreateTime,
            UUID beforeId,
            int limit) {
        ensureLeader(roleCodes);
        requireUser(recipientUserId);
        if ((beforeCreateTime == null) != (beforeId == null)) {
            throw BusinessException.param("beforeCreateTime 与 beforeId 必须同时提供");
        }
        if (limit < 1 || limit > 100) {
            throw BusinessException.param("limit 必须在 1 到 100 之间");
        }
        List<TalentComplaintReminder> reminders = reminderMapper.selectPageByRecipientUserId(
                recipientUserId, beforeCreateTime, beforeId, limit);
        return toReminderVOs(reminders);
    }

    @Transactional(rollbackFor = Exception.class)
    public TalentComplaintReminderVO markReminderRead(
            UUID reminderId,
            UUID recipientUserId,
            Object roleCodes) {
        ensureLeader(roleCodes);
        requireUser(recipientUserId);
        if (reminderId == null) {
            throw BusinessException.notFound("投诉提醒不存在");
        }
        LocalDateTime readAt = LocalDateTime.now();
        if (reminderMapper.markRead(reminderId, recipientUserId, readAt) != 1) {
            throw BusinessException.notFound("投诉提醒不存在或已读");
        }
        TalentComplaintReminder reminder =
                reminderMapper.selectByIdAndRecipientUserId(reminderId, recipientUserId);
        if (reminder == null) {
            throw BusinessException.notFound("投诉提醒不存在");
        }
        TalentComplaint complaint = complaintMapper.selectById(reminder.getComplaintId());
        return toReminderVO(reminder, complaint);
    }

    public TalentComplaintFacade.AttachmentDownload downloadAttachment(
            UUID complaintId,
            UUID attachmentId,
            Object roleCodes) {
        ensureLeader(roleCodes);
        requireComplaint(complaintId);
        TalentComplaintAttachment attachment =
                attachmentMapper.selectByIdAndComplaintId(attachmentId, complaintId);
        if (attachment == null) {
            throw BusinessException.notFound("投诉附件不存在");
        }
        byte[] content = storage.load(attachment.getStorageKey());
        return new TalentComplaintFacade.AttachmentDownload(
                attachment.getOriginalName(),
                attachment.getContentType(),
                content);
    }

    private void requireCreateContext(
            UUID sampleRequestId, UUID talentId, UUID productId, UUID reporterUserId) {
        if (sampleRequestId == null || talentId == null || productId == null || reporterUserId == null) {
            throw BusinessException.param("合作单、达人、商品和投诉人不能为空");
        }
    }

    private void requireUser(UUID userId) {
        if (userId == null) {
            throw new ForbiddenException("未识别当前用户");
        }
    }

    private void ensureLeader(Object roleCodes) {
        if (!permissionChecker.hasAnyRole(
                roleCodes, RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER)) {
            throw new ForbiddenException("仅管理员、招商组长或渠道组长可访问投诉详情与提醒");
        }
    }

    private TalentComplaint requireComplaint(UUID complaintId) {
        if (complaintId == null) {
            throw BusinessException.notFound("投诉记录不存在");
        }
        TalentComplaint complaint = complaintMapper.selectById(complaintId);
        if (complaint == null) {
            throw BusinessException.notFound("投诉记录不存在");
        }
        return complaint;
    }

    private void requireInserted(int affected, String message) {
        if (affected != 1) {
            throw BusinessException.conflict(message);
        }
    }

    private List<UUID> distinctRecipients(Collection<UUID> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(recipients.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }

    private void registerRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storage.deleteQuietly(storageKey);
                }
            }
        });
    }

    private TalentComplaintVO toComplaintVO(
            TalentComplaint complaint,
            List<TalentComplaintAttachment> attachments) {
        List<TalentComplaintVO.AttachmentVO> attachmentVOs = attachments.stream()
                .map(attachment -> new TalentComplaintVO.AttachmentVO(
                        attachment.getId(),
                        attachment.getOriginalName(),
                        attachment.getContentType(),
                        attachment.getFileSize() == null ? 0L : attachment.getFileSize(),
                        attachment.getSha256()))
                .toList();
        return new TalentComplaintVO(
                complaint.getId(),
                complaint.getSampleRequestId(),
                complaint.getTalentId(),
                complaint.getProductId(),
                complaint.getReporterUserId(),
                complaint.getReasonCode(),
                complaint.getContent(),
                complaint.getStatus(),
                attachmentVOs,
                complaint.getCreateTime());
    }

    private List<TalentComplaintReminderVO> toReminderVOs(List<TalentComplaintReminder> reminders) {
        if (reminders == null || reminders.isEmpty()) {
            return List.of();
        }
        List<UUID> complaintIds = reminders.stream()
                .map(TalentComplaintReminder::getComplaintId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<TalentComplaint> complaints = complaintIds.isEmpty()
                ? List.of()
                : complaintMapper.selectBatchIds(complaintIds);
        Map<UUID, TalentComplaint> byId = new LinkedHashMap<>();
        if (complaints != null) {
            complaints.stream()
                    .filter(Objects::nonNull)
                    .filter(complaint -> complaint.getId() != null)
                    .forEach(complaint -> byId.putIfAbsent(complaint.getId(), complaint));
        }
        return reminders.stream()
                .map(reminder -> toReminderVO(reminder, byId.get(reminder.getComplaintId())))
                .toList();
    }

    private TalentComplaintReminderVO toReminderVO(
            TalentComplaintReminder reminder,
            TalentComplaint complaint) {
        return new TalentComplaintReminderVO(
                reminder.getId(),
                reminder.getComplaintId(),
                complaint == null ? null : complaint.getTalentId(),
                complaint == null ? null : complaint.getReasonCode(),
                reminder.getReadAt() != null,
                reminder.getCreateTime(),
                reminder.getReadAt());
    }
}
