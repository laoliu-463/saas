package com.colonel.saas.service;

import com.colonel.saas.douyin.api.PromotionApi;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ProductOperationLogMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private PromotionApi promotionApi;
    @Mock
    private ProductSnapshotMapper snapshotMapper;
    @Mock
    private ProductOperationStateMapper operationStateMapper;
    @Mock
    private ProductOperationLogMapper operationLogMapper;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(promotionApi, snapshotMapper, operationStateMapper, operationLogMapper);
    }

    @Test
    void upsertSnapshots_shouldInsertSnapshotWhenMissing() {
        when(snapshotMapper.selectById(any(UUID.class))).thenReturn(null);

        service.upsertSnapshots("10001", List.of(Map.of(
                "productId", "9001",
                "title", "测试商品",
                "price", 19900,
                "status", 1
        )));

        ArgumentCaptor<ProductSnapshot> captor = ArgumentCaptor.forClass(ProductSnapshot.class);
        verify(snapshotMapper).insert(captor.capture());
        ProductSnapshot snapshot = captor.getValue();
        assertThat(snapshot.getActivityId()).isEqualTo("10001");
        assertThat(snapshot.getProductId()).isEqualTo("9001");
        assertThat(snapshot.getTitle()).isEqualTo("测试商品");
    }

    @Test
    void auditProduct_shouldRejectWithoutReason() {
        assertThatThrownBy(() -> service.auditProduct("10001", "9001", false, "", UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("驳回时必须填写原因");
        verify(operationStateMapper, never()).insert(any(ProductOperationState.class));
    }

    @Test
    void generatePromotionLink_shouldCallPromotionApi() {
        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("10001");
        snapshot.setProductId("9001");
        snapshot.setDetailUrl("https://example.com/detail");
        when(snapshotMapper.selectOne(any())).thenReturn(snapshot);
        when(operationStateMapper.selectOne(any())).thenReturn(null);
        when(promotionApi.generateLink(anyString(), any(Integer.class), anyList(), anyBoolean(), any()))
                .thenReturn(new PromotionApi.PromotionLinkResult("abc", "https://s.link", "https://p.link", "seed"));

        PromotionApi.PromotionLinkResult result = service.generatePromotionLink(
                "10001", "9001", UUID.randomUUID(), UUID.randomUUID(), null, null, true
        );

        assertThat(result.shortId()).isEqualTo("abc");
        verify(promotionApi).generateLink(anyString(), any(Integer.class), anyList(), anyBoolean(), any());
    }
}

