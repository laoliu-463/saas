package com.colonel.saas.domain.talent.application;

import com.colonel.saas.domain.talent.facade.TalentDomainFacade;
import com.colonel.saas.domain.talent.facade.dto.TalentShippingAddressDTO;
import com.colonel.saas.entity.Talent;
import com.colonel.saas.service.TalentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentAddressApplicationServiceTest {

    @Mock
    private TalentService talentService;
    @Mock
    private TalentDomainFacade talentDomainFacade;

    @Test
    void getShippingAddressWithUserShouldReadClaimAddressViaFacade() {
        TalentAddressApplicationService service = new TalentAddressApplicationService(talentService, talentDomainFacade);
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TalentShippingAddressDTO expected = new TalentShippingAddressDTO("张三", "13800138000", "上海市");
        when(talentDomainFacade.findClaimShippingAddress(userId, talentId)).thenReturn(expected);

        TalentShippingAddressDTO actual = service.getShippingAddress(talentId, userId);

        assertThat(actual).isSameAs(expected);
        verify(talentDomainFacade).findClaimShippingAddress(userId, talentId);
    }

    @Test
    void updateShippingAddressShouldDelegateToTalentServiceToKeepClaimOwnerRule() {
        TalentAddressApplicationService service = new TalentAddressApplicationService(talentService, talentDomainFacade);
        UUID talentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Talent updated = new Talent();
        when(talentService.updateShippingAddress(talentId, userId, "李四", "13900139000", "杭州市"))
                .thenReturn(updated);

        Talent result = service.updateShippingAddress(talentId, userId, "李四", "13900139000", "杭州市");

        assertThat(result).isSameAs(updated);
        verify(talentService).updateShippingAddress(talentId, userId, "李四", "13900139000", "杭州市");
    }
}
