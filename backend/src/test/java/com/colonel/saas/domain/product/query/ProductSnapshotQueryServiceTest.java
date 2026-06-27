package com.colonel.saas.domain.product.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.exception.BusinessException;
import com.colonel.saas.entity.ProductSnapshot;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import com.colonel.saas.service.ProductSnapshotQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSnapshotQueryServiceTest {

    @Mock
    private ProductSnapshotMapper snapshotMapper;

    private ProductSnapshotQueryService service;

    @BeforeEach
    void setUp() {
        service = new ProductSnapshotQueryService(snapshotMapper);
    }

    @Test
    void pageLatest_shouldNormalizePageAndSizeBeforeQueryingSnapshots() {
        Page<ProductSnapshot> page = new Page<>(1, 1, 0);
        when(snapshotMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        var result = service.pageLatest(0, 0, 1);

        ArgumentCaptor<Page<ProductSnapshot>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(snapshotMapper).selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class));
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(1);
        assertThat(result).isSameAs(page);
    }

    @Test
    void requireById_shouldThrowExistingNotFoundMessageWhenSnapshotMissing() {
        UUID relationId = UUID.randomUUID();
        when(snapshotMapper.selectById(relationId)).thenReturn(null);

        assertThatThrownBy(() -> service.requireById(relationId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品不存在");
    }

    @Test
    void requireActivityProduct_shouldThrowExistingNotFoundMessageWhenSnapshotMissing() {
        when(snapshotMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.requireActivityProduct("ACT-1", "9001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到商品快照，请先同步活动商品");
    }
}
