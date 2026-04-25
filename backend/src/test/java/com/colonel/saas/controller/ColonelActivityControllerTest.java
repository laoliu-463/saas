package com.colonel.saas.controller;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                "https://detail.test/products/9001"
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
}
