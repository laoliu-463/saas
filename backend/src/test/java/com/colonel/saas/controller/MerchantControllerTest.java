package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.dto.talent.OverrideAssigneeRequest;
import com.colonel.saas.entity.Merchant;
import com.colonel.saas.service.MerchantService;
import com.colonel.saas.vo.PartnerDetailVO;
import com.colonel.saas.vo.PartnerProductVO;
import com.colonel.saas.vo.PartnerVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;
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
    void listPartners_shouldAllowAdminAndRecruitingRoles() throws Exception {
        Method method = MerchantController.class.getMethod(
                "listPartners",
                String.class,
                String.class,
                long.class,
                long.class
        );

        RequireRoles requireRoles = method.getAnnotation(RequireRoles.class);

        assertThat(requireRoles).isNotNull();
        assertThat(requireRoles.value()).containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF);
    }

    @Test
    void listPartners_shouldDelegateToService() {
        MerchantController controller = new MerchantController(merchantService);
        PartnerVO partner = new PartnerVO();
        partner.setPartnerId("1001");
        partner.setPartnerName("清风小店");
        Page<PartnerVO> page = new Page<>(2, 5, 1);
        page.setRecords(List.of(partner));
        when(merchantService.listPartners("清风", "MERCHANT", 2, 5)).thenReturn(page);

        var response = controller.listPartners("清风", "MERCHANT", 2, 5);

        assertThat(response.getData().getTotal()).isEqualTo(1);
        assertThat(response.getData().getRecords()).containsExactly(partner);
        verify(merchantService).listPartners("清风", "MERCHANT", 2, 5);
    }

    @Test
    void getPartnerDetail_shouldAllowAdminAndRecruitingRoles() throws Exception {
        Method method = MerchantController.class.getMethod("getPartnerDetail", String.class, String.class);

        RequireRoles requireRoles = method.getAnnotation(RequireRoles.class);

        assertThat(requireRoles).isNotNull();
        assertThat(requireRoles.value()).containsExactly(RoleCodes.ADMIN, RoleCodes.BIZ_LEADER, RoleCodes.BIZ_STAFF);
    }

    @Test
    void getPartnerDetail_shouldDelegateToService() {
        MerchantController controller = new MerchantController(merchantService);
        PartnerDetailVO detail = new PartnerDetailVO();
        detail.setPartnerId("1001");
        detail.setPartnerName("清风小店");
        when(merchantService.getPartnerDetail("1001", "MERCHANT")).thenReturn(detail);

        var response = controller.getPartnerDetail("1001", "MERCHANT");

        assertThat(response.getData().getPartnerName()).isEqualTo("清风小店");
        verify(merchantService).getPartnerDetail("1001", "MERCHANT");
    }

    @Test
    void listPartnerProducts_shouldDelegateToService() {
        MerchantController controller = new MerchantController(merchantService);
        PartnerProductVO product = new PartnerProductVO();
        product.setProductId("P-1001");
        product.setProductName("夏季爆款水杯");
        Page<PartnerProductVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(product));
        when(merchantService.listPartnerProducts("1001", 1, 10)).thenReturn(page);

        var response = controller.listPartnerProducts("1001", 1, 10);

        assertThat(response.getData().getTotal()).isEqualTo(1);
        assertThat(response.getData().getRecords()).containsExactly(product);
        verify(merchantService).listPartnerProducts("1001", 1, 10);
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
