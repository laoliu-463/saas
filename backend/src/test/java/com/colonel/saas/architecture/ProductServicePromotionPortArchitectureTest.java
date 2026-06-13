package com.colonel.saas.architecture;

import com.colonel.saas.domain.product.application.port.DouyinConvertPort;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductServicePromotionPortArchitectureTest {

    @Test
    void productService_shouldDependOnDouyinConvertPortInsteadOfLegacyGatewayField() {
        List<Class<?>> fieldTypes = Arrays.stream(ProductService.class.getDeclaredFields())
                .map(Field::getType)
                .toList();

        assertThat(fieldTypes).contains(DouyinConvertPort.class);
        assertThat(fieldTypes).doesNotContain(DouyinPromotionGateway.class);
    }
}
