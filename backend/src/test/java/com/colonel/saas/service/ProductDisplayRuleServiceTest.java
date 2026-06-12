package com.colonel.saas.service;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.constant.ProductDisplayStatus;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.domain.product.event.ProductDomainEventPublisher;
import com.colonel.saas.domain.product.policy.ProductDisplayPolicy;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
                productDisplayAuditService,
                new ProductDisplayPolicy());
        when(operationStateMapper.updateById(any(ProductOperationState.class))).thenReturn(1);
    }

    @Test
    void applyForProductId_promotingActivityShouldNotBypassProductStatusEligibility() {
        String productId = "9012";
        ProductOperationState rejectedProduct = libraryState("60001", productId, 3000L, false, LocalDateTime.now());
        ProductOperationState promotingProduct = libraryState("60002", productId, 1000L, false, LocalDateTime.now().minusDays(1));

        ColonelsettlementActivity promotingActivity = new ColonelsettlementActivity();
        promotingActivity.setActivityStatusCode(5);
        promotingActivity.setRecruiterUserId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(colonelActivityMapper.selectByActivityId("60001")).thenReturn(promotingActivity);
        when(colonelActivityMapper.selectByActivityId("60002")).thenReturn(promotingActivity);

        when(operationStateMapper.selectList(any())).thenReturn(List.of(rejectedProduct, promotingProduct));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(
                snapshot("60001", productId, 3000L, 2),
                snapshot("60002", productId, 1000L, 1)
        ));
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, atLeastOnce()).updateById(captor.capture());
        assertThat(captor.getAllValues().stream()
                .filter(state -> state.getId().equals(rejectedProduct.getId()))
                .findFirst())
                .get()
                .extracting(ProductOperationState::getDisplayStatus, ProductOperationState::getHiddenReason)
                .containsExactly(ProductDisplayStatus.HIDDEN.name(), ProductDisplayRuleService.HIDDEN_REASON_UPSTREAM_NOT_PROMOTING);
        assertThat(captor.getAllValues().stream()
                .filter(state -> state.getId().equals(promotingProduct.getId()))
                .findFirst())
                .get()
                .extracting(ProductOperationState::getDisplayStatus)
                .isEqualTo(ProductDisplayStatus.DISPLAYING.name());
    }

    @Test
    void applyForProductId_upstreamPromotingWithoutLocalApprovalShouldEnterDisplayCompetition() {
        String productId = "9013";
        ProductOperationState locallyApproved = libraryState("60003", productId, 2000L, false, LocalDateTime.now());
        locallyApproved.setAuditStatus(null);
        locallyApproved.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());

        when(operationStateMapper.selectList(any())).thenReturn(List.of(locallyApproved));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot("60003", productId, 2000L, 1)));
        when(productBizStatusService.readBizStatus(locallyApproved)).thenReturn(ProductBizStatus.PENDING_AUDIT);

        service.applyForProductId(productId);

        verify(operationStateMapper).updateById(argThat(state ->
                state.getId().equals(locallyApproved.getId())
                        && ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus())
                        && ProductDisplayRuleService.DISPLAY_REASON_RULE.equals(state.getDisplayReason())
        ));
    }

    @Test
    void applyForProductId_upstreamNotPromotingShouldHideEvenWhenLocallyApproved() {
        String productId = "9014";
        ProductOperationState locallyApproved = libraryState("60004", productId, 2000L, false, LocalDateTime.now());

        when(operationStateMapper.selectList(any())).thenReturn(List.of(locallyApproved));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot("60004", productId, 2000L, 0)));
        when(productBizStatusService.readBizStatus(locallyApproved)).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        verify(operationStateMapper).updateById(argThat(state ->
                state.getId().equals(locallyApproved.getId())
                        && ProductDisplayStatus.HIDDEN.name().equals(state.getDisplayStatus())
                        && "UPSTREAM_NOT_PROMOTING".equals(state.getHiddenReason())
        ));
    }

    @Test
    void applyForProductId_upstreamPromotingShouldOverrideHistoricalLocalRejected() {
        String productId = "9015";
        ProductOperationState rejected = libraryState("60005", productId, 2000L, false, LocalDateTime.now());
        rejected.setAuditStatus(3);
        rejected.setBizStatus(ProductBizStatus.REJECTED.name());

        when(operationStateMapper.selectList(any())).thenReturn(List.of(rejected));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot("60005", productId, 2000L, 1)));
        when(productBizStatusService.readBizStatus(rejected)).thenReturn(ProductBizStatus.REJECTED);

        service.applyForProductId(productId);

        verify(operationStateMapper).updateById(argThat(state ->
                state.getId().equals(rejected.getId())
                        && ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus())
                        && state.getHiddenReason() == null
        ));
    }

    @Test
    void applyForProductId_manualPausedShouldHideEvenWhenUpstreamPromoting() {
        String productId = "9016";
        ProductOperationState paused = libraryState("60006", productId, 2000L, false, LocalDateTime.now());
        paused.setManualDisabled(true);

        when(operationStateMapper.selectList(any())).thenReturn(List.of(paused));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot("60006", productId, 2000L, 1)));
        when(productBizStatusService.readBizStatus(paused)).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        verify(operationStateMapper).updateById(argThat(state ->
                state.getId().equals(paused.getId())
                        && ProductDisplayStatus.HIDDEN.name().equals(state.getDisplayStatus())
                        && ProductDisplayRuleService.HIDDEN_REASON_LOCAL_PAUSED.equals(state.getHiddenReason())
        ));
    }

    @Test
    void shouldAutoEnterLibrary_promotingPendingAuditWithoutLocalBlockerReturnsTrue() {
        ProductOperationState pending = pendingState("3859423", "99001");
        ProductSnapshot snapshot = snapshot("3859423", "99001", 2000L, 1);

        assertThat(service.shouldAutoEnterLibrary(snapshot, pending)).isTrue();
    }

    @Test
    void repairLibraryStateForActivity_dryRunShouldReportHistoricalPromotingNotSelectedWithoutWriting() {
        String activityId = "3859423";
        ProductOperationState pending = pendingState(activityId, "99002");
        ProductSnapshot snapshot = snapshot(activityId, "99002", 2000L, 1);

        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot));
        when(operationStateMapper.selectList(any())).thenReturn(List.of(pending));

        ProductDisplayRuleService.LibraryRepairResult result =
                service.repairLibraryStateForActivity(activityId, true, 1000);

        assertThat(result.activityId()).isEqualTo(activityId);
        assertThat(result.dryRun()).isTrue();
        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.promoting()).isEqualTo(1);
        assertThat(result.willSelectToLibrary()).isEqualTo(1);
        assertThat(result.willDisplay()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(0);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0))
                .extracting(
                        ProductDisplayRuleService.LibraryRepairItem::oldSelectedToLibrary,
                        ProductDisplayRuleService.LibraryRepairItem::newSelectedToLibrary,
                        ProductDisplayRuleService.LibraryRepairItem::oldDisplayStatus,
                        ProductDisplayRuleService.LibraryRepairItem::newDisplayStatus,
                        ProductDisplayRuleService.LibraryRepairItem::reason)
                .containsExactly(
                        false,
                        true,
                        ProductDisplayStatus.PENDING.name(),
                        ProductDisplayStatus.PENDING.name(),
                        ProductDisplayRuleService.REPAIR_REASON_UPSTREAM_PROMOTING_AUTO_LIBRARY);
        verify(operationStateMapper, never()).updateById(any(ProductOperationState.class));
    }

    @Test
    void repairLibraryStateForActivity_writeShouldPromoteHistoricalPendingAuditWithoutForcingDisplayRule() {
        String activityId = "3859423";
        ProductOperationState pending = pendingState(activityId, "99003");
        ProductSnapshot snapshot = snapshot(activityId, "99003", 2000L, 1);

        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot), List.of(snapshot));
        when(operationStateMapper.selectList(any())).thenReturn(List.of(pending), List.of(pending), List.of(pending));

        ProductDisplayRuleService.LibraryRepairResult result =
                service.repairLibraryStateForActivity(activityId, false, 1000);

        assertThat(result.dryRun()).isFalse();
        assertThat(result.willSelectToLibrary()).isEqualTo(1);
        assertThat(pending.getSelectedToLibrary()).isTrue();
        assertThat(pending.getAuditStatus()).isEqualTo(2);
        assertThat(pending.getBizStatus()).isEqualTo(ProductBizStatus.APPROVED.name());
        assertThat(pending.getAuditRemark()).isEqualTo("上游状态为推广中，系统自动入库展示");
        assertThat(pending.getDisplayStatus()).isEqualTo(ProductDisplayStatus.PENDING.name());
        assertThat(pending.getHiddenReason()).isNull();
        verify(operationStateMapper).updateById(pending);
    }

    @Test
    void repairLibraryStateForActivity_writeShouldKeepAlreadyDisplayingPromotingProduct() {
        String activityId = "3859423";
        ProductOperationState displaying = pendingState(activityId, "99005");
        displaying.setSelectedToLibrary(true);
        displaying.setAuditStatus(3);
        displaying.setBizStatus(ProductBizStatus.REJECTED.name());
        displaying.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        displaying.setHiddenReason(ProductDisplayRuleService.HIDDEN_REASON_LOCAL_REJECTED);
        ProductSnapshot snapshot = snapshot(activityId, "99005", 2000L, 1);

        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot));
        when(operationStateMapper.selectList(any())).thenReturn(List.of(displaying));

        ProductDisplayRuleService.LibraryRepairResult result =
                service.repairLibraryStateForActivity(activityId, false, 1000);

        assertThat(result.dryRun()).isFalse();
        assertThat(displaying.getSelectedToLibrary()).isTrue();
        assertThat(displaying.getAuditStatus()).isEqualTo(2);
        assertThat(displaying.getBizStatus()).isEqualTo(ProductBizStatus.APPROVED.name());
        assertThat(displaying.getDisplayStatus()).isEqualTo(ProductDisplayStatus.DISPLAYING.name());
        assertThat(displaying.getHiddenReason()).isNull();
        verify(operationStateMapper).updateById(displaying);
    }

    @Test
    void repairLibraryStateForActivity_manualPausedPromotingProductShouldEnterLibraryButStayHidden() {
        String activityId = "3859423";
        ProductOperationState paused = pendingState(activityId, "99004");
        paused.setManualDisabled(true);
        ProductSnapshot snapshot = snapshot(activityId, "99004", 2000L, 1);

        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot));
        when(operationStateMapper.selectList(any())).thenReturn(List.of(paused));

        ProductDisplayRuleService.LibraryRepairResult result =
                service.repairLibraryStateForActivity(activityId, true, 1000);

        assertThat(result.willHideByLocalPaused()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0))
                .extracting(
                        ProductDisplayRuleService.LibraryRepairItem::newSelectedToLibrary,
                        ProductDisplayRuleService.LibraryRepairItem::newAuditStatus,
                        ProductDisplayRuleService.LibraryRepairItem::newBizStatus,
                        ProductDisplayRuleService.LibraryRepairItem::newDisplayStatus,
                        ProductDisplayRuleService.LibraryRepairItem::newHiddenReason)
                .containsExactly(
                        true,
                        2,
                        ProductBizStatus.APPROVED.name(),
                        ProductDisplayStatus.HIDDEN.name(),
                        ProductDisplayRuleService.HIDDEN_REASON_LOCAL_PAUSED);
        verify(operationStateMapper, never()).updateById(any(ProductOperationState.class));
    }

    @Test
    void applyForProductId_displayingWinnerShouldClearStaleHiddenReason() {
        String productId = "9017";
        ProductOperationState displaying = libraryState("60007", productId, 2000L, false, LocalDateTime.now());
        displaying.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        displaying.setHiddenReason(ProductDisplayRuleService.HIDDEN_REASON_NOT_ELIGIBLE);

        when(operationStateMapper.selectList(any())).thenReturn(List.of(displaying));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(snapshot("60007", productId, 2000L, 1)));
        when(productBizStatusService.readBizStatus(displaying)).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        verify(operationStateMapper).updateById(argThat(state ->
                state.getId().equals(displaying.getId())
                        && ProductDisplayStatus.DISPLAYING.name().equals(state.getDisplayStatus())
                        && state.getHiddenReason() == null
        ));
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
    void applyForProductId_shouldHideCurrentDisplayingBeforePromotingNewWinner() {
        String productId = "9020";
        ProductOperationState challenger = libraryState("50202", productId, 3000L, false, LocalDateTime.now().minusDays(1));
        ProductOperationState displaying = libraryState("50201", productId, 1500L, false, LocalDateTime.now().minusMonths(2));
        displaying.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        displaying.setFirstDisplayedAt(LocalDateTime.now().minusMonths(2));

        when(operationStateMapper.selectList(any())).thenReturn(List.of(challenger, displaying));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(
                snapshot("50202", productId, 3000L, 1),
                snapshot("50201", productId, 1500L, 1)
        ));
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ProductOperationState::getId)
                .containsExactly(displaying.getId(), challenger.getId());
        assertThat(captor.getAllValues().get(0))
                .extracting(ProductOperationState::getDisplayStatus, ProductOperationState::getHiddenReason)
                .containsExactly(ProductDisplayStatus.HIDDEN.name(), ProductDisplayRuleService.HIDDEN_REASON_REPLACED_BY_ADVANTAGE);
        assertThat(captor.getAllValues().get(1))
                .extracting(ProductOperationState::getDisplayStatus, ProductOperationState::getDisplayReason)
                .containsExactly(ProductDisplayStatus.DISPLAYING.name(), ProductDisplayRuleService.DISPLAY_REASON_ADVANTAGE);
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

    @Test
    void displaySwitchShouldDemoteOldDisplayingBeforePromotingNewWinner() {
        String productId = "two-pass-1";
        ProductOperationState oldDisplaying = libraryState("A001", productId, 1000L, false, LocalDateTime.now().minusDays(10));
        oldDisplaying.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        oldDisplaying.setFirstDisplayedAt(LocalDateTime.now().minusDays(10));

        ProductOperationState newWinner = libraryState("A002", productId, 3000L, false, LocalDateTime.now().minusDays(1));

        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityStatusCode(5);
        activity.setRecruiterUserId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(colonelActivityMapper.selectByActivityId("A001")).thenReturn(activity);
        when(colonelActivityMapper.selectByActivityId("A002")).thenReturn(activity);

        when(operationStateMapper.selectList(any())).thenReturn(List.of(oldDisplaying, newWinner));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(
                snapshot("A001", productId, 1000L, 1),
                snapshot("A002", productId, 3000L, 1)
        ));
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        var orderVerifier = inOrder(operationStateMapper);
        orderVerifier.verify(operationStateMapper).updateById(argThat(s ->
                s.getId().equals(oldDisplaying.getId())
                        && ProductDisplayStatus.HIDDEN.name().equals(s.getDisplayStatus())));
        orderVerifier.verify(operationStateMapper).updateById(argThat(s ->
                s.getId().equals(newWinner.getId())
                        && ProductDisplayStatus.DISPLAYING.name().equals(s.getDisplayStatus())));
    }

    @Test
    void displaySwitchIdempotentWhenWinnerIsAlreadyDisplaying() {
        String productId = "two-pass-2";
        ProductOperationState currentDisplaying = libraryState("A010", productId, 3000L, false, LocalDateTime.now().minusDays(5));
        currentDisplaying.setDisplayStatus(ProductDisplayStatus.DISPLAYING.name());
        currentDisplaying.setFirstDisplayedAt(LocalDateTime.now().minusDays(5));
        currentDisplaying.setDisplayReason(ProductDisplayRuleService.DISPLAY_REASON_RULE);

        ProductOperationState loser = libraryState("A011", productId, 1000L, false, LocalDateTime.now().minusDays(1));

        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityStatusCode(5);
        activity.setRecruiterUserId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(colonelActivityMapper.selectByActivityId("A010")).thenReturn(activity);
        when(colonelActivityMapper.selectByActivityId("A011")).thenReturn(activity);

        when(operationStateMapper.selectList(any())).thenReturn(List.of(currentDisplaying, loser));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(
                snapshot("A010", productId, 3000L, 1),
                snapshot("A011", productId, 1000L, 1)
        ));
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        verify(operationStateMapper, atLeastOnce()).updateById(argThat(s ->
                s.getId().equals(loser.getId())
                        && ProductDisplayStatus.HIDDEN.name().equals(s.getDisplayStatus())));
        assertThat(currentDisplaying.getDisplayStatus()).isEqualTo(ProductDisplayStatus.DISPLAYING.name());
    }

    @Test
    void multipleCandidatesOnlyOneDisplaying() {
        String productId = "two-pass-3";
        ProductOperationState s1 = libraryState("A020", productId, 1000L, false, LocalDateTime.now().minusDays(10));
        ProductOperationState s2 = libraryState("A021", productId, 2000L, false, LocalDateTime.now().minusDays(5));
        ProductOperationState s3 = libraryState("A022", productId, 3000L, false, LocalDateTime.now().minusDays(1));

        ColonelsettlementActivity activity = new ColonelsettlementActivity();
        activity.setActivityStatusCode(5);
        activity.setRecruiterUserId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(colonelActivityMapper.selectByActivityId(any())).thenReturn(activity);

        when(operationStateMapper.selectList(any())).thenReturn(List.of(s1, s2, s3));
        when(snapshotMapper.selectList(any())).thenReturn(List.of(
                snapshot("A020", productId, 1000L, 1),
                snapshot("A021", productId, 2000L, 1),
                snapshot("A022", productId, 3000L, 1)
        ));
        when(productBizStatusService.readBizStatus(any())).thenReturn(ProductBizStatus.APPROVED);

        service.applyForProductId(productId);

        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, atLeastOnce()).updateById(captor.capture());
        long displayingCount = captor.getAllValues().stream()
                .filter(s -> ProductDisplayStatus.DISPLAYING.name().equals(s.getDisplayStatus()))
                .count();
        assertThat(displayingCount).isEqualTo(1);
        assertThat(captor.getAllValues().stream()
                .filter(s -> ProductDisplayStatus.DISPLAYING.name().equals(s.getDisplayStatus()))
                .findFirst().get().getId()).isEqualTo(s3.getId());
    }

    // ---------------- P-FIX-002E: 批量修复 PENDING 状态 ----------------

    @Test
    void repairLibraryStateForPendingProducts_dryRunShouldReportExpectedChangesWithoutWriting() {
        // 场景: 1 个非推广中(status=0) + 1 个推广中(status=1) 混合 PENDING
        ProductOperationState notPromoting = pendingState("A100", "P-1");
        ProductOperationState promoting = pendingState("A101", "P-2");

        // 第 1 次: PENDING 全表查询(返回 pending 状态集)
        // 第 2 次: loadSnapshots 内部按 activityIds+productIds 联合查询
        when(operationStateMapper.selectList(any()))
                .thenReturn(List.of(notPromoting, promoting));
        when(snapshotMapper.selectList(any()))
                .thenReturn(List.of(
                        snapshot("A100", "P-1", 1000L, 0),
                        snapshot("A101", "P-2", 2000L, 1)
                ));

        ProductDisplayRuleService.LibraryRepairResult result =
                service.repairLibraryStateForPendingProducts(true, 100);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.promoting()).isEqualTo(1);
        assertThat(result.willSelectToLibrary()).isEqualTo(1);
        assertThat(result.willDisplay()).isEqualTo(1);
        assertThat(result.willHideByUpstream()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(0);
        assertThat(result.items()).hasSize(2);

        // dryRun 必须不写入
        verify(operationStateMapper, never()).updateById(any(ProductOperationState.class));
    }

    @Test
    void repairLibraryStateForPendingProducts_writeShouldHideNonPromotingAndSelectPromoting() {
        ProductOperationState notPromoting = pendingState("A100", "P-1");
        ProductOperationState promoting = pendingState("A101", "P-2");

        when(operationStateMapper.selectList(any()))
                .thenReturn(List.of(notPromoting, promoting));
        when(snapshotMapper.selectList(any()))
                .thenReturn(List.of(
                        snapshot("A100", "P-1", 1000L, 0),
                        snapshot("A101", "P-2", 2000L, 1)
                ));

        ProductDisplayRuleService.LibraryRepairResult result =
                service.repairLibraryStateForPendingProducts(false, 100);

        assertThat(result.dryRun()).isFalse();
        assertThat(result.scanned()).isEqualTo(2);

        // 非推广中 -> HIDDEN + UPSTREAM_NOT_PROMOTING
        assertThat(notPromoting.getDisplayStatus()).isEqualTo(ProductDisplayStatus.HIDDEN.name());
        assertThat(notPromoting.getHiddenReason()).isEqualTo(
                ProductDisplayRuleService.HIDDEN_REASON_UPSTREAM_NOT_PROMOTING);

        // 推广中 -> 选入商品库 + 保持 PENDING(展示去重决策交给 applyForProductId)
        assertThat(promoting.getSelectedToLibrary()).isTrue();
        assertThat(promoting.getAuditStatus()).isEqualTo(2);
        assertThat(promoting.getBizStatus()).isEqualTo(ProductBizStatus.APPROVED.name());
        assertThat(promoting.getDisplayStatus()).isEqualTo(ProductDisplayStatus.PENDING.name());
        assertThat(promoting.getHiddenReason()).isNull();

        // 两条记录都应当被写入
        ArgumentCaptor<ProductOperationState> captor = ArgumentCaptor.forClass(ProductOperationState.class);
        verify(operationStateMapper, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ProductOperationState::getId)
                .containsExactlyInAnyOrder(notPromoting.getId(), promoting.getId());
    }

    @Test
    void repairLibraryStateForPendingProducts_writeShouldBeIdempotentOnAlreadyRepaired() {
        // 已修复 -> HIDDEN + UPSTREAM_NOT_PROMOTING 的记录不再变化
        ProductOperationState alreadyHidden = pendingState("A100", "P-1");
        alreadyHidden.setDisplayStatus(ProductDisplayStatus.HIDDEN.name());
        alreadyHidden.setHiddenReason(ProductDisplayRuleService.HIDDEN_REASON_UPSTREAM_NOT_PROMOTING);
        alreadyHidden.setSelectedToLibrary(false);
        alreadyHidden.setAuditStatus(1);
        alreadyHidden.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());

        when(operationStateMapper.selectList(any())).thenReturn(List.of(alreadyHidden));
        when(snapshotMapper.selectList(any()))
                .thenReturn(List.of(snapshot("A100", "P-1", 1000L, 0)));

        ProductDisplayRuleService.LibraryRepairResult result =
                service.repairLibraryStateForPendingProducts(false, 100);

        // 幂等核心: 该记录已被识别为"未变化"，所以不进入 update 路径
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.scanned()).isEqualTo(1);
        // items 中不应包含 unchanged 记录(unchanged 不会进入 items)
        assertThat(result.items()).isEmpty();
        // 幂等: 已修复的不再 updateById
        verify(operationStateMapper, never()).updateById(any(ProductOperationState.class));
    }

    @Test
    void repairLibraryStateForPendingProducts_emptyListShouldReturnEmptyResult() {
        when(operationStateMapper.selectList(any())).thenReturn(List.of());

        ProductDisplayRuleService.LibraryRepairResult result =
                service.repairLibraryStateForPendingProducts(true, 100);

        assertThat(result.scanned()).isEqualTo(0);
        assertThat(result.items()).isEmpty();
        verify(snapshotMapper, never()).selectList(any());
        verify(operationStateMapper, never()).updateById(any(ProductOperationState.class));
    }

    @Test
    void repairLibraryStateForPendingProducts_writeShouldHideManualDisabledEvenIfSnapshotPromoting() {
        // manual_disabled=true + 上游 status=1 -> 进入商品库但保持 HIDDEN + LOCAL_PAUSED
        ProductOperationState paused = pendingState("A200", "P-9");
        paused.setManualDisabled(true);

        when(operationStateMapper.selectList(any())).thenReturn(List.of(paused));
        when(snapshotMapper.selectList(any()))
                .thenReturn(List.of(snapshot("A200", "P-9", 1500L, 1)));

        ProductDisplayRuleService.LibraryRepairResult result =
                service.repairLibraryStateForPendingProducts(false, 100);

        assertThat(result.willHideByLocalPaused()).isEqualTo(1);
        assertThat(paused.getDisplayStatus()).isEqualTo(ProductDisplayStatus.HIDDEN.name());
        assertThat(paused.getHiddenReason()).isEqualTo(
                ProductDisplayRuleService.HIDDEN_REASON_LOCAL_PAUSED);
        assertThat(paused.getSelectedToLibrary()).isTrue();
        verify(operationStateMapper, times(1)).updateById(paused);
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

    private ProductOperationState pendingState(String activityId, String productId) {
        ProductOperationState state = new ProductOperationState();
        state.setId(UUID.randomUUID());
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setSelectedToLibrary(false);
        state.setAuditStatus(1);
        state.setBizStatus(ProductBizStatus.PENDING_AUDIT.name());
        state.setDisplayStatus(ProductDisplayStatus.PENDING.name());
        state.setCreateTime(LocalDateTime.now());
        return state;
    }
}
