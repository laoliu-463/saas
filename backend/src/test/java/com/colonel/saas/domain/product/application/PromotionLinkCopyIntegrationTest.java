package com.colonel.saas.domain.product.application;

import com.colonel.saas.domain.product.application.port.CopyPromotionSupportPort;
import com.colonel.saas.domain.product.application.port.DouyinConvertPort;
import com.colonel.saas.domain.product.infrastructure.DouyinPromotionGatewayConvertAdapter;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionLinkCopyIntegrationTest {

    @Test
    void copyPromotion_shouldFlowThroughApplicationPortAndGatewayAdapter() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        CapturingPromotionGateway gateway = new CapturingPromotionGateway();
        DouyinConvertPort convertPort = new DouyinPromotionGatewayConvertAdapter(gateway);
        CopyPromotionSupportPort supportPort = new AdapterBackedCopyPromotionSupportPort(convertPort);
        CopyPromotionApplicationService applicationService = new CopyPromotionApplicationService(
                supportPort,
                null);

        var result = applicationService.copyPromotion(
                "ACT-20",
                "P-20",
                userId,
                deptId,
                "external-20",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-20",
                "idem-20",
                true,
                true);

        assertThat(result.promotionLinkGenerated()).isTrue();
        assertThat(result.promotionLink()).isEqualTo("https://short.example/P-20");
        assertThat(result.pickSource()).isEqualTo("PS-P-20");
        assertThat(result.copyText()).contains("【链接】https://short.example/P-20");
        assertThat(result.fallbackReason()).isNull();

        DouyinPromotionGateway.PromotionLinkCommand gatewayCommand = gateway.lastCommand;
        assertThat(gatewayCommand.externalUniqueId()).isEqualTo("external-20");
        assertThat(gatewayCommand.promotionScene()).isEqualTo(4);
        assertThat(gatewayCommand.productIds()).containsExactly("P-20");
        assertThat(gatewayCommand.needShortLink()).isTrue();
        assertThat(gatewayCommand.context().userId()).isEqualTo(userId);
        assertThat(gatewayCommand.context().deptId()).isEqualTo(deptId);
        assertThat(gatewayCommand.context().productId()).isEqualTo("P-20");
        assertThat(gatewayCommand.context().activityId()).isEqualTo("ACT-20");
        assertThat(gatewayCommand.context().sourceUrl()).isEqualTo("https://item.example/P-20");
        assertThat(gatewayCommand.context().scene()).isEqualTo("PRODUCT_LIBRARY");
        assertThat(gatewayCommand.context().talentId()).isEqualTo("talent-20");
        assertThat(gatewayCommand.context().pickExtra()).contains("idem-20");
    }

    private static final class AdapterBackedCopyPromotionSupportPort implements CopyPromotionSupportPort {

        private final DouyinConvertPort convertPort;

        private AdapterBackedCopyPromotionSupportPort(DouyinConvertPort convertPort) {
            this.convertPort = convertPort;
        }

        @Override
        public Context prepareCopyPromotionContext(String activityId, String productId, String actionLabel) {
            ProductSnapshot snapshot = new ProductSnapshot();
            snapshot.setActivityId(activityId);
            snapshot.setProductId(productId);
            snapshot.setTitle("集成转链商品");
            snapshot.setShopName("集成测试店铺");
            snapshot.setDetailUrl("https://item.example/" + productId);
            snapshot.setActivityCosRatioText("30%");
            snapshot.setPriceText("99.00");
            snapshot.setSales(1200L);
            ProductOperationState state = new ProductOperationState();
            return new Context(snapshot, state);
        }

        @Override
        public GeneratedPromotionLink generatePromotionLinkForCopy(
                String activityId,
                String productId,
                UUID userId,
                UUID deptId,
                String externalUniqueId,
                Integer promotionScene,
                boolean needShortLink,
                String scene,
                String talentId,
                String idempotencyKey) {
            DouyinConvertPort.ConvertResult result = convertPort.convert(
                    new DouyinConvertPort.ConvertCommand(
                            externalUniqueId,
                            promotionScene == null ? 4 : promotionScene,
                            List.of(productId),
                            needShortLink,
                            new DouyinConvertPort.ConvertContext(
                                    userId,
                                    deptId,
                                    productId,
                                    activityId,
                                    "https://item.example/" + productId,
                                    scene,
                                    talentId,
                                    "copy:" + activityId + ":" + productId + ":" + idempotencyKey)));
            return new GeneratedPromotionLink(
                    result.shortLink(),
                    result.promoteLink(),
                    result.pickSource());
        }
    }

    private static final class CapturingPromotionGateway implements DouyinPromotionGateway {

        private PromotionLinkCommand lastCommand;

        @Override
        public PromotionLinkResult generateLink(PromotionLinkCommand command) {
            lastCommand = command;
            String productId = command.context().productId();
            return new PromotionLinkResult(
                    "PS-" + productId,
                    command.context().pickExtra(),
                    "SID-" + productId,
                    "https://short.example/" + productId,
                    "https://promote.example/" + productId,
                    "uuid-" + productId);
        }

        @Override
        public Map<String, Object> rawUpstreamPost(String appId, String method, Map<String, Object> payload) {
            throw new UnsupportedOperationException("raw upstream probe is outside this copy promotion test");
        }
    }
}
