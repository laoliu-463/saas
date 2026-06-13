package com.colonel.saas.domain.product.application;

import com.colonel.saas.domain.config.facade.ConfigDomainFacade;
import com.colonel.saas.domain.product.application.dto.PromotionLinkCopyResult;
import com.colonel.saas.domain.product.port.DouyinConvertPort;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CopyPromotionApplicationServiceTest {

    @Mock
    private ProductService productService;
    @Mock
    private DouyinConvertPort douyinConvertPort;
    @Mock
    private ConfigDomainFacade configDomainFacade;

    private CopyPromotionApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new CopyPromotionApplicationService(
                productService,
                douyinConvertPort,
                configDomainFacade);
    }

    @Test
    void copyPromotion_shouldDelegateProductService() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();

        var context = new CopyPromotionApplicationService.Context(null, null);
        when(productService.prepareCopyPromotionContext(any(), any(), any())).thenReturn(context);

        PromotionLinkCopyResult result = applicationService.copyPromotion(
                "ACT-1",
                "P-1",
                userId,
                deptId,
                "ext-1",
                4,
                true,
                "PRODUCT_LIBRARY",
                "talent-1",
                "idem-1",
                false,
                false);

        assertThat(result.copyText()).isNotNull();
        assertThat(result.realPromotionWriteEnabled()).isFalse();
    }
}
