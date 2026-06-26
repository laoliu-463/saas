package com.colonel.saas.architecture;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.config.DddRefactorProperties;
import com.colonel.saas.controller.OrderController;
import com.colonel.saas.domain.order.facade.OrderDomainFacade;
import com.colonel.saas.domain.order.query.OrderDetailView;
import com.colonel.saas.domain.order.query.OrderQueryView;
import com.colonel.saas.dto.order.OrderDetailResponse;
import com.colonel.saas.entity.ColonelsettlementOrder;
import com.colonel.saas.mapper.ColonelsettlementOrderMapper;
import com.colonel.saas.service.AttributionService;
import com.colonel.saas.service.OperationLogService;
import com.colonel.saas.service.Order1603SettlementDryRunService;
import com.colonel.saas.service.Order2704SettlementDryRunService;
import com.colonel.saas.service.Order6468PaginationDryRunService;
import com.colonel.saas.service.OrderAttributionReplayService;
import com.colonel.saas.service.OrderQueryService;
import com.colonel.saas.service.OrderService;
import com.colonel.saas.service.OrderSyncService;
import com.colonel.saas.service.ShortTtlCacheService;
import com.colonel.saas.domain.user.facade.UserDomainFacade;
import com.colonel.saas.domain.user.policy.DataScopePolicy;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.colonel.saas.common.handler.UUIDTypeHandler;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DddOrder003RoutingTest {

    @Mock private OrderSyncService orderSyncService;
    @Mock private ColonelsettlementOrderMapper orderMapper;
    @Mock private OrderQueryService orderQueryService;
    @Mock private OrderAttributionReplayService orderAttributionReplayService;
    @Mock private OperationLogService operationLogService;
    @Mock private ShortTtlCacheService shortTtlCacheService;
    @Mock private UserDomainFacade userDomainFacade;
    @Mock private Order6468PaginationDryRunService order6468PaginationDryRunService;
    @Mock private Order1603SettlementDryRunService order1603SettlementDryRunService;
    @Mock private Order2704SettlementDryRunService order2704SettlementDryRunService;
    @Mock private OrderService orderService;
    @Mock private DddRefactorProperties dddRefactorProperties;
    @Mock private DddRefactorProperties.Switch orderApplicationSwitch;
    @Mock private OrderDomainFacade orderDomainFacade;

    private OrderController controller;
    private final UUID userId = UUID.randomUUID();
    private final UUID deptId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        initTableInfo(ColonelsettlementOrder.class);
        controller = new OrderController(
                orderSyncService,
                orderMapper,
                orderQueryService,
                orderAttributionReplayService,
                operationLogService,
                shortTtlCacheService,
                userDomainFacade,
                order6468PaginationDryRunService,
                order1603SettlementDryRunService,
                order2704SettlementDryRunService,
                orderService,
                dddRefactorProperties,
                orderDomainFacade,
                new DataScopePolicy()
        );
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.getTypeHandlerRegistry().register(java.util.UUID.class, UUIDTypeHandler.class);
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }

    private void enableFacadeSwitch() {
        when(dddRefactorProperties.isEnabled()).thenReturn(true);
        when(dddRefactorProperties.getOrderApplication()).thenReturn(orderApplicationSwitch);
        when(orderApplicationSwitch.isEnabled()).thenReturn(true);
    }

    @Test
    @DisplayName("开关关闭时，getOrders调用原有逻辑")
    void shouldDelegateGetOrdersWhenSwitchOff() {
        when(dddRefactorProperties.isEnabled()).thenReturn(false);
        IPage<ColonelsettlementOrder> expected = new Page<>();
        when(orderService.findPage(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(expected);

        var response = controller.getOrders(1, 20, "ORD-1", "ATTRIBUTED", null, null, null, null, null, null, null, null, null, null, null, null, userId, deptId, DataScope.ALL);

        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getRecords()).isEmpty();
        verify(orderService).findPage(eq(1L), eq(20L), eq("ORD-1"), eq("ATTRIBUTED"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(userId), eq(deptId), eq(DataScope.ALL));
        verify(orderDomainFacade, never()).getOrders(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("开关开启时，getOrders路由至OrderDomainFacade")
    void shouldRouteGetOrdersToFacadeWhenSwitchOn() {
        enableFacadeSwitch();
        IPage<OrderQueryView> expected = new Page<>();
        when(orderDomainFacade.getOrders(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(expected);

        var response = controller.getOrders(1, 20, "ORD-1", "ATTRIBUTED", null, null, null, null, null, null, null, null, null, null, null, null, userId, deptId, DataScope.ALL);

        assertThat(response.getData()).isSameAs(expected);
        verify(orderService, never()).findPage(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(orderDomainFacade).getOrders(eq(1L), eq(20L), eq("ORD-1"), eq("ATTRIBUTED"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(userId), eq(deptId), eq(DataScope.ALL));
    }

    @Test
    @DisplayName("开关关闭时，getUnattributedOrders调用原有逻辑")
    void shouldDelegateGetUnattributedOrdersWhenSwitchOff() {
        when(dddRefactorProperties.isEnabled()).thenReturn(false);
        IPage<ColonelsettlementOrder> expected = new Page<>();
        when(orderService.findPage(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(expected);

        var response = controller.getUnattributedOrders(1, 20, "ORD-1", null, null, null, null, null, null, null, null, null, null, null, null, userId, deptId, DataScope.ALL);

        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getRecords()).isEmpty();
        verify(orderService).findPage(eq(1L), eq(20L), eq("ORD-1"), eq(AttributionService.STATUS_UNATTRIBUTED), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(userId), eq(deptId), eq(DataScope.ALL));
        verify(orderDomainFacade, never()).getOrders(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("开关开启时，getUnattributedOrders路由至OrderDomainFacade，强制传参UNATTRIBUTED")
    void shouldRouteGetUnattributedOrdersToFacadeWhenSwitchOn() {
        enableFacadeSwitch();
        IPage<OrderQueryView> expected = new Page<>();
        when(orderDomainFacade.getOrders(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(expected);

        var response = controller.getUnattributedOrders(1, 20, "ORD-1", null, null, null, null, null, null, null, null, null, null, null, null, userId, deptId, DataScope.ALL);

        assertThat(response.getData()).isSameAs(expected);
        verify(orderDomainFacade).getOrders(eq(1L), eq(20L), eq("ORD-1"), eq(AttributionService.STATUS_UNATTRIBUTED), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(userId), eq(deptId), eq(DataScope.ALL));
    }

    @Test
    @DisplayName("开关关闭时，getOrderDetail调用原有逻辑")
    void shouldDelegateGetOrderDetailWhenSwitchOff() {
        when(dddRefactorProperties.isEnabled()).thenReturn(false);
        OrderDetailResponse expected = new OrderDetailResponse();
        when(orderQueryService.getOrderDetail(any(), any(), any(), any())).thenReturn(expected);

        var response = controller.getOrderDetail("ORD-1", userId, deptId, DataScope.ALL);

        assertThat(response.getData()).isInstanceOf(OrderDetailView.class);
        verify(orderDomainFacade, never()).getOrderDetail(any(), any(), any(), any());
    }

    @Test
    @DisplayName("开关开启时，getOrderDetail路由至OrderDomainFacade")
    void shouldRouteGetOrderDetailToFacadeWhenSwitchOn() {
        enableFacadeSwitch();
        OrderDetailView expected = new OrderDetailView();
        when(orderDomainFacade.getOrderDetail(any(), any(), any(), any())).thenReturn(expected);

        var response = controller.getOrderDetail("ORD-1", userId, deptId, DataScope.ALL);

        assertThat(response.getData()).isSameAs(expected);
        verify(orderQueryService, never()).getOrderDetail(any(), any(), any(), any());
        verify(orderDomainFacade).getOrderDetail(eq("ORD-1"), eq(userId), eq(deptId), eq(DataScope.ALL));
    }

    @Test
    @DisplayName("开关关闭时，getStats调用原有逻辑")
    void shouldDelegateGetStatsWhenSwitchOff() {
        when(dddRefactorProperties.isEnabled()).thenReturn(false);
        when(orderService.findStats(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new OrderService.OrderStatsResult(0, 0, 0, 0, 0, null, List.of()));

        var response = controller.getStats("ORD-1", "ATTRIBUTED", null, null, null, null, null, null, null, null, null, null, null, null, userId, deptId, DataScope.ALL);

        assertThat(response.getData()).isNotNull();
        verify(orderService).findStats(eq("ORD-1"), eq("ATTRIBUTED"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(userId), eq(deptId), eq(DataScope.ALL), any());
        verify(orderDomainFacade, never()).getStats(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("开关开启时，getStats路由至OrderDomainFacade")
    void shouldRouteGetStatsToFacadeWhenSwitchOn() {
        enableFacadeSwitch();
        OrderController.OrderStats expected = new OrderController.OrderStats();
        when(orderDomainFacade.getStats(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(expected);

        var response = controller.getStats("ORD-1", "ATTRIBUTED", null, null, null, null, null, null, null, null, null, null, null, null, userId, deptId, DataScope.ALL);

        assertThat(response.getData()).isSameAs(expected);
        verify(orderService, never()).findStats(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any());
        verify(orderDomainFacade).getStats(eq("ORD-1"), eq("ATTRIBUTED"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(userId), eq(deptId), eq(DataScope.ALL));
    }
}
