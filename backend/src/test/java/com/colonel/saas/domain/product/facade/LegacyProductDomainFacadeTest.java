package com.colonel.saas.domain.product.facade;

import com.colonel.saas.domain.product.facade.dto.ProductReadDTO;
import com.colonel.saas.domain.product.facade.dto.ProductSnapshotReadDTO;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyProductDomainFacadeTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductSnapshotMapper productSnapshotMapper;

    private ProductDomainFacade facade;

    @BeforeEach
    void setUp() {
        facade = new LegacyProductDomainFacade(productMapper, productSnapshotMapper);
    }

    @Test
    void findProductById_shouldMapCoreFields() {
        UUID id = UUID.randomUUID();
        Product product = new Product();
        product.setId(id);
        product.setProductId("P-100");
        product.setName("测试商品");
        product.setPrice(990L);
        when(productMapper.selectById(id)).thenReturn(product);

        ProductReadDTO dto = facade.findProductById(id);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.productId()).isEqualTo("P-100");
        assertThat(dto.name()).isEqualTo("测试商品");
        assertThat(dto.price()).isEqualTo(990L);
    }

    @Test
    void findProductById_nullReturnsNull() {
        assertThat(facade.findProductById(null)).isNull();
    }

    @Test
    void findProductById_notFoundReturnsNull() {
        when(productMapper.selectById(any(UUID.class))).thenReturn(null);
        assertThat(facade.findProductById(UUID.randomUUID())).isNull();
    }

    @Test
    void findProductByExternalId_shouldFindByProductId() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setProductId("EXT-001");
        product.setName("外部商品");
        when(productMapper.selectOne(any())).thenReturn(product);

        ProductReadDTO dto = facade.findProductByExternalId("EXT-001");

        assertThat(dto).isNotNull();
        assertThat(dto.productId()).isEqualTo("EXT-001");
        assertThat(dto.name()).isEqualTo("外部商品");
    }

    @Test
    void findProductByExternalId_blankReturnsNull() {
        assertThat(facade.findProductByExternalId("")).isNull();
        assertThat(facade.findProductByExternalId(null)).isNull();
    }

    @Test
    void existsById_nullReturnsFalse() {
        assertThat(facade.existsById(null)).isFalse();
    }

    @Test
    void existsById_foundReturnsTrue() {
        UUID id = UUID.randomUUID();
        when(productMapper.selectById(id)).thenReturn(new Product());
        assertThat(facade.existsById(id)).isTrue();
    }

    @Test
    void findSnapshotById_shouldMapCoreFields() {
        UUID id = UUID.randomUUID();
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setId(id);
        snapshot.setActivityId("ACT-1");
        snapshot.setProductId("P-200");
        snapshot.setTitle("快照标题");
        snapshot.setShopName("演示店铺");
        when(productSnapshotMapper.selectById(id)).thenReturn(snapshot);

        ProductSnapshotReadDTO dto = facade.findSnapshotById(id);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.activityId()).isEqualTo("ACT-1");
        assertThat(dto.title()).isEqualTo("快照标题");
        assertThat(dto.shopName()).isEqualTo("演示店铺");
    }

    @Test
    void findSnapshotById_nullReturnsNull() {
        assertThat(facade.findSnapshotById(null)).isNull();
    }

    @Test
    void loadProductNamesByIds_shouldReturnDistinctNames() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Product p1 = new Product();
        p1.setId(id1);
        p1.setName("商品A");
        Product p2 = new Product();
        p2.setId(id2);
        p2.setName("商品B");
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(p1, p2));

        assertThat(facade.loadProductNamesByIds(List.of(id1, id2, id1)))
                .containsEntry(id1, "商品A")
                .containsEntry(id2, "商品B");
    }

    @Test
    void loadProductNamesByIds_emptyCollectionReturnsEmptyMap() {
        assertThat(facade.loadProductNamesByIds(Collections.emptyList())).isEmpty();
    }

    @Test
    void loadProductNamesByIds_nullCollectionReturnsEmptyMap() {
        assertThat(facade.loadProductNamesByIds(null)).isEmpty();
    }
}
