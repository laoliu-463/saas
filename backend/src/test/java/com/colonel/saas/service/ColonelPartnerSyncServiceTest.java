package com.colonel.saas.service;

import com.colonel.saas.domain.colonel.application.ColonelPartnerSyncApplicationService;
import com.colonel.saas.entity.ColonelPartner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ColonelPartnerSyncService 委派壳冒烟测试（DDD-COLONEL-002 Slice 1）。
 *
 * <p>Service 已是 1-line delegate；本测试仅验证委派路径打通，详细业务逻辑断言
 * 见 {@link ColonelPartnerSyncApplicationServiceTest}。</p>
 */
@ExtendWith(MockitoExtension.class)
class ColonelPartnerSyncServiceTest {

    @Mock
    private ColonelPartnerSyncApplicationService applicationService;

    private ColonelPartnerSyncService service;

    @BeforeEach
    void setUp() {
        service = new ColonelPartnerSyncService(applicationService);
    }

    @Test
    void syncAll_shouldDelegateToApplication() {
        when(applicationService.syncAll()).thenReturn(7);

        int upserted = service.syncAll();

        assertThat(upserted).isEqualTo(7);
        verify(applicationService).syncAll();
    }

    @Test
    void listByNameKeyword_shouldDelegateToApplication() {
        ColonelPartner partner = new ColonelPartner();
        when(applicationService.listByNameKeyword("张", 50)).thenReturn(List.of(partner));

        List<ColonelPartner> result = service.listByNameKeyword("张", 50);

        assertThat(result).containsExactly(partner);
        verify(applicationService).listByNameKeyword("张", 50);
    }

    @Test
    void resolveProductIdsByColonelName_shouldDelegateToApplication() {
        when(applicationService.resolveProductIdsByColonelName("张团长")).thenReturn(Set.of("P1", "P2"));

        Set<String> result = service.resolveProductIdsByColonelName("张团长");

        assertThat(result).containsExactlyInAnyOrder("P1", "P2");
        verify(applicationService).resolveProductIdsByColonelName("张团长");
    }
}