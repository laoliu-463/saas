package com.colonel.saas.controller;

import com.colonel.saas.annotation.RequireRoles;
import com.colonel.saas.constant.RoleCodes;
import com.colonel.saas.common.exception.GlobalExceptionHandler;
import com.colonel.saas.gateway.douyin.DouyinColonelActivityGateway;
import com.colonel.saas.gateway.douyin.DouyinProductGateway;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ColonelActivityControllerTest {

    @Mock
    private DouyinColonelActivityGateway douyinColonelActivityGateway;
    @Mock
    private DouyinProductGateway douyinProductGateway;
    @Mock
    private ProductService productService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ColonelActivityController controller = new ColonelActivityController(
                douyinColonelActivityGateway,
                douyinProductGateway,
                productService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_shouldExposeNormalizedAndLegacyActivityFields() throws Exception {
        DouyinColonelActivityGateway.ActivityItem item = new DouyinColonelActivityGateway.ActivityItem(
                3916506L,
                "星链达客-zy",
                "2026-05-06",
                "2026-08-03",
                3,
                "报名中",
                "2026-05-06",
                "2035-12-31",
                Map.of("9", "美妆"),
                0L
        );
        DouyinColonelActivityGateway.ActivityListResult result =
                new DouyinColonelActivityGateway.ActivityListResult(false, 7351155267604201765L, 21L, List.of(item));

        when(douyinColonelActivityGateway.listActivities(any())).thenReturn(result);

        mockMvc.perform(get("/colonel/activities")
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(21))
                .andExpect(jsonPath("$.data.activityList[0].activityId").value(3916506))
                .andExpect(jsonPath("$.data.activityList[0].status").value(3))
                .andExpect(jsonPath("$.data.activityList[0].activityStatus").value(3))
                .andExpect(jsonPath("$.data.activityList[0].activityStartTime").value("2026-05-06"))
                .andExpect(jsonPath("$.data.activityList[0].startTime").value("2026-05-06"))
                .andExpect(jsonPath("$.data.activityList[0].activityEndTime").value("2026-08-03"))
                .andExpect(jsonPath("$.data.activityList[0].endTime").value("2026-08-03"))
                .andExpect(jsonPath("$.data.activityList[0].statusText").value("报名中"));
    }

    @Test
    void listProducts_shouldExposeBizStatusFields() throws Exception {
        DouyinProductGateway.ActivityProductItem item = new DouyinProductGateway.ActivityProductItem(
                9001L,
                "洁面乳",
                "https://img.test/product.jpg",
                5900L,
                "59.00",
                20L,
                1180L,
                25L,
                "25%",
                1,
                "普通佣金",
                "5%",
                10L,
                true,
                true,
                128L,
                7001L,
                "示例店铺",
                "4.9",
                1,
                "推广中",
                "美妆",
                "1000",
                "满减券",
                "2026-04-25 00:00:00",
                "2026-04-30 23:59:59",
                "2026-04-25 00:00:00",
                "2026-04-30 23:59:59",
                "https://detail.test/products/9001",
                null,
                Map.of()
        );
        DouyinProductGateway.ActivityProductListResult gatewayResult =
                new DouyinProductGateway.ActivityProductListResult(
                        true,
                        100018L,
                        30001L,
                        1L,
                        "next-cursor",
                        List.of(item)
                );

        Map<String, Object> itemView = new LinkedHashMap<>();
        itemView.put("productId", 9001L);
        itemView.put("title", "洁面乳");
        itemView.put("bizStatus", "APPROVED");
        itemView.put("bizStatusLabel", "审核通过");

        Map<String, Object> listView = new LinkedHashMap<>();
        listView.put("mock", true);
        listView.put("activityId", 100018L);
        listView.put("institutionId", 30001L);
        listView.put("total", 1);
        listView.put("nextCursor", "next-cursor");
        listView.put("items", List.of(itemView));

        when(productService.hasActivitySnapshots("100018")).thenReturn(false);
        when(douyinProductGateway.queryActivityProducts(any())).thenReturn(gatewayResult);
        when(productService.buildActivityProductListViewFromDb("100018", 20, null, null, null)).thenReturn(listView);

        mockMvc.perform(get("/colonel/activities/{activityId}/products", "100018")
                        .param("searchType", "4")
                        .param("sortType", "1")
                        .param("count", "20")
                        .param("retrieveMode", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activityId").value(100018))
                .andExpect(jsonPath("$.data.items[0].productId").value(9001))
                .andExpect(jsonPath("$.data.items[0].bizStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.items[0].bizStatusLabel").value("审核通过"));

        verify(productService).hasActivitySnapshots("100018");
        verify(productService).upsertSnapshots(eq("100018"), eq(gatewayResult.items()));
        verify(productService).buildActivityProductListViewFromDb("100018", 20, null, null, null);
    }

    @Test
    void listProducts_refreshTrueShouldBypassExistingSnapshotsAndRefreshFromGateway() throws Exception {
        DouyinProductGateway.ActivityProductItem item = new DouyinProductGateway.ActivityProductItem(
                9002L,
                "防晒霜",
                "https://img.test/product-9002.jpg",
                8900L,
                "89.00",
                30L,
                2670L,
                30L,
                "30%",
                1,
                "普通佣金",
                "6%",
                12L,
                true,
                true,
                129L,
                7002L,
                "真实店铺",
                "4.8",
                1,
                "推广中",
                "美妆",
                "1001",
                "满减券",
                "2026-05-01 00:00:00",
                "2026-05-31 23:59:59",
                "2026-05-01 00:00:00",
                "2026-05-31 23:59:59",
                "https://detail.test/products/9002",
                null,
                Map.of()
        );
        Map<String, Object> itemView = new LinkedHashMap<>();
        itemView.put("productId", 9002L);
        itemView.put("title", "防晒霜");
        itemView.put("bizStatus", "APPROVED");

        Map<String, Object> listView = new LinkedHashMap<>();
        listView.put("mock", false);
        listView.put("activityId", 100018L);
        listView.put("total", 1);
        listView.put("nextCursor", "fresh-cursor");
        listView.put("items", List.of(itemView));

        when(productService.buildActivityProductListViewFromDb("100018", 20, null, null, null)).thenReturn(listView);

        mockMvc.perform(get("/colonel/activities/{activityId}/products", "100018")
                        .param("count", "20")
                        .param("refresh", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.activityId").value(100018))
                .andExpect(jsonPath("$.data.items[0].productId").value(9002))
                .andExpect(jsonPath("$.data.nextCursor").value("fresh-cursor"));

        verify(productService, never()).hasActivitySnapshots("100018");
        verify(productService).refreshActivitySnapshots(any());
        verify(douyinProductGateway, never()).queryActivityProducts(any());
        verify(productService, never()).upsertSnapshots(eq("100018"), any());
        verify(productService).buildActivityProductListViewFromDb("100018", 20, null, null, null);
    }

    @Test
    void controller_shouldNotAllowBizStaffAtClassLevel() {
        RequireRoles requireRoles = ColonelActivityController.class.getAnnotation(RequireRoles.class);
        assertThat(requireRoles).isNotNull();
        assertThat(requireRoles.value()).containsExactly(RoleCodes.BIZ_LEADER, RoleCodes.ADMIN, RoleCodes.COLONEL_LEADER);
    }
}
