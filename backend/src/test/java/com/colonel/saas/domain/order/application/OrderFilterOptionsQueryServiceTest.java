package com.colonel.saas.domain.order.application;

import com.colonel.saas.common.enums.DataScope;
import com.colonel.saas.domain.order.application.dto.OrderFilterOptionItem;
import com.colonel.saas.domain.order.application.dto.OrderFilterOptionsQuery;
import com.colonel.saas.domain.order.application.dto.OrderFilterOptionsResult;
import com.colonel.saas.domain.order.application.port.OrderFilterOptionsPort;
import com.colonel.saas.service.AttributionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFilterOptionsQueryServiceTest {

    @Mock
    private OrderFilterOptionsPort port;

    @Test
    void getFilterOptions_shouldPreserveLegacyLabelsAndSkipInvalidValues() {
        when(port.listOrderStatusValues(any())).thenReturn(List.of("2", "bad"));
        when(port.listAttributionStatusValues(any())).thenReturn(List.of("PARTIAL", "FAILED"));
        when(port.listUnattributedReasonValues(any())).thenReturn(List.of(
                AttributionService.REASON_NO_PICK_SOURCE,
                AttributionService.REASON_COLONEL_MAPPING_NOT_FOUND,
                "自定义原因"));
        when(port.listProductOptions(any())).thenReturn(List.of(
                new OrderFilterOptionItem("P-1", "商品一"),
                new OrderFilterOptionItem("P-2", null),
                new OrderFilterOptionItem("", "空值")));
        when(port.listChannelOptions(any())).thenReturn(List.of(new OrderFilterOptionItem("渠道甲", "渠道甲")));
        when(port.listColonelOptions(any())).thenReturn(List.of(new OrderFilterOptionItem("团长甲", "团长甲")));

        OrderFilterOptionsQueryService service = new OrderFilterOptionsQueryService(port);
        OrderFilterOptionsResult result = service.getFilterOptions(
                new OrderFilterOptionsQuery("甲", UUID.randomUUID(), null, DataScope.PERSONAL));

        assertThat(result.orderStatuses()).containsExactly(new OrderFilterOptionItem("2", "已发货"));
        assertThat(result.attributionStatuses()).containsExactly(
                new OrderFilterOptionItem("PARTIAL", "部分归因"),
                new OrderFilterOptionItem("FAILED", "同步/归因失败"));
        assertThat(result.unattributedReasons()).containsExactly(
                new OrderFilterOptionItem(AttributionService.REASON_NO_PICK_SOURCE, "订单未携带推广参数"),
                new OrderFilterOptionItem(AttributionService.REASON_COLONEL_MAPPING_NOT_FOUND, "原生团长订单未找到归因映射"),
                new OrderFilterOptionItem("自定义原因", "自定义原因"));
        assertThat(result.products()).containsExactly(
                new OrderFilterOptionItem("P-1", "商品一"),
                new OrderFilterOptionItem("P-2", "P-2"));
        assertThat(result.channels()).containsExactly(new OrderFilterOptionItem("渠道甲", "渠道甲"));
        assertThat(result.colonels()).containsExactly(new OrderFilterOptionItem("团长甲", "团长甲"));
    }

    @Test
    void getFilterOptions_shouldHandleNullPortLists() {
        when(port.listOrderStatusValues(any())).thenReturn(null);
        when(port.listAttributionStatusValues(any())).thenReturn(null);
        when(port.listUnattributedReasonValues(any())).thenReturn(null);
        when(port.listProductOptions(any())).thenReturn(null);
        when(port.listChannelOptions(any())).thenReturn(null);
        when(port.listColonelOptions(any())).thenReturn(null);

        OrderFilterOptionsQueryService service = new OrderFilterOptionsQueryService(port);
        OrderFilterOptionsResult result = service.getFilterOptions(null);

        assertThat(result.orderStatuses()).isEmpty();
        assertThat(result.attributionStatuses()).isEmpty();
        assertThat(result.unattributedReasons()).isEmpty();
        assertThat(result.products()).isEmpty();
        assertThat(result.channels()).isEmpty();
        assertThat(result.colonels()).isEmpty();
    }
}
