package com.colonel.saas.service;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.order.application.OrderDetailQueryApplicationService;
import com.colonel.saas.dto.order.OrderDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderQueryService 委派壳冒烟测试（DDD-ORDER-006 Slice 1）。
 *
 * <p>Service 已是 1-line delegate；本测试仅验证委派路径打通，详细业务逻辑断言
 * 见 {@link OrderDetailQueryApplicationServiceTest}。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrderDetailQueryApplicationService applicationService;

    private OrderQueryService service;

    @BeforeEach
    void setUp() {
        service = new OrderQueryService(applicationService);
    }

    @Test
    void getOrderDetail_shouldDelegateToApplication() {
        OrderDetailResponse expected = new OrderDetailResponse();
        expected.setOrderId("ord-1");
        when(applicationService.getOrderDetail("ord-1", null, null, DataScope.ALL))
                .thenReturn(expected);

        OrderDetailResponse result = service.getOrderDetail("ord-1", null, null, DataScope.ALL);

        assertThat(result).isSameAs(expected);
        verify(applicationService).getOrderDetail("ord-1", null, null, DataScope.ALL);
    }

    @Test
    void getOrderDetail_shouldDelegateToApplicationWithAllParameters() {
        UUID userId = UUID.randomUUID();
        UUID deptId = UUID.randomUUID();
        OrderDetailResponse expected = new OrderDetailResponse();
        when(applicationService.getOrderDetail("ord-2", userId, deptId, DataScope.DEPT))
                .thenReturn(expected);

        OrderDetailResponse result = service.getOrderDetail("ord-2", userId, deptId, DataScope.DEPT);

        assertThat(result).isSameAs(expected);
        verify(applicationService).getOrderDetail("ord-2", userId, deptId, DataScope.DEPT);
    }
}