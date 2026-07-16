package com.colonel.saas.domain.product.infrastructure;

import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.common.exception.UpstreamErrorCode;
import com.colonel.saas.common.result.ResultCode;
import com.colonel.saas.domain.product.application.port.DouyinConvertPort;
import com.colonel.saas.douyin.DouyinApiException;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DouyinPromotionGatewayConvertAdapterTest {

    @Mock
    private DouyinPromotionGateway gateway;

    private DouyinPromotionGatewayConvertAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DouyinPromotionGatewayConvertAdapter(gateway);
    }

    @Test
    void convert_shouldMapDomainPortCommandToLegacyGateway() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        DouyinConvertPort.ConvertContext context = new DouyinConvertPort.ConvertContext(
                userId,
                deptId,
                "P-1",
                "ACT-1",
                "https://item",
                "PRODUCT_LIBRARY",
                "talent-1",
                "pick-extra");
        DouyinConvertPort.ConvertCommand command = new DouyinConvertPort.ConvertCommand(
                "ext-1",
                4,
                List.of("P-1"),
                true,
                context);
        when(gateway.generateLink(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new DouyinPromotionGateway.PromotionLinkResult(
                        "PS-1",
                        "pick-extra",
                        "SID-1",
                        "https://s.link",
                        "https://p.link",
                        "uuid-seed"));

        DouyinConvertPort.ConvertResult result = adapter.convert(command);

        ArgumentCaptor<DouyinPromotionGateway.PromotionLinkCommand> captor =
                ArgumentCaptor.forClass(DouyinPromotionGateway.PromotionLinkCommand.class);
        verify(gateway).generateLink(captor.capture());
        DouyinPromotionGateway.PromotionLinkCommand mapped = captor.getValue();
        assertThat(mapped.externalUniqueId()).isEqualTo("ext-1");
        assertThat(mapped.promotionScene()).isEqualTo(4);
        assertThat(mapped.productIds()).containsExactly("P-1");
        assertThat(mapped.needShortLink()).isTrue();
        assertThat(mapped.context().userId()).isEqualTo(userId);
        assertThat(mapped.context().deptId()).isEqualTo(deptId);
        assertThat(mapped.context().productId()).isEqualTo("P-1");
        assertThat(mapped.context().activityId()).isEqualTo("ACT-1");
        assertThat(mapped.context().sourceUrl()).isEqualTo("https://item");
        assertThat(mapped.context().scene()).isEqualTo("PRODUCT_LIBRARY");
        assertThat(mapped.context().talentId()).isEqualTo("talent-1");
        assertThat(mapped.context().pickExtra()).isEqualTo("pick-extra");
        assertThat(result.pickSource()).isEqualTo("PS-1");
        assertThat(result.shortLink()).isEqualTo("https://s.link");
        assertThat(result.promoteLink()).isEqualTo("https://p.link");
    }

    @Test
    void convert_shouldTranslateRealDouyinApiFailureAtInfrastructureBoundary() {
        DouyinConvertPort.ConvertCommand command = new DouyinConvertPort.ConvertCommand(
                "ext-1",
                4,
                List.of("P-1"),
                true,
                new DouyinConvertPort.ConvertContext(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "P-1",
                        "ACT-1",
                        "https://item",
                        "SAMPLE_COOPERATION",
                        "talent-1",
                        "pick-extra"));
        DouyinApiException upstream = new DouyinApiException(
                401, "access token expired", "isv.business-failed:4197", "log-1", "buyin.instPickSourceConvert");
        when(gateway.generateLink(any())).thenThrow(upstream);

        assertThatThrownBy(() -> adapter.convert(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException business = (BusinessException) error;
                    assertThat(business.getCode()).isEqualTo(ResultCode.EXTERNAL_SERVICE.getCode());
                    assertThat(business.getErrorCode()).isEqualTo(UpstreamErrorCode.DOUYIN_TOKEN_INVALID.name());
                    assertThat(business.getCause()).isSameAs(upstream);
                });
    }
}
