package com.colonel.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.mapper.ColonelsettlementActivityMapper;
import com.colonel.saas.mapper.ProductOperationStateMapper;
import com.colonel.saas.mapper.ProductSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSyncDryRunProbeServiceTest {

    @Mock
    private DouyinProductGateway douyinProductGateway;
    @Mock
    private ProductSnapshotMapper snapshotMapper;
    @Mock
    private ProductOperationStateMapper operationStateMapper;
    @Mock
    private ColonelsettlementActivityMapper activityMapper;

    private ProductSyncDryRunProbeService service;

    @BeforeEach
    void setUp() {
        service = new ProductSyncDryRunProbeService(
                douyinProductGateway,
                snapshotMapper,
                operationStateMapper,
                activityMapper);
    }

    @Test
    void deepDryRun_shouldCallUpstreamOnlyAndReportPaginationSummary() {
        when(douyinProductGateway.queryActivityProducts(any()))
                .thenReturn(page("20", products(1, 20)))
                .thenReturn(page("", products(21, 5)));
        when(snapshotMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(10L);

        ProductSyncDryRunProbeService.ActivityDryRunResult result = service.deepDryRun(
                new ProductSyncDryRunProbeService.ActivityDeepDryRunRequest(
                        "3859423",
                        20,
                        300,
                        true,
                        true));

        assertThat(result.dryRun()).isTrue();
        assertThat(result.activityId()).isEqualTo("3859423");
        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.totalFetchedRows()).isEqualTo(25);
        assertThat(result.distinctProductIds()).isEqualTo(25);
        assertThat(result.currentDbRowsForActivity()).isEqualTo(10);
        assertThat(result.estimatedGapRows()).isEqualTo(15);
        assertThat(result.stoppedReason()).isEqualTo(ActivityProductPaginationRunner.StopReason.DONE_NO_MORE.name());
        verify(snapshotMapper, never()).upsert(any());
        verify(operationStateMapper, never()).insert(any());
        verify(operationStateMapper, never()).updateById(any());
    }

    @Test
    void fullDryRun_shouldContinueWhenSingleActivityFails() {
        when(activityMapper.selectActivityIdsForProductSyncProbe("ACTIVE_ONLY", 50, null, null))
                .thenReturn(List.of("ACT-1", "ACT-2"));
        when(douyinProductGateway.queryActivityProducts(any()))
                .thenThrow(new IllegalStateException("ACT-1 failed"))
                .thenReturn(page("", products(1, 3)));
        when(snapshotMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        ProductSyncDryRunProbeService.FullDryRunResult result = service.fullDryRun(
                new ProductSyncDryRunProbeService.FullDryRunRequest(
                        "ACTIVE_ONLY",
                        List.of(),
                        50,
                        20,
                        300,
                        true));

        assertThat(result.dryRun()).isTrue();
        assertThat(result.activitiesScanned()).isEqualTo(2);
        assertThat(result.activitiesWithProducts()).isEqualTo(1);
        assertThat(result.apiFetchedRows()).isEqualTo(3);
        assertThat(result.apiErrors()).hasSize(1);
        assertThat(result.activityResults())
                .extracting(ProductSyncDryRunProbeService.ActivityDryRunResult::activityId)
                .containsExactly("ACT-1", "ACT-2");
        verify(snapshotMapper, never()).upsert(any());
        verify(operationStateMapper, never()).insert(any());
        verify(operationStateMapper, never()).updateById(any());
    }

    private DouyinProductGateway.ActivityProductListResult page(
            String nextCursor,
            List<DouyinProductGateway.ActivityProductItem> products) {
        return new DouyinProductGateway.ActivityProductListResult(false, 3859423L, 1L, null, nextCursor, products);
    }

    private List<DouyinProductGateway.ActivityProductItem> products(int start, int count) {
        List<DouyinProductGateway.ActivityProductItem> products = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long productId = start + i;
            products.add(new DouyinProductGateway.ActivityProductItem(
                    productId,
                    "Product " + productId,
                    "",
                    100L,
                    "1.00",
                    1000L,
                    10L,
                    1000L,
                    "10%",
                    1,
                    "普通",
                    null,
                    null,
                    false,
                    true,
                    0L,
                    1L,
                    "Shop",
                    null,
                    1,
                    "推广中",
                    "category",
                    "100",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of()));
        }
        return products;
    }
}
