package com.colonel.saas.domain.sample.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.common.enums.SampleStatus;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.sample.policy.SampleCooperationActionPolicy;
import com.colonel.saas.domain.sample.policy.SampleRemarkPolicy;
import com.colonel.saas.domain.talent.facade.TalentDomainFacade;
import com.colonel.saas.domain.talent.facade.dto.TalentClaimAddressDTO;
import com.colonel.saas.domain.talent.facade.dto.TalentComplaintRiskDTO;
import com.colonel.saas.domain.talent.facade.dto.TalentReadDTO;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionChecker;
import com.colonel.saas.domain.user.policy.CurrentUserPermissionPolicy;
import com.colonel.saas.dto.sample.SampleCooperationUpdateRequest;
import com.colonel.saas.dto.sample.SamplePrivateNoteRequest;
import com.colonel.saas.entity.SamplePrivateNote;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SamplePrivateNoteMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.vo.sample.SampleEditContextVO;
import com.colonel.saas.vo.sample.SamplePrivateNoteVO;
import com.colonel.saas.vo.sample.SampleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleCooperationApplicationServiceTest {

    @Mock
    private SampleQueryApplicationService sampleQueryApplicationService;
    @Mock
    private SampleRequestMapper sampleRequestMapper;
    @Mock
    private SamplePrivateNoteMapper samplePrivateNoteMapper;
    @Mock
    private TalentDomainFacade talentDomainFacade;

    private SampleCooperationApplicationService service;

    @BeforeEach
    void setUp() {
        CurrentUserPermissionChecker checker =
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy());
        service = new SampleCooperationApplicationService(
                sampleQueryApplicationService,
                sampleRequestMapper,
                samplePrivateNoteMapper,
                talentDomainFacade,
                new SampleCooperationActionPolicy(checker),
                new SampleRemarkPolicy());
    }

    @Test
    void getEditContext_shouldUseSampleOwnerAddressAndNeverFallbackToSampleAddress() {
        UUID sampleId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        SampleRequest sample = sample(sampleId, ownerId, talentId, SampleStatus.PENDING_AUDIT, 3);
        sample.setRecipientName("历史收件人");
        sample.setRecipientPhone("13800000000");
        sample.setRecipientAddress("历史样本地址");
        sample.setExtraData(new LinkedHashMap<>(Map.of(
                "applyReason", "  当前申请原因  ",
                "specification", "红色 / M")));
        SampleVO visible = visibleSample(sample);
        visible.setProductExternalId("P-100");
        visible.setProductName("测试商品");
        visible.setShopName("测试店铺");
        visible.setActivityId("HISTORICAL-ACTIVITY-001");

        when(sampleQueryApplicationService.getSampleById(
                sampleId, viewerId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF)))
                .thenReturn(visible);
        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(talentDomainFacade.findTalentById(talentId)).thenReturn(new TalentReadDTO(
                talentId, "uid-1", "douyin-1", "达人甲", 1000L, 1,
                null, null, null, null, 88L));
        when(talentDomainFacade.findActiveClaimAddress(talentId, ownerId)).thenReturn(null);

        SampleEditContextVO context = service.getEditContext(
                sampleId, viewerId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(context.addressAvailable()).isFalse();
        assertThat(context.recipientName()).isNull();
        assertThat(context.recipientPhone()).isNull();
        assertThat(context.recipientAddress()).isNull();
        assertThat(context.remark()).isEqualTo("当前申请原因");
        assertThat(context.productSpecification()).isEqualTo("红色 / M");
        assertThat(context.talentDouyinNo()).isEqualTo("douyin-1");
        assertThat(context.talentWindowSales30d()).isEqualTo(88L);
        assertThat(context.sampleThreshold()).isNull();
        assertThat(context.activityId()).isEqualTo("HISTORICAL-ACTIVITY-001");
        assertThat(context.activityName()).isNull();
        assertThat(context.version()).isEqualTo(3);
        InOrder visibilityOrder = inOrder(
                sampleQueryApplicationService, sampleRequestMapper, talentDomainFacade);
        visibilityOrder.verify(sampleQueryApplicationService).getSampleById(
                sampleId, viewerId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF));
        visibilityOrder.verify(sampleRequestMapper).selectById(sampleId);
        visibilityOrder.verify(talentDomainFacade).findTalentById(talentId);
        visibilityOrder.verify(talentDomainFacade).findActiveClaimAddress(talentId, ownerId);
        verify(sampleRequestMapper, never()).updateById(any(SampleRequest.class));
        verify(talentDomainFacade, never()).findActiveClaimAddress(talentId, viewerId);
    }

    @Test
    void getEditContext_shouldOnlyMarkCompleteTrimmedOwnerAddressAvailable() {
        UUID sampleId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        SampleRequest sample = sample(sampleId, ownerId, talentId, SampleStatus.PENDING_AUDIT, 1);
        sample.setRecipientName("不得回退的样本收件人");
        sample.setRecipientPhone("13100000000");
        sample.setRecipientAddress("不得回退的样本地址");
        SampleVO visible = visibleSample(sample);

        when(sampleQueryApplicationService.getSampleById(
                sampleId, ownerId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF)))
                .thenReturn(visible);
        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(talentDomainFacade.findActiveClaimAddress(talentId, ownerId)).thenReturn(
                new TalentClaimAddressDTO(talentId, ownerId, null, "13800000000", "完整地址"),
                new TalentClaimAddressDTO(talentId, ownerId, "   ", "13800000000", "完整地址"),
                new TalentClaimAddressDTO(talentId, ownerId, "半地址", "13800000000", null),
                new TalentClaimAddressDTO(talentId, ownerId, "  完整收件人  ", "  13800000000  ", "  完整地址  "));

        SampleEditContextVO missingName = service.getEditContext(
                sampleId, ownerId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF));
        SampleEditContextVO blankName = service.getEditContext(
                sampleId, ownerId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF));
        SampleEditContextVO partialAddress = service.getEditContext(
                sampleId, ownerId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF));
        SampleEditContextVO completeAddress = service.getEditContext(
                sampleId, ownerId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(missingName.addressAvailable()).isFalse();
        assertThat(blankName.addressAvailable()).isFalse();
        assertThat(partialAddress.addressAvailable()).isFalse();
        assertThat(completeAddress.addressAvailable()).isTrue();
        assertThat(completeAddress.recipientName()).isEqualTo("完整收件人");
        assertThat(completeAddress.recipientPhone()).isEqualTo("13800000000");
        assertThat(completeAddress.recipientAddress()).isEqualTo("完整地址");
        assertThat(missingName.recipientName()).isNotEqualTo("不得回退的样本收件人");
        assertThat(partialAddress.recipientAddress()).isNotEqualTo("不得回退的样本地址");
    }

    @Test
    void updateCooperationDetails_shouldUpdateSampleBeforeOwnerClaimAddressAndPreserveExtraData() {
        UUID sampleId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        SampleRequest sample = sample(sampleId, ownerId, talentId, SampleStatus.SHIPPING, 4);
        sample.setExtraData(new LinkedHashMap<>(Map.of("preserved", "yes", "applyReason", "旧原因")));
        when(sampleQueryApplicationService.getSampleById(
                sampleId, ownerId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF)))
                .thenReturn(visibleSample(sample));
        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(sampleRequestMapper.updateById(any(SampleRequest.class))).thenReturn(1);
        when(talentDomainFacade.findActiveClaimAddress(talentId, ownerId)).thenReturn(
                new TalentClaimAddressDTO(talentId, ownerId, "新收件人", "13900000000", "新地址"));

        service.updateCooperationDetails(
                sampleId,
                new SampleCooperationUpdateRequest(4, "  新原因  ", "新收件人", "13900000000", "新地址"),
                ownerId,
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(sample.getVersion()).isEqualTo(4);
        assertThat(sample.getRemark()).isEqualTo("新原因");
        assertThat(sample.getExtraData()).containsEntry("applyReason", "新原因").containsEntry("preserved", "yes");
        assertThat(sample.getRecipientName()).isEqualTo("新收件人");
        InOrder order = inOrder(sampleRequestMapper, talentDomainFacade);
        order.verify(sampleRequestMapper).updateById(sample);
        order.verify(talentDomainFacade).updateActiveClaimAddress(
                talentId, ownerId, "新收件人", "13900000000", "新地址");
    }

    @Test
    void updateCooperationDetails_shouldReturnConflictBeforeAddressUpdateWhenVersionIsStale() {
        UUID sampleId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        SampleRequest sample = sample(sampleId, ownerId, UUID.randomUUID(), SampleStatus.PENDING_SHIP, 8);
        when(sampleQueryApplicationService.getSampleById(
                sampleId, ownerId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF)))
                .thenReturn(visibleSample(sample));
        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(sampleRequestMapper.updateById(any(SampleRequest.class))).thenReturn(0);

        assertThatThrownBy(() -> service.updateCooperationDetails(
                sampleId,
                new SampleCooperationUpdateRequest(7, "原因", "收件人", "13700000000", "地址"),
                ownerId,
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(409));

        verify(talentDomainFacade, never()).updateActiveClaimAddress(any(), any(), any(), any(), any());
    }

    @Test
    void updateCooperationDetails_shouldRejectSystemOwnedTerminalStatus() {
        UUID sampleId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        SampleRequest sample = sample(sampleId, ownerId, UUID.randomUUID(), SampleStatus.COMPLETED, 1);
        when(sampleQueryApplicationService.getSampleById(
                sampleId, ownerId, null, DataScope.ALL, List.of(RoleCodes.ADMIN)))
                .thenReturn(visibleSample(sample));
        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);

        assertThatThrownBy(() -> service.updateCooperationDetails(
                sampleId,
                new SampleCooperationUpdateRequest(1, "原因", null, null, null),
                ownerId,
                null,
                DataScope.ALL,
                List.of(RoleCodes.ADMIN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不允许编辑");

        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    void updateCooperationDetails_shouldRejectVisibleNonOwnerWithoutAdminRole() {
        UUID sampleId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        SampleRequest sample = sample(sampleId, ownerId, UUID.randomUUID(), SampleStatus.PENDING_AUDIT, 1);
        when(sampleQueryApplicationService.getSampleById(
                sampleId, viewerId, null, DataScope.DEPT, List.of(RoleCodes.BIZ_STAFF)))
                .thenReturn(visibleSample(sample));
        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);

        assertThatThrownBy(() -> service.updateCooperationDetails(
                sampleId,
                new SampleCooperationUpdateRequest(1, "原因", null, null, null),
                viewerId,
                null,
                DataScope.DEPT,
                List.of(RoleCodes.BIZ_STAFF)))
                .isInstanceOf(com.colonel.saas.common.exception.ForbiddenException.class)
                .hasMessageContaining("仅申请人或管理员");

        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    void updateCooperationDetails_shouldRejectPartialAddressBeforePersistence() {
        UUID sampleId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        SampleRequest sample = sample(sampleId, ownerId, UUID.randomUUID(), SampleStatus.PENDING_AUDIT, 1);
        when(sampleQueryApplicationService.getSampleById(
                sampleId, ownerId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF)))
                .thenReturn(visibleSample(sample));
        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);

        assertThatThrownBy(() -> service.updateCooperationDetails(
                sampleId,
                new SampleCooperationUpdateRequest(1, "原因", "收件人", null, "地址"),
                ownerId,
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须同时填写");

        verify(sampleRequestMapper, never()).updateById(any());
        verify(talentDomainFacade, never()).updateActiveClaimAddress(any(), any(), any(), any(), any());
    }

    @Test
    void updateCooperationDetails_shouldRollbackSpringTransactionWhenOwnerAddressUpdateFails() throws Exception {
        UUID sampleId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        SampleRequest sample = sample(sampleId, ownerId, talentId, SampleStatus.PENDING_AUDIT, 2);
        when(sampleQueryApplicationService.getSampleById(
                sampleId, ownerId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF)))
                .thenReturn(visibleSample(sample));
        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(sampleRequestMapper.updateById(any())).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("claim update failed"))
                .when(talentDomainFacade)
                .updateActiveClaimAddress(talentId, ownerId, "收件人", "13600000000", "地址");

        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        TransactionInterceptor transactionInterceptor = new TransactionInterceptor(
                transactionManager,
                new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(service);
        proxyFactory.addAdvice(transactionInterceptor);
        SampleCooperationApplicationService transactionalService =
                (SampleCooperationApplicationService) proxyFactory.getProxy();

        assertThatThrownBy(() -> transactionalService.updateCooperationDetails(
                sampleId,
                new SampleCooperationUpdateRequest(2, "原因", "收件人", "13600000000", "地址"),
                ownerId,
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("claim update failed");

        Method method = SampleCooperationApplicationService.class.getMethod(
                "updateCooperationDetails",
                UUID.class,
                SampleCooperationUpdateRequest.class,
                UUID.class,
                UUID.class,
                DataScope.class,
                Object.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
        assertThat(transactionManager.beginCount).isEqualTo(1);
        assertThat(transactionManager.rollbackCount).isEqualTo(1);
        assertThat(transactionManager.commitCount).isZero();
        verify(sampleRequestMapper).updateById(sample);
        verify(talentDomainFacade).updateActiveClaimAddress(
                talentId, ownerId, "收件人", "13600000000", "地址");
    }

    @Test
    void privateNote_shouldAlwaysUseCurrentUserEvenForAdminAndBlankShouldSoftDelete() {
        UUID sampleId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        SampleRequest sample = sample(sampleId, ownerId, UUID.randomUUID(), SampleStatus.PENDING_AUDIT, 1);
        when(sampleQueryApplicationService.getSampleById(
                sampleId, adminId, null, DataScope.ALL, List.of(RoleCodes.ADMIN)))
                .thenReturn(visibleSample(sample));
        SamplePrivateNote note = new SamplePrivateNote();
        note.setId(UUID.randomUUID());
        note.setSampleRequestId(sampleId);
        note.setUserId(adminId);
        note.setContent("仅管理员本人可见");
        note.setVersion(5);
        SamplePrivateNoteMapper atomicMapper = mock(SamplePrivateNoteMapper.class, invocation ->
                invocation.getMethod().getReturnType() == int.class
                        ? 1
                        : org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation));
        when(atomicMapper.selectBySampleRequestAndUser(sampleId, adminId)).thenReturn(note);
        SampleCooperationApplicationService atomicService = serviceWithPrivateNoteMapper(atomicMapper);

        SamplePrivateNoteVO read = atomicService.getPrivateNote(
                sampleId, adminId, null, DataScope.ALL, List.of(RoleCodes.ADMIN));
        assertThat(read.content()).isEqualTo("仅管理员本人可见");
        assertThat(read.version()).isEqualTo(5);

        SamplePrivateNoteVO deleted = atomicService.updatePrivateNote(
                sampleId,
                new SamplePrivateNoteRequest("   "),
                adminId,
                null,
                DataScope.ALL,
                List.of(RoleCodes.ADMIN));
        assertThat(deleted.content()).isNull();
        assertThat(deleted.version()).isEqualTo(6);
        assertThat(note.getDeleted()).isZero();
        List<String> writeMethods = org.mockito.Mockito.mockingDetails(atomicMapper).getInvocations().stream()
                .map(invocation -> invocation.getMethod().getName())
                .toList();
        assertThat(writeMethods).contains("softDeleteActive").doesNotContain("updateById");
        verify(atomicMapper, times(2)).selectBySampleRequestAndUser(sampleId, adminId);
        verify(atomicMapper, never()).selectBySampleRequestAndUser(sampleId, ownerId);
    }

    @Test
    void updatePrivateNote_shouldTrimAndUpdateOnlyCurrentUsersActiveNote() {
        UUID sampleId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        SampleVO visible = new SampleVO();
        visible.setId(sampleId);
        when(sampleQueryApplicationService.getSampleById(
                sampleId, currentUserId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF)))
                .thenReturn(visible);
        SamplePrivateNote note = new SamplePrivateNote();
        note.setId(UUID.randomUUID());
        note.setSampleRequestId(sampleId);
        note.setUserId(currentUserId);
        note.setContent("旧备注");
        note.setVersion(2);
        SamplePrivateNoteMapper atomicMapper = mock(SamplePrivateNoteMapper.class, invocation ->
                invocation.getMethod().getReturnType() == int.class
                        ? 1
                        : org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation));
        note.setContent("新备注");
        note.setVersion(3);
        when(atomicMapper.selectBySampleRequestAndUser(sampleId, currentUserId)).thenReturn(note);
        SampleCooperationApplicationService atomicService = serviceWithPrivateNoteMapper(atomicMapper);

        SamplePrivateNoteVO updated = atomicService.updatePrivateNote(
                sampleId,
                new SamplePrivateNoteRequest("  新备注  "),
                currentUserId,
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.CHANNEL_STAFF));

        assertThat(updated.content()).isEqualTo("新备注");
        assertThat(note.getContent()).isEqualTo("新备注");
        assertThat(note.getDeleted()).isZero();
        List<String> writeMethods = org.mockito.Mockito.mockingDetails(atomicMapper).getInvocations().stream()
                .map(invocation -> invocation.getMethod().getName())
                .toList();
        assertThat(writeMethods).contains("upsertActive").doesNotContain("insert", "updateById");
    }

    @Test
    void updatePrivateNote_shouldUpsertTrimmedNoteForCurrentUser() {
        UUID sampleId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        SampleVO visible = new SampleVO();
        visible.setId(sampleId);
        when(sampleQueryApplicationService.getSampleById(
                sampleId, currentUserId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF)))
                .thenReturn(visible);
        SamplePrivateNote persisted = new SamplePrivateNote();
        persisted.setId(UUID.randomUUID());
        persisted.setSampleRequestId(sampleId);
        persisted.setUserId(currentUserId);
        persisted.setContent("首次备注");
        persisted.setVersion(0);
        when(samplePrivateNoteMapper.upsertActive(any(SamplePrivateNote.class))).thenReturn(1);
        when(samplePrivateNoteMapper.selectBySampleRequestAndUser(sampleId, currentUserId))
                .thenReturn(persisted);

        SamplePrivateNoteVO created = service.updatePrivateNote(
                sampleId,
                new SamplePrivateNoteRequest("  首次备注  "),
                currentUserId,
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.CHANNEL_STAFF));

        org.mockito.ArgumentCaptor<SamplePrivateNote> captor =
                org.mockito.ArgumentCaptor.forClass(SamplePrivateNote.class);
        verify(samplePrivateNoteMapper).upsertActive(captor.capture());
        assertThat(captor.getValue().getSampleRequestId()).isEqualTo(sampleId);
        assertThat(captor.getValue().getUserId()).isEqualTo(currentUserId);
        assertThat(captor.getValue().getContent()).isEqualTo("首次备注");
        assertThat(captor.getValue().getCreateBy()).isEqualTo(currentUserId);
        assertThat(captor.getValue().getUpdateBy()).isEqualTo(currentUserId);
        assertThat(created.content()).isEqualTo("首次备注");
        verify(samplePrivateNoteMapper, never()).insert(any(SamplePrivateNote.class));
        verify(samplePrivateNoteMapper, never()).updateById(any(SamplePrivateNote.class));
    }

    @Test
    void updatePrivateNote_shouldNotExposeContentInFailureAndServiceHasNoLoggingDependency() {
        UUID sampleId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        String sensitiveContent = "仅当前用户可见-敏感正文";
        SampleVO visible = new SampleVO();
        visible.setId(sampleId);
        when(sampleQueryApplicationService.getSampleById(
                sampleId, currentUserId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF)))
                .thenReturn(visible);
        assertThatThrownBy(() -> service.updatePrivateNote(
                sampleId,
                new SamplePrivateNoteRequest(sensitiveContent),
                currentUserId,
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain(sensitiveContent));
        verify(samplePrivateNoteMapper).upsertActive(any(SamplePrivateNote.class));

        List<String> dependencyTypes = java.util.Arrays.stream(
                        SampleCooperationApplicationService.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .toList();
        assertThat(dependencyTypes).noneMatch(type ->
                type.contains("OperationLog") || type.contains("Logger"));
    }

    @Test
    void updatePrivateNote_shouldMapAtomicSoftDeleteMissToConflict() {
        UUID sampleId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        SampleVO visible = new SampleVO();
        visible.setId(sampleId);
        when(sampleQueryApplicationService.getSampleById(
                sampleId, currentUserId, null, DataScope.PERSONAL, List.of(RoleCodes.CHANNEL_STAFF)))
                .thenReturn(visible);
        SamplePrivateNote note = new SamplePrivateNote();
        note.setId(UUID.randomUUID());
        note.setSampleRequestId(sampleId);
        note.setUserId(currentUserId);
        note.setVersion(4);
        when(samplePrivateNoteMapper.selectBySampleRequestAndUser(sampleId, currentUserId)).thenReturn(note);

        assertThatThrownBy(() -> service.updatePrivateNote(
                sampleId,
                new SamplePrivateNoteRequest(" "),
                currentUserId,
                null,
                DataScope.PERSONAL,
                List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(409));

        assertThat(org.mockito.Mockito.mockingDetails(samplePrivateNoteMapper).getInvocations().stream()
                .map(invocation -> invocation.getMethod().getName()))
                .contains("softDeleteActive");
    }

    @Test
    void unifiedSampleApplicationService_shouldKeepLegacyAndSpringConstructorsUnambiguous() throws Exception {
        Class<com.colonel.saas.domain.sample.application.SampleApplicationService> serviceType =
                com.colonel.saas.domain.sample.application.SampleApplicationService.class;
        var legacy = serviceType.getConstructor(
                SampleQueryApplicationService.class,
                SampleCommandApplicationService.class);
        var spring = serviceType.getConstructor(
                SampleQueryApplicationService.class,
                SampleCommandApplicationService.class,
                SampleCooperationActionPolicy.class,
                TalentDomainFacade.class,
                SampleCooperationApplicationService.class);

        assertThat(serviceType.getConstructors()).hasSize(2);
        assertThat(legacy.getAnnotation(Autowired.class)).isNull();
        assertThat(spring.getAnnotation(Autowired.class)).isNotNull();
    }

    @Test
    void unifiedSampleApplicationService_shouldEnrichListAndDetailWithActionsAndComplaintRisk() {
        SampleQueryApplicationService queryService = mock(SampleQueryApplicationService.class);
        SampleCommandApplicationService commandService = mock(SampleCommandApplicationService.class);
        TalentDomainFacade riskFacade = mock(TalentDomainFacade.class);
        SampleCooperationApplicationService cooperationService =
                mock(SampleCooperationApplicationService.class);
        CurrentUserPermissionChecker checker =
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy());
        com.colonel.saas.domain.sample.application.SampleApplicationService unifiedService =
                new com.colonel.saas.domain.sample.application.SampleApplicationService(
                        queryService,
                        commandService,
                        new SampleCooperationActionPolicy(checker),
                        riskFacade,
                        cooperationService);
        UUID currentUserId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        Object roles = List.of(RoleCodes.CHANNEL_STAFF);
        SampleVO listRow = sampleView(UUID.randomUUID(), currentUserId, talentId, "PENDING_AUDIT");
        SampleVO detail = sampleView(UUID.randomUUID(), currentUserId, talentId, "SHIPPED");
        PageResult<SampleVO> page = new PageResult<>();
        page.setRecords(List.of(listRow));
        TalentComplaintRiskDTO risk = new TalentComplaintRiskDTO(
                talentId, 2L, LocalDateTime.of(2026, 7, 16, 12, 0));
        when(riskFacade.loadComplaintRisks(List.of(talentId))).thenReturn(Map.of(talentId, risk));
        when(queryService.getSamplePage(
                1L, 20L, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                currentUserId, null, DataScope.PERSONAL, roles))
                .thenReturn(page);
        when(queryService.getSampleById(
                detail.getId(), currentUserId, null, DataScope.PERSONAL, roles))
                .thenReturn(detail);

        PageResult<SampleVO> listed = unifiedService.getSamplePage(
                1L, 20L, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                currentUserId, null, DataScope.PERSONAL, roles);
        SampleVO loadedDetail = unifiedService.getSampleById(
                detail.getId(), currentUserId, null, DataScope.PERSONAL, roles);

        assertActionEnvelope(listed.getRecords().get(0), risk);
        assertActionEnvelope(loadedDetail, risk);
    }

    private static void assertActionEnvelope(SampleVO sample, TalentComplaintRiskDTO risk) {
        assertThat(new java.util.ArrayList<>(sample.getActionAvailability().keySet())).containsExactly(
                "APPROVE", "REJECT", "EDIT", "PROGRESS",
                "COPY_LINK", "COPY_ORDER", "COMPLAIN", "NOTE");
        assertThat(sample.getComplaintRisk()).isEqualTo(risk);
    }

    private SampleCooperationApplicationService serviceWithPrivateNoteMapper(
            SamplePrivateNoteMapper privateNoteMapper) {
        CurrentUserPermissionChecker checker =
                new CurrentUserPermissionChecker(new CurrentUserPermissionPolicy());
        return new SampleCooperationApplicationService(
                sampleQueryApplicationService,
                sampleRequestMapper,
                privateNoteMapper,
                talentDomainFacade,
                new SampleCooperationActionPolicy(checker),
                new SampleRemarkPolicy());
    }

    private static SampleVO sampleView(
            UUID sampleId, UUID ownerId, UUID talentId, String status) {
        SampleVO sample = new SampleVO();
        sample.setId(sampleId);
        sample.setApplicantUserId(ownerId);
        sample.setTalentId(talentId);
        sample.setStatus(status);
        return sample;
    }

    private static SampleRequest sample(
            UUID sampleId,
            UUID ownerId,
            UUID talentId,
            SampleStatus status,
            int version) {
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setUserId(ownerId);
        sample.setTalentId(talentId);
        sample.setProductId(UUID.randomUUID());
        sample.setExpectedSampleNum(1);
        sample.setStatus(status.getCode());
        sample.setVersion(version);
        return sample;
    }

    private static SampleVO visibleSample(SampleRequest sample) {
        SampleVO visible = new SampleVO();
        visible.setId(sample.getId());
        visible.setApplicantUserId(sample.getUserId());
        visible.setTalentId(sample.getTalentId());
        visible.setProductId(sample.getProductId());
        visible.setQuantity(sample.getExpectedSampleNum());
        visible.setVersion(sample.getVersion());
        visible.setStatus(sample.getStatus() == SampleStatus.SHIPPING.getCode()
                ? "SHIPPED"
                : SampleStatus.fromCode(sample.getStatus()).getApiStatus());
        return visible;
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private int beginCount;
        private int commitCount;
        private int rollbackCount;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            beginCount++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount++;
        }
    }
}
