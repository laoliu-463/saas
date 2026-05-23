package com.colonel.saas.service;

import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.vo.PinnedProductVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductPinServiceListTest {

    @Mock
    private ProductOperationStateMapper operationStateMapper;
    @Mock
    private ProductSnapshotMapper productSnapshotMapper;

    private ProductPinService service;

    @BeforeEach
    void setUp() {
        service = new ProductPinService(operationStateMapper, productSnapshotMapper);
    }

    @Test
    void listPinnedProducts_shouldReturnActivePins() {
        UUID userId = UUID.randomUUID();
        ProductOperationState state = new ProductOperationState();
        state.setActivityId("ACT-1");
        state.setProductId("P-1");
        state.setPinnedAt(LocalDateTime.now().minusHours(1));
        state.setPinnedUntil(LocalDateTime.now().plusHours(23));

        ProductSnapshot snapshot = new ProductSnapshot();
        snapshot.setActivityId("ACT-1");
        snapshot.setProductId("P-1");
        snapshot.setTitle("测试商品");
        snapshot.setCover("https://img/cover.jpg");

        when(operationStateMapper.selectList(any())).thenReturn(List.of(state));
        when(productSnapshotMapper.selectOne(any())).thenReturn(snapshot);

        List<PinnedProductVO> pinned = service.listPinnedProducts(userId);

        assertThat(pinned).hasSize(1);
        assertThat(pinned.get(0).productName()).isEqualTo("测试商品");
        assertThat(pinned.get(0).activityId()).isEqualTo("ACT-1");
    }
}
