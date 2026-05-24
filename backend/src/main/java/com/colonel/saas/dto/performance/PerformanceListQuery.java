package com.colonel.saas.dto.performance;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class PerformanceListQuery {
    private String orderId;
    private String productId;
    private String productName;
    private Long partnerId;
    private String partnerName;
    private String activityId;
    private UUID talentId;
    private UUID channelId;
    private UUID recruiterId;
    private String orderStatus;
    private String timeFilterType;
    private LocalDateTime timeStart;
    private LocalDateTime timeEnd;
    private String amountTrack;
    private long page = 1;
    private long pageSize = 20;
    private String sortBy;
    private String sortOrder;
}
