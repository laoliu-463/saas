package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.service.ProductActivityBackfillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
                        List.of());
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
                        false);

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
}
