package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.colonel.saas.entity.ProductOperationState;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductPinServiceTest {

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
    void isPinned_shouldBeTrueWhenPinnedUntilInFuture() {
        ProductOperationState state = new ProductOperationState();
        state.setPinnedUntil(LocalDateTime.now().plusHours(2));
        assertThat(ProductPinService.isPinned(state, LocalDateTime.now())).isTrue();
    }

    @Test
    void isPinned_shouldBeFalseWhenPinnedUntilExpired() {
        ProductOperationState state = new ProductOperationState();
        state.setPinnedUntil(LocalDateTime.now().minusMinutes(1));
        assertThat(ProductPinService.isPinned(state, LocalDateTime.now())).isFalse();
    }

    @Test
    void isPinned_shouldBeFalseAfterExpireColumnsCleared() {
        ProductOperationState state = new ProductOperationState();
        state.setPinnedAt(null);
        state.setPinnedUntil(null);
        state.setPinnedBy(null);
        assertThat(ProductPinService.isPinned(state, LocalDateTime.now())).isFalse();
    }

    @Test
    void expirePinnedProducts_shouldReturnCleanupCount() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 23, 10, 0);
        when(operationStateMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(2);

        int expired = service.expirePinnedProducts(now);

        assertThat(expired).isEqualTo(2);
    }

    @Test
    void expirePinnedProducts_shouldClearPinnedColumnsForExpiredRows() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 23, 10, 0);
        when(operationStateMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        service.expirePinnedProducts(now);

        ArgumentCaptor<UpdateWrapper<ProductOperationState>> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(operationStateMapper).update(isNull(), captor.capture());
        String sql = captor.getValue().getSqlSet();
        assertThat(sql)
                .contains("pinned_at")
                .contains("pinned_until")
                .contains("pinned_by");
    }

    @Test
    void expirePinnedProducts_shouldNotTouchActivePinsWhenNothingExpired() {
        when(operationStateMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(0);

        int expired = service.expirePinnedProducts(LocalDateTime.now());

        assertThat(expired).isZero();
        verify(operationStateMapper).update(isNull(), any(UpdateWrapper.class));
    }
}
