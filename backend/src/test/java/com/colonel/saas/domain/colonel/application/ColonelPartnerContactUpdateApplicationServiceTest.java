package com.colonel.saas.domain.colonel.application;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.domain.colonel.domain.ColonelPartnerRepository;
import com.colonel.saas.dto.colonel.ColonelPartnerContactUpdateRequest;
import com.colonel.saas.entity.ColonelPartner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColonelPartnerContactUpdateApplicationServiceTest {

    @Mock
    private ColonelPartnerRepository colonelPartnerRepository;

    private ColonelPartnerContactUpdateApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ColonelPartnerContactUpdateApplicationService(colonelPartnerRepository);
    }

    @Test
    void updateContactInfo_shouldMatchLegacyTrimAndPartialUpdateBehavior() {
        UUID id = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        ColonelPartner partner = partner(id);
        partner.setContactPhone("old-phone");
        when(colonelPartnerRepository.findById(id)).thenReturn(partner);

        ColonelPartner result = service.updateContactInfo(
                id,
                new ColonelPartnerContactUpdateRequest(" 张三 ", null, "\t", " 重点维护 "),
                operatorId);

        assertThat(result).isSameAs(partner);
        ArgumentCaptor<ColonelPartner> captor = ArgumentCaptor.forClass(ColonelPartner.class);
        verify(colonelPartnerRepository).save(captor.capture());
        assertThat(captor.getValue().getContactName()).isEqualTo("张三");
        assertThat(captor.getValue().getContactPhone()).isEqualTo("old-phone");
        assertThat(captor.getValue().getContactWechat()).isNull();
        assertThat(captor.getValue().getContactRemark()).isEqualTo("重点维护");
        assertThat(captor.getValue().getManualContactUpdatedAt()).isNotNull();
        assertThat(captor.getValue().getManualContactUpdatedBy()).isEqualTo(operatorId.toString());
    }

    @Test
    void updateContactInfo_shouldThrowWhenPartnerMissing() {
        UUID id = UUID.randomUUID();
        when(colonelPartnerRepository.findById(id)).thenReturn(null);

        assertThatThrownBy(() -> service.updateContactInfo(
                id,
                new ColonelPartnerContactUpdateRequest("张三", null, null, null),
                UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("团长主数据不存在");
        verify(colonelPartnerRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static ColonelPartner partner(UUID id) {
        ColonelPartner partner = new ColonelPartner();
        partner.setId(id);
        partner.setColonelBuyinId("BUYIN-1");
        partner.setColonelName("团长");
        return partner;
    }
}
