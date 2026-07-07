package com.colonel.saas.domain.order.application.port;

import com.colonel.saas.domain.order.application.dto.OrderFilterOptionItem;
import com.colonel.saas.domain.order.application.dto.OrderFilterOptionsQuery;

import java.util.List;

public interface OrderFilterOptionsPort {

    List<Object> listOrderStatusValues(OrderFilterOptionsQuery query);

    List<String> listAttributionStatusValues(OrderFilterOptionsQuery query);

    List<String> listUnattributedReasonValues(OrderFilterOptionsQuery query);

    List<OrderFilterOptionItem> listProductOptions(OrderFilterOptionsQuery query);

    List<OrderFilterOptionItem> listChannelOptions(OrderFilterOptionsQuery query);

    List<OrderFilterOptionItem> listColonelOptions(OrderFilterOptionsQuery query);
}
