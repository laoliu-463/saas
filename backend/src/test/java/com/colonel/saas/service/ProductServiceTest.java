package com.colonel.saas.service;

import com.colonel.saas.common.enums.ProductBizStatus;
import com.colonel.saas.common.exception.ForbiddenException;
import com.colonel.saas.common.result.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.entity.ColonelsettlementActivity;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.entity.PromotionLink;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.SysUser;
import com.colonel.saas.entity.TalentFollowRecord;
import com.colonel.saas.gateway.douyin.DouyinActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.LinkedHashMap;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.lenient;
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
    private ColonelsettlementActivityMapper colonelActivityMapper;
    @Mock
    private TalentFollowService talentFollowService;
    @Mock
    private DouyinActivityGateway douyinActivityGateway;
    @Mock
    private BusinessRuleConfigService businessRuleConfigService;
    private PromotionLinkIdempotencyService promotionLinkIdempotencyService;

    private ProductService service;

    @BeforeEach
    void setUp() {
        promotionLinkIdempotencyService = new PromotionLinkIdempotencyService(new com.fasterxml.jackson.databind.ObjectMapper());
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
                colonelActivityMapper,
                talentFollowService,
                douyinActivityGateway,
                promotionLinkIdempotencyService,
                businessRuleConfigService
        );
        lenient().when(operationStateMapper.updateById(any(ProductOperationState.class))).thenReturn(1);
        lenient().when(businessRuleConfigService.getPromotionPickExtraRule())
                .thenReturn(new BusinessRuleConfigService.PromotionPickExtraRuleConfig(
                        "channel_{channel_code}",
                        "none",
                        Map.of()));
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
    void refreshActivitySnapshots_shouldFollowCursorUntilNoNextPage() {
        when(snapshotMapper.selectById(any(UUID.class))).thenReturn(null);
        when(snapshotMapper.upsert(any(ProductSnapshot.class))).thenReturn(1);
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        List<DouyinProductGateway.ActivityProductItem> firstPageItems = java.util.stream.LongStream.rangeClosed(9001L, 9020L)
                .mapToObj(id -> buildItem(id, "第一页商品-" + id))
                .toList();
        when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false,
                        10001L,
                        30001L,
                        40L,
                        "cursor-2",
                        firstPageItems
                ))
                .thenReturn(new DouyinProductGateway.ActivityProductListResult(
                        false,
                        10001L,
                        30001L,
                        40L,
                        "",
                        List.of(buildItem(9021L, "第二页商品"))
                ));

        int refreshed = service.refreshActivitySnapshots(new DouyinProductGateway.ActivityProductQueryRequest(
                null,
                "10001",
                4L,
                1L,
                20,
                null,
                0,
                null,
                null,
                1L,
                null,
                null
        ));

        assertThat(refreshed).isEqualTo(21);
        verify(douyinProductGateway, times(2)).queryActivityProducts(any());
        verify(snapshotMapper, times(21)).upsert(any(ProductSnapshot.class));
        verify(productBizStatusService, times(21)).initStateIfAbsent(any(), any(), any(), any(), any(), any());
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
                fullAuditSupplement(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        assertThat(state.getSelectedToLibrary()).isTrue();
    }

    @Test
    void auditProduct_shouldRejectApprovalWithoutCompleteSupplement() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        ProductOperationState state = buildState("PENDING_AUDIT");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);

        assertThatThrownBy(() -> service.auditProduct(
                "10001",
                "9001",
                true,
                null,
                Map.of("exclusivePriceRemark", "专属价 129"),
                UUID.randomUUID(),
                UUID.randomUUID()
        ))
                .hasMessageContaining("审核通过前请补充")
                .hasMessageContaining("发货信息")
                .hasMessageContaining("手卡素材");

        assertThat(Boolean.TRUE.equals(state.getSelectedToLibrary())).isFalse();
        verify(productBizStatusService, never()).changeStatus(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void auditProduct_shouldPersistSupplementPayloadAndReturnLibraryVisible() {
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
                fullAuditSupplement(),
                operatorId,
                deptId
        );

        assertThat(state.getAuditPayload())
                .contains("\"exclusivePriceRemark\":\"专属价 129\"")
                .contains("\"rewardRemark\":\"达成目标有额外奖励\"")
                .contains("\"participationRequirements\":\"食品饮料达人优先\"")
                .contains("\"materialFiles\":[\"https://example.com/material.png\"]")
                .contains("\"sampleThresholdSales\":30000");
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
        snapshot.setRawPayload("{\"extra_data\":{\"colonel_buyin_id\":\"7351155267604218149\"}}");
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
        verify(pickSourceMappingService).saveOrUpdate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(productBizStatusService).changeStatus(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generatePromotionLink_shouldUseConfiguredPickExtraRule() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
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
        user.setChannelCode("leaderA");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(businessRuleConfigService.getPromotionPickExtraRule())
                .thenReturn(new BusinessRuleConfigService.PromotionPickExtraRuleConfig(
                        "{channel_code}_{product_id}",
                        "none",
                        Map.of("format", "{channel_code}_{product_id}")));
        when(douyinPromotionGateway.generateLink(any()))
                .thenAnswer(invocation -> {
                    DouyinPromotionGateway.PromotionLinkCommand command = invocation.getArgument(0);
                    return new DouyinPromotionGateway.PromotionLinkResult(
                            "abc12345",
                            command.context().pickExtra(),
                            "abc12345",
                            "https://s.link",
                            "https://p.link",
                            "seed"
                    );
                });

        DouyinPromotionGateway.PromotionLinkResult result = service.generatePromotionLink(
                "10001", "9001", userId, deptId, null, null, true
        );

        assertThat(result.pickExtra()).isEqualTo("leadera_9001");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<DouyinPromotionGateway.PromotionLinkCommand> commandCaptor =
                ArgumentCaptor.forClass(DouyinPromotionGateway.PromotionLinkCommand.class);
        verify(douyinPromotionGateway).generateLink(commandCaptor.capture());
        assertThat(commandCaptor.getValue().context().pickExtra()).isEqualTo("leadera_9001");
    }

    @Test
    void generatePromotionLink_withIdempotencyKey_shouldReturnCachedResultWithoutSecondGatewayCall() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
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
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(douyinPromotionGateway.generateLink(any()))
                .thenReturn(new DouyinPromotionGateway.PromotionLinkResult(
                        "idem-pick",
                        null,
                        "idem-short",
                        "https://s.link",
                        "https://p.link",
                        "seed"
                ));

        DouyinPromotionGateway.PromotionLinkResult first = service.generatePromotionLink(
                "10001", "9001", userId, deptId, null, null, true, null, null, "idem-key-1");
        DouyinPromotionGateway.PromotionLinkResult second = service.generatePromotionLink(
                "10001", "9001", userId, deptId, null, null, true, null, null, "idem-key-1");

        assertThat(second.pickSource()).isEqualTo(first.pickSource());
        verify(douyinPromotionGateway, times(1)).generateLink(any());
        verify(promotionLinkMapper, times(1)).insert(any(PromotionLink.class));
    }

    @Test
    void generatePromotionLink_shouldAllowApprovedLibraryProduct() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("100018");
        snapshot.setProductId("10901827");
        snapshot.setDetailUrl("https://example.com/detail");
        snapshot.setRawPayload("{\"colonel_buyin_id\":\"46128341673481018\"}");
        ProductOperationState state = new ProductOperationState();
        state.setActivityId("100018");
        state.setProductId("10901827");
        state.setBizStatus("APPROVED");
        state.setSelectedToLibrary(true);
        SysUser user = new SysUser();
        user.setRealName("渠道专员测试");
        user.setChannelCode("channelstaff");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.APPROVED);
        when(douyinPromotionGateway.generateLink(any()))
                .thenReturn(new DouyinPromotionGateway.PromotionLinkResult(
                        "MOCK123456",
                        "channel_channelstaff",
                        "MOCK123456",
                        "https://test.short.link/MOCK123456",
                        "https://test.promote.link/activity/100018/product/10901827?pick_source=MOCK123456",
                        "seed"
                ));

        DouyinPromotionGateway.PromotionLinkResult result = service.generatePromotionLink(
                "100018", "10901827", userId, deptId, null, null, true, "PRODUCT_LIBRARY", null
        );

        assertThat(result.promoteLink()).contains("test.promote.link");
        verify(douyinPromotionGateway).generateLink(any());
        verify(promotionLinkMapper).insert(any(PromotionLink.class));
        verify(productBizStatusService).changeStatus(
                eq(state),
                eq(ProductBizStatus.LINKED),
                eq("PROMOTION_LINK"),
                eq(userId),
                eq(deptId),
                any(),
                eq("转链成功"),
                any()
        );
    }

    @Test
    void generatePromotionLink_shouldPreferSnapshotOriginBuyinIdBeforeActivityLevelBuyin() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("3859423");
        snapshot.setProductId("3816127512791089531");
        snapshot.setDetailUrl("https://example.com/detail");
        snapshot.setRawPayload("{\"origin_colonel_buyin_id\":\"7293293346398011698\",\"colonel_buyin_id\":\"7351155267604218149\"}");
        ProductOperationState state = new ProductOperationState();
        state.setActivityId("3859423");
        state.setProductId("3816127512791089531");
        state.setBizStatus("ASSIGNED");
        state.setSelectedToLibrary(true);
        SysUser user = new SysUser();
        user.setRealName("渠道A");
        user.setChannelCode("channela");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(douyinPromotionGateway.generateLink(any())).thenReturn(
                new DouyinPromotionGateway.PromotionLinkResult(
                        "SID12345",
                        "channel_channela",
                        "SID12345",
                        "https://s.link",
                        "https://p.link",
                        "seed"
                )
        );

        service.generatePromotionLink("3859423", "3816127512791089531", userId, deptId, null, null, true);

        ArgumentCaptor<String> buyinCaptor = ArgumentCaptor.forClass(String.class);
        verify(pickSourceMappingService).saveOrUpdate(
                eq(userId),
                any(),
                eq(deptId),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq("3816127512791089531"),
                eq("3859423"),
                any(),
                any(),
                any(),
                any(),
                any(),
                buyinCaptor.capture(),
                eq(PickSourceMappingService.SOURCE_TYPE_NATIVE)
        );
        assertThat(buyinCaptor.getValue()).isEqualTo("7293293346398011698");
        verify(colonelActivityMapper, never()).selectByActivityId("3859423");
        verify(colonelActivityMapper, never()).selectExtraDataByActivityId("3859423");
        verify(douyinActivityGateway, never()).activityDetail(any(), any());
    }

    @Test
    void generatePromotionLink_shouldPersistNativeMappingWhenLinkedProductIsRetried() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("3859423");
        snapshot.setProductId("3816127512791089531");
        snapshot.setDetailUrl("https://example.com/detail");
        snapshot.setRawPayload("{\"origin_colonel_buyin_id\":\"7293293346398011698\"}");
        ProductOperationState state = new ProductOperationState();
        state.setId(UUID.randomUUID());
        state.setActivityId("3859423");
        state.setProductId("3816127512791089531");
        state.setBizStatus("LINKED");
        state.setSelectedToLibrary(true);
        SysUser user = new SysUser();
        user.setRealName("渠道A");
        user.setChannelCode("channela");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(douyinPromotionGateway.generateLink(any())).thenReturn(
                new DouyinPromotionGateway.PromotionLinkResult(
                        "SID12345",
                        "channel_channela",
                        "SID12345",
                        "https://s.link",
                        "https://p.link",
                        "seed"
                )
        );

        DouyinPromotionGateway.PromotionLinkResult result =
                service.generatePromotionLink("3859423", "3816127512791089531", userId, deptId, null, null, true);

        assertThat(result.promoteLink()).isEqualTo("https://p.link");
        verify(promotionLinkMapper).insert(any(PromotionLink.class));
        verify(operationStateMapper).updateById(state);
        verify(productBizStatusService).logStatusChange(
                eq("3859423"),
                eq("3816127512791089531"),
                eq("PROMOTION_LINK"),
                eq(ProductBizStatus.LINKED),
                eq(ProductBizStatus.LINKED),
                eq(userId),
                eq(deptId),
                any(),
                eq("已转链商品重新生成推广链接"),
                eq(true),
                eq(null)
        );
        verify(productBizStatusService, never()).changeStatus(any(), any(), any(), any(), any(), any(), any(), any());
        verify(pickSourceMappingService).saveOrUpdate(
                eq(userId),
                any(),
                eq(deptId),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq("3816127512791089531"),
                eq("3859423"),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq("7293293346398011698"),
                eq(PickSourceMappingService.SOURCE_TYPE_NATIVE)
        );
    }

    @Test
    void generatePromotionLink_shouldHydrateActivityDetailWhenLocalActivityMissing() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("3859423");
        snapshot.setProductId("3816127512791089531");
        snapshot.setDetailUrl("https://example.com/detail");
        ProductOperationState state = new ProductOperationState();
        state.setActivityId("3859423");
        state.setProductId("3816127512791089531");
        state.setBizStatus("ASSIGNED");
        state.setSelectedToLibrary(true);
        SysUser user = new SysUser();
        user.setRealName("渠道A");
        user.setChannelCode("channela");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(sysUserMapper.selectById(userId)).thenReturn(user);
        when(colonelActivityMapper.selectByActivityId("3859423")).thenReturn(null);
        when(colonelActivityMapper.selectExtraDataByActivityId("3859423")).thenReturn(Map.of());
        when(douyinActivityGateway.activityDetail(null, "3859423")).thenReturn(Map.of(
                "data", Map.of(
                        "colonel_buyin_id", "7351155267604218149",
                        "activity_name", "活动A",
                        "shop_id", "123",
                        "shop_name", "店铺A",
                        "commission_rate", "10",
                        "service_rate", "5",
                        "status", "ONLINE"
                )
        ));
        when(douyinPromotionGateway.generateLink(any())).thenReturn(
                new DouyinPromotionGateway.PromotionLinkResult(
                        "SID12345",
                        "channel_channela",
                        "SID12345",
                        "https://s.link",
                        "https://p.link",
                        "seed"
                )
        );

        service.generatePromotionLink("3859423", "3816127512791089531", userId, deptId, null, null, true);

        ArgumentCaptor<BigDecimal> commissionRateCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BigDecimal> serviceRateCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(colonelActivityMapper).upsertRealActivityMeta(
                any(),
                eq("3859423"),
                any(),
                any(),
                any(),
                eq(7351155267604218149L),
                commissionRateCaptor.capture(),
                serviceRateCaptor.capture(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
        assertThat(commissionRateCaptor.getValue()).isEqualByComparingTo("0.1000");
        assertThat(serviceRateCaptor.getValue()).isEqualByComparingTo("0.0500");
        verify(pickSourceMappingService).saveOrUpdate(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq("7351155267604218149"), eq(PickSourceMappingService.SOURCE_TYPE_NATIVE));
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
    void getPromotionLinkHistory_shouldReturnEmptyForBlankProductId() {
        PageResult<Map<String, Object>> result = service.getPromotionLinkHistory(" ", 0, 0);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
        verify(promotionLinkMapper, never()).selectList(any());
    }

    @Test
    void getPromotionLinkHistory_shouldReturnEmptyWhenNoLinks() {
        when(promotionLinkMapper.selectList(any())).thenReturn(List.of());

        PageResult<Map<String, Object>> result = service.getPromotionLinkHistory("3810562766247428542", 1, 10);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    void getPromotionLinkHistory_shouldReturnEmptyWhenPageOutOfRange() {
        PromotionLink first = new PromotionLink();
        first.setId(UUID.randomUUID());
        first.setProductId("3810562766247428542");
        PromotionLink second = new PromotionLink();
        second.setId(UUID.randomUUID());
        second.setProductId("3810562766247428542");
        when(promotionLinkMapper.selectList(any())).thenReturn(List.of(first, second));

        PageResult<Map<String, Object>> result = service.getPromotionLinkHistory("3810562766247428542", 3, 2);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).isEmpty();
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
    void bindActivity_shouldRejectWhenAssignedProductBelongsToOtherDept() {
        UUID operatorId = UUID.randomUUID();
        UUID operatorDeptId = UUID.randomUUID();
        UUID otherDeptId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        ProductOperationState state = buildState("APPROVED");
        state.setAssigneeId(assigneeId);
        SysUser assignee = new SysUser();
        assignee.setId(assigneeId);
        assignee.setDeptId(otherDeptId);

        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(sysUserMapper.selectById(assigneeId)).thenReturn(assignee);

        assertThatThrownBy(() -> service.bindActivity("10001", "9001", "20002", operatorId, operatorDeptId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权跨部门修改商品绑定活动");

        verify(operationStateMapper, never()).updateById(any(ProductOperationState.class));
        verify(operationLogMapper, never()).insert(any(ProductOperationLog.class));
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
    void assignProduct_shouldRepairApprovedProductLibraryFlagBeforeAssigning() {
        UUID assigneeId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setShopId(3001L);
        ProductOperationState state = buildState("APPROVED");
        state.setSelectedToLibrary(false);
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.APPROVED);

        service.assignProduct("10001", "9001", assigneeId, operatorId, deptId);

        assertThat(state.getSelectedToLibrary()).isTrue();
        assertThat(state.getSelectedBy()).isEqualTo(operatorId);
        assertThat(state.getSelectedAt()).isNotNull();
        verify(operationStateMapper).updateById(state);
        verify(productBizStatusService).changeStatus(
                eq(state),
                eq(ProductBizStatus.ASSIGNED),
                eq("ASSIGN"),
                eq(operatorId),
                eq(deptId),
                any(),
                eq("分配招商成功"),
                any()
        );
    }

    @Test
    void assignAuditOwner_shouldAssignPendingAuditReviewerWithoutChangingBizStatus() {
        UUID assigneeId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setShopId(3001L);
        ProductOperationState state = buildState("PENDING_AUDIT");
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
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.PENDING_AUDIT);

        Map<String, Object> result = service.assignAuditOwner("10001", "9001", assigneeId, operatorId, deptId);

        assertThat(state.getAssigneeId()).isEqualTo(assigneeId);
        assertThat(state.getBizStatus()).isEqualTo("PENDING_AUDIT");
        assertThat(result.get("bizStatus")).isEqualTo("PENDING_AUDIT");
        assertThat(result.get("assigneeId")).isEqualTo(assigneeId);
        assertThat(result.get("assigneeName")).isEqualTo("招商李四 (lisi)");
        verify(operationStateMapper).updateById(state);
        verify(productBizStatusService, never()).changeStatus(any(), any(), any(), any(), any(), any(), any(), any());

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(productBizStatusService).logStatusChange(
                eq("10001"),
                eq("9001"),
                eq("ASSIGN_AUDIT"),
                eq(ProductBizStatus.PENDING_AUDIT),
                eq(ProductBizStatus.PENDING_AUDIT),
                eq(operatorId),
                eq(deptId),
                payloadCaptor.capture(),
                eq("分配审核人成功"),
                eq(true),
                eq(null)
        );
        assertThat(payloadCaptor.getValue())
                .containsEntry("assigneeId", assigneeId)
                .containsEntry("assigneeName", "招商李四 (lisi)")
                .containsEntry("operatorId", operatorId)
                .containsEntry("operatorName", "组长张三 (zhangsan)")
                .containsEntry("eventLabel", "商品已分配给审核负责人");
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
    void putIntoLibrary_shouldReturnExistingDetailWhenAlreadySelected() {
        UUID operatorId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ProductSnapshot snapshot = selectedLibrarySnapshot("10001", "9001", "已入库商品");
        ProductOperationState state = buildState("APPROVED");
        state.setAuditStatus(2);
        state.setSelectedToLibrary(true);
        stubActivityProductDetail(snapshot, state, ProductBizStatus.APPROVED);

        Map<String, Object> result = service.putIntoLibrary("10001", "9001", operatorId, deptId);

        assertThat(result.get("selectedToLibrary")).isEqualTo(true);
        assertThat(result.get("libraryVisible")).isEqualTo(true);
        verify(operationStateMapper, never()).updateById(any());
        verify(operationLogMapper, never()).insert(any());
    }

    @Test
    void putIntoLibrary_shouldPersistExistingApprovedStateAndWriteLog() {
        UUID operatorId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ProductSnapshot snapshot = selectedLibrarySnapshot("10001", "9001", "已审核商品");
        ProductOperationState state = buildState("APPROVED");
        state.setAuditStatus(2);
        stubActivityProductDetail(snapshot, state, ProductBizStatus.APPROVED);

        Map<String, Object> result = service.putIntoLibrary("10001", "9001", operatorId, deptId);

        assertThat(result.get("selectedToLibrary")).isEqualTo(true);
        assertThat(state.getSelectedToLibrary()).isTrue();
        verify(operationStateMapper).updateById(state);
        verify(operationLogMapper).insert(any(ProductOperationLog.class));
    }

    @Test
    void putIntoLibrary_shouldInsertNewApprovedStateAndWriteLog() {
        UUID operatorId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        ProductSnapshot snapshot = selectedLibrarySnapshot("10001", "9001", "新状态商品");
        ProductOperationState state = buildState("APPROVED");
        state.setId(null);
        state.setAuditStatus(2);
        stubActivityProductDetail(snapshot, state, ProductBizStatus.APPROVED);

        Map<String, Object> result = service.putIntoLibrary("10001", "9001", operatorId, deptId);

        assertThat(result.get("selectedToLibrary")).isEqualTo(true);
        assertThat(state.getSelectedBy()).isEqualTo(operatorId);
        verify(operationStateMapper).insert(state);
        verify(operationLogMapper).insert(any(ProductOperationLog.class));
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
    void getPage_withStatusFilter_shouldReturnEmptyPageSafely() {
        Page<ProductSnapshot> snapshotPage = new Page<>(1, 1, 0);
        snapshotPage.setRecords(List.of());
        when(snapshotMapper.selectPage(any(Page.class), any())).thenReturn(snapshotPage);

        var result = service.getPage(0, 0, 1);

        assertThat(result.getCurrent()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getRecords()).isEmpty();
        verify(operationStateMapper, never()).selectList(any());
        verify(sysUserMapper, never()).selectBatchIds(any());
    }

    @Test
    void getPage_withAssignee_shouldLoadAssignedStatesAndSkipMissingSnapshots() {
        UUID assigneeId = UUID.randomUUID();
        ProductOperationState matchedState = buildState("APPROVED");
        matchedState.setAssigneeId(assigneeId);
        ProductOperationState missingSnapshotState = buildState("APPROVED");
        missingSnapshotState.setProductId("9002");
        missingSnapshotState.setAssigneeId(assigneeId);

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setTitle("负责人商品");

        SysUser assignee = new SysUser();
        assignee.setId(assigneeId);
        assignee.setUsername("owner_user");

        Page<ProductOperationState> statePage = new Page<>(1, 10, 2);
        statePage.setRecords(List.of(matchedState, missingSnapshotState));
        when(operationStateMapper.selectPage(any(Page.class), any())).thenReturn(statePage);
        when(snapshotMapper.selectBatchIds(any())).thenReturn(List.of(snapshot));
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(assignee));

        var result = service.getPage(1, 10, null, assigneeId);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getName()).isEqualTo("负责人商品");
        assertThat(result.getRecords().get(0).getAssigneeName()).isEqualTo("owner_user");
        assertThat(result.getRecords().get(0).getBizStatus()).isEqualTo(ProductBizStatus.PENDING_AUDIT.name());
        verify(snapshotMapper, never()).selectPage(any(Page.class), any());
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
    void getSelectedLibraryPage_shouldSupportKeywordSearchByProductId() {
        ProductSnapshot selected = new ProductSnapshot();
        selected.setId(UUID.randomUUID());
        selected.setActivityId("10001");
        selected.setProductId("3811666925088539057");
        selected.setTitle("寄样候选商品");

        ProductOperationState selectedState = buildState("APPROVED");
        selectedState.setActivityId("10001");
        selectedState.setProductId("3811666925088539057");
        selectedState.setSelectedToLibrary(true);

        Page<ProductOperationState> statePage = new Page<>(1, 200, 1);
        statePage.setRecords(List.of(selectedState));
        when(operationStateMapper.selectPage(any(Page.class), any())).thenReturn(statePage);
        when(snapshotMapper.selectBatchIds(any())).thenReturn(List.of(selected));
        when(productBizStatusService.readBizStatus(selectedState)).thenReturn(ProductBizStatus.APPROVED);

        var result = service.getSelectedLibraryPage(1, 10, "3811666925088539057", null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getProductId()).isEqualTo("3811666925088539057");
        assertThat(result.getRecords().get(0).getName()).isEqualTo("寄样候选商品");
    }

    @Test
    void getSelectedLibraryPage_shouldApplyServerSideAdvancedFilters() {
        ProductSnapshot matched = new ProductSnapshot();
        matched.setId(UUID.randomUUID());
        matched.setActivityId("10001");
        matched.setProductId("9001");
        matched.setTitle("高佣食品");
        matched.setShopName("好店铺");
        matched.setCategoryName("食品饮料");
        matched.setStatus(1);
        matched.setStatusText("推广中");
        matched.setSales(40000L);
        matched.setActivityCosRatioText("25%");
        matched.setHasDouinGoodsTag(true);

        ProductSnapshot ignored = new ProductSnapshot();
        ignored.setId(UUID.randomUUID());
        ignored.setActivityId("10001");
        ignored.setProductId("9002");
        ignored.setTitle("低佣商品");
        ignored.setShopName("普通店铺");
        ignored.setCategoryName("数码家电");
        ignored.setStatus(2);
        ignored.setStatusText("申请未通过");
        ignored.setSales(10L);
        ignored.setActivityCosRatioText("5%");

        ProductOperationState matchedState = buildState("LINKED");
        matchedState.setActivityId("10001");
        matchedState.setProductId("9001");
        matchedState.setSelectedToLibrary(true);
        matchedState.setPromoteLink("https://example.com/link");

        ProductOperationState ignoredState = buildState("APPROVED");
        ignoredState.setActivityId("10001");
        ignoredState.setProductId("9002");
        ignoredState.setSelectedToLibrary(true);

        Page<ProductOperationState> statePage = new Page<>(1, 200, 2);
        statePage.setRecords(List.of(matchedState, ignoredState));
        when(operationStateMapper.selectPage(any(Page.class), any())).thenReturn(statePage);
        when(snapshotMapper.selectBatchIds(any())).thenReturn(List.of(matched, ignored));
        when(productBizStatusService.readBizStatus(matchedState)).thenReturn(ProductBizStatus.LINKED);
        when(productBizStatusService.readBizStatus(ignoredState)).thenReturn(ProductBizStatus.APPROVED);

        var result = service.getSelectedLibraryPage(1, 10, new ProductService.SelectedLibraryFilter(
                null,
                null,
                "好店",
                "食品",
                "gte30000",
                "LINKED",
                "promoting",
                "gt20",
                "1",
                null,
                "traffic",
                null
        ));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement()
                .satisfies(product -> {
                    assertThat(product.getProductId()).isEqualTo("9001");
                    assertThat(product.getCategoryName()).isEqualTo("食品饮料");
                    assertThat(product.getStatusText()).isEqualTo("推广中");
                    assertThat(product.getSales30d()).isEqualTo(40000L);
                });
    }

    @Test
    void getSelectedLibraryPage_shouldReturnLinkedStateWithoutLinkForFailedPromotionFilter() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setTitle("已转链但缺少链接商品");
        snapshot.setShopName("测试店铺");

        ProductOperationState state = buildState("LINKED");
        state.setActivityId("10001");
        state.setProductId("9001");
        state.setSelectedToLibrary(true);

        Page<ProductOperationState> statePage = new Page<>(1, 200, 1);
        statePage.setRecords(List.of(state));
        when(operationStateMapper.selectPage(any(Page.class), any())).thenReturn(statePage);
        when(snapshotMapper.selectBatchIds(any())).thenReturn(List.of(snapshot));
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.LINKED);

        var result = service.getSelectedLibraryPage(1, 10, new ProductService.SelectedLibraryFilter(
                null,
                null,
                null,
                null,
                null,
                "FAILED",
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement()
                .satisfies(product -> assertThat(product.getProductId()).isEqualTo("9001"));
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
    void getSelectedLibraryPage_withNullFilterAndNullStateRecords_shouldReturnEmptyNormalizedPage() {
        Page<ProductOperationState> statePage = new Page<>(1, 200, 0);
        statePage.setRecords(null);
        when(operationStateMapper.selectPage(any(Page.class), any())).thenReturn(statePage);

        var result = service.getSelectedLibraryPage(0, 0, (ProductService.SelectedLibraryFilter) null);

        assertThat(result.getCurrent()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
        verify(snapshotMapper, never()).selectBatchIds(any());
    }

    @Test
    void getSelectedLibraryPage_shouldSkipStateWhenSnapshotIsMissing() {
        ProductOperationState state = buildState("APPROVED");
        state.setActivityId("10001");
        state.setProductId("9001");
        state.setSelectedToLibrary(true);

        Page<ProductOperationState> statePage = new Page<>(1, 200, 1);
        statePage.setRecords(List.of(state));
        when(operationStateMapper.selectPage(any(Page.class), any())).thenReturn(statePage);
        when(snapshotMapper.selectBatchIds(any())).thenReturn(List.of());

        var result = service.getSelectedLibraryPage(1, 10, null, null);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
        verify(productBizStatusService, never()).readBizStatus(any());
    }

    @Test
    void getSelectedLibraryPage_shouldMatchPendingUnassignedNewProductWithoutDecision() {
        ProductSnapshot snapshot = selectedLibrarySnapshot("10001", "9001", "新品待审");
        snapshot.setStatus(0);
        snapshot.setSales(20L);
        snapshot.setActivityCosRatioText("5%");

        ProductOperationState state = selectedLibraryState("APPROVED", "10001", "9001");
        stubSingleSelectedLibraryPage(snapshot, state, ProductBizStatus.APPROVED);
        when(operationLogMapper.selectOne(any())).thenReturn(null);

        var result = service.getSelectedLibraryPage(1, 10, new ProductService.SelectedLibraryFilter(
                null,
                null,
                null,
                null,
                "lt100",
                "PENDING",
                "pending_audit",
                "lt10",
                "0",
                "unassigned",
                "new",
                "NONE"
        ));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement()
                .satisfies(product -> {
                    assertThat(product.getProductId()).isEqualTo("9001");
                    assertThat(product.getHasSampleRule()).isFalse();
                    assertThat(product.getSales30d()).isEqualTo(20L);
                });
    }

    @Test
    void getSelectedLibraryPage_shouldMatchRejectedTextMidCommissionAssignedAndDecision() {
        UUID assigneeId = UUID.randomUUID();
        ProductSnapshot snapshot = selectedLibrarySnapshot("10001", "9001", "审核未过商品");
        snapshot.setStatusText("申请未通过");
        snapshot.setPromotionEndTime("2026-12-31");
        snapshot.setSales(500L);
        snapshot.setActivityCosRatioText("15%");

        ProductOperationState state = selectedLibraryState("APPROVED", "10001", "9001");
        state.setAssigneeId(assigneeId);
        ProductOperationLog decisionLog = new ProductOperationLog();
        decisionLog.setActivityId("10001");
        decisionLog.setProductId("9001");
        decisionLog.setOperationType("DECISION");
        decisionLog.setOperationPayload("{decisionLevel=MAIN, decisionLabel=主推}");
        decisionLog.setOperationRemark("优先推进");
        decisionLog.setCreateTime(LocalDateTime.now());

        stubSingleSelectedLibraryPage(snapshot, state, ProductBizStatus.APPROVED);
        when(operationLogMapper.selectOne(any())).thenReturn(decisionLog);

        var result = service.getSelectedLibraryPage(1, 10, new ProductService.SelectedLibraryFilter(
                null,
                null,
                null,
                null,
                "100_999",
                null,
                "rejected",
                "10_20",
                "1",
                "assigned",
                "unknown_tag",
                "MAIN"
        ));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement()
                .satisfies(product -> {
                    assertThat(product.getProductId()).isEqualTo("9001");
                    assertThat(product.getAssigneeId()).isEqualTo(assigneeId);
                    assertThat(product.getHasSampleRule()).isTrue();
                });
    }

    @Test
    void getSelectedLibraryPage_shouldMatchMidSalesHighPriceAndTerminatedStatus() {
        ProductSnapshot snapshot = selectedLibrarySnapshot("10001", "9001", "高客单商品");
        snapshot.setStatus(3);
        snapshot.setSales(1500L);
        snapshot.setPriceText("¥399.99");

        ProductOperationState state = selectedLibraryState("APPROVED", "10001", "9001");
        stubSingleSelectedLibraryPage(snapshot, state, ProductBizStatus.APPROVED);

        var result = service.getSelectedLibraryPage(1, 10, new ProductService.SelectedLibraryFilter(
                null,
                null,
                null,
                null,
                "1k_29k",
                null,
                "terminated",
                "unknown",
                null,
                null,
                "high_price",
                null
        ));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement()
                .satisfies(product -> assertThat(product.getProductId()).isEqualTo("9001"));
    }

    @Test
    void getSelectedLibraryPage_shouldMatchExpiredHighCommissionAndLargeSales() {
        ProductSnapshot snapshot = selectedLibrarySnapshot("10001", "9001", "高佣过期商品");
        snapshot.setStatusText("已过期");
        snapshot.setSales(30000L);
        snapshot.setActivityCosRatioText("20%");

        ProductOperationState state = selectedLibraryState("APPROVED", "10001", "9001");
        stubSingleSelectedLibraryPage(snapshot, state, ProductBizStatus.APPROVED);

        var result = service.getSelectedLibraryPage(1, 10, new ProductService.SelectedLibraryFilter(
                null,
                null,
                null,
                null,
                "gte30000",
                null,
                "expired",
                "gt20",
                null,
                null,
                "high_commission",
                null
        ));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement()
                .satisfies(product -> assertThat(product.getSystemTags()).contains("高佣"));
    }

    @Test
    void hasActivitySnapshots_shouldReturnFalseForNullCountAndTrueForPositiveCount() {
        when(snapshotMapper.selectCount(any())).thenReturn(null).thenReturn(2L);

        assertThat(service.hasActivitySnapshots("10001")).isFalse();
        assertThat(service.hasActivitySnapshots("10001")).isTrue();
    }

    @Test
    void listActivityProductSkus_shouldReturnEmptyForBlankProductIdWithoutGatewayCall() {
        assertThat(service.listActivityProductSkus(" ")).isEmpty();

        verify(douyinProductGateway, never()).queryProductSkus(any());
    }

    @Test
    void listActivityProductSkus_shouldMapSkuRowsAndFormatFenPrice() {
        when(douyinProductGateway.queryProductSkus("9001")).thenReturn(List.of(
                new DouyinProductGateway.ProductSkuResult("sku-1", "红色", 12345L, 8, "https://cover"),
                new DouyinProductGateway.ProductSkuResult("sku-2", "赠品", null, 0, null)
        ));

        List<Map<String, Object>> rows = service.listActivityProductSkus(" 9001 ");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0))
                .containsEntry("skuId", "sku-1")
                .containsEntry("skuName", "红色")
                .containsEntry("priceText", "¥123.45")
                .containsEntry("stock", 8)
                .containsEntry("cover", "https://cover");
        assertThat(rows.get(1)).containsEntry("priceText", "-");
    }

    @Test
    void listActivityProductSkus_shouldReturnEmptyWhenGatewayThrows() {
        when(douyinProductGateway.queryProductSkus("9001")).thenThrow(new RuntimeException("timeout"));

        assertThat(service.listActivityProductSkus("9001")).isEmpty();
    }

    @Test
    void getOperationLogs_shouldNormalizePageAndSize() {
        ProductOperationLog log = new ProductOperationLog();
        log.setActivityId("10001");
        log.setProductId("9001");
        log.setOperationType("AUDIT");
        when(operationLogMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<ProductOperationLog> query = invocation.getArgument(0);
            query.setTotal(1);
            query.setRecords(List.of(log));
            return query;
        });

        var result = service.getOperationLogs("10001", "9001", 0, 0);

        assertThat(result.getCurrent()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement()
                .satisfies(item -> assertThat(item.getOperationType()).isEqualTo("AUDIT"));
    }

    @Test
    void getById_shouldReturnLegacyProductAndThrowWhenMissing() {
        UUID snapshotId = UUID.randomUUID();
        ProductSnapshot snapshot = buildSnapshot(snapshotId);
        snapshot.setActivityId("not-a-uuid");
        snapshot.setProductId("9001");
        snapshot.setTitle("本地商品");
        when(snapshotMapper.selectById(snapshotId)).thenReturn(snapshot).thenReturn(null);

        var product = service.getById(snapshotId);

        assertThat(product.getName()).isEqualTo("本地商品");
        assertThat(product.getActivityId()).isNull();
        assertThatThrownBy(() -> service.getById(snapshotId))
                .hasMessageContaining("商品不存在");
    }

    @Test
    void buildActivityProductListViewFromDb_shouldAggregateOrdersPromotionLinksAndTags() {
        LocalDateTime now = LocalDateTime.now();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setTitle("聚合商品");
        snapshot.setShopId(3001L);
        snapshot.setShopName("测试店铺");
        snapshot.setStatus(1);
        snapshot.setPrice(19900L);
        snapshot.setPriceText("199.00");
        snapshot.setDetailUrl("https://example.com/detail");
        snapshot.setActivityCosRatioText("25%");
        snapshot.setAdServiceRatio("12%");
        snapshot.setSales(1200L);
        snapshot.setHasDouinGoodsTag(true);
        snapshot.setProductStock("9件");
        snapshot.setPromotionEndTime(now.plusDays(2).toString());

        ProductOperationState state = buildState("LINKED");
        state.setPromoteLink("https://promo.latest");
        state.setShortLink("https://short.latest");

        Page<ProductSnapshot> snapshotPage = new Page<>(1, 20, 1);
        snapshotPage.setRecords(List.of(snapshot));

        ColonelsettlementOrder attributed = new ColonelsettlementOrder();
        attributed.setProductId("9001");
        attributed.setAttributionStatus("ATTRIBUTED");
        attributed.setOrderAmount(10000L);
        attributed.setSettleColonelCommission(1000L);
        attributed.setCreateTime(now.minusDays(2));

        ColonelsettlementOrder unattributed = new ColonelsettlementOrder();
        unattributed.setProductId("9001");
        unattributed.setAttributionStatus("UNATTRIBUTED");
        unattributed.setOrderAmount(20000L);
        unattributed.setSettleColonelCommission(2500L);
        unattributed.setCreateTime(now.minusDays(3));
        unattributed.setSettleTime(now.minusDays(1));

        ColonelsettlementOrder ignored = new ColonelsettlementOrder();
        ignored.setProductId(" ");
        ignored.setOrderAmount(99900L);

        java.util.ArrayList<PromotionLink> links = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            PromotionLink link = new PromotionLink();
            link.setId(UUID.randomUUID());
            link.setActivityId("10001");
            link.setProductId("9001");
            link.setChannelUserId(UUID.randomUUID());
            link.setChannelUserName("达人" + i);
            link.setPickSource("MANUAL");
            link.setPromotionUrl("https://promo/" + i);
            link.setShortUrl("https://short/" + i);
            link.setLinkStatus("SUCCESS");
            link.setCreatedAt(now.minusHours(i));
            link.setExpireTime(now.plusDays(30));
            links.add(link);
        }
        PromotionLink ignoredLink = new PromotionLink();
        ignoredLink.setProductId(" ");
        links.add(ignoredLink);

        when(snapshotMapper.selectCount(any())).thenReturn(1L);
        when(snapshotMapper.selectPage(any(Page.class), any())).thenReturn(snapshotPage);
        when(operationStateMapper.selectList(any())).thenReturn(List.of(state));
        when(operationLogMapper.selectList(any())).thenReturn(List.of());
        when(orderMapper.selectList(any())).thenReturn(List.of(attributed, unattributed, ignored));
        when(promotionLinkMapper.selectList(any())).thenReturn(links);
        when(merchantMapper.selectList(any())).thenReturn(List.of());
        when(productBizStatusService.readBizStatus(state)).thenReturn(ProductBizStatus.LINKED);

        Map<String, Object> result = service.buildActivityProductListViewFromDb(
                "10001",
                50,
                "bad-cursor",
                "聚合",
                "",
                1
        );

        assertThat(result.get("total")).isEqualTo(1L);
        assertThat(result.get("hasMore")).isEqualTo(false);
        Map<?, ?> item = (Map<?, ?>) ((List<?>) result.get("items")).get(0);
        assertThat(item.get("orderCount")).isEqualTo(2L);
        assertThat(item.get("attributedCount")).isEqualTo(1L);
        assertThat(item.get("unattributedCount")).isEqualTo(1L);
        assertThat(item.get("gmv")).isEqualTo("300.00");
        assertThat(item.get("serviceFee")).isEqualTo("35.00");
        assertThat(item.get("promotionLinkCount")).isEqualTo(12);
        assertThat(item.get("promotionLinkStatus")).isEqualTo("READY");
        assertThat(item.get("promotionLinkGeneratedAt")).isEqualTo(now.toString());
        @SuppressWarnings("unchecked")
        List<Object> systemTags = (List<Object>) item.get("systemTags");
        @SuppressWarnings("unchecked")
        List<Object> alertTags = (List<Object>) item.get("alertTags");
        assertThat(systemTags)
                .contains("高佣", "高服务费", "高销量", "抖音商品标", "活动临期", "已转链", "已有推广记录");
        assertThat(alertTags).contains("库存不足");
    }

    @Test
    void buildActivityProductListViewFromDb_shouldReturnEmptyWhenNonPendingBizStatusHasNoMatches() {
        when(operationStateMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.buildActivityProductListViewFromDb(
                "10001",
                20,
                null,
                null,
                "APPROVED",
                null
        );

        assertThat(result).doesNotContainKey("test");
        assertThat(result.get("total")).isEqualTo(0L);
        assertThat(result.get("items")).isEqualTo(List.of());
        verify(snapshotMapper, never()).selectCount(any());
        verify(snapshotMapper, never()).selectPage(any(Page.class), any());
    }

    @Test
    void bizStatusFilter_shouldCoverAllFilterModes() {
        ProductOperationState approved = buildState("APPROVED");
        approved.setProductId("9001");
        ProductOperationState rejected = buildState("REJECTED");
        rejected.setProductId("9002");
        ProductOperationState pending = buildState("PENDING_AUDIT");
        pending.setProductId("9003");
        when(operationStateMapper.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of())
                .thenReturn(List.of(approved, rejected, pending))
                .thenReturn(List.of(approved, rejected, pending));

        Object noActivityFilter = ReflectionTestUtils.invokeMethod(service, "resolveBizStatusFilter", null, "APPROVED");
        Object pendingWithoutStates = ReflectionTestUtils.invokeMethod(service, "resolveBizStatusFilter", "10001", "PENDING_AUDIT");
        Object emptyFilter = ReflectionTestUtils.invokeMethod(service, "resolveBizStatusFilter", "10001", "APPROVED");
        Object includeFilter = ReflectionTestUtils.invokeMethod(service, "resolveBizStatusFilter", "10001", "APPROVED");
        Object pendingFilter = ReflectionTestUtils.invokeMethod(service, "resolveBizStatusFilter", "10001", "PENDING_AUDIT");

        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(emptyFilter, "isEmptyFilter")).isTrue();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(noActivityFilter, "isEmptyFilter")).isFalse();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(pendingWithoutStates, "isEmptyFilter")).isFalse();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(includeFilter, "isEmptyFilter")).isFalse();
        assertThat(ReflectionTestUtils.<Boolean>invokeMethod(pendingFilter, "isEmptyFilter")).isFalse();

        ReflectionTestUtils.invokeMethod(service, "applyBizStatusFilter", null, includeFilter);
        ReflectionTestUtils.invokeMethod(service, "applyBizStatusFilter", new LambdaQueryWrapper<ProductSnapshot>(), noActivityFilter);
        ReflectionTestUtils.invokeMethod(service, "applyBizStatusFilter", new LambdaQueryWrapper<ProductSnapshot>(), emptyFilter);
        ReflectionTestUtils.invokeMethod(service, "applyBizStatusFilter", new LambdaQueryWrapper<ProductSnapshot>(), includeFilter);
        ReflectionTestUtils.invokeMethod(service, "applyBizStatusFilter", new LambdaQueryWrapper<ProductSnapshot>(), pendingFilter);
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
                "PENDING_AUDIT",
                null
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
                "ASSIGNED",
                null
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
                "APPROVED",
                null
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

    private ProductSnapshot selectedLibrarySnapshot(String activityId, String productId, String title) {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setActivityId(activityId);
        snapshot.setProductId(productId);
        snapshot.setTitle(title);
        snapshot.setShopId(3001L);
        snapshot.setShopName("测试店铺");
        snapshot.setCategoryName("食品饮料");
        snapshot.setPrice(19900L);
        snapshot.setPriceText("199.00");
        return snapshot;
    }

    private ProductOperationState selectedLibraryState(String bizStatus, String activityId, String productId) {
        ProductOperationState state = buildState(bizStatus);
        state.setActivityId(activityId);
        state.setProductId(productId);
        state.setSelectedToLibrary(true);
        return state;
    }

    private void stubSingleSelectedLibraryPage(
            ProductSnapshot snapshot,
            ProductOperationState state,
            ProductBizStatus bizStatus) {
        Page<ProductOperationState> statePage = new Page<>(1, 200, 1);
        statePage.setRecords(List.of(state));
        when(operationStateMapper.selectPage(any(Page.class), any())).thenReturn(statePage);
        when(snapshotMapper.selectBatchIds(any())).thenReturn(List.of(snapshot));
        when(productBizStatusService.readBizStatus(state)).thenReturn(bizStatus);
    }

    private void stubActivityProductDetail(
            ProductSnapshot snapshot,
            ProductOperationState state,
            ProductBizStatus bizStatus) {
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(state);
        when(operationLogMapper.selectOne(any())).thenReturn(null);
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(promotionLinkMapper.selectList(any())).thenReturn(List.of());
        when(merchantMapper.selectOne(any())).thenReturn(null);
        when(talentFollowService.listByProduct(snapshot.getActivityId(), snapshot.getProductId())).thenReturn(List.of());
        when(productBizStatusService.readBizStatus(state)).thenReturn(bizStatus);
    }

    private Map<String, Object> fullAuditSupplement() {
        Map<String, Object> supplement = new LinkedHashMap<>();
        supplement.put("exclusivePriceRemark", "专属价 129");
        supplement.put("shippingInfo", "48 小时内发货");
        supplement.put("sellingPoints", List.of("高复购", "送礼场景强"));
        supplement.put("promotionScript", "主推复购场景");
        supplement.put("supportsAds", true);
        supplement.put("rewardRemark", "达成目标有额外奖励");
        supplement.put("participationRequirements", "食品饮料达人优先");
        supplement.put("campaignTimeRemark", "长期在线");
        supplement.put("materialFiles", List.of("https://example.com/material.png"));
        supplement.put("sampleThresholdSales", 30000L);
        supplement.put("sampleThresholdLevel", 2);
        supplement.put("sampleThresholdRemark", "需真人出镜");
        return supplement;
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
                "https://example.com/detail",
                null,
                Map.of()
        );
    }
}
