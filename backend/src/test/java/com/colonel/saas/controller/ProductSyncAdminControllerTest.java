package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.ProductActivityBackfillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSyncAdminControllerTest {

    @Mock
    private ProductActivityBackfillService backfillService;

    private ProductSyncAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductSyncAdminController(backfillService);
    }

    @Test
    void backfillActivityProducts_shouldRequireAdminAndDelegateUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        ProductActivityBackfillService.BackfillResult result =
                new ProductActivityBackfillService.BackfillResult(
                        "job-1",
                        true,
                        "CUSTOM_ACTIVITY_IDS",
                        1,
                        1,
                        0,
                        0,
                        120,
                        100,
                        20,
                        100,
                        0,
                        0,
                        0,
                        0,
                        Map.of("DONE_NO_MORE", 1L),
                        List.of(),
                        0L,
                        0L,
                        0);
        when(backfillService.backfill(any(), eq(userId))).thenReturn(result);

        ProductActivityBackfillService.BackfillRequest request =
                new ProductActivityBackfillService.BackfillRequest(
                        "CUSTOM_ACTIVITY_IDS",
                        List.of("3859423"),
                        20,
                        50,
                        1000,
                        50_000,
                        true,
                        false,
                        "DEFERRED");

        var response = controller.backfillActivityProducts(request, userId);

        assertThat(response.getData().jobId()).isEqualTo("job-1");
        verify(backfillService).backfill(request, userId);

        Method method = ProductSyncAdminController.class.getMethod(
                "backfillActivityProducts",
                ProductActivityBackfillService.BackfillRequest.class,
                UUID.class);
        assertThat(ProductSyncAdminController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/product-sync/admin");
        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/backfill-activity-products");
        assertThat(method.getAnnotation(RequireRoles.class).value())
                .containsExactly(RoleCodes.ADMIN);
    }

    @Test
    void backfillActivityProductsAsync_shouldRequireAdminAndDelegateUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        ProductActivityBackfillService.BackfillAsyncResponse result =
                new ProductActivityBackfillService.BackfillAsyncResponse("job-async-1", "RUNNING");
        ProductActivityBackfillService.BackfillRequest request =
                new ProductActivityBackfillService.BackfillRequest(
                        "CUSTOM_ACTIVITY_IDS",
                        List.of("3859423"),
                        20,
                        50,
                        1000,
                        50_000,
                        true,
                        false,
                        "DEFERRED");
        when(backfillService.backfillAsync(request, userId)).thenReturn(result);

        var response = controller.backfillActivityProductsAsync(request, userId);

        assertThat(response.getData().jobId()).isEqualTo("job-async-1");
        assertThat(response.getData().status()).isEqualTo("RUNNING");
        verify(backfillService).backfillAsync(request, userId);

        Method method = ProductSyncAdminController.class.getMethod(
                "backfillActivityProductsAsync",
                ProductActivityBackfillService.BackfillRequest.class,
                UUID.class);
        assertThat(method.getAnnotation(PostMapping.class).value())
                .containsExactly("/backfill-activity-products/async");
        assertThat(method.getAnnotation(RequireRoles.class).value())
                .containsExactly(RoleCodes.ADMIN);
    }

    @Test
    void getBackfillJobStatus_shouldRequireAdminAndDelegateJobId() throws Exception {
        ProductActivityBackfillService.BackfillJobStatus status =
                new ProductActivityBackfillService.BackfillJobStatus(
                        "job-1",
                        "RUNNING",
                        true,
                        "CUSTOM_ACTIVITY_IDS",
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        Map.of(),
                        "ACT-1",
                        "2026-06-27T15:20:00",
                        0L,
                        0L,
                        0,
                        "2026-06-27T15:19:00",
                        null);
        when(backfillService.getJobStatus("job-1")).thenReturn(status);

        var response = controller.getBackfillJobStatus("job-1");

        assertThat(response.getData().currentActivityId()).isEqualTo("ACT-1");
        verify(backfillService).getJobStatus("job-1");

        Method method = ProductSyncAdminController.class.getMethod("getBackfillJobStatus", String.class);
        assertThat(method.getAnnotation(GetMapping.class).value())
                .containsExactly("/backfill-jobs/{jobId}");
        assertThat(method.getAnnotation(RequireRoles.class).value())
                .containsExactly(RoleCodes.ADMIN);
    }
}
