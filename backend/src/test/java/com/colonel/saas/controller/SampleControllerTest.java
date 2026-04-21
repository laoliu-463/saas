package com.colonel.saas.controller;

import com.colonel.saas.dto.SampleApplyRequest;
import com.colonel.saas.dto.SampleTalentQueryRequest;
import com.colonel.saas.entity.CrawlerTalentInfo;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.TalentMapper;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.CrawlerTalentInfoService;
import com.colonel.saas.service.SampleStatusLogService;
import com.colonel.saas.vo.SampleTalentVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleControllerTest {

    @Mock
    private SampleRequestMapper sampleRequestMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private TalentMapper talentMapper;
    @Mock
    private SampleStatusLogService sampleStatusLogService;
    @Mock
    private CrawlerTalentInfoService crawlerTalentInfoService;

    private SampleController sampleController;

    @BeforeEach
    void setUp() {
        sampleController = new SampleController(
                sampleRequestMapper,
                productMapper,
                talentMapper,
                sampleStatusLogService,
                crawlerTalentInfoService
        );
    }

    @Test
    void createSample_shouldRejectWhenDuplicateWithinSevenDays() {
        UUID talentUuid = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        CrawlerTalentInfo crawlerTalentInfo = new CrawlerTalentInfo();
        crawlerTalentInfo.setTalentId("talent_001");
        crawlerTalentInfo.setNickname("test talent");

        Talent talent = new Talent();
        talent.setId(talentUuid);
        talent.setDouyinUid("talent_001");
        talent.setNickname("test talent");

        SampleApplyRequest request = new SampleApplyRequest();
        request.setTalentId("talent_001");
        request.setProductId(productId);
        request.setQuantity(1);

        when(productMapper.selectById(productId)).thenReturn(product);
        when(crawlerTalentInfoService.findByTalentId("talent_001")).thenReturn(crawlerTalentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(sampleRequestMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> sampleController.createSample(
                request,
                UUID.randomUUID(),
                List.of(RoleCodes.CHANNEL_STAFF)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("7 days");

        verify(sampleRequestMapper, never()).insert(any(SampleRequest.class));
    }

    @Test
    void createSample_shouldPersistTalentSnapshotFromCrawler() {
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID talentUuid = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        CrawlerTalentInfo crawlerTalentInfo = new CrawlerTalentInfo();
        crawlerTalentInfo.setTalentId("talent_002");
        crawlerTalentInfo.setNickname("crawler talent");
        crawlerTalentInfo.setFansCount(120000L);
        crawlerTalentInfo.setCreditScore(new BigDecimal("4.80"));
        crawlerTalentInfo.setMainCategory("food");

        Talent talent = new Talent();
        talent.setId(talentUuid);
        talent.setDouyinUid("talent_002");
        talent.setNickname("crawler talent");

        SampleApplyRequest request = new SampleApplyRequest();
        request.setTalentId("talent_002");
        request.setProductId(productId);
        request.setQuantity(2);

        when(productMapper.selectById(productId)).thenReturn(product);
        when(crawlerTalentInfoService.findByTalentId("talent_002")).thenReturn(crawlerTalentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);
        when(sampleRequestMapper.selectCount(any())).thenReturn(0L);

        sampleController.createSample(request, userId, List.of(RoleCodes.CHANNEL_STAFF));

        ArgumentCaptor<SampleRequest> captor = ArgumentCaptor.forClass(SampleRequest.class);
        verify(sampleRequestMapper).insert(captor.capture());
        SampleRequest saved = captor.getValue();
        assertThat(saved.getTalentUid()).isEqualTo("talent_002");
        assertThat(saved.getTalentNickname()).isEqualTo("crawler talent");
        assertThat(saved.getTalentFansCount()).isEqualTo(120000L);
        assertThat(saved.getTalentCreditScore()).isEqualTo(new BigDecimal("4.80"));
        assertThat(saved.getTalentMainCategory()).isEqualTo("food");
        assertThat(saved.getStatus()).isEqualTo(1);
    }

    @Test
    void createSample_shouldAllowLeaderBypassSevenDaysLimit() {
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID talentUuid = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        CrawlerTalentInfo crawlerTalentInfo = new CrawlerTalentInfo();
        crawlerTalentInfo.setTalentId("talent_leader_001");
        crawlerTalentInfo.setNickname("leader talent");

        Talent talent = new Talent();
        talent.setId(talentUuid);
        talent.setDouyinUid("talent_leader_001");

        SampleApplyRequest request = new SampleApplyRequest();
        request.setTalentId("talent_leader_001");
        request.setProductId(productId);
        request.setQuantity(1);

        when(productMapper.selectById(productId)).thenReturn(product);
        when(crawlerTalentInfoService.findByTalentId("talent_leader_001")).thenReturn(crawlerTalentInfo);
        when(talentMapper.selectOne(any())).thenReturn(talent);

        sampleController.createSample(request, userId, List.of(RoleCodes.BIZ_LEADER));

        verify(sampleRequestMapper).insert(any(SampleRequest.class));
        verify(sampleRequestMapper, never()).selectCount(any());
    }

    @Test
    void searchTalents_shouldReturnPagedResults() {
        SampleTalentVO vo = new SampleTalentVO();
        vo.setTalentId("talent_001");
        vo.setNickname("老铁好物");
        vo.setFansCount(100000L);
        vo.setCreditScore(new BigDecimal("4.6"));
        IPage<SampleTalentVO> page = new Page<>(1, 20);
        page.setRecords(java.util.List.of(vo));
        page.setTotal(1);

        when(crawlerTalentInfoService.searchTalents(
                eq("老铁"),
                eq("浙江"),
                eq(10000L),
                eq(500000L),
                eq(new BigDecimal("4.5")),
                eq(1),
                eq(20)
        )).thenReturn(page);

        SampleTalentQueryRequest query = new SampleTalentQueryRequest();
        query.setKeyword("老铁");
        query.setRegion("浙江");
        query.setMinFans(10000L);
        query.setMaxFans(500000L);
        query.setMinScore(new BigDecimal("4.5"));
        query.setPage(1);
        query.setSize(20);

        var response = sampleController.searchTalents(query);

        assertThat(response.getData().getTotal()).isEqualTo(1L);
        assertThat(response.getData().getRecords()).hasSize(1);
        assertThat(response.getData().getRecords().get(0).getNickname()).isEqualTo("老铁好物");
    }

    @Test
    void actionSample_shouldAllowApproveFromPendingAudit() {
        UUID sampleId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(1);
        sample.setProductId(productId);
        sample.setTalentId(talentId);
        sample.setTalentNickname("test talent");
        sample.setRequestNo("SM20260421000001");
        sample.setExpectedSampleNum(1);

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        SampleController.SampleActionRequest request = new SampleController.SampleActionRequest();
        request.setAction("APPROVED");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productMapper.selectById(productId)).thenReturn(product);

        var response = sampleController.actionSample(sampleId, request, userId);

        assertThat(response.getData().getStatus()).isEqualTo("PENDING_SHIP");
        verify(sampleRequestMapper).updateById(sample);
        verify(sampleStatusLogService).log(sampleId, 1, 2, userId, null);
    }

    @Test
    void actionSample_shouldAllowShippingFromPendingShip() {
        UUID sampleId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(2);
        sample.setProductId(productId);
        sample.setTalentId(talentId);
        sample.setTalentNickname("test talent");
        sample.setRequestNo("SM20260421000002");
        sample.setExpectedSampleNum(1);

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        SampleController.SampleActionRequest request = new SampleController.SampleActionRequest();
        request.setAction("SHIPPED");
        request.setTrackingNo("YT123456");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productMapper.selectById(productId)).thenReturn(product);

        var response = sampleController.actionSample(sampleId, request, userId);

        assertThat(response.getData().getStatus()).isEqualTo("SHIPPED");
        assertThat(response.getData().getTrackingNo()).isEqualTo("YT123456");
        verify(sampleRequestMapper).updateById(sample);
        verify(sampleStatusLogService).log(sampleId, 2, 3, userId, null);
    }

    @Test
    void actionSample_shouldAllowRejectFromPendingAudit() {
        UUID sampleId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(1);
        sample.setProductId(productId);
        sample.setRequestNo("SM20260421000003");

        Product product = new Product();
        product.setId(productId);
        product.setName("test product");

        SampleController.SampleActionRequest request = new SampleController.SampleActionRequest();
        request.setAction("REJECTED");
        request.setReason("not matched");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);
        when(productMapper.selectById(productId)).thenReturn(product);

        var response = sampleController.actionSample(sampleId, request, userId);

        assertThat(response.getData().getStatus()).isEqualTo("REJECTED");
        assertThat(response.getData().getRejectReason()).isEqualTo("not matched");
        verify(sampleStatusLogService).log(sampleId, 1, 7, userId, "not matched");
    }

    @Test
    void actionSample_shouldRejectInvalidTransition() {
        UUID sampleId = UUID.randomUUID();
        SampleRequest sample = new SampleRequest();
        sample.setId(sampleId);
        sample.setStatus(8);

        SampleController.SampleActionRequest request = new SampleController.SampleActionRequest();
        request.setAction("COMPLETED");

        when(sampleRequestMapper.selectById(sampleId)).thenReturn(sample);

        assertThatThrownBy(() -> sampleController.actionSample(sampleId, request, UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Current status does not allow this action");

        verify(sampleRequestMapper, never()).updateById(any(SampleRequest.class));
    }
}

