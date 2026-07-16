package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.shared.attribution.AttributionOwnerType;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.entity.PickSourceMapping;
import com.colonel.saas.entity.PromotionLink;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.PickSourceMappingMapper;
import com.colonel.saas.mapper.PromotionLinkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributionOwnerReconciliationServiceTest {

    @Mock private PickSourceMappingMapper mappingMapper;
    @Mock private PromotionLinkMapper promotionLinkMapper;
    @Mock private ColonelsettlementOrderMapper orderMapper;
    @Mock private UserDomainFacade userDomainFacade;

    private AttributionOwnerReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new AttributionOwnerReconciliationService(
                mappingMapper, promotionLinkMapper, orderMapper, userDomainFacade);
    }

    @Test
    void applyMustRequireConfirmBeforeAnyReadOrWrite() {
        AttributionOwnerReconciliationService.ReconcileRequest request =
                new AttributionOwnerReconciliationService.ReconcileRequest(
                        List.of(UUID.randomUUID()), 200, false, false);

        assertThatThrownBy(() -> service.reconcile(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("confirm=true");

        verifyNoInteractions(mappingMapper, promotionLinkMapper, orderMapper, userDomainFacade);
    }

    @Test
    void defaultDryRunShouldClassifyWithoutWrites() {
        UUID userId = UUID.randomUUID();
        PickSourceMapping mapping = mapping(userId, null);
        when(mappingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(mapping));
        when(userDomainFacade.loadActiveRoleCodesByUserIds(List.of(userId)))
                .thenReturn(Map.of(userId, Set.of("biz_staff")));
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        AttributionOwnerReconciliationService.ReconcileResult result = service.reconcile(
                new AttributionOwnerReconciliationService.ReconcileRequest(List.of(userId), null, null, null));

        assertThat(result.dryRun()).isTrue();
        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.classifiable()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.items()).singleElement()
                .extracting(
                        AttributionOwnerReconciliationService.ReconcileItem::proposedOwnerType,
                        AttributionOwnerReconciliationService.ReconcileItem::potentialOrderCount,
                        AttributionOwnerReconciliationService.ReconcileItem::status)
                .containsExactly(AttributionOwnerType.RECRUITER.name(), 3L, "DRY_RUN");
        verify(mappingMapper, never()).updateById(any());
        verify(promotionLinkMapper, never()).updateById(any());
    }

    @Test
    void applyShouldClassifyMappingAndLinkedPromotionInOneRun() {
        UUID userId = UUID.randomUUID();
        UUID promotionLinkId = UUID.randomUUID();
        PickSourceMapping mapping = mapping(userId, promotionLinkId);
        PromotionLink link = new PromotionLink();
        link.setId(promotionLinkId);
        link.setAttributionOwnerType(null);
        when(mappingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(mapping));
        when(userDomainFacade.loadActiveRoleCodesByUserIds(List.of(userId)))
                .thenReturn(Map.of(userId, Set.of("channel_staff")));
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(promotionLinkMapper.selectById(promotionLinkId)).thenReturn(link);
        when(mappingMapper.updateById(any())).thenReturn(1);
        when(promotionLinkMapper.updateById(any())).thenReturn(1);

        AttributionOwnerReconciliationService.ReconcileResult result = service.reconcile(
                new AttributionOwnerReconciliationService.ReconcileRequest(List.of(userId), 200, false, true));

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.items()).singleElement()
                .extracting(
                        AttributionOwnerReconciliationService.ReconcileItem::proposedOwnerType,
                        AttributionOwnerReconciliationService.ReconcileItem::status)
                .containsExactly(AttributionOwnerType.CHANNEL.name(), "UPDATED");
        verify(mappingMapper).updateById(any());
        verify(promotionLinkMapper).updateById(any());
    }

    @Test
    void roleConflictShouldProduceDiagnosticWithoutWrites() {
        UUID userId = UUID.randomUUID();
        when(mappingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(mapping(userId, null)));
        when(userDomainFacade.loadActiveRoleCodesByUserIds(List.of(userId)))
                .thenReturn(Map.of(userId, Set.of("channel_staff", "biz_staff")));
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        AttributionOwnerReconciliationService.ReconcileResult result = service.reconcile(
                new AttributionOwnerReconciliationService.ReconcileRequest(List.of(userId), 200, false, true));

        assertThat(result.conflicts()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.items()).singleElement()
                .extracting(AttributionOwnerReconciliationService.ReconcileItem::status)
                .isEqualTo("CONFLICT");
        verify(mappingMapper, never()).updateById(any());
        verify(promotionLinkMapper, never()).updateById(any());
    }

    @Test
    void secondApplyShouldBeIdempotentBecauseOnlyNullOwnerTypesAreScanned() {
        UUID userId = UUID.randomUUID();
        when(mappingMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(mapping(userId, null)))
                .thenReturn(List.of());
        when(userDomainFacade.loadActiveRoleCodesByUserIds(List.of(userId)))
                .thenReturn(Map.of(userId, Set.of("biz_staff")));
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(mappingMapper.updateById(any())).thenReturn(1);

        AttributionOwnerReconciliationService.ReconcileRequest request =
                new AttributionOwnerReconciliationService.ReconcileRequest(List.of(userId), 200, false, true);

        assertThat(service.reconcile(request).updated()).isEqualTo(1);
        AttributionOwnerReconciliationService.ReconcileResult second = service.reconcile(request);

        assertThat(second.scanned()).isZero();
        assertThat(second.updated()).isZero();
    }

    @Test
    void resultShouldCapScannedMappingsAtTwoHundred() {
        UUID userId = UUID.randomUUID();
        List<PickSourceMapping> mappings = java.util.stream.IntStream.range(0, 201)
                .mapToObj(ignored -> mapping(userId, null))
                .toList();
        when(mappingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(mappings);
        when(userDomainFacade.loadActiveRoleCodesByUserIds(List.of(userId)))
                .thenReturn(Map.of(userId, Set.of("biz_staff")));
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        AttributionOwnerReconciliationService.ReconcileResult result = service.reconcile(
                new AttributionOwnerReconciliationService.ReconcileRequest(List.of(userId), 999, true, false));

        assertThat(result.scanned()).isEqualTo(200);
        assertThat(result.items()).hasSize(200);
    }

    private PickSourceMapping mapping(UUID userId, UUID promotionLinkId) {
        PickSourceMapping mapping = new PickSourceMapping();
        mapping.setId(UUID.randomUUID());
        mapping.setUserId(userId);
        mapping.setPromotionLinkId(promotionLinkId);
        mapping.setActivityId("3916506");
        mapping.setProductId("3829804874841849888");
        mapping.setValidFrom(LocalDateTime.of(2026, 7, 1, 0, 0));
        mapping.setValidUntil(LocalDateTime.of(2026, 8, 1, 0, 0));
        mapping.setStatus(1);
        mapping.setDeleted(0);
        return mapping;
    }
}
