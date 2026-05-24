package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.sample.SampleFilterOptionsDTO;
import com.colonel.saas.entity.SampleRequest;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.mapper.SampleRequestMapper;
import com.colonel.saas.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleFilterOptionsServiceTest {

    @Mock private SampleRequestMapper sampleRequestMapper;
    @Mock private ProductMapper productMapper;
    @Mock private ProductSnapshotMapper productSnapshotMapper;
    @Mock private ProductOperationStateMapper productOperationStateMapper;
    @Mock private SysUserMapper sysUserMapper;

    private SampleFilterOptionsService service;

    @BeforeEach
    void setUp() {
        service = new SampleFilterOptionsService(
                sampleRequestMapper,
                productMapper,
                productSnapshotMapper,
                productOperationStateMapper,
                sysUserMapper);
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
}
