package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.domain.product.facade.ProductDomainFacade;
import com.colonel.saas.domain.product.facade.dto.ProductReadDTO;
import com.colonel.saas.domain.product.facade.dto.ProductSnapshotReadDTO;
import com.colonel.saas.dto.sample.SampleFilterOptionsDTO;
import com.colonel.saas.dto.user.UserOptionResponse;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleFilterOptionsServiceTest {

    @Mock private SampleRequestMapper sampleRequestMapper;
    @Mock private ProductDomainFacade productDomainFacade;
    @Mock private UserDomainFacade userDomainFacade;

    private SampleFilterOptionsService service;

    @BeforeEach
    void setUp() {
        service = new SampleFilterOptionsService(
                sampleRequestMapper,
                productDomainFacade,
                userDomainFacade);
    }

    @Test
    void buildOptions_noSamples_shouldReturnStaticEnumsAndEmptyDynamicLists() {
        Page<SampleRequest> emptyPage = new Page<>(1, 200);
        emptyPage.setRecords(List.of());
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any())).thenReturn(emptyPage);

        SampleFilterOptionsDTO options = service.buildOptions(
                UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL, List.of(RoleCodes.ADMIN));

        assertThat(options.getStatuses()).hasSize(7);
        assertThat(options.getCooperationTypes()).isNotEmpty();
        assertThat(options.getChannels()).isEmpty();
        assertThat(options.getProducts()).isEmpty();
    }

    @Test
    void buildOptions_bizStaffPersonalScope_shouldUseAuditorQuery() {
        UUID userId = UUID.randomUUID();
        Page<SampleRequest> emptyPage = new Page<>(1, 200);
        emptyPage.setRecords(List.of());
        when(sampleRequestMapper.findPageForAuditor(any(Page.class), eq(userId), any())).thenReturn(emptyPage);

        service.buildOptions(userId, UUID.randomUUID(), DataScope.PERSONAL, List.of(RoleCodes.BIZ_STAFF));

        org.mockito.Mockito.verify(sampleRequestMapper).findPageForAuditor(any(Page.class), eq(userId), any());
    }

    @Test
    void buildOptions_shouldReturnCooperationWorkbenchDynamicOptions() {
        UUID productId = UUID.randomUUID();
        UUID channelUserId = UUID.randomUUID();
        UUID recruiterUserId = UUID.randomUUID();

        SampleRequest sample = new SampleRequest();
        sample.setProductId(productId);
        sample.setChannelUserId(channelUserId);
        sample.setShipperCode("SF");

        ProductReadDTO product = new ProductReadDTO(
                productId, "10901825", null, "合作单商品", null, null, null, null, null, null);
        ProductSnapshotReadDTO snapshot = new ProductSnapshotReadDTO(
                productId, "ACT-001", "10901825", null, null, 123456L, "合作单店铺", null, null, null, null);

        UserOptionResponse channel = new UserOptionResponse(channelUserId, null, "渠道A", null, List.of(), null);
        UserOptionResponse recruiter = new UserOptionResponse(recruiterUserId, null, "招商A", null, List.of(), null);

        Page<SampleRequest> page = new Page<>(1, 200, 1);
        page.setRecords(List.of(sample));
        when(sampleRequestMapper.findPageWithScope(any(Page.class), any())).thenReturn(page);
        when(productDomainFacade.loadProductsByIds(any())).thenReturn(Map.of(productId, product));
        when(productDomainFacade.findSnapshotById(productId)).thenReturn(snapshot);
        when(productDomainFacade.findProductSnapshotAssigneeId(productId)).thenReturn(recruiterUserId);
        when(userDomainFacade.getUsersByIds(any())).thenReturn(List.of(channel));
        when(userDomainFacade.getUserById(recruiterUserId)).thenReturn(recruiter);

        SampleFilterOptionsDTO options = service.buildOptions(
                UUID.randomUUID(), UUID.randomUUID(), DataScope.ALL, List.of(RoleCodes.ADMIN));

        assertThat(options.getCooperationTypes()).extracting("value").contains("FREE_SAMPLE");
        assertThat(options.getSampleOwnerTypes()).extracting("value").contains("MERCHANT");
        assertThat(options.getHomeworkTypes()).extracting("value").contains("HAS_ORDER");
        assertThat(options.getProducts()).extracting("label").contains("合作单商品");
        assertThat(options.getShops()).extracting("label").contains("合作单店铺");
        assertThat(options.getPartners()).extracting("value").contains("ACT-001");
        assertThat(options.getLogisticsCompanies()).extracting("value").contains("SF");
        assertThat(options.getChannels()).extracting("label").contains("渠道A");
        assertThat(options.getRecruiters()).extracting("label").contains("招商A");
    }
}
