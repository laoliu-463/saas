package com.colonel.saas.domain.order.application.dto;

import java.util.List;

public record OrderFilterOptionsResult(
        List<OrderFilterOptionItem> orderStatuses,
        List<OrderFilterOptionItem> attributionStatuses,
        List<OrderFilterOptionItem> unattributedReasons,
        List<OrderFilterOptionItem> products,
        List<OrderFilterOptionItem> channels,
        List<OrderFilterOptionItem> colonels) {
}
