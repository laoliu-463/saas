package com.colonel.saas.service;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.PromotionLink;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.TalentFollowRecord;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.MerchantMapper;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.PromotionLinkMapper;
import com.colonel.saas.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
    private PromotionLinkMapper promotionLinkMapper;
    @Mock
    private ColonelsettlementOrderMapper orderMapper;
    @Mock
    private MerchantMapper merchantMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private PickSourceMappingService pickSourceMappingService;
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
                promotionLinkMapper,
                orderMapper,
                merchantMapper,
                sysUserMapper,
                pickSourceMappingService,
                productBizStatusService,
                talentFollowService
        );
    }

    @Test
    void upsertSnapshots_shouldInsertSnapshotWhenMissing() {
        when(snapshotMapper.selectById(any(UUID.class))).thenReturn(null);
        when(snapshotMapper.upsert(any(ProductSnapshot.class))).thenReturn(1);
        when(operationStateMapper.selectOne(any())).thenReturn(null);

        service.upsertSnapshots("10001", List.of(buildItem(9001L, "测试商品")));

        ArgumentCaptor<ProductSnapshot> captor = ArgumentCaptor.forClass(ProductSnapshot.class);
        verify(snapshotMapper).upsert(captor.capture());
        ProductSnapshot snapshot = captor.getValue();
        assertThat(snapshot.getActivityId()).isEqualTo("10001");
        assertThat(snapshot.getProductId()).isEqualTo("9001");
        assertThat(snapshot.getTitle()).isEqualTo("测试商品");
        verify(productBizStatusService).initStateIfAbsent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void upsertSnapshots_shouldUpdateExistingSnapshotEvenWhenCreateTimeMissing() {
        ProductSnapshot existing = new ProductSnapshot();
        existing.setId(UUID.randomUUID());
        existing.setActivityId("10001");
        existing.setProductId("9001");
        existing.setCreateTime(null);
        existing.setUpdateTime(LocalDateTime.now());
        when(snapshotMapper.selectById(any(UUID.class))).thenReturn(existing);
        when(snapshotMapper.upsert(any(ProductSnapshot.class))).thenReturn(1);
        when(operationStateMapper.selectOne(any())).thenReturn(null);

        service.upsertSnapshots("10001", List.of(buildItem(9001L, "更新后的商品")));

        verify(snapshotMapper, never()).insert(any(ProductSnapshot.class));
        verify(snapshotMapper, never()).updateById(any(ProductSnapshot.class));
        verify(snapshotMapper).upsert(any(ProductSnapshot.class));
    }

    @Test
    void auditProduct_shouldRejectWithoutReason() {
        assertThatThrownBy(() -> service.auditProduct("10001", "9001", false, "", UUID.randomUUID(), UUID.randomUUID()))
                .hasMessageContaining("原因");
        verify(operationStateMapper, never()).insert(any(ProductOperationState.class));
    }

    @Test
    void auditProduct_shouldMarkSelectedToLibraryWhenApproved() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        ProductOperationState state = buildState("PENDING_AUDIT");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state, state);

        service.auditProduct(
                "10001",
                "9001",
                true,
                null,
                Map.of("exclusivePriceRemark", "专属价 129"),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        assertThat(state.getSelectedToLibrary()).isTrue();
    }

    @Test
    void auditProduct_shouldClearSupplementPayloadAndReturnLibraryVisible() {
        UUID operatorId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setTitle("测试商品");
        snapshot.setShopId(3001L);
        snapshot.setShopName("测试店铺");
        ProductOperationState state = buildState("PENDING_AUDIT");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);

        Map<String, Object> result = service.auditProduct(
                "10001",
                "9001",
                true,
                null,
                Map.of("exclusivePriceRemark", "专属价 129"),
                operatorId,
                deptId
        );

        assertThat(state.getAuditPayload()).isNull();
        assertThat(state.getSelectedToLibrary()).isTrue();
        assertThat(result.get("selectedToLibrary")).isEqualTo(true);
    }

    @Test
    void generatePromotionLink_shouldPersistLinkAndAdvanceStatus() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        String expectedPickExtra = "channel_channelleader";
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setDetailUrl("https://example.com/detail");
        ProductOperationState state = new ProductOperationState();
        state.setActivityId("10001");
        state.setProductId("9001");
        state.setBizStatus("ASSIGNED");
        state.setSelectedToLibrary(true);
        SysUser user = new SysUser();
        user.setRealName("渠道A");
        user.setChannelCode("channelleader");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(douyinPromotionGateway.generateLink(any()))
                .thenReturn(new DouyinPromotionGateway.PromotionLinkResult(
                        "abc12345",
                        expectedPickExtra,
                        "abc12345",
                        "https://s.link",
                        "https://p.link",
                        "seed"
                ));

        DouyinPromotionGateway.PromotionLinkResult result = service.generatePromotionLink(
                "10001", "9001", userId, deptId, null, null, true
        );

        assertThat(result.shortId()).isEqualTo("abc12345");
        assertThat(result.pickExtra()).isEqualTo(expectedPickExtra);
        verify(douyinPromotionGateway).generateLink(any());
        verify(promotionLinkMapper).insert(any(PromotionLink.class));
        verify(pickSourceMappingService).saveOrUpdate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(productBizStatusService).changeStatus(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generatePromotionLink_shouldLogFailureWhenGatewayThrows() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setDetailUrl("https://example.com/detail");
        ProductOperationState state = new ProductOperationState();
        state.setActivityId("10001");
        state.setProductId("9001");
        state.setBizStatus("ASSIGNED");
        state.setSelectedToLibrary(true);
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.ASSIGNED);
        when(douyinPromotionGateway.generateLink(any())).thenThrow(new RuntimeException("mock convert failed"));

        assertThatThrownBy(() -> service.generatePromotionLink(
                "10001", "9001", UUID.randomUUID(), UUID.randomUUID(), null, null, true
        )).hasMessageContaining("mock convert failed");

        verify(productBizStatusService).logFailure(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getPromotionLinkHistory_shouldReturnPagedRecords() {
        PromotionLink latest = new PromotionLink();
        latest.setId(UUID.randomUUID());
        latest.setProductId("3810562766247428542");
        latest.setActivityId("3916506");
        latest.setPromotionUrl("https://p.latest");
        latest.setShortUrl("https://s.latest");
        latest.setPickSource("v.latest");
        latest.setLinkStatus("SUCCESS");
        latest.setCreatedAt(LocalDateTime.of(2026, 5, 8, 10, 0));

        PromotionLink earlier = new PromotionLink();
        earlier.setId(UUID.randomUUID());
        earlier.setProductId("3810562766247428542");
        earlier.setActivityId("3916506");
        earlier.setPromotionUrl("https://p.earlier");
        earlier.setShortUrl("https://s.earlier");
        earlier.setPickSource("v.earlier");
        earlier.setLinkStatus("SUCCESS");
        earlier.setCreatedAt(LocalDateTime.of(2026, 5, 7, 10, 0));

        when(promotionLinkMapper.selectList(any())).thenReturn(List.of(latest, earlier));

        PageResult<Map<String, Object>> result = service.getPromotionLinkHistory("3810562766247428542", 1, 1);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0))
                .containsEntry("productId", "3810562766247428542")
                .containsEntry("activityId", "3916506")
                .containsEntry("promotionUrl", "https://p.latest")
                .containsEntry("promoteLink", "https://p.latest")
                .containsEntry("shortLink", "https://s.latest")
                .containsEntry("pickSource", "v.latest");
    }

    @Test
    void bindActivity_shouldUpdateBoundActivityWithoutChangingBizStatus() {
        UUID operatorId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        ProductOperationState state = buildState("APPROVED");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.APPROVED);

        service.bindActivity("10001", "9001", "20002", operatorId, deptId);

        assertThat(state.getBoundActivityId()).isEqualTo("20002");
        verify(operationStateMapper).updateById(state);
        verify(productBizStatusService, never()).changeStatus(any(), any(), any(), any(), any(), any(), any(), any());

        ArgumentCaptor<ProductOperationLog> logCaptor = ArgumentCaptor.forClass(ProductOperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getOperationType()).isEqualTo("BIND_ACTIVITY");
        assertThat(logCaptor.getValue().getBeforeStatus()).isEqualTo("APPROVED");
        assertThat(logCaptor.getValue().getAfterStatus()).isEqualTo("APPROVED");
        assertThat(logCaptor.getValue().getOperationPayload()).contains("boundActivityId=20002");
    }

    @Test
    void assignProduct_shouldSendBusinessFriendlyPayload() {
        UUID assigneeId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setShopId(3001L);
        ProductOperationState state = buildState("APPROVED");
        state.setSelectedToLibrary(true);
        SysUser assignee = new SysUser();
        assignee.setRealName("招商李四");
        assignee.setUsername("lisi");
        SysUser operator = new SysUser();
        operator.setRealName("组长张三");
        operator.setUsername("zhangsan");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(sysUserMapper.selectById(assigneeId)).thenReturn(assignee);
        when(sysUserMapper.selectById(operatorId)).thenReturn(operator);
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.ASSIGNED);

        service.assignProduct("10001", "9001", assigneeId, operatorId, deptId);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(productBizStatusService).changeStatus(
                eq(state),
                eq(ProductBizStatus.ASSIGNED),
                eq("ASSIGN"),
                eq(operatorId),
                eq(deptId),
                payloadCaptor.capture(),
                eq("分配招商成功"),
                any()
        );
        assertThat(payloadCaptor.getValue())
                .containsEntry("assigneeId", assigneeId)
                .containsEntry("assigneeName", "招商李四 (lisi)")
                .containsEntry("operatorId", operatorId)
                .containsEntry("operatorName", "组长张三 (zhangsan)")
                .containsEntry("eventLabel", "商品已分配给招商负责人");
    }

    @Test
    void recordProductDecision_shouldInsertDecisionLog() {
        UUID operatorId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setShopId(3001L);
        ProductOperationState state = buildState("ASSIGNED");
        state.setSelectedToLibrary(true);
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.ASSIGNED);

        service.recordProductDecision("10001", "9001", "MAIN", "佣金高，适合优先推", operatorId, deptId);

        ArgumentCaptor<ProductOperationLog> logCaptor = ArgumentCaptor.forClass(ProductOperationLog.class);
        verify(operationLogMapper).insert(logCaptor.capture());
        ProductOperationLog log = logCaptor.getValue();
        assertThat(log.getOperationType()).isEqualTo("DECISION");
        assertThat(log.getBeforeStatus()).isEqualTo("ASSIGNED");
        assertThat(log.getAfterStatus()).isEqualTo("ASSIGNED");
        assertThat(log.getOperationPayload())
                .contains("decisionLevel=MAIN")
                .contains("decisionLabel=主推")
                .contains("eventLabel=商品推进判断已更新");
        assertThat(log.getOperationRemark()).isEqualTo("佣金高，适合优先推");
        assertThat(log.getOperatorId()).isEqualTo(operatorId);
        assertThat(log.getOperatorDeptId()).isEqualTo(deptId);
    }

    @Test
    void startTalentFollow_shouldChangeStatusFromLinked() {
        UUID productId = UUID.randomUUID();
        ProductSnapshot snapshot = buildSnapshot(productId);
        ProductOperationState state = buildState("LINKED");
        state.setSelectedToLibrary(true);
        TalentFollowRecord record = new TalentFollowRecord();
        record.setId(UUID.randomUUID());
        when(snapshotMapper.selectById(productId)).thenReturn(snapshot);
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(talentFollowService.createRecord(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(record);
        when(talentFollowService.listByProduct(any(), any())).thenReturn(List.of(record));
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.LINKED);

        Map<String, Object> result = service.startTalentFollow(
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
    void buildActivityProductListView_shouldExposeBizStatusFields() {
        UUID assigneeId = UUID.randomUUID();
        ProductOperationState state = buildState("LINKED");
        state.setAssigneeId(assigneeId);
        state.setShortLink("https://s.link");
        state.setPromoteLink("https://p.link");
        SysUser assignee = new SysUser();
        assignee.setId(assigneeId);
        assignee.setRealName("招商赵六");
        assignee.setUsername("zhaoliu");
        when(operationStateMapper.selectList(any())).thenReturn(List.of(state));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(assignee));
        ProductOperationLog decisionLog = new ProductOperationLog();
        decisionLog.setActivityId("10001");
        decisionLog.setProductId("9001");
        decisionLog.setOperationType("DECISION");
        decisionLog.setOperationPayload("{decisionLevel=MAIN, decisionLabel=主推, eventLabel=商品推进判断已更新}");
        decisionLog.setOperationRemark("佣金高，优先推进");
        when(operationLogMapper.selectList(any())).thenReturn(List.of(decisionLog));
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.LINKED);

        DouyinProductGateway.ActivityProductListResult result = new DouyinProductGateway.ActivityProductListResult(
                true,
                10001L,
                111111L,
                1L,
                "1",
                List.of(buildItem(9001L, "测试商品"))
        );

        Map<String, Object> view = service.buildActivityProductListView(result);

        assertThat(view).doesNotContainKey("test");

        List<?> items = (List<?>) view.get("items");
        assertThat(items).hasSize(1);
        Map<?, ?> first = (Map<?, ?>) items.get(0);
        assertThat(first.get("bizStatus")).isEqualTo("LINKED");
        assertThat(first.get("bizStatusLabel")).isEqualTo(ProductBizStatus.LINKED.getLabel());
        assertThat(first.get("assigneeName")).isEqualTo("招商赵六 (zhaoliu)");
        assertThat(first.get("shortLink")).isEqualTo("https://s.link");
        assertThat(first.get("promoteLink")).isEqualTo("https://p.link");
        assertThat(first.get("latestDecisionLevel")).isEqualTo("MAIN");
        assertThat(first.get("latestDecisionLabel")).isEqualTo("主推");
        assertThat(first.get("latestDecisionReason")).isEqualTo("佣金高，优先推进");
        verify(sysUserMapper, never()).selectById(any());
    }

    @Test
    void putIntoLibrary_shouldMarkStateAndWriteLog() {
        UUID operatorId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setTitle("精选商品");
        ProductOperationState state = buildState("PENDING_AUDIT");
        state.setAuditStatus(2);
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.PENDING_AUDIT);

        Map<String, Object> result = service.putIntoLibrary("10001", "9001", operatorId, deptId);

        assertThat(result.get("selectedToLibrary")).isEqualTo(true);
        assertThat(result.get("libraryVisible")).isEqualTo(true);
        verify(productBizStatusService).changeStatus(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void getPage_shouldBatchLoadStatesAndAssignees() {
        UUID assigneeId = UUID.randomUUID();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setTitle("分页商品");

        ProductOperationState state = buildState("APPROVED");
        state.setAssigneeId(assigneeId);
        state.setAuditStatus(2);

        SysUser assignee = new SysUser();
        assignee.setId(assigneeId);
        assignee.setRealName("招商李四");
        assignee.setUsername("lisi");

        Page<ProductSnapshot> snapshotPage = new Page<>(1, 10, 1);
        snapshotPage.setRecords(List.of(snapshot));

        when(snapshotMapper.selectPage(any(Page.class), any())).thenReturn(snapshotPage);
        when(operationStateMapper.selectList(any())).thenReturn(List.of(state));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(assignee));
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.APPROVED);

        var result = service.getPage(1, 10, null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getAssigneeName()).isEqualTo("招商李四 (lisi)");
        verify(operationStateMapper, never()).selectOne(any());
        verify(sysUserMapper, never()).selectById(any());
    }

    @Test
    void getSelectedLibraryPage_shouldReturnOnlyPickedProducts() {
        UUID assigneeId = UUID.randomUUID();
        ProductSnapshot selected = new ProductSnapshot();
        selected.setId(UUID.randomUUID());
        selected.setActivityId("10001");
        selected.setProductId("9001");
        selected.setTitle("已入库商品");
        selected.setShopId(3001L);
        selected.setShopName("测试店铺");

        ProductOperationState selectedState = buildState("APPROVED");
        selectedState.setActivityId("10001");
        selectedState.setProductId("9001");
        selectedState.setSelectedToLibrary(true);
        selectedState.setAssigneeId(assigneeId);

        SysUser assignee = new SysUser();
        assignee.setId(assigneeId);
        assignee.setRealName("招商王五");
        assignee.setUsername("wangwu");

        Page<ProductOperationState> statePage = new Page<>(1, 200, 1);
        statePage.setRecords(List.of(selectedState));
        when(operationStateMapper.selectPage(any(Page.class), any())).thenReturn(statePage);
        when(snapshotMapper.selectBatchIds(any())).thenReturn(List.of(selected));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(assignee));
        when(productBizStatusService.readBizStatus(selectedState)).thenReturn(ProductBizStatus.APPROVED);

        var result = service.getSelectedLibraryPage(1, 10, null, null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getName()).isEqualTo("已入库商品");
        assertThat(result.getRecords().get(0).getAssigneeName()).isEqualTo("招商王五 (wangwu)");
        verify(operationStateMapper, never()).selectOne(any());
        verify(sysUserMapper, never()).selectById(any());
    }

    @Test
    void getSelectedLibraryPage_shouldTraverseStateBatches() {
        ProductOperationState firstState = buildState("APPROVED");
        firstState.setActivityId("10001");
        firstState.setProductId("9001");
        firstState.setSelectedToLibrary(true);

        ProductOperationState secondState = buildState("APPROVED");
        secondState.setActivityId("10001");
        secondState.setProductId("9002");
        secondState.setSelectedToLibrary(true);

        Page<ProductOperationState> firstPage = new Page<>(1, 200);
        firstPage.setTotal(201);
        firstPage.setRecords(List.of(firstState));
        Page<ProductOperationState> secondPage = new Page<>(2, 200);
        secondPage.setTotal(201);
        secondPage.setRecords(List.of(secondState));

        ProductSnapshot firstSnapshot = new ProductSnapshot();
        firstSnapshot.setActivityId("10001");
        firstSnapshot.setProductId("9001");
        firstSnapshot.setTitle("第一页商品");

        ProductSnapshot secondSnapshot = new ProductSnapshot();
        secondSnapshot.setActivityId("10001");
        secondSnapshot.setProductId("9002");
        secondSnapshot.setTitle("第二页商品");

        doAnswer(invocation -> {
            Page<ProductOperationState> requestedPage = invocation.getArgument(0);
            return requestedPage.getCurrent() <= 1 ? firstPage : secondPage;
        }).when(operationStateMapper).selectPage(any(Page.class), any());
        when(snapshotMapper.selectBatchIds(any()))
                .thenReturn(List.of(firstSnapshot))
                .thenReturn(List.of(secondSnapshot));

        var result = service.getSelectedLibraryPage(1, 10, null, null);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).extracting(com.colonel.saas.entity.Product::getName)
                .containsExactly("第一页商品", "第二页商品");
    }

    @Test
    void buildActivityProductListViewFromDb_shouldReturnEmptyWhenNonPendingBizStatusHasNoMatches() {
        when(operationStateMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.buildActivityProductListViewFromDb(
                "10001",
                20,
                null,
                null,
                "APPROVED"
        );

        assertThat(result).doesNotContainKey("test");
        assertThat(result.get("total")).isEqualTo(0L);
        assertThat(result.get("items")).isEqualTo(List.of());
        verify(snapshotMapper, never()).selectCount(any());
        verify(snapshotMapper, never()).selectPage(any(Page.class), any());
    }

    @Test
    void buildActivityProductListViewFromDb_shouldKeepPendingAuditProductsWithoutState() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setTitle("无状态商品");
        snapshot.setShopId(3001L);
        snapshot.setShopName("测试店铺");
        snapshot.setPriceText("99.00");

        Page<ProductSnapshot> snapshotPage = new Page<>(1, 20, 1);
        snapshotPage.setRecords(List.of(snapshot));

        when(operationStateMapper.selectList(any())).thenReturn(List.of());
        when(snapshotMapper.selectCount(any())).thenReturn(1L);
        when(snapshotMapper.selectPage(any(Page.class), any())).thenReturn(snapshotPage);
        when(operationLogMapper.selectList(any())).thenReturn(List.of());
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(promotionLinkMapper.selectList(any())).thenReturn(List.of());
        when(merchantMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.buildActivityProductListViewFromDb(
                "10001",
                20,
                null,
                null,
                "PENDING_AUDIT"
        );

        assertThat(result.get("total")).isEqualTo(1L);
        assertThat((Boolean) result.get("hasMore")).isFalse();
        assertThat((List<?>) result.get("items")).hasSize(1);
        Map<?, ?> item = (Map<?, ?>) ((List<?>) result.get("items")).get(0);
        assertThat(item.get("bizStatus")).isEqualTo("PENDING_AUDIT");
    }

    @Test
    void buildActivityProductListViewFromDb_shouldBatchLoadAssigneeNames() {
        UUID assigneeId = UUID.randomUUID();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setTitle("已分配商品");
        snapshot.setShopId(3001L);
        snapshot.setShopName("测试店铺");
        snapshot.setPriceText("99.00");

        ProductOperationState state = buildState("ASSIGNED");
        state.setAssigneeId(assigneeId);

        SysUser assignee = new SysUser();
        assignee.setId(assigneeId);
        assignee.setRealName("招商陈七");
        assignee.setUsername("chenqi");

        Page<ProductSnapshot> snapshotPage = new Page<>(1, 20, 1);
        snapshotPage.setRecords(List.of(snapshot));

        when(operationStateMapper.selectList(any())).thenReturn(List.of(state));
        when(snapshotMapper.selectCount(any())).thenReturn(1L);
        when(snapshotMapper.selectPage(any(Page.class), any())).thenReturn(snapshotPage);
        when(operationLogMapper.selectList(any())).thenReturn(List.of());
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(promotionLinkMapper.selectList(any())).thenReturn(List.of());
        when(merchantMapper.selectList(any())).thenReturn(List.of());
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(assignee));
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.ASSIGNED);

        Map<String, Object> result = service.buildActivityProductListViewFromDb(
                "10001",
                20,
                null,
                null,
                "ASSIGNED"
        );

        Map<?, ?> item = (Map<?, ?>) ((List<?>) result.get("items")).get(0);
        assertThat(item.get("assigneeName")).isEqualTo("招商陈七 (chenqi)");
        verify(sysUserMapper, never()).selectById(any());
    }

    @Test
    void buildActivityProductListViewFromDb_shouldExposeSelectedLibraryFields() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setTitle("已审核商品");
        snapshot.setShopId(3001L);
        snapshot.setShopName("测试店铺");
        snapshot.setPriceText("99.00");
        snapshot.setDetailUrl("https://example.com/detail");
        snapshot.setActivityCosRatioText("20%");

        ProductOperationState state = buildState("APPROVED");
        state.setSelectedToLibrary(true);

        Page<ProductSnapshot> snapshotPage = new Page<>(1, 20, 1);
        snapshotPage.setRecords(List.of(snapshot));

        when(operationStateMapper.selectList(any())).thenReturn(List.of(state));
        when(snapshotMapper.selectCount(any())).thenReturn(1L);
        when(snapshotMapper.selectPage(any(Page.class), any())).thenReturn(snapshotPage);
        when(operationLogMapper.selectList(any())).thenReturn(List.of());
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(promotionLinkMapper.selectList(any())).thenReturn(List.of());
        when(merchantMapper.selectList(any())).thenReturn(List.of());
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.APPROVED);

        Map<String, Object> result = service.buildActivityProductListViewFromDb(
                "10001",
                20,
                null,
                null,
                "APPROVED"
        );

        Map<?, ?> item = (Map<?, ?>) ((List<?>) result.get("items")).get(0);
        assertThat(item.get("selectedToLibrary")).isEqualTo(true);
        assertThat(item.get("libraryVisible")).isEqualTo(true);
        assertThat(item.get("bizStatus")).isEqualTo("APPROVED");
    }

    @Test
    void getActivityProductDetail_shouldUseSingleItemAggregates() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setTitle("详情商品");
        snapshot.setDetailUrl("https://example.com/detail");
        snapshot.setShopId(3001L);
        snapshot.setShopName("测试店铺");
        snapshot.setPrice(19900L);
        snapshot.setPriceText("199.00");
        snapshot.setActivityCosRatioText("20");
        snapshot.setAdServiceRatio("10");
        snapshot.setStatus(1);
        snapshot.setStatusText("推广中");

        ProductOperationState state = buildState("LINKED");
        state.setPromoteLink("https://promo.link");
        state.setShortLink("https://short.link");

        ProductOperationLog decisionLog = new ProductOperationLog();
        decisionLog.setActivityId("10001");
        decisionLog.setProductId("9001");
        decisionLog.setOperationType("DECISION");
        decisionLog.setOperationPayload("{decisionLevel=MAIN, decisionLabel=主推}");
        decisionLog.setOperationRemark("优先推进");
        decisionLog.setCreateTime(LocalDateTime.now());

        ColonelsettlementOrder order = new ColonelsettlementOrder();
        order.setActivityId("10001");
        order.setProductId("9001");
        order.setAttributionStatus("ATTRIBUTED");
        order.setOrderAmount(19900L);
        order.setSettleColonelCommission(2600L);
        order.setCreateTime(LocalDateTime.now());

        PromotionLink link = new PromotionLink();
        link.setProductId("9001");
        link.setActivityId("10001");
        link.setPromotionUrl("https://promo.link");
        link.setShortUrl("https://short.link");
        link.setCreatedAt(LocalDateTime.now());

        com.colonel.saas.entity.Merchant merchant = new com.colonel.saas.entity.Merchant();
        merchant.setShopId(3001L);
        merchant.setMerchantId("M001");
        merchant.setMerchantName("测试商家");
        merchant.setShopName("测试店铺");

        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(operationLogMapper.selectOne(any())).thenReturn(decisionLog);
        when(orderMapper.selectList(any())).thenReturn(List.of(order));
        when(promotionLinkMapper.selectList(any())).thenReturn(List.of(link));
        when(merchantMapper.selectOne(any())).thenReturn(merchant);
        when(talentFollowService.listByProduct("10001", "9001")).thenReturn(List.of());
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.LINKED);

        Map<String, Object> detail = service.getActivityProductDetail("10001", "9001");

        assertThat(detail.get("bizStatus")).isEqualTo("LINKED");
        assertThat(detail.get("promotionLinkCount")).isEqualTo(1);
        assertThat(detail.get("orderCount")).isEqualTo(1L);
        assertThat(detail.get("merchantName")).isEqualTo("测试商家");
        verify(operationLogMapper, never()).selectList(any());
        verify(merchantMapper, never()).selectList(any());
    }

    private ProductSnapshot buildSnapshot(UUID productId) {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(productId);
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setDetailUrl("https://example.com/detail");
        snapshot.setShopId(3001L);
        snapshot.setShopName("测试店铺");
        return snapshot;
    }

    private ProductOperationState buildState(String bizStatus) {
        ProductOperationState state = new ProductOperationState();
        state.setId(UUID.randomUUID());
        state.setActivityId("10001");
        state.setProductId("9001");
        state.setBizStatus(bizStatus);
        if ("APPROVED".equals(bizStatus)) {
            state.setAuditStatus(2);
        } else if ("REJECTED".equals(bizStatus)) {
            state.setAuditStatus(3);
        }
        return state;
    }

    private DouyinProductGateway.ActivityProductItem buildItem(long productId, String title) {
        return new DouyinProductGateway.ActivityProductItem(
                productId,
                title,
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
                "满100减20",
                "2026-04-01",
                "2026-05-01",
                "2026-04-01",
                "2026-05-01",
                "https://example.com/detail"
        );
    }
}
