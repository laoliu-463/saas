package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.talent.OverrideAssigneeRequest;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.service.MerchantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantControllerTest {

    @Mock
    private MerchantService merchantService;

    @Test
    void controller_shouldRequireAdminOnly() {
        MerchantController controller = new MerchantController(merchantService);
        RequireRoles requireRoles = controller.getClass().getAnnotation(RequireRoles.class);

        assertThat(requireRoles).isNotNull();
        assertThat(requireRoles.value()).containsExactly(RoleCodes.ADMIN);
    }

    @Test
    void overrideAssignee_shouldDelegateToService() {
        MerchantController controller = new MerchantController(merchantService);
        UUID newUserId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        OverrideAssigneeRequest request = new OverrideAssigneeRequest(newUserId, "重新分配");
        Merchant merchant = new Merchant();
        merchant.setMerchantId("M-1001");
        merchant.setOwnerId(newUserId);
        when(merchantService.overrideMerchantAssignment("M-1001", newUserId, "重新分配", operatorId))
                .thenReturn(merchant);

        var response = controller.overrideAssignee("M-1001", request, operatorId);

        assertThat(response.getData().getMerchantId()).isEqualTo("M-1001");
        assertThat(response.getData().getOwnerId()).isEqualTo(newUserId);
        verify(merchantService).overrideMerchantAssignment("M-1001", newUserId, "重新分配", operatorId);
    }
}
