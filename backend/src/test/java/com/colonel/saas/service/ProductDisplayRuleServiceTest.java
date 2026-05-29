package com.colonel.saas.service;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import com.colonel.saas.service.display.ProductDisplayAuditService;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductDisplayRuleServiceTest {

    @Mock
    private ProductOperationStateMapper operationStateMapper;
    @Mock
    private ProductSnapshotMapper snapshotMapper;
    @Mock
    private ProductBizStatusService productBizStatusService;
    @Mock
    private ColonelsettlementActivityMapper colonelActivityMapper;
    @Mock
    private ProductDomainEventPublisher productDomainEventPublisher;

    @Mock
    private ProductDisplayAuditService productDisplayAuditService;

    private ProductDisplayRuleService service;

    @BeforeEach
    void setUp() {
        service = new ProductDisplayRuleService(
                operationStateMapper,
                snapshotMapper,
                productBizStatusService,
                colonelActivityMapper,
                productDomainEventPublisher,
                productDisplayAuditService);
        when(operationStateMapper.updateById(any(ProductOperationState.class))).thenReturn(1);
    }

    @Test
    void applyForProductId_promotingActivityShouldKeepAllRelationsDisplaying() {
        String productId = "9012";
        ProductOperationState promotingA = libraryState("60001", productId, 1000L, false, LocalDateTime.now());
        ProductOperationState promotingB = libraryState("60002", productId, 3000L, false, LocalDateTime.now().minusDays(1));

        ColonelsettlementActivity promotingActivity = new ColonelsettlementActivity();
        promotingActivity.setActivityStatusCode(5);
        when(colonelActivityMapper.selectByActivityId("60001")).thenReturn(promotingActivity);
        when(colonelActivityMapper.selectByActivityId("60002")).thenReturn(promotingActivity);

        when(operationStateMapper.selectList(any())).thenReturn(List.of(promotingA, promotingB));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(
                snapshot("60001", productId, 1000L, 0),
                snapshot("60002", productId, 3000L, 0)
        ));

        service.applyForProductId(productId);

        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, atLeastOnce()).updateById(captor.capture());
        long displayingCount = captor.getAllValues().stream()
                .filter(state -> ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus()))
                .count();
        assertThat(displayingCount).isEqualTo(2);
    }

    @Test
    void applyForProductId_shouldKeepOnlyOneDisplayingWhenSameProductIdHasMultipleRelations() {
        String productId = "9001";
        ProductOperationState lowCommission = libraryState("10001", productId, 1500L, false, LocalDateTime.now().minusDays(2));
        ProductOperationState highCommission = libraryState("10002", productId, 3000L, false, LocalDateTime.now().minusDays(1));
        ProductOperationState adsWinner = libraryState("10003", productId, 1500L, true, LocalDateTime.now().minusDays(3));

        when(operationStateMapper.selectList(any())).thenReturn(List.of(lowCommission, highCommission, adsWinner));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(
                snapshot("10001", productId, 1500L, 1),
                snapshot("10002", productId, 3000L, 1),
                snapshot("10003", productId, 1500L, 1)
        ));
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, atLeastOnce()).updateById(captor.capture());

        List<ProductOperationState> updated = captor.getAllValues();
        long displayingCount = updated.stream()
                .filter(state -> ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus()))
                .count();
        assertThat(displayingCount).isEqualTo(1);
        assertThat(updated.stream().anyMatch(state -> state.getId().equals(adsWinner.getId())
                && ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus()))).isTrue();
        assertThat(updated.stream().filter(state -> state.getId().equals(highCommission.getId())).findFirst())
                .get()
                .extracting(ProductOperationState::getDisplayStatus, ProductOperationState::getHiddenReason)
                .containsExactly(ProductDisplayStatus.HIDDEN.name(), ProductDisplayRuleService.HIDDEN_REASON_REPLACED);
    }

    @Test
    void applyForProductId_shouldPreferHigherCommissionWhenAdsFlagEqual() {
        String productId = "9002";
        ProductOperationState lower = libraryState("20001", productId, 1000L, false, LocalDateTime.now().minusDays(1));
        ProductOperationState higher = libraryState("20002", productId, 2800L, false, LocalDateTime.now());

        when(operationStateMapper.selectList(any())).thenReturn(List.of(lower, higher));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(
                snapshot("20001", productId, 1000L, 1),
                snapshot("20002", productId, 2800L, 1)
        ));
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, atLeastOnce()).updateById(captor.capture());
        assertThat(captor.getAllValues().stream().anyMatch(state -> state.getId().equals(higher.getId())
                && ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus()))).isTrue();
    }

    @Test
    void applyForProductId_shouldHideIneligiblePromotedProducts() {
        String productId = "9003";
        ProductOperationState eligible = libraryState("30001", productId, 2000L, false, LocalDateTime.now());
        ProductOperationState expired = libraryState("30002", productId, 4000L, true, LocalDateTime.now());
        ProductSnapshot expiredSnapshot = snapshot("30002", productId, 4000L, 1);
        expiredSnapshot.setPromotionEndTime(LocalDateTime.now().minusDays(1).toString());

        when(operationStateMapper.selectList(any())).thenReturn(List.of(eligible, expired));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(
                snapshot("30001", productId, 2000L, 1),
                expiredSnapshot
        ));
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, atLeastOnce()).updateById(captor.capture());
        assertThat(captor.getAllValues().stream().filter(state -> state.getId().equals(expired.getId())).findFirst())
                .get()
                .extracting(ProductOperationState::getDisplayStatus, ProductOperationState::getHiddenReason)
                .containsExactly(ProductDisplayStatus.HIDDEN.name(), ProductDisplayRuleService.HIDDEN_REASON_ACTIVITY_EXPIRED);
    }

    // --- Protection period tests ---

    @Test
    void protectionPeriod_shouldBlockSwitchWhenNoAdvantage() {
        String productId = "9010";
        // 当前展示记录：2个月前首次展示，仍在3个月保护期内
        ProductOperationState displaying = libraryState("40001", productId, 2000L, false, LocalDateTime.now().minusMonths(2));
        displaying.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        displaying.setFirstDisplayedAt(LocalDateTime.now().minusMonths(2));

        // 新候选：佣金率相同，服务费相同，无投流优势
        ProductOperationState challenger = libraryState("40002", productId, 2000L, false, LocalDateTime.now().minusDays(1));

        when(operationStateMapper.selectList(any())).thenReturn(List.of(displaying, challenger));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(
                snapshot("40001", productId, 2000L, 1),
                snapshot("40002", productId, 2000L, 1)
        ));
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        // 当前展示记录状态未变（已经是 DISPLAYING），updateById 不会为其调用
        // 验证挑战者被隐藏（被保护期阻挡）
        verify(operationStateMapper).updateById(argThat(s ->
                s.getId().equals(challenger.getId())
                        && ProductDisplayStatus.HIDDEN.name().equals(s.getDisplayStatus())
                        && ProductDisplayRuleService.HIDDEN_REASON_REPLACED.equals(s.getHiddenReason())
        ));
    }

    @Test
    void protectionPeriod_shouldAllowOverrideWithHigherCommission() {
        String productId = "9011";
        ProductOperationState displaying = libraryState("50001", productId, 1500L, false, LocalDateTime.now().minusMonths(2));
        displaying.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        displaying.setFirstDisplayedAt(LocalDateTime.now().minusMonths(2));

        // 新候选：佣金率更高
        ProductOperationState challenger = libraryState("50002", productId, 3000L, false, LocalDateTime.now().minusDays(1));

        when(operationStateMapper.selectList(any())).thenReturn(List.of(displaying, challenger));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(
                snapshot("50001", productId, 1500L, 1),
                snapshot("50002", productId, 3000L, 1)
        ));
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, atLeastOnce()).updateById(captor.capture());

        // 新候选应成为 DISPLAYING
        assertThat(captor.getAllValues().stream()
                .filter(s -> s.getId().equals(challenger.getId())).findFirst())
                .get()
                .extracting(ProductOperationState::getDisplayStatus)
                .isEqualTo(ProductDisplayStatus.DISPLAYING.name());

        // 旧记录应被隐藏，reason 为优势覆盖
        assertThat(captor.getAllValues().stream()
                .filter(s -> s.getId().equals(displaying.getId())).findFirst())
                .get()
                .extracting(ProductOperationState::getDisplayStatus, ProductOperationState::getHiddenReason)
                .containsExactly(ProductDisplayStatus.HIDDEN.name(),
                        ProductDisplayRuleService.HIDDEN_REASON_REPLACED_BY_ADVANTAGE);
    }

    @Test
    void protectionPeriod_shouldAllowOverrideWithLowerServiceFee() {
        String productId = "9012";
        ProductOperationState displaying = libraryState("60001", productId, 2000L, false, LocalDateTime.now().minusMonths(1));
        displaying.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        displaying.setFirstDisplayedAt(LocalDateTime.now().minusMonths(1));

        ProductOperationState challenger = libraryState("60002", productId, 2000L, false, LocalDateTime.now().minusDays(1));

        when(operationStateMapper.selectList(any())).thenReturn(List.of(displaying, challenger));
        // 当前展示记录服务费 5%，新候选服务费 3%
        ProductSnapshot displayingSnap = snapshot("60001", productId, 2000L, 1);
        displayingSnap.setAdServiceRatio("5%");
        ProductSnapshot challengerSnap = snapshot("60002", productId, 2000L, 1);
        challengerSnap.setAdServiceRatio("3%");
        when(snapshotMapper.selectList(any())).thenReturn(List.of(displayingSnap, challengerSnap));
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, atLeastOnce()).updateById(captor.capture());

        assertThat(captor.getAllValues().stream()
                .filter(s -> s.getId().equals(challenger.getId())).findFirst())
                .get()
                .extracting(ProductOperationState::getDisplayStatus)
                .isEqualTo(ProductDisplayStatus.DISPLAYING.name());
    }

    @Test
    void protectionPeriod_shouldAllowOverrideWithAdsWhenCurrentHasNone() {
        String productId = "9013";
        ProductOperationState displaying = libraryState("70001", productId, 2000L, false, LocalDateTime.now().minusMonths(1));
        displaying.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        displaying.setFirstDisplayedAt(LocalDateTime.now().minusMonths(1));

        // 新候选有投流，当前无投流
        ProductOperationState challenger = libraryState("70002", productId, 2000L, true, LocalDateTime.now().minusDays(1));

        when(operationStateMapper.selectList(any())).thenReturn(List.of(displaying, challenger));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(
                snapshot("70001", productId, 2000L, 1),
                snapshot("70002", productId, 2000L, 1)
        ));
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, atLeastOnce()).updateById(captor.capture());

        assertThat(captor.getAllValues().stream()
                .filter(s -> s.getId().equals(challenger.getId())).findFirst())
                .get()
                .extracting(ProductOperationState::getDisplayStatus)
                .isEqualTo(ProductDisplayStatus.DISPLAYING.name());
    }

    @Test
    void protectionPeriod_shouldAllowSwitchAfterProtectionExpires() {
        String productId = "9014";
        // 4个月前首次展示，已超过3个月保护期
        ProductOperationState displaying = libraryState("80001", productId, 2000L, false, LocalDateTime.now().minusMonths(4));
        displaying.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        displaying.setFirstDisplayedAt(LocalDateTime.now().minusMonths(4));

        ProductOperationState challenger = libraryState("80002", productId, 3000L, false, LocalDateTime.now().minusDays(1));

        when(operationStateMapper.selectList(any())).thenReturn(List.of(displaying, challenger));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(
                snapshot("80001", productId, 2000L, 1),
                snapshot("80002", productId, 3000L, 1)
        ));
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, atLeastOnce()).updateById(captor.capture());

        assertThat(captor.getAllValues().stream()
                .filter(s -> s.getId().equals(challenger.getId())).findFirst())
                .get()
                .extracting(ProductOperationState::getDisplayStatus)
                .isEqualTo(ProductDisplayStatus.DISPLAYING.name());

        // 保护期已过，更高佣金候选切换，reason 为优势覆盖
        assertThat(captor.getAllValues().stream()
                .filter(s -> s.getId().equals(displaying.getId())).findFirst())
                .get()
                .extracting(ProductOperationState::getHiddenReason)
                .isEqualTo(ProductDisplayRuleService.HIDDEN_REASON_REPLACED_BY_ADVANTAGE);
    }

    @Test
    void protectionPeriod_shouldForceHideWhenCurrentBecomesIneligible() {
        String productId = "9015";
        ProductOperationState displaying = libraryState("90001", productId, 2000L, false, LocalDateTime.now().minusMonths(1));
        displaying.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        displaying.setFirstDisplayedAt(LocalDateTime.now().minusMonths(1));

        ProductOperationState challenger = libraryState("90002", productId, 1500L, false, LocalDateTime.now().minusDays(1));

        // 当前展示记录活动已过期
        ProductSnapshot expiredSnap = snapshot("90001", productId, 2000L, 1);
        expiredSnap.setPromotionEndTime(LocalDateTime.now().minusDays(1).toString());

        when(operationStateMapper.selectList(any())).thenReturn(List.of(displaying, challenger));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(
                expiredSnap,
                snapshot("90002", productId, 1500L, 1)
        ));
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, atLeastOnce()).updateById(captor.capture());

        // 旧记录变为 HIDDEN（因为活动过期不再 eligible）
        assertThat(captor.getAllValues().stream()
                .filter(s -> s.getId().equals(displaying.getId())).findFirst())
                .get()
                .extracting(ProductOperationState::getDisplayStatus)
                .isEqualTo(ProductDisplayStatus.HIDDEN.name());

        // 新候选成为 DISPLAYING（即使佣金更低）
        assertThat(captor.getAllValues().stream()
                .filter(s -> s.getId().equals(challenger.getId())).findFirst())
                .get()
                .extracting(ProductOperationState::getDisplayStatus)
                .isEqualTo(ProductDisplayStatus.DISPLAYING.name());
    }

    @Test
    void applyForProductId_shouldPublishEmptyDetailWhenNoDisplayCandidateWins() {
        String productId = "9016";
        ProductOperationState displaying = libraryState("91001", productId, 2000L, false, LocalDateTime.now().minusMonths(1));
        displaying.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        displaying.setFirstDisplayedAt(LocalDateTime.now().minusMonths(1));

        ProductSnapshot expiredSnap = snapshot("91001", productId, 2000L, 1);
        expiredSnap.setPromotionEndTime(LocalDateTime.now().minusDays(1).toString());

        when(operationStateMapper.selectList(any())).thenReturn(List.of(displaying));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(expiredSnap));
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detailCaptor = ArgumentCaptor.forClass(Map.class);
        verify(productDomainEventPublisher).publishDisplayRuleApplied(
                eq(productId),
                eq(displaying.getId()),
                isNull(),
                eq(ProductDisplayRuleService.DISPLAY_RULE_VERSION),
                any(),
                isNull(),
                detailCaptor.capture());
        assertThat(detailCaptor.getValue()).isEmpty();
    }

    // --- Advantage comparator unit tests ---

    @Test
    void hasAdvantageOver_higherCommissionReturnsTrue() {
        ProductOperationState challengerState = new ProductOperationState();
        challengerState.setId(UUID.randomUUID());
        ProductOperationState incumbentState = new ProductOperationState();
        incumbentState.setId(UUID.randomUUID());
        var challenger = new ProductDisplayRuleService.DisplayCandidate(
                challengerState, false, BigDecimal.valueOf(30), BigDecimal.ZERO, LocalDateTime.now());
        var incumbent = new ProductDisplayRuleService.DisplayCandidate(
                incumbentState, false, BigDecimal.valueOf(20), BigDecimal.ZERO, LocalDateTime.now());
        assertThat(service.hasAdvantageOver(challenger, incumbent)).isTrue();
    }

    @Test
    void hasAdvantageOver_lowerServiceFeeReturnsTrue() {
        ProductOperationState challengerState = new ProductOperationState();
        challengerState.setId(UUID.randomUUID());
        ProductOperationState incumbentState = new ProductOperationState();
        incumbentState.setId(UUID.randomUUID());
        var challenger = new ProductDisplayRuleService.DisplayCandidate(
                challengerState, false, BigDecimal.valueOf(20), BigDecimal.valueOf(3), LocalDateTime.now());
        var incumbent = new ProductDisplayRuleService.DisplayCandidate(
                incumbentState, false, BigDecimal.valueOf(20), BigDecimal.valueOf(5), LocalDateTime.now());
        assertThat(service.hasAdvantageOver(challenger, incumbent)).isTrue();
    }

    @Test
    void hasAdvantageOver_adsAdvantageReturnsTrue() {
        ProductOperationState challengerState = new ProductOperationState();
        challengerState.setId(UUID.randomUUID());
        ProductOperationState incumbentState = new ProductOperationState();
        incumbentState.setId(UUID.randomUUID());
        var challenger = new ProductDisplayRuleService.DisplayCandidate(
                challengerState, true, BigDecimal.valueOf(20), BigDecimal.ZERO, LocalDateTime.now());
        var incumbent = new ProductDisplayRuleService.DisplayCandidate(
                incumbentState, false, BigDecimal.valueOf(20), BigDecimal.ZERO, LocalDateTime.now());
        assertThat(service.hasAdvantageOver(challenger, incumbent)).isTrue();
    }

    @Test
    void hasAdvantageOver_noAdvantageReturnsFalse() {
        ProductOperationState challengerState = new ProductOperationState();
        challengerState.setId(UUID.randomUUID());
        ProductOperationState incumbentState = new ProductOperationState();
        incumbentState.setId(UUID.randomUUID());
        var challenger = new ProductDisplayRuleService.DisplayCandidate(
                challengerState, false, BigDecimal.valueOf(20), BigDecimal.valueOf(5), LocalDateTime.now());
        var incumbent = new ProductDisplayRuleService.DisplayCandidate(
                incumbentState, false, BigDecimal.valueOf(20), BigDecimal.valueOf(5), LocalDateTime.now());
        assertThat(service.hasAdvantageOver(challenger, incumbent)).isFalse();
    }

    @Test
    void isInProtectionPeriod_withinProtectionReturnsTrue() {
        ProductOperationState state = new ProductOperationState();
        state.setFirstDisplayedAt(LocalDateTime.now().minusMonths(2));
        assertThat(service.isInProtectionPeriod(state.getFirstDisplayedAt(), 3, LocalDateTime.now())).isTrue();
    }

    @Test
    void isInProtectionPeriod_afterProtectionReturnsFalse() {
        ProductOperationState state = new ProductOperationState();
        state.setFirstDisplayedAt(LocalDateTime.now().minusMonths(4));
        assertThat(service.isInProtectionPeriod(state.getFirstDisplayedAt(), 3, LocalDateTime.now())).isFalse();
    }

    private ProductOperationState libraryState(
            String activityId,
            String productId,
            long commissionRatio,
            boolean supportsAds,
            LocalDateTime selectedAt) {
        ProductOperationState state = new ProductOperationState();
        state.setId(UUID.randomUUID());
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setSelectedToLibrary(true);
        state.setSelectedAt(selectedAt);
        state.setAuditStatus(2);
        state.setBizStatus(ProductBizStatus.APPROVED.name());
        state.setCreateTime(selectedAt);
        if (supportsAds) {
            state.setAuditPayload("{\"supportsAds\":true}");
        }
        return state;
    }

    private ProductSnapshot snapshot(String activityId, String productId, long commissionRatio, int status) {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId(activityId);
        snapshot.setProductId(productId);
        snapshot.setActivityCosRatio(commissionRatio);
        snapshot.setStatus(status);
        snapshot.setSyncTime(LocalDateTime.now());
        return snapshot;
    }
}
