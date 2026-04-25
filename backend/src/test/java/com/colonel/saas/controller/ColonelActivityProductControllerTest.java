package com.colonel.saas.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.entity.ProductOperationLog;
import com.colonel.saas.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColonelActivityProductControllerTest {

    @Mock
    private ProductService productService;

    private ColonelActivityProductController controller;

    @BeforeEach
    void setUp() {
        controller = new ColonelActivityProductController(productService);
    }

    @Test
    void follow_shouldCallSharedProductService() {
        UUID userId = UUID.randomUUID();
        when(productService.startTalentFollow(
                eq("10001"),
                eq("9001"),
                any(),
                eq("达人A"),
                eq("INVITED"),
                eq("已发送邀约"),
                any(),
                eq(userId),
                eq("操作人")
        )).thenReturn(Map.of("bizStatus", "FOLLOWING"));

        ColonelActivityProductController.TalentFollowRequest request = new ColonelActivityProductController.TalentFollowRequest();
        request.setTalentName("达人A");
        request.setFollowStatus("INVITED");
        request.setContent("已发送邀约");
        request.setOperatorName("操作人");

        var response = controller.follow("10001", "9001", request, userId);

        assertThat(response.getData().get("bizStatus")).isEqualTo("FOLLOWING");
        verify(productService).startTalentFollow("10001", "9001", null, "达人A", "INVITED", "已发送邀约", null, userId, "操作人");
    }

    @Test
    void operationLogs_shouldReturnPage() {
        Page<ProductOperationLog> page = new Page<>(1, 20);
        page.setTotal(1);
        when(productService.getOperationLogs("10001", "9001", 1, 20)).thenReturn(page);

        var response = controller.operationLogs("10001", "9001", 1, 20);

        assertThat(response.getData().getTotal()).isEqualTo(1);
    }

    @Test
    void assign_shouldReturnAssignedStatus() {
        UUID assigneeId = UUID.randomUUID();
        when(productService.assignProduct("10001", "9001", assigneeId, null, null))
                .thenReturn(Map.of(
                        "bizStatus", "ASSIGNED",
                        "bizStatusLabel", "已分配招商",
                        "assigneeId", assigneeId
                ));

        ColonelActivityProductController.AssignRequest request = new ColonelActivityProductController.AssignRequest();
        request.setAssigneeId(assigneeId);

        var response = controller.assign("10001", "9001", request, null, null);

        assertThat(response.getData().get("bizStatus")).isEqualTo("ASSIGNED");
        assertThat(response.getData().get("assigneeId")).isEqualTo(assigneeId);
        verify(productService).assignProduct("10001", "9001", assigneeId, null, null);
    }
}
