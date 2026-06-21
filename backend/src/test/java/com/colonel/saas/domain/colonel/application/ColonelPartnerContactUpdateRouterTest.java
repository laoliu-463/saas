package com.colonel.saas.domain.colonel.application;

import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.dto.colonel.ColonelPartnerContactUpdateRequest;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.service.ColonelPartnerAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColonelPartnerContactUpdateRouterTest {

    @Mock
    private ColonelPartnerAdminService legacyService;
    @Mock
    private ColonelPartnerContactUpdateApplicationService dddService;

    private DddRefactorProperties properties;
    private ColonelPartnerContactUpdateRouter router;

    @BeforeEach
    void setUp() {
        properties = new DddRefactorProperties();
        router = new ColonelPartnerContactUpdateRouter(properties, legacyService, dddService);
    }

    @Test
    void updateContactInfo_shouldUseLegacyPathByDefault() {
        UUID id = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        ColonelPartnerContactUpdateRequest request =
                new ColonelPartnerContactUpdateRequest("张三", null, null, null);
        ColonelPartner legacyResult = new ColonelPartner();
        when(legacyService.updateContactInfo(id, request, operatorId)).thenReturn(legacyResult);

        ColonelPartner result = router.updateContactInfo(id, request, operatorId);

        assertThat(result).isSameAs(legacyResult);
        verify(legacyService).updateContactInfo(id, request, operatorId);
        verify(dddService, never()).updateContactInfo(id, request, operatorId);
    }

    @Test
    void updateContactInfo_shouldUseDddPathOnlyWhenRootAndSliceSwitchAreEnabled() {
        UUID id = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        ColonelPartnerContactUpdateRequest request =
                new ColonelPartnerContactUpdateRequest("张三", null, null, null);
        ColonelPartner dddResult = new ColonelPartner();
        properties.setEnabled(true);
        properties.getColonelPartnerContact().setEnabled(true);
        when(dddService.updateContactInfo(id, request, operatorId)).thenReturn(dddResult);

        ColonelPartner result = router.updateContactInfo(id, request, operatorId);

        assertThat(result).isSameAs(dddResult);
        verify(dddService).updateContactInfo(id, request, operatorId);
        verify(legacyService, never()).updateContactInfo(id, request, operatorId);
    }
}
