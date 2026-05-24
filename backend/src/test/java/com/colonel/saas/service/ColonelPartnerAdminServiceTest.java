package com.colonel.saas.service;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.dto.colonel.ColonelPartnerContactUpdateRequest;
import com.colonel.saas.entity.ColonelPartner;
import com.colonel.saas.mapper.ColonelPartnerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColonelPartnerAdminServiceTest {

    @Mock
    private ColonelPartnerMapper colonelPartnerMapper;

    private ColonelPartnerAdminService service;

    @BeforeEach
    void setUp() {
        service = new ColonelPartnerAdminService(colonelPartnerMapper);
    }

    @Test
    void updateContactInfo_shouldTrimTextAndPersistOperator() {
        UUID id = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        ColonelPartner partner = partner(id);
        when(colonelPartnerMapper.selectById(id)).thenReturn(partner);
        when(colonelPartnerMapper.updateById(any())).thenReturn(1);

        ColonelPartner result = service.updateContactInfo(
                id,
                new ColonelPartnerContactUpdateRequest(" 张三 ", " 13800000000 ", " wx001 ", " 重点维护 "),
                operatorId);

        assertThat(result).isSameAs(partner);
        ArgumentCaptor<ColonelPartner> captor = ArgumentCaptor.forClass(ColonelPartner.class);
        verify(colonelPartnerMapper).updateById(captor.capture());
        assertThat(captor.getValue().getContactName()).isEqualTo("张三");
        assertThat(captor.getValue().getContactPhone()).isEqualTo("13800000000");
        assertThat(captor.getValue().getContactWechat()).isEqualTo("wx001");
        assertThat(captor.getValue().getContactRemark()).isEqualTo("重点维护");
        assertThat(captor.getValue().getManualContactUpdatedAt()).isNotNull();
        assertThat(captor.getValue().getManualContactUpdatedBy()).isEqualTo(operatorId.toString());
    }

    @Test
    void updateContactInfo_shouldNullBlankFieldsAndPreserveNullRequestFields() {
        UUID id = UUID.randomUUID();
        ColonelPartner partner = partner(id);
        partner.setContactName("old-name");
        partner.setContactPhone("old-phone");
        partner.setContactWechat("old-wechat");
        partner.setContactRemark("old-remark");
        when(colonelPartnerMapper.selectById(id)).thenReturn(partner);
        when(colonelPartnerMapper.updateById(any())).thenReturn(1);

        service.updateContactInfo(
                id,
                new ColonelPartnerContactUpdateRequest(" ", null, "\t", null),
                null);

        ArgumentCaptor<ColonelPartner> captor = ArgumentCaptor.forClass(ColonelPartner.class);
        verify(colonelPartnerMapper).updateById(captor.capture());
        assertThat(captor.getValue().getContactName()).isNull();
        assertThat(captor.getValue().getContactPhone()).isEqualTo("old-phone");
        assertThat(captor.getValue().getContactWechat()).isNull();
        assertThat(captor.getValue().getContactRemark()).isEqualTo("old-remark");
        assertThat(captor.getValue().getManualContactUpdatedBy()).isNull();
    }

    @Test
    void updateContactInfo_shouldThrowWhenPartnerMissing() {
        UUID id = UUID.randomUUID();
        when(colonelPartnerMapper.selectById(id)).thenReturn(null);

        assertThatThrownBy(() -> service.updateContactInfo(
                id,
                new ColonelPartnerContactUpdateRequest("张三", null, null, null),
                UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("团长主数据不存在");
        verify(colonelPartnerMapper, never()).updateById(any());
    }

    @Test
    void updateContactInfo_shouldThrowWhenOptimisticUpdateFails() {
        UUID id = UUID.randomUUID();
        when(colonelPartnerMapper.selectById(id)).thenReturn(partner(id));
        when(colonelPartnerMapper.updateById(any())).thenReturn(0);

        assertThatThrownBy(() -> service.updateContactInfo(
                id,
                new ColonelPartnerContactUpdateRequest("张三", null, null, null),
                UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("数据已被他人修改");
    }

    private static ColonelPartner partner(UUID id) {
        ColonelPartner partner = new ColonelPartner();
        partner.setId(id);
        partner.setColonelBuyinId("BUYIN-1");
        partner.setColonelName("团长");
        return partner;
    }
}
