package com.colonel.saas.service;

import com.colonel.saas.common.result.PageResult;
import com.colonel.saas.domain.colonel.application.ColonelPartnerMasterDataApplicationService;
import com.colonel.saas.entity.ColonelPartner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ColonelPartnerMasterDataService 委派壳冒烟测试（DDD-COLONEL-002 Slice 3）。
 *
 * <p>Service 已是 1-line delegate；本测试仅验证委派路径打通，详细业务逻辑断言
 * 见 {@link ColonelPartnerMasterDataApplicationServiceTest}。</p>
 */
@ExtendWith(MockitoExtension.class)
class ColonelPartnerMasterDataServiceTest {

    @Mock
    private ColonelPartnerMasterDataApplicationService applicationService;

    private ColonelPartnerMasterDataService service;

    @BeforeEach
    void setUp() {
        service = new ColonelPartnerMasterDataService(applicationService);
    }

    @Test
    void list_shouldDelegateToApplication() {
        PageResult<ColonelPartner> expected = new PageResult<>();
        expected.setTotal(1L);
        expected.setRecords(List.of());
        when(applicationService.list("k", "BUYIN", Boolean.TRUE, 1L, 20L)).thenReturn(expected);

        PageResult<ColonelPartner> result = service.list("k", "BUYIN", Boolean.TRUE, 1L, 20L);

        assertThat(result).isSameAs(expected);
        verify(applicationService).list("k", "BUYIN", Boolean.TRUE, 1L, 20L);
    }

    @Test
    void detail_shouldDelegateToApplication() {
        UUID id = UUID.randomUUID();
        ColonelPartner partner = new ColonelPartner();
        partner.setId(id);
        when(applicationService.detail(id)).thenReturn(partner);

        ColonelPartner result = service.detail(id);

        assertThat(result).isSameAs(partner);
        verify(applicationService).detail(id);
    }

    @Test
    void listSources_shouldDelegateToApplication() {
        List<String> expected = List.of("BUYIN", "MANUAL");
        when(applicationService.listSources()).thenReturn(expected);

        List<String> result = service.listSources();

        assertThat(result).isSameAs(expected);
        verify(applicationService).listSources();
    }
}
