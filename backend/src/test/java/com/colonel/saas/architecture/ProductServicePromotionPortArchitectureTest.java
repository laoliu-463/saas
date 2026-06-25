package com.colonel.saas.architecture;

import com.colonel.saas.domain.product.application.port.DouyinConvertPort;
import com.colonel.saas.gateway.douyin.DouyinPromotionGateway;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ProductServicePromotionPortArchitectureTest {

    @Test
    void productService_shouldDependOnDouyinConvertPortInsteadOfLegacyGatewayField() {
        var fieldTypeNames = Arrays.stream(ProductService.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .toList();

        assertThat(fieldTypeNames).contains(DouyinConvertPort.class.getName());
        assertThat(fieldTypeNames).doesNotContain(DouyinPromotionGateway.class.getName());
    }
}
