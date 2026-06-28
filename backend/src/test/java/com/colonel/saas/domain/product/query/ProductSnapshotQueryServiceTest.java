package com.colonel.saas.domain.product.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ProductSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSnapshotQueryServiceTest {

    @Mock
    private ProductSnapshotQueryRepository snapshotRepository;

    private ProductSnapshotQueryService service;

    @BeforeEach
    void setUp() {
        service = new ProductSnapshotQueryService(snapshotRepository);
    }

    @Test
    void pageLatest_shouldNormalizePageAndSizeBeforeQueryingSnapshots() {
        Page<ProductSnapshot> page = new Page<>(1, 1, 0);
        when(snapshotRepository.pageLatest(anyLong(), anyLong(), eq(1))).thenReturn(page);

        var result = service.pageLatest(0, 0, 1);

        ArgumentCaptor<Long> pageCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> sizeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(snapshotRepository).pageLatest(pageCaptor.capture(), sizeCaptor.capture(), eq(1));
        assertThat(pageCaptor.getValue()).isEqualTo(1L);
        assertThat(sizeCaptor.getValue()).isEqualTo(1L);
        assertThat(result).isSameAs(page);
    }

    @Test
    void requireById_shouldThrowExistingNotFoundMessageWhenSnapshotMissing() {
        UUID relationId = UUID.randomUUID();
        when(snapshotRepository.findById(relationId)).thenReturn(null);

        assertThatThrownBy(() -> service.requireById(relationId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品不存在");
    }

    @Test
    void requireActivityProduct_shouldThrowExistingNotFoundMessageWhenSnapshotMissing() {
        when(snapshotRepository.findActivityProduct("ACT-1", "9001")).thenReturn(null);

        assertThatThrownBy(() -> service.requireActivityProduct("ACT-1", "9001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到商品快照，请先同步活动商品");
    }
}
