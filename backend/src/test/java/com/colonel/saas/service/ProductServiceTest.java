package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.colonel.saas.douyin.api.PromotionApi;
import com.colonel.saas.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private PromotionApi promotionApi;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(promotionApi);
    }

    @Test
    void getPage_shouldReturnPagedRecords() {
        IPage<Product> page = service.getPage(1, 3, null);
        assertThat(page.getCurrent()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(3);
        assertThat(page.getTotal()).isEqualTo(10);
        assertThat(page.getRecords()).hasSize(3);
    }

    @Test
    void getPage_shouldFilterByStatus() {
        IPage<Product> page = service.getPage(1, 20, 0);
        assertThat(page.getRecords()).isNotEmpty();
        assertThat(page.getRecords()).allMatch(product -> product.getStatus() == 0);
    }

    @Test
    void getById_shouldReturnProduct() {
        UUID targetId = UUID.nameUUIDFromBytes("product-1".getBytes());
        Product product = service.getById(targetId);
        assertThat(product.getId()).isEqualTo(targetId);
        assertThat(product.getName()).isEqualTo("test product-1");
    }

    @Test
    void getById_shouldThrowWhenMissing() {
        assertThatThrownBy(() -> service.getById(UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("product not found");
    }

    @Test
    void auditProduct_shouldRejectWithoutReason() {
        UUID targetId = UUID.nameUUIDFromBytes("product-1".getBytes());
        assertThatThrownBy(() -> service.auditProduct(targetId, false, ""))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("reason is required");
    }

    @Test
    void generatePromotionLink_shouldReturnApiResult() {
        UUID targetId = UUID.nameUUIDFromBytes("product-1".getBytes());
        PromotionApi.PromotionLinkResult linkResult =
                new PromotionApi.PromotionLinkResult("ABC12345", "https://s.link", "https://p.link", UUID.randomUUID().toString());
        when(promotionApi.generateLink(any(), any(Integer.class), any(List.class), any(Boolean.class), any()))
                .thenReturn(linkResult);

        PromotionApi.PromotionLinkResult result = service.generatePromotionLink(
                targetId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                true
        );
        assertThat(result.shortId()).isEqualTo("ABC12345");
    }
}
