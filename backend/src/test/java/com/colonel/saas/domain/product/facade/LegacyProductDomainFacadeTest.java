package com.colonel.saas.domain.product.facade;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import com.colonel.saas.domain.product.facade.dto.ProductReadDTO;
import com.colonel.saas.domain.product.facade.dto.ProductOrderDisplayDTO;
import com.colonel.saas.domain.product.facade.dto.ProductSnapshotOrderDisplayDTO;
import com.colonel.saas.domain.product.facade.dto.ProductSnapshotReadDTO;
import com.colonel.saas.entity.Product;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    @Mock
    private ProductOperationStateMapper productOperationStateMapper;
    @Mock
    private ColonelsettlementActivityMapper colonelsettlementActivityMapper;

    private ProductDomainFacade facade;

    @BeforeEach
    void setUp() {
        initTableInfo(Product.class);
        initTableInfo(ProductSnapshot.class);
        facade = new LegacyProductDomainFacade(
                productMapper,
                productSnapshotMapper,
                productOperationStateMapper,
                colonelsettlementActivityMapper);
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.getTypeHandlerRegistry().register(UUID.class, UUIDTypeHandler.class);
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
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

    @Test
    void loadOrderDisplaySnapshots_shouldMapOrderDisplayFields() {
        LocalDateTime syncTime = LocalDateTime.of(2026, 6, 29, 10, 30);
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("ACT-ORDER");
        snapshot.setProductId("P-ORDER");
        snapshot.setTitle("订单快照标题");
        snapshot.setCover("https://cdn.example.com/snapshot.jpg");
        snapshot.setShopName("订单店铺");
        snapshot.setActivityCosRatio(1200L);
        snapshot.setActivityCosRatioText("12%");
        snapshot.setAdServiceRatio("1.5%");
        snapshot.setActivityAdCosRatio(150L);
        snapshot.setSyncTime(syncTime);
        when(productSnapshotMapper.selectList(any())).thenReturn(List.of(snapshot));

        List<ProductSnapshotOrderDisplayDTO> snapshots =
                facade.loadOrderDisplaySnapshots(List.of(" P-ORDER ", "P-ORDER"), List.of("ACT-ORDER"));

        assertThat(snapshots).hasSize(1);
        ProductSnapshotOrderDisplayDTO dto = snapshots.get(0);
        assertThat(dto.activityId()).isEqualTo("ACT-ORDER");
        assertThat(dto.productId()).isEqualTo("P-ORDER");
        assertThat(dto.title()).isEqualTo("订单快照标题");
        assertThat(dto.cover()).isEqualTo("https://cdn.example.com/snapshot.jpg");
        assertThat(dto.shopName()).isEqualTo("订单店铺");
        assertThat(dto.activityCosRatio()).isEqualTo(1200L);
        assertThat(dto.activityCosRatioText()).isEqualTo("12%");
        assertThat(dto.adServiceRatio()).isEqualTo("1.5%");
        assertThat(dto.activityAdCosRatio()).isEqualTo(150L);
        assertThat(dto.syncTime()).isEqualTo(syncTime);
    }

    @Test
    void loadOrderDisplayProducts_shouldMapProductAndOuterIds() {
        Product product = new Product();
        product.setProductId("P-MAIN");
        product.setOuterProductId("P-OUTER");
        product.setName("订单商品");
        product.setCover("https://cdn.example.com/product.jpg");
        product.setCosRatio(new BigDecimal("18.50"));
        product.setServiceRatio(new BigDecimal("1.20"));
        when(productMapper.selectList(any())).thenReturn(List.of(product));

        List<ProductOrderDisplayDTO> products =
                facade.loadOrderDisplayProducts(List.of("P-MAIN", "P-OUTER"));

        assertThat(products).hasSize(1);
        ProductOrderDisplayDTO dto = products.get(0);
        assertThat(dto.productId()).isEqualTo("P-MAIN");
        assertThat(dto.outerProductId()).isEqualTo("P-OUTER");
        assertThat(dto.name()).isEqualTo("订单商品");
        assertThat(dto.cover()).isEqualTo("https://cdn.example.com/product.jpg");
        assertThat(dto.cosRatio()).isEqualByComparingTo("18.50");
        assertThat(dto.serviceRatio()).isEqualByComparingTo("1.20");
    }
}
