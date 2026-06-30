package com.colonel.saas.domain.product.application;

import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.domain.product.application.port.ProductActivitySyncSchedulePort;
import com.colonel.saas.domain.product.application.port.ProductActivitySyncStatePort;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductActivitySyncApplicationServiceTest {

    @Mock
    private ProductService productService;
    @Mock
    private ProductActivitySyncStatePort productActivitySyncStatePort;
    @Mock
    private ProductActivitySyncSchedulePort productActivitySyncSchedulePort;

    @Test
    void refreshActivitySnapshots_shouldDelegateToLegacyProductServiceAndMapStats() {
        ProductActivitySyncApplicationService applicationService = applicationService();
        DouyinProductGateway.ActivityProductQueryRequest request =
                new DouyinProductGateway.ActivityProductQueryRequest(
                        null, "ACT-1", 4L, 1L, 20, null, null, null, null, 1L, null, null);
        when(productService.refreshActivitySnapshots(request, 7, 900))
                .thenReturn(new ProductService.ActivityProductRefreshResult(
                        11,
                        2,
                        3,
                        4,
                        1,
                        5,
                        12,
                        11,
                        1,
                        "MAX_PAGES_REACHED",
                        true,
                        false));

        ProductActivitySyncApplicationService.ActivityProductRefreshResult result =
                applicationService.refreshActivitySnapshots(request, 7, 900);

        assertThat(result.syncedProductCount()).isEqualTo(11);
        assertThat(result.libraryEntryCount()).isEqualTo(2);
        assertThat(result.createdCount()).isEqualTo(3);
        assertThat(result.updatedCount()).isEqualTo(4);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.pagesFetched()).isEqualTo(5);
        assertThat(result.fetchedRows()).isEqualTo(12);
        assertThat(result.distinctProductIds()).isEqualTo(11);
        assertThat(result.duplicateProductIds()).isEqualTo(1);
        assertThat(result.stoppedReason()).isEqualTo("MAX_PAGES_REACHED");
        assertThat(result.stillHasNextWhenStopped()).isTrue();
        assertThat(result.complete()).isFalse();
        verify(productService).refreshActivitySnapshots(request, 7, 900);
    }

    @Test
    void refreshManualActivitySnapshots_shouldBuildClampedRequestAndDelegate() {
        ProductActivitySyncApplicationService applicationService = applicationService();
        when(productService.refreshActivitySnapshots(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ProductService.ActivityProductRefreshResult(
                        9,
                        3,
                        4,
                        5,
                        0,
                        2,
                        9,
                        8,
                        1,
                        "DONE_NO_MORE",
                        false,
                        true));

        ProductActivitySyncApplicationService.ActivityProductRefreshResult result =
                applicationService.refreshManualActivitySnapshots(" ACT-1 ", "APP-1", 100);

        ArgumentCaptor<DouyinProductGateway.ActivityProductQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinProductGateway.ActivityProductQueryRequest.class);
        verify(productService).refreshActivitySnapshots(captor.capture());
        assertThat(captor.getValue().appId()).isEqualTo("APP-1");
        assertThat(captor.getValue().activityId()).isEqualTo("ACT-1");
        assertThat(captor.getValue().searchType()).isEqualTo(4L);
        assertThat(captor.getValue().sortType()).isEqualTo(1L);
        assertThat(captor.getValue().count()).isEqualTo(20);
        assertThat(captor.getValue().retrieveMode()).isEqualTo(1L);
        assertThat(captor.getValue().page()).isNull();
        assertThat(result.libraryEntryCount()).isEqualTo(3);
        assertThat(result.pagesFetched()).isEqualTo(2);
        assertThat(result.complete()).isTrue();
    }

    @Test
    void refreshScheduledActivitySnapshots_shouldBuildLegacyScheduledRequestAndDelegate() {
        ProductActivitySyncApplicationService applicationService = applicationService();
        when(productService.refreshActivitySnapshots(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ProductService.ActivityProductRefreshResult(7, 2, 3, 4, 0));

        ProductActivitySyncApplicationService.ActivityProductRefreshResult result =
                applicationService.refreshScheduledActivitySnapshots(" ACT-9 ", 0);

        ArgumentCaptor<DouyinProductGateway.ActivityProductQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinProductGateway.ActivityProductQueryRequest.class);
        verify(productService).refreshActivitySnapshots(captor.capture());
        assertThat(captor.getValue().appId()).isNull();
        assertThat(captor.getValue().activityId()).isEqualTo("ACT-9");
        assertThat(captor.getValue().searchType()).isEqualTo(4L);
        assertThat(captor.getValue().sortType()).isEqualTo(1L);
        assertThat(captor.getValue().count()).isEqualTo(1);
        assertThat(captor.getValue().retrieveMode()).isEqualTo(1L);
        assertThat(captor.getValue().page()).isNull();
        assertThat(result.syncedProductCount()).isEqualTo(7);
        assertThat(result.libraryEntryCount()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void refreshActivityProductList_shouldBuildLegacyRefreshRequestAndAppendSyncStats() throws Exception {
        ProductActivitySyncApplicationService applicationService = applicationService();
        when(productService.refreshActivitySnapshots(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ProductService.ActivityProductRefreshResult(6, 2, 1, 5, 0));
        Map<String, Object> listView = new LinkedHashMap<>();
        listView.put("items", List.of(Map.of("productId", 9002L)));
        listView.put("total", 1L);
        when(productService.buildActivityProductListViewFromDb(
                "100018",
                20,
                "cursor-1",
                "防晒",
                "APPROVED",
                1,
                "commissionDesc",
                "tag-a",
                "tag-b"))
                .thenReturn(listView);

        ProductActivitySyncApplicationService.ActivityProductListRefreshCommand command =
                new ProductActivitySyncApplicationService.ActivityProductListRefreshCommand(
                        "100018",
                        4L,
                        1L,
                        20,
                        "coop",
                        2,
                        "防晒",
                        "APPROVED",
                        1,
                        1L,
                        "cursor-1",
                        3L,
                        "APP-1",
                        "commissionDesc",
                        "tag-a",
                        "tag-b");

        Map<String, Object> result = applicationService.refreshActivityProductList(command);

        ArgumentCaptor<DouyinProductGateway.ActivityProductQueryRequest> captor =
                ArgumentCaptor.forClass(DouyinProductGateway.ActivityProductQueryRequest.class);
        verify(productService).refreshActivitySnapshots(captor.capture());
        assertThat(captor.getValue().appId()).isEqualTo("APP-1");
        assertThat(captor.getValue().activityId()).isEqualTo("100018");
        assertThat(captor.getValue().searchType()).isEqualTo(4L);
        assertThat(captor.getValue().sortType()).isEqualTo(1L);
        assertThat(captor.getValue().count()).isEqualTo(20);
        assertThat(captor.getValue().cooperationInfo()).isEqualTo("coop");
        assertThat(captor.getValue().cooperationType()).isEqualTo(2);
        assertThat(captor.getValue().productInfo()).isEqualTo("防晒");
        assertThat(captor.getValue().status()).isEqualTo(1);
        assertThat(captor.getValue().retrieveMode()).isEqualTo(1L);
        assertThat(captor.getValue().cursor()).isEqualTo("cursor-1");
        assertThat(captor.getValue().page()).isEqualTo(3L);
        verify(productService).buildActivityProductListViewFromDb(
                "100018", 20, "cursor-1", "防晒", "APPROVED", 1, "commissionDesc", "tag-a", "tag-b");
        assertThat(result).isSameAs(listView);
        assertThat((Map<String, Object>) result.get("syncStats"))
                .containsOnlyKeys(
                        "syncedProductCount",
                        "libraryEntryCount",
                        "createdCount",
                        "updatedCount",
                        "skippedCount",
                        "autoLibraryEligible")
                .containsEntry("syncedProductCount", 6)
                .containsEntry("libraryEntryCount", 2)
                .containsEntry("createdCount", 1)
                .containsEntry("updatedCount", 5)
                .containsEntry("skippedCount", 0)
                .containsEntry("autoLibraryEligible", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadActivityProductList_shouldReturnLocalSnapshotViewWhenSnapshotsExist() throws Exception {
        ProductActivitySyncApplicationService applicationService = applicationService();
        Map<String, Object> listView = new LinkedHashMap<>();
        listView.put("activityId", "100018");
        listView.put("items", List.of(Map.of("productId", 9002L)));
        listView.put("total", 1L);
        when(productService.hasActivitySnapshots("100018")).thenReturn(true);
        when(productService.buildActivityProductListViewFromDb(
                "100018", 20, "cursor-1", "防晒", "APPROVED", 1, "commissionDesc", "tag-a", "tag-b"))
                .thenReturn(listView);

        ProductActivitySyncApplicationService.ActivityProductListQueryCommand command =
                new ProductActivitySyncApplicationService.ActivityProductListQueryCommand(
                "100018", 20, "cursor-1", "防晒", "APPROVED", 1, "commissionDesc", "tag-a", "tag-b");

        Map<String, Object> result = applicationService.loadActivityProductList(command);

        assertThat(result).isSameAs(listView);
        verify(productService).hasActivitySnapshots("100018");
        verify(productService).buildActivityProductListViewFromDb(
                "100018", 20, "cursor-1", "防晒", "APPROVED", 1, "commissionDesc", "tag-a", "tag-b");
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadActivityProductList_shouldReturnLegacyNeedSyncHintWhenSnapshotsMissing() throws Exception {
        ProductActivitySyncApplicationService applicationService = applicationService();
        when(productService.hasActivitySnapshots("100018")).thenReturn(false);

        ProductActivitySyncApplicationService.ActivityProductListQueryCommand command =
                new ProductActivitySyncApplicationService.ActivityProductListQueryCommand(
                        "100018", 20, null, null, null, null, null, null, null);

        Map<String, Object> result = applicationService.loadActivityProductList(command);

        assertThat(result)
                .containsEntry("total", 0L)
                .containsEntry("activityId", "100018")
                .containsEntry("needSync", Boolean.TRUE)
                .containsEntry("errorCode", "DATA_NOT_READY")
                .containsEntry("message", "该活动尚未同步商品，请先点击「同步商品」")
                .containsEntry("lastSyncAt", null);
        assertThat((List<?>) result.get("items")).isEmpty();
        verify(productService).hasActivitySnapshots("100018");
        verify(productService, never()).buildActivityProductListViewFromDb(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void buildManualSyncTriggerPayload_shouldPreserveLegacyStatusMessages() {
        ProductActivitySyncApplicationService applicationService = applicationService();

        Map<String, Object> accepted =
                applicationService.buildManualSyncTriggerPayload("100018", "ACCEPTED");
        Map<String, Object> running =
                applicationService.buildManualSyncTriggerPayload("100018", "RUNNING");
        Map<String, Object> invalid =
                applicationService.buildManualSyncTriggerPayload("", "INVALID");

        assertThat(accepted)
                .containsEntry("activityId", "100018")
                .containsEntry("syncStatus", "ACCEPTED")
                .containsEntry("message", "商品同步已转入后台执行");
        assertThat(running)
                .containsEntry("activityId", "100018")
                .containsEntry("syncStatus", "RUNNING")
                .containsEntry("message", "商品同步已在后台执行，请稍后刷新列表");
        assertThat(invalid)
                .containsEntry("activityId", "")
                .containsEntry("syncStatus", "INVALID")
                .containsEntry("message", "商品同步已转入后台执行");
    }

    @Test
    void markActivitySyncCompleted_shouldDelegateToStatePortWithCurrentTimestamp() {
        ProductActivitySyncApplicationService applicationService = applicationService();

        applicationService.markActivitySyncCompleted("ACT-1");

        ArgumentCaptor<LocalDateTime> completedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(productActivitySyncStatePort).markActivitySyncCompleted(
                org.mockito.ArgumentMatchers.eq("ACT-1"),
                completedAtCaptor.capture());
        assertThat(completedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void loadScheduledActivityIds_shouldDelegateToSchedulePort() {
        ProductActivitySyncApplicationService applicationService = applicationService();
        LocalDateTime lastSyncedBefore = LocalDateTime.of(2026, 6, 30, 14, 30);
        when(productActivitySyncSchedulePort.findActivitiesDueForSync(20, lastSyncedBefore))
                .thenReturn(List.of("ACT-10", "ACT-20"));

        List<String> result = applicationService.loadScheduledActivityIds(20, lastSyncedBefore);

        assertThat(result).containsExactly("ACT-10", "ACT-20");
        verify(productActivitySyncSchedulePort).findActivitiesDueForSync(20, lastSyncedBefore);
    }

    private ProductActivitySyncApplicationService applicationService() {
        return new ProductActivitySyncApplicationService(
                productService,
                productActivitySyncStatePort,
                productActivitySyncSchedulePort);
    }

}
