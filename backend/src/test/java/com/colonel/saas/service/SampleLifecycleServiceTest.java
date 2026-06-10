package com.colonel.saas.service;

import com.colonel.saas.domain.config.facade.dto.SampleDefaultStandardDTO;
import com.colonel.saas.domain.config.facade.dto.SampleRulesDTO;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.entity.TalentClaim;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.TalentClaimMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleLifecycleServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SampleRequestMapper sampleRequestMapper;
    @Mock
    private SampleStatusLogService sampleStatusLogService;
    @Mock
    private com.colonel.saas.domain.config.facade.ConfigDomainFacade configDomainFacade;
    @Mock
    private TalentClaimMapper talentClaimMapper;

    private SampleLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new SampleLifecycleService(
                jdbcTemplate,
                sampleRequestMapper,
                talentClaimMapper,
                sampleStatusLogService,
                configDomainFacade,
                org.mockito.Mockito.mock(com.colonel.saas.domain.sample.event.SampleDomainEventPublisher.class));
        lenient().when(jdbcTemplate.batchUpdate(
                anyString(),
                anyList(),
                anyInt(),
                any(ParameterizedPreparedStatementSetter.class)))
                .thenAnswer(invocation -> {
                    List<?> batch = invocation.getArgument(1);
                    int[][] result = new int[batch.size()][];
                    for (int i = 0; i < batch.size(); i++) {
                        result[i] = new int[] {1};
                    }
                    return result;
                });
    }

    @Test
    void automaticLifecycleEntrypoints_shouldRollbackOnCheckedExceptions() throws NoSuchMethodException {
        AnnotationTransactionAttributeSource transactionAttributeSource = new AnnotationTransactionAttributeSource();
        List<Method> methods = List.of(
                SampleLifecycleService.class.getMethod("completePendingHomeworkByOrder", ColonelsettlementOrder.class),
                SampleLifecycleService.class.getMethod("autoCloseTimeoutPendingHomework", int.class),
                SampleLifecycleService.class.getMethod("autoCloseTimeoutPendingShip", int.class),
                SampleLifecycleService.class.getMethod("autoCloseTimeoutPendingHomework"),
                SampleLifecycleService.class.getMethod("autoCloseTimeoutPendingShip")
        );

        for (Method method : methods) {
            TransactionAttribute attribute = transactionAttributeSource.getTransactionAttribute(method, SampleLifecycleService.class);

            assertThat(attribute)
                    .as(method.toGenericString())
                    .isNotNull();
            assertThat(attribute.rollbackOn(new Exception("log write failed")))
                    .as(method.toGenericString())
                    .isTrue();
        }
    }

    @Test
    void completePendingHomeworkByOrder_shouldCompleteMatchedRequests() {
        UUID requestId = UUID.randomUUID();
        UUID channelUserId = UUID.randomUUID();

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("order-1");
        order.setProductId("dp-1");
        order.setChannelUserId(channelUserId);
        order.setExtraData(Map.of("talent_uid", "talent-1"));

        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<UUID>>any(),
                any(),
                any(),
                any()))
                .thenReturn(List.of(requestId));

        SampleRequest sample = new SampleRequest();
        sample.setId(requestId);
        sample.setStatus(5);
        when(sampleRequestMapper.selectBatchIds(List.of(requestId))).thenReturn(List.of(sample));

        int completed = service.completePendingHomeworkByOrder(order);

        assertThat(completed).isEqualTo(1);
        verify(sampleRequestMapper, never()).updateById(any());
        verify(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any());
        assertThat(sample.getStatus()).isEqualTo(6);
        verify(sampleStatusLogService).logBatch(org.mockito.ArgumentMatchers.argThat(entries ->
                entries.size() == 1
                        && entries.get(0).requestId().equals(requestId)
                        && entries.get(0).fromStatus() == 5
                        && entries.get(0).toStatus() == 6
                        && "auto complete by order: order-1".equals(entries.get(0).remark())
        ));
    }

    @Test
    void completePendingHomeworkByOrder_shouldCompleteOnlyOldestMatchedRequest() {
        UUID firstRequestId = UUID.randomUUID();
        UUID secondRequestId = UUID.randomUUID();
        UUID channelUserId = UUID.randomUUID();

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("order-oldest");
        order.setProductId("dp-1");
        order.setChannelUserId(channelUserId);
        order.setExtraData(Map.of("talent_uid", "talent-1"));

        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(),
                any(),
                any(),
                any()))
                .thenReturn(List.of(firstRequestId, secondRequestId));

        SampleRequest firstSample = new SampleRequest();
        firstSample.setId(firstRequestId);
        firstSample.setStatus(5);
        when(sampleRequestMapper.selectBatchIds(anyList())).thenAnswer(invocation -> {
            List<UUID> ids = invocation.getArgument(0);
            assertThat(ids).containsExactly(firstRequestId);
            return List.of(firstSample);
        });

        int completed = service.completePendingHomeworkByOrder(order);

        assertThat(completed).isEqualTo(1);
        assertThat(firstSample.getStatus()).isEqualTo(6);
        verify(sampleStatusLogService).logBatch(org.mockito.ArgumentMatchers.argThat(entries ->
                entries.size() == 1
                        && entries.get(0).requestId().equals(firstRequestId)
                        && "auto complete by order: order-oldest".equals(entries.get(0).remark())
        ));
    }

    @Test
    void completePendingHomeworkByOrder_shouldSkipWhenRequiredOrderFieldsMissing() {
        ColonelsettlementOrder missingOwner = new ColonelsettlementOrder();
        missingOwner.setProductId("dp-1");

        ColonelsettlementOrder blankProduct = new ColonelsettlementOrder();
        blankProduct.setUserId(UUID.randomUUID());
        blankProduct.setProductId(" ");

        assertThat(service.completePendingHomeworkByOrder(null)).isZero();
        assertThat(service.completePendingHomeworkByOrder(missingOwner)).isZero();
        assertThat(service.completePendingHomeworkByOrder(blankProduct)).isZero();
        verifyNoInteractions(jdbcTemplate, sampleRequestMapper, talentClaimMapper, sampleStatusLogService, configDomainFacade);
    }

    @Test
    void completePendingHomeworkByOrder_shouldPreferTalentClaimOwnerWhenAttributionUserDiffers() {
        UUID requestId = UUID.randomUUID();
        UUID claimOwnerId = UUID.randomUUID();
        UUID attributedUserId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("order-claim-owner");
        order.setProductId("dp-1");
        order.setUserId(attributedUserId);
        order.setChannelUserId(attributedUserId);
        order.setTalentId(talentId);
        order.setExtraData(Map.of("talent_uid", "talent-claim"));

        TalentClaim claim = new TalentClaim();
        claim.setTalentId(talentId);
        claim.setUserId(claimOwnerId);
        claim.setStatus(1);
        claim.setClaimedAt(LocalDateTime.now().minusDays(1));
        when(talentClaimMapper.findActiveByTalentId(talentId)).thenReturn(List.of(claim));

        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(),
                eq(claimOwnerId),
                eq("talent-claim"),
                eq("dp-1")))
                .thenReturn(List.of(requestId));

        SampleRequest sample = new SampleRequest();
        sample.setId(requestId);
        sample.setStatus(5);
        when(sampleRequestMapper.selectBatchIds(List.of(requestId))).thenReturn(List.of(sample));

        int completed = service.completePendingHomeworkByOrder(order);

        assertThat(completed).isEqualTo(1);
        verify(talentClaimMapper).findActiveByTalentId(talentId);
    }

    @Test
    void completePendingHomeworkByOrder_shouldSkipWhenNoTalentUid() {
        ColonelsettlementOrder missingExtra = new ColonelsettlementOrder();
        missingExtra.setProductId("dp-1");
        missingExtra.setChannelUserId(UUID.randomUUID());

        ColonelsettlementOrder emptyExtra = new ColonelsettlementOrder();
        emptyExtra.setProductId("dp-1");
        emptyExtra.setChannelUserId(UUID.randomUUID());
        emptyExtra.setExtraData(Map.of());

        int missingExtraCompleted = service.completePendingHomeworkByOrder(missingExtra);
        int emptyExtraCompleted = service.completePendingHomeworkByOrder(emptyExtra);

        assertThat(missingExtraCompleted).isZero();
        assertThat(emptyExtraCompleted).isZero();
        verify(sampleRequestMapper, never()).updateById(any());
    }

    @Test
    void completePendingHomeworkByOrder_shouldUseAuthorIdAndFilterInvalidRowsAndSamples() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID channelUserId = UUID.randomUUID();

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setOrderId("order-author");
        order.setProductId("dp-1");
        order.setChannelUserId(channelUserId);
        order.setExtraData(Map.of("author_id", "author-1"));

        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(),
                any(),
                any(),
                any()))
                .thenAnswer(invocation -> {
                    RowMapper<UUID> rowMapper = invocation.getArgument(1);
                    return Arrays.asList(
                            rowMapper.mapRow(resultSetWithId(requestId.toString()), 0),
                            rowMapper.mapRow(resultSetWithId("not-a-uuid"), 1),
                            rowMapper.mapRow(resultSetWithId(" "), 2)
                    );
                });

        SampleRequest statusMissing = new SampleRequest();
        statusMissing.setId(UUID.randomUUID());
        SampleRequest wrongStatus = new SampleRequest();
        wrongStatus.setId(UUID.randomUUID());
        wrongStatus.setStatus(4);
        SampleRequest matched = new SampleRequest();
        matched.setId(requestId);
        matched.setStatus(5);
        when(sampleRequestMapper.selectBatchIds(List.of(requestId))).thenReturn(Arrays.asList(null, statusMissing, wrongStatus, matched));

        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        doAnswer(invocation -> {
            Collection<SampleRequest> batch = invocation.getArgument(1);
            ParameterizedPreparedStatementSetter<SampleRequest> setter = invocation.getArgument(3);
            for (SampleRequest sample : batch) {
                setter.setValues(preparedStatement, sample);
            }
            return new int[][]{{1}};
        }).when(jdbcTemplate).batchUpdate(
                anyString(),
                org.mockito.ArgumentMatchers.<Collection<SampleRequest>>any(),
                anyInt(),
                org.mockito.ArgumentMatchers.<ParameterizedPreparedStatementSetter<SampleRequest>>any());

        int completed = service.completePendingHomeworkByOrder(order);

        assertThat(completed).isEqualTo(1);
        assertThat(matched.getStatus()).isEqualTo(6);
        assertThat(matched.getCompleteTime()).isNotNull();
        verify(preparedStatement).setObject(1, 6);
        verify(preparedStatement).setObject(2, matched.getCompleteTime());
        verify(preparedStatement).setObject(3, matched.getCloseTime());
        verify(preparedStatement).setObject(4, matched.getCloseReason());
        verify(preparedStatement).setObject(5, matched.getUpdateTime());
        verify(preparedStatement).setObject(6, matched.getId());
        verify(sampleStatusLogService).logBatch(org.mockito.ArgumentMatchers.argThat(entries ->
                entries.size() == 1
                        && entries.get(0).requestId().equals(requestId)
                        && "auto complete by order: order-author".equals(entries.get(0).remark())
        ));
    }

    @Test
    void autoCloseTimeoutPendingHomework_shouldCloseTimedOutRequests() {
        UUID requestId = UUID.randomUUID();
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<UUID>>any(),
                any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    RowMapper<UUID> rowMapper = invocation.getArgument(1);
                    return List.of(rowMapper.mapRow(resultSetWithId(requestId.toString()), 0));
                });

        SampleRequest sample = new SampleRequest();
        sample.setId(requestId);
        sample.setStatus(5);
        when(sampleRequestMapper.selectBatchIds(List.of(requestId))).thenReturn(List.of(sample));

        int closed = service.autoCloseTimeoutPendingHomework(30);

        assertThat(closed).isEqualTo(1);
        verify(sampleRequestMapper, never()).updateById(any());
        verify(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any());
        assertThat(sample.getStatus()).isEqualTo(8);
        assertThat(sample.getCloseReason()).contains("30天");
        verify(sampleStatusLogService).logBatch(org.mockito.ArgumentMatchers.argThat(entries ->
                entries.size() == 1
                        && entries.get(0).requestId().equals(requestId)
                        && entries.get(0).fromStatus() == 5
                        && entries.get(0).toStatus() == 8
                        && entries.get(0).remark().contains("30天")
        ));
    }

    @Test
    void autoCloseTimeoutPendingHomework_shouldUseConfiguredTimeout() {
        when(configDomainFacade.getSampleAutoCloseDays()).thenReturn(12);
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<UUID>>any(),
                any(LocalDateTime.class)))
                .thenReturn(List.of());

        int closed = service.autoCloseTimeoutPendingHomework();

        assertThat(closed).isZero();
        verify(configDomainFacade).getSampleAutoCloseDays();
    }

    @Test
    void autoCloseTimeoutPendingShip_shouldUseConfiguredTimeout() {
        when(configDomainFacade.getSampleRules())
                .thenReturn(new SampleRulesDTO(7, true, 30, 9, new SampleDefaultStandardDTO(null, null, null)));
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<UUID>>any(),
                any(LocalDateTime.class)))
                .thenReturn(List.of());

        int closed = service.autoCloseTimeoutPendingShip();

        assertThat(closed).isZero();
        verify(configDomainFacade).getSampleRules();
    }

    @Test
    void autoCloseTimeoutPendingShip_shouldUseDynamicCloseReason() {
        UUID requestId = UUID.randomUUID();
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<UUID>>any(),
                any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    RowMapper<UUID> rowMapper = invocation.getArgument(1);
                    return List.of(rowMapper.mapRow(resultSetWithId(requestId.toString()), 0));
                });

        SampleRequest sample = new SampleRequest();
        sample.setId(requestId);
        sample.setStatus(2);
        when(sampleRequestMapper.selectBatchIds(List.of(requestId))).thenReturn(List.of(sample));

        int closed = service.autoCloseTimeoutPendingShip(7);

        assertThat(closed).isEqualTo(1);
        verify(sampleRequestMapper, never()).updateById(any());
        verify(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any());
        assertThat(sample.getCloseReason()).isEqualTo("超时7天未发货自动关闭");
        verify(sampleStatusLogService).logBatch(org.mockito.ArgumentMatchers.argThat(entries ->
                entries.size() == 1
                        && entries.get(0).requestId().equals(requestId)
                        && entries.get(0).fromStatus() == 2
                        && entries.get(0).toStatus() == 8
                        && "超时7天未发货自动关闭".equals(entries.get(0).remark())
        ));
    }

    @Test
    @SuppressWarnings("unchecked")
    void privateDefensiveHelpers_shouldHandleNullEmptyAndNonListCollections() throws Exception {
        Method batchUpdateSamples = SampleLifecycleService.class.getDeclaredMethod("batchUpdateSamples", List.class);
        batchUpdateSamples.setAccessible(true);
        invokePrivate(batchUpdateSamples, service, (Object) null);
        invokePrivate(batchUpdateSamples, service, List.of());

        Method loadSamplesInStatus = SampleLifecycleService.class.getDeclaredMethod("loadSamplesInStatus", List.class, int.class);
        loadSamplesInStatus.setAccessible(true);
        assertThat((List<SampleRequest>) invokePrivate(loadSamplesInStatus, service, null, 5)).isEmpty();

        Method partition = SampleLifecycleService.class.getDeclaredMethod("partition", Collection.class, int.class);
        partition.setAccessible(true);
        assertThat((List<List<String>>) invokePrivate(partition, service, null, 200)).isEmpty();
        assertThat((List<List<String>>) invokePrivate(partition, service, List.of(), 200)).isEmpty();
        assertThat((List<List<String>>) invokePrivate(partition, service, new LinkedHashSet<>(List.of("a", "b", "c")), 2))
                .containsExactly(List.of("a", "b"), List.of("c"));
    }

    private ResultSet resultSetWithId(String id) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("id")).thenReturn(id);
        return resultSet;
    }

    private Object invokePrivate(Method method, Object target, Object... args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw ex;
        }
    }
}
