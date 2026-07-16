package com.colonel.saas.domain.talent.application;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.talent.infrastructure.ComplaintAttachmentStorage;
import com.colonel.saas.domain.talent.policy.ComplaintImagePolicy;
import com.colonel.saas.domain.talent.policy.TalentComplaintPolicy;
import com.colonel.saas.domain.user.port.UserRoleRecipientLookup;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.dto.talent.TalentComplaintCreateRequest;
import com.colonel.saas.dto.talent.TalentComplaintRiskRequest;
import com.colonel.saas.entity.TalentComplaint;
import com.colonel.saas.entity.TalentComplaintAttachment;
import com.colonel.saas.entity.TalentComplaintReminder;
import com.colonel.saas.mapper.TalentComplaintAttachmentMapper;
import com.colonel.saas.mapper.TalentComplaintMapper;
import com.colonel.saas.mapper.TalentComplaintReminderMapper;
import com.colonel.saas.service.OperationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentComplaintApplicationServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private TalentComplaintMapper complaintMapper;
    @Mock
    private TalentComplaintAttachmentMapper attachmentMapper;
    @Mock
    private TalentComplaintReminderMapper reminderMapper;
    @Mock
    private UserRoleRecipientLookup recipientLookup;
    @Mock
    private ComplaintAttachmentStorage storage;
    @Mock
    private OperationLogService operationLogService;

    private TalentComplaintApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TalentComplaintApplicationService(
                complaintMapper,
                attachmentMapper,
                reminderMapper,
                recipientLookup,
                new TalentComplaintPolicy(),
                new ComplaintImagePolicy(),
                storage,
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()),
                operationLogService);
    }

    @Test
    void create_shouldValidateAllBusinessInputBeforeAnyWrite() {
        UUID sampleId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(
                sampleId, talentId, productId, reporterId,
                new TalentComplaintCreateRequest("UNKNOWN", "内容"), List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("投诉原因");

        assertThatThrownBy(() -> service.create(
                sampleId, talentId, productId, reporterId,
                new TalentComplaintCreateRequest("OTHER", "   "), List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("投诉内容");

        assertThatThrownBy(() -> service.create(
                sampleId, talentId, productId, reporterId,
                new TalentComplaintCreateRequest("LOW_PRICE_RESALE", "😀".repeat(201)), List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("200");

        verifyNoInteractions(complaintMapper, attachmentMapper, reminderMapper, recipientLookup, storage);
    }

    @Test
    void create_shouldTrimContentByUnicodeCodePointPersistAttachmentsAndDeduplicateRecipients() {
        UUID sampleId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID recipientA = UUID.randomUUID();
        UUID recipientB = UUID.randomUUID();
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
        MockMultipartFile file = new MockMultipartFile(
                "files", "proof.jpg", "image/jpeg", jpeg);
        when(complaintMapper.insert(any(TalentComplaint.class))).thenReturn(1);
        when(storage.store(any())).thenReturn(new ComplaintAttachmentStorage.StoredAttachment(
                "ab/random.jpg", "proof.jpg", "image/jpeg", 4L, "a".repeat(64)));
        when(attachmentMapper.insert(any(TalentComplaintAttachment.class))).thenReturn(1);
        when(recipientLookup.findActiveUserIdsByRoleCodes(any())).thenReturn(
                List.of(recipientA, recipientA, recipientB));
        when(reminderMapper.insert(any(TalentComplaintReminder.class))).thenReturn(1);

        var result = service.create(
                sampleId, talentId, productId, reporterId,
                new TalentComplaintCreateRequest(
                        "REPEATED_NO_FULFILLMENT", "  " + "😀".repeat(200) + "  "),
                List.of(file));

        assertThat(result.sampleRequestId()).isEqualTo(sampleId);
        assertThat(result.talentId()).isEqualTo(talentId);
        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.content()).isEqualTo("😀".repeat(200));
        assertThat(result.attachments()).hasSize(1);

        ArgumentCaptor<TalentComplaint> complaintCaptor = ArgumentCaptor.forClass(TalentComplaint.class);
        verify(complaintMapper).insert(complaintCaptor.capture());
        assertThat(complaintCaptor.getValue().getReporterUserId()).isEqualTo(reporterId);
        assertThat(complaintCaptor.getValue().getContent()).isEqualTo("😀".repeat(200));

        ArgumentCaptor<TalentComplaintReminder> reminderCaptor =
                ArgumentCaptor.forClass(TalentComplaintReminder.class);
        verify(reminderMapper, org.mockito.Mockito.times(2)).insert(reminderCaptor.capture());
        assertThat(reminderCaptor.getAllValues())
                .extracting(TalentComplaintReminder::getRecipientUserId)
                .containsExactly(recipientA, recipientB);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> rolesCaptor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(recipientLookup).findActiveUserIdsByRoleCodes(rolesCaptor.capture());
        assertThat(rolesCaptor.getValue()).containsExactlyInAnyOrder(
                RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.CHANNEL_LEADER);

        verify(operationLogService).recordSystemAction(
                eq(reporterId), eq("TALENT"), eq("COMPLAINT"), eq("POST"),
                eq("TalentComplaint"), anyString(), eq(talentId.toString()),
                eq("提交达人投诉"));
    }

    @Test
    void create_shouldStopBeforeDatabaseWritesWhenFileValidationFails() {
        MockMultipartFile fake = new MockMultipartFile(
                "files", "fake.jpg", "image/jpeg",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        assertThatThrownBy(() -> service.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new TalentComplaintCreateRequest("LOW_PRICE_RESALE", null), List.of(fake)))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(complaintMapper, attachmentMapper, reminderMapper, recipientLookup, storage);
    }

    @Test
    void create_shouldNotInsertAttachmentOrReminderWhenStorageFails() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "proof.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01});
        when(complaintMapper.insert(any(TalentComplaint.class))).thenReturn(1);
        when(storage.store(any())).thenThrow(new IllegalStateException("disk full"));

        assertThatThrownBy(() -> service.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new TalentComplaintCreateRequest("LOW_PRICE_RESALE", null), List.of(file)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disk full");

        verify(attachmentMapper, never()).insert(any());
        verify(reminderMapper, never()).insert(any());
        verifyNoInteractions(recipientLookup);
    }

    @Test
    void create_shouldDeleteStoredFileWhenDatabaseTransactionRollsBack() throws Exception {
        ComplaintAttachmentStorage realStorage = new ComplaintAttachmentStorage(tempDir);
        TalentComplaintApplicationService rollbackService = new TalentComplaintApplicationService(
                complaintMapper,
                attachmentMapper,
                reminderMapper,
                recipientLookup,
                new TalentComplaintPolicy(),
                new ComplaintImagePolicy(),
                realStorage,
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy()),
                operationLogService);
        MockMultipartFile file = new MockMultipartFile(
                "files", "proof.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01});
        when(complaintMapper.insert(any(TalentComplaint.class))).thenReturn(1);
        when(attachmentMapper.insert(any(TalentComplaintAttachment.class)))
                .thenThrow(new IllegalStateException("attachment insert failed"));

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> rollbackService.create(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    new TalentComplaintCreateRequest("LOW_PRICE_RESALE", null), List.of(file)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("attachment insert failed");

            assertThat(countRegularFiles(tempDir)).isEqualTo(1L);
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            synchronizations.forEach(synchronization -> synchronization.afterCompletion(
                    TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(countRegularFiles(tempDir)).isZero();
            verifyNoInteractions(recipientLookup);
            verify(reminderMapper, never()).insert(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void detailAndAttachment_shouldRequireLeaderRole() {
        UUID complaintId = UUID.randomUUID();

        assertThatThrownBy(() -> service.getDetail(
                complaintId, List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.downloadAttachment(
                complaintId, UUID.randomUUID(), List.of(RoleCodes.BIZ_STAFF)))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(complaintMapper, attachmentMapper, storage);
    }

    @Test
    void risks_shouldRejectMoreThanOneHundredIdsBeforeQueryAndExposeOnlySummary() {
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            ids.add(UUID.randomUUID());
        }

        assertThatThrownBy(() -> service.loadRisks(new TalentComplaintRiskRequest(ids)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("100");
        verifyNoInteractions(complaintMapper);
    }

    @Test
    void reminders_shouldBeRecipientScopedAndMarkReadConditionally() {
        UUID recipientId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        UUID complaintId = UUID.randomUUID();
        TalentComplaintReminder reminder = new TalentComplaintReminder();
        reminder.setId(reminderId);
        reminder.setComplaintId(complaintId);
        reminder.setRecipientUserId(recipientId);
        reminder.setCreateTime(LocalDateTime.now());
        when(reminderMapper.markRead(eq(reminderId), eq(recipientId), any(LocalDateTime.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.markReminderRead(
                reminderId, recipientId, List.of(RoleCodes.BIZ_LEADER)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("提醒");

        verify(reminderMapper).markRead(eq(reminderId), eq(recipientId), any(LocalDateTime.class));
        verify(reminderMapper, never()).updateById(any());
        verify(complaintMapper, never()).selectById(any());
    }

    @Test
    void reminderQueries_shouldAlwaysUseCurrentRecipientAndRejectOrdinaryUsers() {
        UUID recipientId = UUID.randomUUID();
        when(reminderMapper.countUnreadByRecipientUserId(recipientId)).thenReturn(3L);
        when(reminderMapper.selectPageByRecipientUserId(recipientId, null, null, 20))
                .thenReturn(List.of());

        assertThat(service.unreadCount(recipientId, List.of(RoleCodes.CHANNEL_LEADER)))
                .isEqualTo(3L);
        assertThat(service.listReminders(
                recipientId, List.of(RoleCodes.CHANNEL_LEADER), null, null, 20))
                .isEmpty();

        verify(reminderMapper).countUnreadByRecipientUserId(recipientId);
        verify(reminderMapper).selectPageByRecipientUserId(recipientId, null, null, 20);

        assertThatThrownBy(() -> service.unreadCount(
                UUID.randomUUID(), List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_shouldKeepStrictWriteOrder() {
        when(complaintMapper.insert(any())).thenReturn(1);
        when(recipientLookup.findActiveUserIdsByRoleCodes(any())).thenReturn(List.of());

        service.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new TalentComplaintCreateRequest("LOW_PRICE_RESALE", null), List.of());

        InOrder order = inOrder(complaintMapper, recipientLookup, operationLogService);
        order.verify(complaintMapper).insert(any());
        order.verify(recipientLookup).findActiveUserIdsByRoleCodes(any());
        order.verify(operationLogService).recordSystemAction(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static long countRegularFiles(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }
}
