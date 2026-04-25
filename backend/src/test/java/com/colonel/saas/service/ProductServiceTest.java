package com.colonel.saas.service;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.TalentFollowRecord;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private DouyinPromotionGateway douyinPromotionGateway;
    @Mock
    private DouyinProductGateway douyinProductGateway;
    @Mock
    private ProductSnapshotMapper snapshotMapper;
    @Mock
    private ProductOperationStateMapper operationStateMapper;
    @Mock
    private ProductOperationLogMapper operationLogMapper;
    @Mock
    private ProductBizStatusService productBizStatusService;
    @Mock
    private TalentFollowService talentFollowService;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(
                douyinPromotionGateway,
                douyinProductGateway,
                snapshotMapper,
                operationStateMapper,
                operationLogMapper,
                productBizStatusService,
                talentFollowService
        );
    }

    @Test
    void upsertSnapshots_shouldInsertSnapshotWhenMissing() {
        when(snapshotMapper.selectById(any(UUID.class))).thenReturn(null);
        when(operationStateMapper.selectOne(any())).thenReturn(null);

        service.upsertSnapshots("10001", List.of(new DouyinProductGateway.ActivityProductItem(
                9001L,
                "测试商品",
                "https://example.com/cover.png",
                19900L,
                "199.00",
                20L,
                1000L,
                2000L,
                "20%",
                1,
                "双佣金",
                "10",
                8L,
                true,
                true,
                12L,
                3001L,
                "测试店铺",
                "4.9",
                1,
                "推广中",
                "美妆",
                "99",
                "满100减10",
                "2026-04-01",
                "2026-05-01",
                "2026-04-01",
                "2026-05-01",
                "https://example.com/detail"
        )));

        ArgumentCaptor<ProductSnapshot> captor = ArgumentCaptor.forClass(ProductSnapshot.class);
        verify(snapshotMapper).insert(captor.capture());
        ProductSnapshot snapshot = captor.getValue();
        assertThat(snapshot.getActivityId()).isEqualTo("10001");
        assertThat(snapshot.getProductId()).isEqualTo("9001");
        assertThat(snapshot.getTitle()).isEqualTo("测试商品");
        verify(productBizStatusService).initStateIfAbsent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void auditProduct_shouldRejectWithoutReason() {
        assertThatThrownBy(() -> service.auditProduct("10001", "9001", false, "", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("原因");
        verify(operationStateMapper, never()).insert(any(ProductOperationState.class));
    }

    @Test
    void generatePromotionLink_shouldCallPromotionApi() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setDetailUrl("https://example.com/detail");
        ProductOperationState state = new ProductOperationState();
        state.setActivityId("10001");
        state.setProductId("9001");
        state.setBizStatus("ASSIGNED");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(douyinPromotionGateway.generateLink(any()))
                .thenReturn(new DouyinPromotionGateway.PromotionLinkResult("abc", "https://s.link", "https://p.link", "seed"));

        DouyinPromotionGateway.PromotionLinkResult result = service.generatePromotionLink(
                "10001", "9001", UUID.randomUUID(), UUID.randomUUID(), null, null, true
        );

        assertThat(result.shortId()).isEqualTo("abc");
        verify(douyinPromotionGateway).generateLink(any());
        verify(productBizStatusService).changeStatus(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generatePromotionLink_shouldKeepStatusWhenFailed() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setDetailUrl("https://example.com/detail");
        ProductOperationState state = new ProductOperationState();
        state.setActivityId("10001");
        state.setProductId("9001");
        state.setBizStatus("ASSIGNED");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.ASSIGNED);
        when(douyinPromotionGateway.generateLink(any())).thenThrow(new RuntimeException("mock convert failed"));

        assertThatThrownBy(() -> service.generatePromotionLink(
                "10001", "9001", UUID.randomUUID(), UUID.randomUUID(), null, null, true
        )).isInstanceOf(RuntimeException.class).hasMessageContaining("mock convert failed");

        verify(productBizStatusService).logFailure(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void startTalentFollow_shouldChangeStatusFromLinked() {
        UUID productId = UUID.randomUUID();
        ProductSnapshot snapshot = buildSnapshot(productId);
        ProductOperationState state = buildState("LINKED");
        TalentFollowRecord record = new TalentFollowRecord();
        record.setId(UUID.randomUUID());
        when(snapshotMapper.selectById(productId)).thenReturn(snapshot);
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(talentFollowService.createRecord(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(record);
        when(talentFollowService.listByProduct(any(), any())).thenReturn(List.of(record));
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.LINKED);

        var result = service.startTalentFollow(
                productId,
                null,
                "达人A",
                "INVITED",
                "已发送邀约",
                null,
                UUID.randomUUID(),
                "操作人"
        );

        assertThat(result).containsKey("followRecords");
        verify(productBizStatusService).changeStatus(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void startTalentFollow_shouldAppendRecordWhenAlreadyFollowing() {
        UUID productId = UUID.randomUUID();
        ProductSnapshot snapshot = buildSnapshot(productId);
        ProductOperationState state = buildState("FOLLOWING");
        TalentFollowRecord record = new TalentFollowRecord();
        record.setId(UUID.randomUUID());
        when(snapshotMapper.selectById(productId)).thenReturn(snapshot);
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(talentFollowService.createRecord(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(record);
        when(talentFollowService.listByProduct(any(), any())).thenReturn(List.of(record));
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.FOLLOWING);

        var result = service.startTalentFollow(
                productId,
                null,
                "达人B",
                "REPLIED",
                "达人已回复",
                null,
                UUID.randomUUID(),
                "操作人"
        );

        assertThat(result).containsKey("followRecords");
        verify(productBizStatusService, never()).changeStatus(any(), any(), any(), any(), any(), any(), any(), any());
        verify(productBizStatusService).logFailure(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void buildActivityProductListView_shouldExposeBizStatusFields() {
        ProductOperationState state = buildState("LINKED");
        state.setShortLink("https://s.link");
        state.setPromoteLink("https://p.link");
        when(operationStateMapper.selectList(any())).thenReturn(List.of(state));
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.LINKED);

        DouyinProductGateway.ActivityProductListResult result = new DouyinProductGateway.ActivityProductListResult(
                true,
                10001L,
                111111L,
                1L,
                "1",
                List.of(new DouyinProductGateway.ActivityProductItem(
                        9001L,
                        "测试商品",
                        "https://example.com/cover.png",
                        19900L,
                        "199.00",
                        20L,
                        1000L,
                        2000L,
                        "20%",
                        1,
                        "双佣金",
                        "10",
                        8L,
                        true,
                        true,
                        12L,
                        3001L,
                        "测试店铺",
                        "4.9",
                        1,
                        "推广中",
                        "美妆",
                        "99",
                        "满200减20",
                        "2026-04-01",
                        "2026-05-01",
                        "2026-04-01",
                        "2026-05-01",
                        "https://example.com/detail"
                ))
        );

        Map<String, Object> view = service.buildActivityProductListView(result);

        List<?> items = (List<?>) view.get("items");
        assertThat(items).hasSize(1);
        Map<?, ?> first = (Map<?, ?>) items.get(0);
        assertThat(first.get("bizStatus")).isEqualTo("LINKED");
        assertThat(first.get("bizStatusLabel")).isEqualTo(ProductBizStatus.LINKED.getLabel());
        assertThat(first.get("shortLink")).isEqualTo("https://s.link");
        assertThat(first.get("promoteLink")).isEqualTo("https://p.link");
    }

    @Test
    void getActivityProductDetail_shouldExposeAssignedStatusAndAssignee() {
        UUID assigneeId = UUID.randomUUID();
        ProductSnapshot snapshot = buildSnapshot(UUID.randomUUID());
        snapshot.setTitle("测试商品");
        ProductOperationState state = buildState("ASSIGNED");
        state.setAssigneeId(assigneeId);
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.ASSIGNED);
        when(talentFollowService.listByProduct("10001", "9001")).thenReturn(List.of());

        Map<String, Object> detail = service.getActivityProductDetail("10001", "9001");

        assertThat(detail.get("bizStatus")).isEqualTo("ASSIGNED");
        assertThat(detail.get("bizStatusLabel")).isEqualTo(ProductBizStatus.ASSIGNED.getLabel());
        assertThat(detail.get("assigneeId")).isEqualTo(assigneeId);
    }

    @Test
    void buildActivityProductListView_shouldExposeAssignedStateForListRefresh() {
        UUID assigneeId = UUID.randomUUID();
        ProductOperationState state = buildState("ASSIGNED");
        state.setAssigneeId(assigneeId);
        when(operationStateMapper.selectList(any())).thenReturn(List.of(state));
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.ASSIGNED);

        DouyinProductGateway.ActivityProductListResult result = new DouyinProductGateway.ActivityProductListResult(
                true,
                10001L,
                111111L,
                1L,
                "1",
                List.of(new DouyinProductGateway.ActivityProductItem(
                        9001L,
                        "测试商品",
                        "https://example.com/cover.png",
                        19900L,
                        "199.00",
                        20L,
                        1000L,
                        2000L,
                        "20%",
                        1,
                        "双佣金",
                        "10",
                        8L,
                        true,
                        true,
                        12L,
                        3001L,
                        "测试店铺",
                        "4.9",
                        1,
                        "推广中",
                        "美妆",
                        "99",
                        "满100减10",
                        "2026-04-01",
                        "2026-05-01",
                        "2026-04-01",
                        "2026-05-01",
                        "https://example.com/detail"
                ))
        );

        Map<String, Object> view = service.buildActivityProductListView(result);

        List<?> items = (List<?>) view.get("items");
        Map<?, ?> first = (Map<?, ?>) items.get(0);
        assertThat(first.get("bizStatus")).isEqualTo("ASSIGNED");
        assertThat(first.get("bizStatusLabel")).isEqualTo(ProductBizStatus.ASSIGNED.getLabel());
        assertThat(first.get("assigneeId")).isEqualTo(assigneeId);
    }

    private ProductSnapshot buildSnapshot(UUID productId) {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(productId);
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setDetailUrl("https://example.com/detail");
        return snapshot;
    }

    private ProductOperationState buildState(String bizStatus) {
        ProductOperationState state = new ProductOperationState();
        state.setActivityId("10001");
        state.setProductId("9001");
        state.setBizStatus(bizStatus);
        return state;
    }
}
